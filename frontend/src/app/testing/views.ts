import { ActionView, GameView } from '../api/protocol';

/** A game view as the server sends it, with only what a test sets. */
export function aView(version: number, changes: Partial<GameView> = {}): GameView {
  return {
    gameId: 'g',
    version,
    status: 'in_progress',
    turn: 5,
    activePlayer: 'you',
    yourTurn: true,
    you: {
      faction: 'ember',
      hp: 50,
      maxHp: 50,
      shards: 3,
      maxShards: 3,
      lockedNextTurn: 0,
      fatigue: 0,
      deckCount: 23,
      hand: [{ id: 3, card: 'ember.spark-dart', cost: 1, fractureStep: null }],
      mulliganDecided: true,
      units: [unit(1, 'ember.cinderling', 'you')],
      relics: [],
      graveyard: [],
    },
    opponent: {
      faction: 'root',
      hp: 50,
      maxHp: 50,
      shards: 0,
      maxShards: 2,
      lockedNextTurn: 0,
      fatigue: 0,
      deckCount: 23,
      handCount: 5,
      units: [unit(61, 'root.sprout', 'opponent'), unit(36, 'root.root-sentinel', 'opponent')],
      relics: [],
      graveyard: [],
    },
    decision: {
      id: `d-${version + 1}`,
      kind: 'main',
      prompt: 'Your turn: play a card, attack, or end your turn.',
      actions: MAIN_ACTIONS,
    },
    waitingFor: null,
    result: null,
    ...changes,
  };
}

/** The turn 5 decision of the examples doc, cut down. */
export const MAIN_ACTIONS: ActionView[] = [
  play(0, 3, false, [61], 'Play Spark Dart (1 Shard) on Sprout #61'),
  play(1, 3, false, [36], 'Play Spark Dart (1 Shard) on Root Sentinel #36'),
  play(2, 19, true, [], 'Play Ember Lance overcharged (2 Shards, locks 2 next turn)'),
  play(3, 15, false, [], 'Play Ash Warden (3 Shards)'),
  attack(4, 1, 61, 'Cinderling #1 attacks Sprout #61 with Flick (1 Shard)'),
  attack(5, 1, 36, 'Cinderling #1 attacks Root Sentinel #36 with Flick (1 Shard)'),
  { index: 6, type: 'end_turn', label: 'End your turn' },
];

function play(index: number, card: number, overcharge: boolean, targets: number[], label: string): ActionView {
  return {
    index,
    type: 'play',
    label,
    card,
    overcharge,
    targets: targets.map((id) => ({ kind: 'unit', id })),
    sacrificed: [],
  };
}

function attack(index: number, attacker: number, target: number, label: string): ActionView {
  return { index, type: 'attack', label, attacker, attackIndex: 0, targets: [{ kind: 'unit', id: target }] };
}

function unit(id: number, card: string, controller: 'you' | 'opponent') {
  return {
    id,
    card,
    controller,
    token: false,
    defense: 2,
    maxDefense: 2,
    attacks: [{ index: 0, name: 'Flick', cost: 1, damage: 3, hasTarget: true, echo: null }],
    arrivedThisTurn: false,
    hasAttackedThisTurn: false,
    hasInterceptedThisTurn: false,
    frozen: false,
    anchorProtected: false,
    doomed: false,
    linkedTo: null,
    modifiers: [],
  };
}
