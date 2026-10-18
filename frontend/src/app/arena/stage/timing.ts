/** Every animation, including impacts and particles, shares one tempo: 1× reads comfortably, 2× skims, ½× studies. */
export function playbackScale(speed: number): number { return speed > 0 ? 1.5 / speed : 0; }
