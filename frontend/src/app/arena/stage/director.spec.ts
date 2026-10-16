import { Director, StagePlayer } from './director';
import { aView } from '../../testing/views';

describe('Director', () => {
  let finish: (() => void)[];
  let stage: StagePlayer;
  beforeEach(() => {
    finish = [];
    stage = { capture: vi.fn(() => 'old positions'), play: vi.fn(() => new Promise<void>(resolve => finish.push(resolve))), reconcile: vi.fn(async () => {}), cancel: vi.fn() };
  });
  it('keeps the old board through the beats, commits, then reconciles before accepting input', async () => {
    const director = new Director(stage);
    director.reset(aView(1), []);
    director.enqueue(aView(2), []);
    director.enqueue(aView(3), []);
    expect(director.busy()).toBe(true);
    expect(director.shownView()?.version).toBe(1);
    finish.shift()!();
    await vi.waitFor(() => expect(director.shownView()?.version).toBe(2));
    expect(stage.reconcile).toHaveBeenCalledWith('old positions', expect.any(AbortSignal));
    expect(director.busy()).toBe(true);
    finish.shift()!();
    await vi.waitFor(() => expect(director.busy()).toBe(false));
    expect(director.shownView()?.version).toBe(3);
  });
  it('skip snaps to the latest view and late completions cannot overwrite it', async () => {
    const director = new Director(stage);
    director.reset(aView(1), []);
    director.enqueue(aView(2), []);
    director.enqueue(aView(3), []);
    director.skip();
    finish.shift()!();
    await Promise.resolve();
    expect(director.shownView()?.version).toBe(3);
    expect(director.busy()).toBe(false);
  });
  it('state and sync cancel old animations and replace the history', async () => {
    const director = new Director(stage);
    director.reset(aView(1), []);
    director.enqueue(aView(2), []);
    director.reset(aView(20), [{ type: 'game_started', text: 'You play first.', rules: ['5.1.1'] }]);
    finish.shift()!();
    await Promise.resolve();
    expect(director.shownView()?.version).toBe(20);
    expect(director.history()).toHaveLength(1);
    expect(stage.cancel).toHaveBeenCalledTimes(2);
  });
});
