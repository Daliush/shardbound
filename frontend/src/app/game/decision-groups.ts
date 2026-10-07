import { ActionView, TargetView } from '../api/protocol';

/**
 * What can be clicked on the board: "card:3" for a card in hand, "unit:61", "relic:5", "player:opponent".
 * Instance ids are unique in a game, so a key names one element.
 */
export type ElementKey = string;

/** How an element shows the decision: a source to select, the selected one, or a target to click. */
export type Mark = 'selectable' | 'selected' | 'targetable' | null;

/**
 * A decision's actions grouped by what the player clicks (spec §14): the card or unit that acts (its source),
 * then one of its targets. It only filters the decision's list: the client never builds an action.
 */
export class DecisionGroups {
  constructor(private readonly actions: ActionView[]) {}

  /** The cards and units that are the source of at least one action. */
  sources(): Set<ElementKey> {
    return new Set(this.actions.map(sourceOf).filter((key): key is ElementKey => key !== null));
  }

  /** What a click can target now: the selected source's targets, or those of actions without a source. */
  targets(selected: ElementKey | null): Set<ElementKey> {
    return new Set(this.of(selected).flatMap((action) => onBoardTargets(action).map(targetKey)));
  }

  /** Actions shown as buttons: those with nothing to click on the board, plus the selected source's own. */
  buttons(selected: ElementKey | null): ActionView[] {
    const plain = this.actions.filter((action) => sourceOf(action) === null && !hasBoardTarget(action));
    const ofSelection = selected === null ? [] : this.of(selected).filter((action) => !hasBoardTarget(action));
    return [...ofSelection, ...plain];
  }

  /** The actions a click on {@code target} picks, for the selection. */
  pick(selected: ElementKey | null, target: ElementKey): ActionView[] {
    return this.of(selected).filter((action) => onBoardTargets(action).map(targetKey).includes(target));
  }

  private of(selected: ElementKey | null): ActionView[] {
    return this.actions.filter((action) => sourceOf(action) === selected);
  }
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

function hasBoardTarget(action: ActionView): boolean {
  return onBoardTargets(action).length > 0;
}
