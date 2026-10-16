/** Every animation, including impacts and particles, shares the same readable tempo. */
export function playbackScale(speed: number): number { return speed > 0 ? 3 / speed : 0; }
