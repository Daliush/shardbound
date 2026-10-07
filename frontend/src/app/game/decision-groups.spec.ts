import { MAIN_ACTIONS } from '../testing/views';
import { DecisionGroups } from './decision-groups';

describe('DecisionGroups', () => {
  const groups = new DecisionGroups(MAIN_ACTIONS);

  it('lists the cards and units that can act', () => {
    expect([...groups.sources()]).toEqual(['card:3', 'card:19', 'card:15', 'unit:1']);
  });

  it('lights up the targets of the selected card, and keeps End turn as a button', () => {
    expect([...groups.targets('card:3')]).toEqual(['unit:61', 'unit:36']);
    expect(groups.buttons(null).map((a) => a.label)).toEqual(['End your turn']);
  });

  it('picks the one action that the clicked target leaves', () => {
    expect(groups.pick('unit:1', 'unit:36').map((a) => a.index)).toEqual([5]);
  });

  it('shows the actions of a selected card without a target as buttons', () => {
    expect(groups.buttons('card:19').map((a) => a.index)).toEqual([2, 6]);
    expect(groups.targets('card:19').size).toBe(0);
  });

  it('lets a choice of target be clicked directly, without a source', () => {
    const choose = new DecisionGroups([
      { index: 0, type: 'choose_target', label: 'Choose Sprout #61', targets: [{ kind: 'unit', id: 61 }] },
      { index: 1, type: 'choose_target', label: 'Choose your opponent', targets: [{ kind: 'player', player: 'opponent' }] },
    ]);

    expect([...choose.targets(null)]).toEqual(['unit:61', 'player:opponent']);
    expect(choose.pick(null, 'player:opponent').map((a) => a.index)).toEqual([1]);
  });
});
