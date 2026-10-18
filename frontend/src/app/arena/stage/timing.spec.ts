import { playbackScale } from './timing';

describe('animation pacing', () => {
  it('slows every beat by half at 1×, and scales with the chosen speed', () => {
    expect(450 * playbackScale(1)).toBe(675);
    expect(450 * playbackScale(2)).toBeCloseTo(337.5);
    expect(450 * playbackScale(.5)).toBe(1350);
    expect(playbackScale(0)).toBe(0);
  });
});
