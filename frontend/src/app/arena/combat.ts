import { AttackView, EventView, GameView, UnitView } from '../api/protocol';
import { readCard } from './stage/choreographer';

export interface PendingCombat { attacker: string; target: string; attackerCard: string; targetCard: string; attackIndex: number; }

/** Keep the declared attack visible throughout the server's interception decision, including after sync. */
export function pendingCombat(view: GameView | null, history: EventView[]): PendingCombat | null {
  if (!view || (view.decision?.kind !== 'intercept' && view.waitingFor?.kind !== 'intercept')) return null;
  const event = [...history].reverse().find(event => event.type === 'attack_declared');
  const attacker = readCard(event?.['attacker']);
  const target = event?.['target'] as { kind?: string; id?: number } | undefined;
  if (!attacker || target?.kind !== 'unit' || typeof target.id !== 'number') return null;
  const unit = [...view.you.units, ...(view.opponent?.units ?? [])].find(unit => unit.id === target.id);
  if (!unit) return null;
  return { attacker: 'unit:' + attacker.id, target: 'unit:' + unit.id, attackerCard: attacker.card, targetCard: unit.card, attackIndex: Number(event?.['attackIndex'] ?? 0) };
}

/**
 * What an attack would do to one target, read from the engine's numbers: the attack's damage (modifiers and auras
 * included) against the target's defense or HP. A linked unit shares the damage (11.5.2), an anchored one is doomed
 * instead of destroyed (11.3.4); the defender may still intercept (7.5).
 */
export interface Projection { amount: number | null; before: number; after: number | null; outcome: 'hit' | 'lethal' | 'anchored' | 'linked' | 'effect'; }

export function project(damage: number | null, target: UnitView | { hp: number }): Projection {
  const before = 'hp' in target ? target.hp : target.defense;
  if (damage === null) return { amount: null, before, after: null, outcome: 'effect' };
  if (!('hp' in target) && target.linkedTo !== null) return { amount: damage, before, after: null, outcome: 'linked' };
  const after = Math.max(0, before - damage);
  if (after > 0) return { amount: damage, before, after, outcome: 'hit' };
  return { amount: damage, before, after, outcome: !('hp' in target) && target.anchorProtected ? 'anchored' : 'lethal' };
}

export function unitOf(view: GameView, key: string | null): UnitView | undefined {
  return [...view.you.units, ...(view.opponent?.units ?? [])].find(unit => 'unit:' + unit.id === key);
}

export function attackOf(view: GameView, attacker: string | null, attackIndex: number | null): AttackView | undefined {
  return attackIndex === null ? undefined : unitOf(view, attacker)?.attacks.find(attack => attack.index === attackIndex);
}

/** The projection of one attack on each of the given targets ("unit:61", "player:opponent"). */
export function projectAttack(view: GameView, attacker: string | null, attackIndex: number | null, targets: Iterable<string>): Record<string, Projection> {
  const attack = attackOf(view, attacker, attackIndex);
  const result: Record<string, Projection> = {};
  if (!attack) return result;
  for (const key of targets) {
    const side = key.startsWith('player:') ? key.slice(7) as 'you' | 'opponent' : null;
    const player = side ? view[side] : null, unit = side ? undefined : unitOf(view, key);
    if (player) result[key] = project(attack.damage, { hp: player.hp });
    else if (unit) result[key] = project(attack.damage, unit);
  }
  return result;
}

/** Whether the defender may still redirect an attack on {@code target}: another unit of theirs can intercept (7.5). */
export function interceptable(view: GameView, target: string): boolean {
  const unit = unitOf(view, target);
  if (!unit) return false;
  const allies = unit.controller === 'you' ? view.you.units : view.opponent?.units ?? [];
  return allies.some(ally => ally.id !== unit.id && !ally.frozen && !ally.hasInterceptedThisTurn);
}
