import { Inspect } from './inspect';

describe('hover card preview', () => {
  afterEach(() => vi.useRealTimers());
  it('cancels a pending preview when dragging starts and does not close another card’s preview', () => {
    vi.useFakeTimers();
    const inspect = new Inspect(), first = {}, second = {};
    inspect.previewAfter(first, {id: 'ember.spark-dart'});
    inspect.clearPreview();
    vi.advanceTimersByTime(500);
    expect(inspect.preview()).toBeNull();
    inspect.previewAfter(second, {id: 'root.sprout'});
    inspect.clearPreview(first);
    vi.advanceTimersByTime(500);
    expect(inspect.preview()?.id).toBe('root.sprout');
    inspect.open({id: 'ember.ash-warden'});
    expect(inspect.preview()).toBeNull();
    expect(inspect.card()?.id).toBe('ember.ash-warden');
  });
});
