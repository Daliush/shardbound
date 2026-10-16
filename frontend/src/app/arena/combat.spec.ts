import { aView } from '../testing/views';
import { pendingCombat } from './combat';
import { resolveGesture } from './gestures';
import { EventView } from '../api/protocol';

describe('interception on the field', () => {
  const history: EventView[] = [{ type: 'attack_declared', rules: [], text: 'Attack', attacker: {id: 61, card: 'root.sprout'}, attackIndex: 0, target: {kind: 'unit', id: 1} }];
  function view() {
    const state = aView(4);
    return {...state, you: {...state.you, units: [...state.you.units, {...state.you.units[0], id: 2}]}, decision: { id: 'intercept-5', kind: 'intercept' as const, prompt: 'Intercept?', actions: [{index: 7, type: 'intercept' as const, interceptor: 2, label: 'Intercept with unit 2'}, {index: 9, type: 'decline_intercept' as const, label: 'Decline'}] }};
  }
  it('recovers the attacker and defender from history after a reconnect', () => {
    expect(pendingCombat(view(), history)).toMatchObject({ attacker: 'unit:61', target: 'unit:1' });
    expect(pendingCombat(aView(4), history)).toBeNull();
    expect(pendingCombat(view(), [])).toBeNull();
  });
  it('only accepts a legal interceptor dropped on the actual defender', () => {
    expect(resolveGesture(view(), 'unit:2', 'unit:1', null, 'unit:1').map(action => action.index)).toEqual([7]);
    expect(resolveGesture(view(), 'unit:61', 'unit:1', null, 'unit:1')).toEqual([]);
    expect(resolveGesture(view(), 'unit:2', 'unit:61', null, 'unit:1')).toEqual([]);
    expect(resolveGesture(view(), 'unit:2', 'board', null, 'unit:1')).toEqual([]);
    expect(resolveGesture(view(), 'unit:2', 'unit:1')).toEqual([]);
  });
  it('also exposes the attack while waiting for the opponent to intercept', () => {
    const state = view();
    expect(pendingCombat({...state, decision: null, waitingFor: {player: 'opponent', kind: 'intercept'}}, history)?.target).toBe('unit:1');
  });
});
