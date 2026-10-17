import { EventView, GameView } from '../api/protocol';
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
