import { MAIN_ACTIONS, aView } from '../testing/views';
import { DecisionGroups, locateIn } from './decision-groups';

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

  it('lets a choice of one card be clicked on that card', () => {
    const discard = new DecisionGroups(
      [
        { index: 0, type: 'choose_cards', label: 'Discard Spark Dart #3', cards: [3] },
        { index: 1, type: 'choose_cards', label: 'Discard Shardling #4', cards: [4] },
      ],
      (id) => `card:${id}`,
    );

    expect([...discard.targets(null)]).toEqual(['card:3', 'card:4']);
    expect(discard.pick(null, 'card:4').map((a) => a.index)).toEqual([1]);
    expect(discard.buttons(null)).toEqual([]);
  });

  it('keeps a choice of several cards or units as buttons', () => {
    const sacrifice = new DecisionGroups(
      [
        { index: 0, type: 'choose_cards', label: 'Sacrifice Cinderling #1, Shardling #2', cards: [1, 2] },
        { index: 1, type: 'choose_cards', label: 'Sacrifice Cinderling #1, Sprout #5', cards: [1, 5] },
      ],
      (id) => `unit:${id}`,
    );

    expect(sacrifice.targets(null).size).toBe(0);
    expect(sacrifice.buttons(null).map((a) => a.index)).toEqual([0, 1]);
  });

  it('offers a card that can be overcharged twice, both as buttons', () => {
    const lance = new DecisionGroups([
      { index: 0, type: 'play', label: 'Play Ember Lance (4 Shards)', card: 19, overcharge: false, targets: [] },
      {
        index: 1,
        type: 'play',
        label: 'Play Ember Lance overcharged (2 Shards, locks 2 next turn)',
        card: 19,
        overcharge: true,
        targets: [],
      },
      { index: 2, type: 'end_turn', label: 'End your turn' },
    ]);

    expect(lance.buttons('card:19').map((a) => a.index)).toEqual([0, 1, 2]);
  });

  it('lights up every unit of a Link pair, then offers the pairs of the clicked unit', () => {
    const link = new DecisionGroups(
      [
        [1, 61],
        [1, 36],
        [61, 36],
      ].map((pair, index) => ({
        index,
        type: 'play' as const,
        label: `Play Binding Thread (1 Shard) on #${pair[0]}, #${pair[1]}`,
        card: 7,
        overcharge: false,
        targets: pair.map((id) => ({ kind: 'unit' as const, id })),
      })),
    );

    expect([...link.targets('card:7')]).toEqual(['unit:1', 'unit:61', 'unit:36']);
    expect(link.pick('card:7', 'unit:61').map((a) => a.index)).toEqual([0, 2]);
  });

  it('shows the order of two echoes as buttons', () => {
    const order = new DecisionGroups([
      { index: 0, type: 'choose_order', label: 'Replay Fang first, then Frost Breath', order: [0, 1] },
      { index: 1, type: 'choose_order', label: 'Replay Frost Breath first, then Fang', order: [1, 0] },
    ]);

    expect(order.sources().size).toBe(0);
    expect(order.targets(null).size).toBe(0);
    expect(order.buttons(null).map((a) => a.index)).toEqual([0, 1]);
  });

  it('finds a card of the hand or a unit of either board from its id', () => {
    const locate = locateIn(aView(1));

    expect([3, 1, 61, 99].map(locate)).toEqual(['card:3', 'unit:1', 'unit:61', null]);
  });
});
