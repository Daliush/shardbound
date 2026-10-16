import { ActionView, GameView } from '../api/protocol';
import { DecisionGroups, locateIn, sourceOf } from '../game/decision-groups';

/** Both a pointer drop and the tap fallback resolve to existing action indexes. */
export function resolveGesture(view: GameView, source: string | null, target: string, attackIndex: number | null = null, interceptTarget: string | null = null): ActionView[] {
  if (view.decision?.kind === 'intercept') {
    return target === interceptTarget ? view.decision.actions.filter(action => action.type === 'intercept' && sourceOf(action) === source) : [];
  }
  const actions = (view.decision?.actions ?? []).filter(action => attackIndex === null || action.type !== 'attack' || action.attackIndex === attackIndex);
  const groups = new DecisionGroups(actions, locateIn(view));
  return target === 'board'
    ? groups.buttons(source).filter(action => sourceOf(action) === source && (action.type === 'play' || action.type === 'attack'))
    : groups.pick(source, target);
}
