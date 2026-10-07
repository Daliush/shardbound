import { ActionView, GameView, TargetView } from '../api/protocol';

/**
 * What can be clicked on the board: "card:3" for a card in hand, "unit:61", "relic:5", "player:opponent".
 * Instance ids are unique in a game, so a key names one element.
 */
export type ElementKey = string;

/** How an element shows the decision: a source to select, the selected one, or a target to click. */
export type Mark = 'selectable' | 'selected' | 'targetable' | null;

/** Where a card known by its instance id is drawn: "card:3" in your hand, "unit:61" on a board. */
export type Locate = (id: number) => ElementKey | null;

/**
 * A decision's actions grouped by what the player clicks (spec §14): the card or unit that acts (its source),
 * then one of its targets. A choice of a single card or unit is clicked like a target; a combination of several
 * stays a button. It only filters the decision's list: the client never builds an action.
 */
export class DecisionGroups {
  constructor(
    private readonly actions: ActionView[],
    private readonly locate: Locate = () => null,
  ) {}

  /** The cards and units that are the source of at least one action. */
  sources(): Set<ElementKey> {
    return new Set(this.actions.map(sourceOf).filter((key): key is ElementKey => key !== null));
  }

  /** What a click can target now: the selected source's targets, or those of actions without a source. */
  targets(selected: ElementKey | null): Set<ElementKey> {
    return new Set(this.of(selected).flatMap((action) => this.clickable(action)));
  }

  /** Actions shown as buttons: those with nothing to click on the board, plus the selected source's own. */
  buttons(selected: ElementKey | null): ActionView[] {
    const plain = this.actions.filter(
      (action) => sourceOf(action) === null && this.clickable(action).length === 0,
    );
    const ofSelection =
      selected === null
        ? []
        : this.of(selected).filter((action) => this.clickable(action).length === 0);
    return [...ofSelection, ...plain];
  }

  /** The actions a click on {@code target} picks, for the selection. */
  pick(selected: ElementKey | null, target: ElementKey): ActionView[] {
    return this.of(selected).filter((action) => this.clickable(action).includes(target));
  }

  private of(selected: ElementKey | null): ActionView[] {
    return this.actions.filter((action) => sourceOf(action) === selected);
  }

  /** What a click on the board picks the action by: its targets, or the one card or unit a choice names. */
  private clickable(action: ActionView): ElementKey[] {
    if (action.type === 'choose_cards') {
      const only = action.cards?.length === 1 ? this.locate(action.cards[0]) : null;
      return only === null ? [] : [only];
    }
    return onBoardTargets(action).map(targetKey);
  }
}

/** Instance ids are unique in a game, so an id says whether it is a card of your hand or a unit. */
export function locateIn(view: GameView | null): Locate {
  return (id) => {
    if (view?.you.hand.some((card) => card.id === id)) {
      return `card:${id}`;
    }
    const units = [...(view?.you.units ?? []), ...(view?.opponent?.units ?? [])];
    return units.some((unit) => unit.id === id) ? `unit:${id}` : null;
  };
}

export function sourceOf(action: ActionView): ElementKey | null {
  switch (action.type) {
    case 'play':
      return `card:${action.card}`;
    case 'attack':
      return `unit:${action.attacker}`;
    case 'intercept':
      return `unit:${action.interceptor}`;
    default:
      return null;
  }
}

export function targetKey(target: TargetView): ElementKey {
  return target.kind === 'player' ? `player:${target.player}` : `${target.kind}:${target.id}`;
}

/** Graveyard cards are not drawn on the board: an action that targets one is chosen by its button. */
function onBoardTargets(action: ActionView): TargetView[] {
  return (action.targets ?? []).filter((target) => target.kind !== 'graveyard_card');
}
