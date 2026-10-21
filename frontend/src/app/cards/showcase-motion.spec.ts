import { ShowcaseMotion } from './showcase-motion';

/** Runs the motion for a while at 60 frames per second, without the idle sway. */
function settle(motion: ShowcaseMotion, frames = 240) {
  for (let frame = 0; frame < frames; frame++) motion.step(1000 / 60, false);
  return motion.pose;
}

describe('ShowcaseMotion', () => {
  it('enters turned away and spins in to face the viewer', () => {
    const motion = new ShowcaseMotion();
    motion.enter();
    expect(motion.pose.y).toBeLessThan(-90);
    const pose = settle(motion);
    expect(pose.y).toBeCloseTo(0, 0);
    expect(pose.x).toBeCloseTo(0, 0);
  });

  it('settles on its back after a flick, springs back from a small drag, and turns home after another flick', () => {
    const motion = new ShowcaseMotion();
    motion.enter(); settle(motion);
    const flick = (dx: number) => { motion.grab(); for (let move = 0; move < 6; move++) motion.turn(dx, 0); motion.release(); };
    flick(40);
    expect(settle(motion).y).toBeCloseTo(180, 0);
    motion.grab(); motion.turn(-40, 0); motion.turn(-2, 0); motion.release();
    expect(settle(motion).y).toBeCloseTo(180, 0);
    flick(-40);
    expect(settle(motion).y).toBeCloseTo(0, 0);
  });

  it('leans toward the pointer, no further than its limit, and straightens at rest', () => {
    const motion = new ShowcaseMotion();
    motion.enter(); settle(motion);
    motion.leanTo(5, -5);
    const leaning = { ...settle(motion) };
    expect(leaning.y).toBeCloseTo(18, 0);
    expect(leaning.x).toBeCloseTo(14, 0);
    motion.rest();
    expect(settle(motion).y).toBeCloseTo(0, 0);
  });
});
