import { aView } from '../testing/views';
import { interceptable, pendingCombat, project, projectAttack } from './combat';
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

describe('attack projections', () => {
  const sprout = () => aView(1).opponent!.units[0];
  it('writes the defense left, or lethal', () => {
    expect(project(1, {...sprout(), defense: 5})).toEqual({amount: 1, before: 5, after: 4, outcome: 'hit'});
    expect(project(3, sprout())).toEqual({amount: 3, before: 2, after: 0, outcome: 'lethal'});
    expect(project(30, {hp: 20})).toMatchObject({after: 0, outcome: 'lethal'});
  });
  it('says when Anchor or Link changes the outcome, and when the attack deals no damage', () => {
    expect(project(3, {...sprout(), anchorProtected: true}).outcome).toBe('anchored');
    expect(project(3, {...sprout(), linkedTo: 36})).toMatchObject({after: null, outcome: 'linked'});
    expect(project(null, sprout()).outcome).toBe('effect');
  });
  it('projects the selected attack on each target, with the engine damage', () => {
    const state = aView(1);
    const result = projectAttack(state, 'unit:1', 0, ['unit:61', 'player:opponent']);
    expect(result['unit:61']).toMatchObject({amount: 3, outcome: 'lethal'});
    expect(result['player:opponent']).toMatchObject({before: 50, after: 47});
    expect(projectAttack(state, 'unit:1', null, ['unit:61'])).toEqual({});
  });
  it('knows whether another unit can still intercept', () => {
    const state = aView(1);
    expect(interceptable(state, 'unit:61')).toBe(true);
    state.opponent!.units[1].hasInterceptedThisTurn = true;
    expect(interceptable(state, 'unit:61')).toBe(false);
  });
});
