// The server's contract, as typed in specs/phase-2-engine.md §12 and §13.3.

export type Side = 'you' | 'opponent';

export type DecisionKind =
  | 'mulligan'
  | 'main'
  | 'intercept'
  | 'choose_target'
  | 'choose_cards'
  | 'choose_order';

export interface GameView {
  gameId: string;
  version: number;
  status: 'waiting_for_opponent' | 'in_progress' | 'finished';
  turn: number;
  activePlayer: Side | null;
  yourTurn: boolean;
  you: SelfView;
  opponent: OpponentView | null;
  decision: DecisionView | null;
  waitingFor: { player: Side; kind: DecisionKind } | null;
  result: { outcome: 'win' | 'loss' | 'draw'; reason: 'hp' | 'double_ko' | 'turn_limit' } | null;
}

export interface PlayerBase {
  faction: string;
  hp: number;
  maxHp: number;
  shards: number;
  maxShards: number;
  lockedNextTurn: number;
  fatigue: number;
  deckCount: number;
  units: UnitView[];
  relics: RelicView[];
  graveyard: CardRef[];
}

export interface SelfView extends PlayerBase {
  hand: HandCardView[];
  mulliganDecided: boolean;
}

export interface OpponentView extends PlayerBase {
  handCount: number;
}

export interface CardRef {
  id: number;
  card: string;
}

export interface HandCardView extends CardRef {
  cost: number;
  fractureStep: number | null;
}

export interface RelicView extends CardRef {
  controller: Side;
}

export interface AttackView {
  index: number;
  name: string | null;
  cost: number;
  damage: number | null;
  hasTarget: boolean;
  echo: number | null;
}

export interface UnitView extends CardRef {
  controller: Side;
  token: boolean;
  defense: number;
  maxDefense: number;
  attacks: AttackView[];
  arrivedThisTurn: boolean;
  hasAttackedThisTurn: boolean;
  hasInterceptedThisTurn: boolean;
  frozen: boolean;
  anchorProtected: boolean;
  doomed: boolean;
  linkedTo: number | null;
  modifiers: { attackDamage: number; defense: number; duration: 'permanent' | 'end_of_turn' }[];
}

export interface DecisionView {
  id: string;
  kind: DecisionKind;
  prompt: string;
  actions: ActionView[];
}

export interface ActionView {
  index: number;
  label: string;
  type:
    | 'keep_hand'
    | 'mulligan'
    | 'play'
    | 'attack'
    | 'intercept'
    | 'decline_intercept'
    | 'choose_target'
    | 'choose_cards'
    | 'choose_order'
    | 'end_turn';
  card?: number;
  overcharge?: boolean;
  targets?: TargetView[];
  sacrificed?: number[];
  attacker?: number;
  attackIndex?: number;
  interceptor?: number;
  cards?: number[];
  order?: number[];
}

export interface TargetView {
  kind: 'unit' | 'relic' | 'player' | 'graveyard_card';
  id?: number;
  player?: Side;
}

export interface EventView {
  type: string;
  rules: string[];
  text: string;
  [field: string]: unknown;
}

export type ServerMessage =
  | { type: 'state'; view: GameView; history: EventView[] }
  | { type: 'update'; view: GameView; events: EventView[] }
  | { type: 'rejected'; requestId: string | null; reason: string; message: string };

export type ClientMessage =
  | { type: 'act'; requestId: string; decisionId: string; action: number }
  | { type: 'sync' };

// REST

export interface Card {
  id: string;
  name: string;
  faction: string;
  type: 'unit' | 'spell' | 'relic';
  cost?: number;
  defense?: number;
  token: boolean;
  keywords: string[];
  flavor?: string;
  fracture?: { step: number; cost: number }[];
}

export interface Deck {
  id: string;
  name: string;
  description?: string;
  faction: string;
  cards: { card: string; count: number }[];
}

export type Opponent = { type: 'bot'; bot: string; deck: string } | { type: 'human' };

export interface SeatAccess {
  gameId: string;
  playerToken: string;
  joinCode?: string;
  websocketPath: string;
}
