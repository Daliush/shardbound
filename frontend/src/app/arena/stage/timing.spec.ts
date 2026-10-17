import { playbackScale } from './timing';

describe('animation pacing', () => {
  it('triples every former speed and provides an extra slow mode', () => {
    expect(450 * playbackScale(1)).toBe(1350);
    expect(450 * playbackScale(2)).toBe(675);
    expect(450 * playbackScale(.5)).toBe(2700);
    expect(playbackScale(0)).toBe(0);
  });
});
