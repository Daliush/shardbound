import { aView } from '../../testing/views';
import { choreograph, EVENT_BEATS } from './choreographer';

describe('Choreographer', () => {
  it('reads the new defense from the view, not from damage dealt', () => {
    const old = aView(1);
    const next = aView(2);
    next.opponent!.units[0].defense = 0;
    const [beat] = choreograph(old, [{ type: 'unit_damaged', unit: { id: 61, card: 'root.sprout' }, amount: 3, rules: ['8.1'], text: 'Sprout takes 3 damage.' }], next);
    expect(beat.event['amount']).toBe(3);
    expect(beat.counters).toEqual([{ key: 'defense:61', value: 0 }]);
  });
  it('preserves hidden draws and has a harmless fallback for unknown events', () => {
    const beats = choreograph(aView(1), [{ type: 'card_drawn', player: 'opponent', card: null, rules: ['5.2'], text: 'Your opponent draws a card.' }, { type: 'future_event', rules: [], text: 'Future event' }], aView(2));
    expect(beats[0].card).toBeNull();
    expect(beats[0].key).toBe('deck:opponent');
    expect(beats[1].kind).toBe('log');
  });
  it('updates a counter only on its final beat', () => {
    const event = { type: 'player_damaged', player: 'you', amount: 2, rules: ['8.1'], text: 'You take 2 damage.' };
    const beats = choreograph(aView(1), [event, event], aView(2));
    expect(beats[0].counters).toEqual([]);
    expect(beats[1].counters).toEqual([{ key: 'hp:you', value: 50 }]);
  });
  it('maps all current events to intentional beats', () => {
    expect(Object.keys(EVENT_BEATS)).toHaveLength(51);
    for (const type of Object.keys(EVENT_BEATS)) expect(choreograph(aView(1), [{ type, text: type, rules: [] }], aView(2))[0].kind).not.toBe('log');
  });
});
