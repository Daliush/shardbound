import { CardRef, EventView, GameView, Side } from '../../api/protocol';

export type BeatKind = 'opening' | 'draw' | 'mulligan' | 'turn' | 'shards' | 'play' | 'arrive' | 'summon' | 'attack' | 'intercept' | 'damage' | 'heal' | 'shatter' | 'sacrifice' | 'vanish' | 'cast' | 'modify' | 'freeze' | 'thaw' | 'echo' | 'fracture' | 'anchor' | 'doom' | 'link' | 'unlink' | 'share' | 'return' | 'recall' | 'discard' | 'ability' | 'fizzle' | 'end' | 'victory' | 'log';
/** Kept exhaustive against the Java event records by scripts/event-coverage.test.mjs. */
export const EVENT_BEATS: Record<string, BeatKind> = {
  game_started: 'opening',
  card_drawn: 'draw',
  hand_kept: 'mulligan',
  mulligan_taken: 'mulligan',
  turn_started: 'turn',
  shards_refilled: 'shards',
  anchor_protection_ended: 'anchor',
  hp_lost: 'damage',
  card_discarded: 'discard',
  turn_ended: 'end',
  game_ended: 'victory',
  card_played: 'play',
  fracture_advanced: 'fracture',
  shards_locked: 'shards',
  unit_arrived: 'arrive',
  relic_arrived: 'arrive',
  spell_resolved: 'cast',
  token_summoned: 'summon',
  summon_failed: 'fizzle',
  sacrifice_failed: 'fizzle',
  ability_triggered: 'ability',
  echo_triggered: 'echo',
  attack_declared: 'attack',
  attack_intercepted: 'intercept',
  intercept_declined: 'end',
  attack_cancelled: 'fizzle',
  unit_damaged: 'damage',
  damage_shared: 'share',
  player_damaged: 'damage',
  unit_healed: 'heal',
  player_healed: 'heal',
  unit_destroyed: 'shatter',
  relic_destroyed: 'shatter',
  token_vanished: 'vanish',
  modified: 'modify',
  modifier_expired: 'modify',
  shards_gained: 'shards',
  aura_applied: 'modify',
  aura_removed: 'modify',
  recalled: 'recall',
  recall_failed: 'fizzle',
  linked: 'link',
  link_broken: 'unlink',
  frozen: 'freeze',
  unit_thawed: 'thaw',
  returned_to_hand: 'return',
  sent_to_graveyard_hand_full: 'discard',
  unit_sacrificed: 'sacrifice',
  anchor_prevented: 'anchor',
  unit_doomed: 'doom',
  doom_lifted: 'doom',
};
export interface Beat {
  kind: BeatKind;
  event: EventView;
  key: string | null;
  target: string | null;
  card: CardRef | null;
  side: Side | null;
  duration: number;
  counters: { key: string; value: number }[];
}
export function readCard(value: unknown): CardRef | null {
  if (!value || typeof value !== 'object') return null;
  const ref = value as Record<string, unknown>;
  return typeof ref['id'] === 'number' && typeof ref['card'] === 'string' ? { id: ref['id'], card: ref['card'] } : null;
}
export function locateCard(ref: CardRef, view: GameView): string {
  if ([...view.you.units, ...(view.opponent?.units ?? [])].some(unit => unit.id === ref.id)) return 'unit:' + ref.id;
  if ([...view.you.relics, ...(view.opponent?.relics ?? [])].some(relic => relic.id === ref.id)) return 'relic:' + ref.id;
  if (view.you.hand.some(card => card.id === ref.id)) return 'card:' + ref.id;
  if (view.you.graveyard.some(card => card.id === ref.id)) return 'graveyard:you';
  if (view.opponent?.graveyard.some(card => card.id === ref.id)) return 'graveyard:opponent';
  return 'card:' + ref.id;
}
function sideOf(event: EventView, ref: CardRef | null, old: GameView, next: GameView): Side | null {
  for (const field of ['player', 'controller', 'defender', 'firstPlayer']) if (event[field] === 'you' || event[field] === 'opponent') return event[field] as Side;
  if (ref) {
    for (const view of [old, next]) {
      for (const side of ['you', 'opponent'] as const) {
        const player = view[side];
        if (player && [...player.units, ...player.relics, ...player.graveyard, ...(side === 'you' ? view.you.hand : [])].some(card => card.id === ref.id)) return side;
      }
    }
  }
  return null;
}
function targetOf(value: unknown): string | null {
  if (!value || typeof value !== 'object') return null;
  const target = value as Record<string, unknown>;
  if (target['kind'] === 'player') return 'player:' + target['player'];
  return typeof target['id'] === 'number' ? 'unit:' + target['id'] : null;
}
export function choreograph(old: GameView, events: EventView[], next: GameView): Beat[] {
  const beats = events.map(event => {
    const kind = EVENT_BEATS[event.type] ?? 'log';
    const card = readCard(event['unit'] ?? event['relic'] ?? event['card'] ?? event['attacker'] ?? event['interceptor'] ?? event['source'] ?? event['spell'] ?? event['first'] ?? event['from'] ?? event['left']);
    const side = sideOf(event, card, old, next);
    let key = card ? locateCard(card, old) : side ? 'player:' + side : null;
    if (kind === 'shards') key = 'shards:' + side;
    if (kind === 'draw') key = 'deck:' + side;
    const target = targetOf(event['target'] ?? event['originalTarget'] ?? event['second'] ?? event['to'] ?? event['partner']);
    const duration = ({ shatter: 650, victory: 1100, play: side === 'opponent' ? 900 : 480, attack: 480, intercept: 650, draw: 300, turn: 760, echo: 550, cast: 480, damage: 440, heal: 380, arrive: 360, summon: 360, shards: 200, end: 200, log: 80 } as Partial<Record<BeatKind, number>>)[kind] ?? 260;
    const counters: Beat['counters'] = [];
    if (side && next[side]) {
      if (['damage', 'heal', 'victory'].includes(kind) && !card) counters.push({ key: 'hp:' + side, value: next[side]!.hp });
      if (['shards', 'play', 'attack', 'end'].includes(kind)) counters.push({ key: 'shards:' + side, value: next[side]!.shards });
      if (kind === 'draw') counters.push({ key: 'deck:' + side, value: next[side]!.deckCount });
    }
    if (card && ['damage', 'heal', 'modify', 'doom'].includes(kind)) {
      const unit = [...next.you.units, ...(next.opponent?.units ?? [])].find(unit => unit.id === card.id);
      if (unit) counters.push({ key: 'defense:' + card.id, value: unit.defense });
    }
    return { kind, event, card, side, key, target, duration, counters };
  });
  // The final event touching a counter owns its update; never subtract an event amount.
  for (let index = 0; index < beats.length; index++) {
    beats[index].counters = beats[index].counters.filter(counter => !beats.slice(index + 1).some(beat => beat.counters.some(later => later.key === counter.key)));
  }
  return beats.length ? beats : [{ kind: 'log', event: { type: 'pause', text: '', rules: [] }, key: null, target: null, card: null, side: null, duration: 90, counters: [] }];
}
