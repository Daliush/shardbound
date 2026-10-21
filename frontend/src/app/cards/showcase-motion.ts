import { Pose } from './card';

const STIFFNESS = .075;
const DAMPING = .28;
const LEAN = { x: 14, y: 18 };
const clamp = (value: number, limit: number) => Math.max(-limit, Math.min(limit, value));

/**
 * How the showcased card moves, one frame at a time: a spring pulls it toward its face (the front, or its back after
 * a half turn) plus the lean the pointer asks for. It enters turned away and spins in; a drag turns it by hand, and
 * letting go keeps the spin, which settles on the nearest face. With nothing to follow, it sways gently.
 */
export class ShowcaseMotion {
  readonly pose: Pose = { x: 0, y: 0 };
  private readonly velocity: Pose = { x: 0, y: 0 };
  private lean: Pose = { x: 0, y: 0 };
  private face = 0;
  private held = false;
  private idle = true;
  private time = 0;

  enter(): void {
    Object.assign(this.pose, { x: 10, y: -200 });
    Object.assign(this.velocity, { x: 0, y: 0 });
    this.lean = { x: 0, y: 0 }; this.face = 0; this.held = false; this.idle = true;
  }
  /** The pointer's place from the card's center, in half sizes of the card: the card leans toward it. */
  leanTo(right: number, down: number): void {
    this.lean = { x: -clamp(down, 1) * LEAN.x, y: clamp(right, 1) * LEAN.y };
    this.idle = false;
  }
  rest(): void { this.lean = { x: 0, y: 0 }; this.idle = true; }
  grab(): void { this.held = true; }
  /** A drag of dx, dy pixels since the last one turns the card by hand. */
  turn(dx: number, dy: number): void {
    this.velocity.y = clamp(dx * .5, 20); this.velocity.x = clamp(-dy * .3, 10);
    this.pose.y += this.velocity.y;
    this.pose.x = clamp(this.pose.x + this.velocity.x, 40);
  }
  release(): void {
    if (!this.held) return;
    this.held = false;
    this.face = Math.round((this.pose.y + this.velocity.y * 7) / 180) * 180;
  }
  /** Moves the card by one frame of dt milliseconds; sway adds the idle drift. */
  step(dt: number, sway = true): Pose {
    if (this.held) return this.pose;
    const frames = Math.min(dt, 50) / (1000 / 60);
    this.time += dt;
    const drift = sway && this.idle ? { x: Math.sin(this.time / 1300) * 3, y: Math.sin(this.time / 1900) * 6 } : { x: 0, y: 0 };
    const target = { x: this.lean.x + drift.x, y: this.face + this.lean.y + drift.y };
    for (const axis of ['x', 'y'] as const) {
      this.velocity[axis] += ((target[axis] - this.pose[axis]) * STIFFNESS - this.velocity[axis] * DAMPING) * frames;
      this.pose[axis] += this.velocity[axis] * frames;
    }
    return this.pose;
  }
}
