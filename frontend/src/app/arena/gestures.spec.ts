import { aView } from '../testing/views';
import { DecisionGroups, locateIn } from '../game/decision-groups';
import { resolveGesture } from './gestures';

describe('decision gestures', () => {
  it('drag and tap pick the same existing target action', () => {
    const view = aView(1), groups = new DecisionGroups(view.decision!.actions, locateIn(view));
    expect(resolveGesture(view, 'card:3', 'unit:61')).toEqual(groups.pick('card:3', 'unit:61'));
    expect(resolveGesture(view, 'unit:1', 'unit:36')[0].index).toBe(5);
  });
  it('rejects illegal drops, and never silently chooses between candidates', () => {
    expect(resolveGesture(aView(1), 'card:3', 'board')).toEqual([]);
    expect(resolveGesture(aView(1), 'unit:1', 'unit:999')).toEqual([]);
    const view = aView(1);
    view.decision!.actions = [
      { index: 4, label: 'Play', type: 'play', card: 2, targets: [] },
      { index: 9, label: 'Play overcharged', type: 'play', card: 2, targets: [], overcharge: true },
    ];
    expect(resolveGesture(view, 'card:2', 'board').map(action => action.index)).toEqual([4, 9]);
  });
});
