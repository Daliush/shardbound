import { ActionView } from '../api/protocol';
import { matchChoice } from './choice-editor';
describe('choice drafts', () => {
  it('submits only a server-offered combination, independent of selection order', () => {
    const action: ActionView = {index:17,type:'choose_cards',label:'Discard both cards',cards:[4,8]};
    expect(matchChoice([action],[8,4],false)).toBe(action);
    expect(matchChoice([action],[4],false)).toBeUndefined();
    expect(matchChoice([action],[4,9],false)).toBeUndefined();
  });
  it('preserves the offered order and action index', () => {
    const actions: ActionView[] = [{index:3,type:'choose_order',label:'Replay Fang first, then Breath',order:[0,1]},{index:8,type:'choose_order',label:'Replay Breath first, then Fang',order:[1,0]}];
    expect(matchChoice(actions,[1,0],true)?.index).toBe(8);
    expect(matchChoice(actions,[0,0],true)).toBeUndefined();
  });
});
