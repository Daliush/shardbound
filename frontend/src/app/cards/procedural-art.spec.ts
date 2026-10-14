import { drawPlaceholder } from './procedural-art';

describe('procedural card art', () => {
  const palette = { light: 'white', mid: 'orange', dark: 'red', ink: 'black' };
  it('keeps each card stable and distinct without requesting an absent image', () => {
    const first = drawPlaceholder('ember.ash-warden', palette);
    expect(first).toBe(drawPlaceholder('ember.ash-warden', palette));
    expect(first).not.toBe(drawPlaceholder('ember.cinderling', palette));
    expect(first).toContain('viewBox="0 0 800 600"');
    expect(first).not.toContain('undefined');
  });
});
