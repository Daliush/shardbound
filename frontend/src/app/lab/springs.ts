import type { AnimationCallbackEvent } from '@angular/core';
import type { Settings } from '../core/settings';

/**
 * A spring, as Apple's interfaces move: `response` is how long one swing takes, in seconds, and `damping` how fast it
 * settles, 1 without a bounce, lower with one. It gives the same curve three ways: an ease for GSAP, its duration, and
 * a CSS `linear()` easing, so that a tween and a transition of the lab move alike.
 */
export interface Spring { readonly duration: number; readonly ease: (progress: number) => number; readonly css: string; }

/** The swing is over once it stays this close to rest. */
const SETTLED = .002;
const SAMPLES = 48;

export function spring(response: number, damping: number): Spring {
  const position = positionOf(2*Math.PI/response, Math.min(1,damping));
  const duration = settleTime(position);
  const ease = (progress: number) => progress >= 1 ? 1 : progress <= 0 ? 0 : position(progress*duration);
  const css = 'linear(' + Array.from({length:SAMPLES+1}, (_,index) => +ease(index/SAMPLES).toFixed(4)).join(', ') + ')';
  return {duration:+duration.toFixed(3), ease, css};
}

/** Where a spring released at 0 stands after `time` seconds, heading to 1. */
function positionOf(omega: number, damping: number): (time: number) => number {
  if (damping >= 1) return time => 1 - Math.exp(-omega*time)*(1 + omega*time);
  const decay = damping*omega, frequency = omega*Math.sqrt(1 - damping*damping);
  return time => 1 - Math.exp(-decay*time)*(Math.cos(frequency*time) + decay/frequency*Math.sin(frequency*time));
}

function settleTime(position: (time: number) => number): number {
  let last = 0;
  for (let time = 0; time < 4; time += 1/240) if (Math.abs(1 - position(time)) > SETTLED) last = time;
  return last;
}

/** The lab's springs: `snappy` moves rows and markers, `bouncy` brings a value in, `gentle` fades a view. */
export const SPRINGS = {
  snappy: spring(.42, .8),
  bouncy: spring(.5, .62),
  gentle: spring(.55, 1),
} as const;

/** The springs as CSS custom properties, `--spring-snappy` and `--spring-snappy-duration` for each. */
export const SPRING_PROPERTIES: Readonly<Record<string,string>> = Object.fromEntries(Object.entries(SPRINGS).flatMap(([name,value]) =>
  [['--spring-' + name, value.css], ['--spring-' + name + '-duration', value.duration + 's']]));

/** Nothing moves under reduced motion or the instant setting. */
export function still(settings: Settings): boolean { return settings.reducedMotion() || settings.preferences().speed === 0; }

/**
 * Puts a marker over its target, a tab's underline or the pill behind a choice. Its CSS glides it on a spring once it
 * carries `data-placed`, which it gets after its first placement, so that it never glides in from nowhere.
 */
export function placeMarker(marker: HTMLElement, box: { x: number; y: number; width?: number; height?: number }, settings: Settings): void {
  marker.style.transform = 'translate(' + box.x + 'px,' + box.y + 'px)';
  if (box.width !== undefined) marker.style.width = box.width + 'px';
  if (box.height !== undefined) marker.style.height = box.height + 'px';
  marker.style.opacity = '1';
  if (!marker.dataset['placed'] && !still(settings) && typeof requestAnimationFrame === 'function') requestAnimationFrame(() => marker.dataset['placed'] = 'true');
}

/**
 * Ends a leave animation once: when its tween ends, or a moment after it should have, since a page that draws no frame,
 * a hidden tab say, never ends a tween. Angular then removes the element.
 */
export function leaving(event: AnimationCallbackEvent, seconds: number): () => void {
  let done = false;
  const end = () => { if (done) return; done = true; clearTimeout(timer); event.animationComplete(); };
  const timer = setTimeout(end,seconds*1000 + 80);
  return end;
}
