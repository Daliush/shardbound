import { AnimationCallbackEvent, Component, ElementRef, OnDestroy, afterRenderEffect, computed, effect, inject, input, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { gsap } from 'gsap';
import { Settings } from '../core/settings';
import { SPRINGS, leaving, spring, still } from './springs';

/** One place of the written value, counted from its right end, and the glyph it shows; a new glyph is a new id. */
interface Place { place: number; glyph: { id: number; char: string } }
type Direction = 'up' | 'down' | 'none';
/** A text that is one number between a prefix and a suffix: "71.1%", "114 ms", "#04". */
export interface Countable { prefix: string; number: number; decimals: number; width: number; suffix: string; }

/** A count settles without overshooting: a number never runs past its value and back. */
const COUNT = spring(.7,1);

/** The number a text writes, when it writes one and only one; null for "—", "11–20" or "1,234". */
export function countable(text: string): Countable | null {
  const match = /^([^\d,]*?)(-?)(\d+)(?:\.(\d+))?([^\d,]*)$/.exec(text);
  if (!match) return null;
  const [,prefix,sign,whole,fraction = '',suffix] = match;
  return {prefix,number:Number(sign + whole + (fraction ? '.' + fraction : '')),decimals:fraction.length,width:whole.length > 1 && whole.startsWith('0') ? whole.length : 0,suffix};
}
/** The text the count shows at `number`, written as its target is: "70.4%", "07". */
export function countText(target: Countable, number: number): string {
  const [whole,fraction] = Math.abs(number).toFixed(target.decimals).split('.');
  return target.prefix + (number < 0 && +number.toFixed(target.decimals) !== 0 ? '-' : '') + whole.padStart(target.width,'0') + (fraction ? '.' + fraction : '') + target.suffix;
}

/**
 * A value written as its format writes it, which counts to its new value when it changes, up or down through every
 * value between, as Apple's numbers do; the whole number is tinted for a moment by whether the change is better or
 * worse. A text that is not one number in the same unit, "1.27 s" to "780 ms" or "1–10" to "11–20", rolls its changed
 * glyphs instead: up when the value grows, down when it shrinks. Places are counted from the right.
 */
@Component({
  selector:'app-rolling-number',
  host:{'[class.better]':"tone() === 'better'",'[class.worse]':"tone() === 'worse'"},
  template:`<span class="sr-only">{{ text() }}</span><span #digits class="digits" aria-hidden="true">@for (slot of places(); track slot.place) {<span class="place">@for (glyph of [slot.glyph]; track glyph.id) {<span class="glyph" (animate.enter)="enter($event)" (animate.leave)="leave($event)">{{ glyph.char }}</span>}</span>}</span>`,
  styles:`
    :host { display: inline-flex; font-variant-numeric: tabular-nums; white-space: nowrap; }
    :host(.better) { --tint: var(--heal); } :host(.worse) { --tint: var(--alert); }
    .digits { display: inline-flex; }
    .place { position: relative; display: inline-block; }
    .glyph { display: inline-block; }
  `,
})
export class RollingNumber implements OnDestroy {
  readonly text = input.required<string>();
  /** The value written, which says whether it grew. */
  readonly value = input<number | null>(null);
  /** A time is better lower; a count is neither better nor worse. */
  readonly better = input<'higher' | 'lower' | null>('higher');
  private readonly settings = inject(Settings);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly digits = viewChild.required<ElementRef<HTMLElement>>('digits');
  private readonly motion = gsap.context(() => {});
  /** The text a count in progress shows; null once it reached the value. */
  private readonly counted = signal<string | null>(null);
  protected readonly display = computed(() => this.counted() ?? this.text());
  private counter?: gsap.core.Tween;
  private ids = 0;
  private written?: string;
  private flashed: number | null | undefined;

  protected readonly places = linkedSignal<string,Place[]>({
    source:this.display,
    computation:(text,previous) => {
      const before = new Map((previous?.value ?? []).map(slot => [slot.place,slot])), chars = [...text].reverse();
      return Array.from({length:Math.max(chars.length,before.size)},(_,place) => {
        const char = chars[place] ?? '', old = before.get(place);
        return old && old.glyph.char === char ? old : {place,glyph:{id:++this.ids,char}};
      }).reverse();
    },
  });
  private readonly direction = linkedSignal<number | null,Direction>({
    source:this.value,
    computation:(value,previous) => {
      const before = previous?.source ?? null;
      if (value === null || before === null || value === before) return previous?.value ?? 'none';
      return value > before ? 'up' : 'down';
    },
  });
  protected readonly tone = computed(() => {
    const direction = this.direction(), better = this.better();
    if (direction === 'none' || better === null) return 'none';
    return (direction === 'up') === (better === 'higher') ? 'better' : 'worse';
  });

  constructor() {
    effect(() => { const text = this.text(); untracked(() => this.count(text)); });
    afterRenderEffect(() => this.flash(this.value()));
  }
  ngOnDestroy(): void { this.counter?.kill(); this.motion.revert(); }

  /** The first glyphs show at once; while a count runs, glyphs change in place; else a changed one rolls in. */
  protected enter(event: AnimationCallbackEvent): void {
    const glyph = event.target as HTMLElement;
    if (this.counter || still(this.settings) || this.direction() === 'none' || !glyph.textContent) return;
    const from = this.direction() === 'down' ? -1 : 1;
    this.motion.add(() => gsap.fromTo(glyph,{yPercent:from*70,opacity:0,filter:'blur(3px)'},{yPercent:0,opacity:1,filter:'blur(0px)',duration:SPRINGS.bouncy.duration,ease:SPRINGS.bouncy.ease,clearProps:'transform,opacity,filter'}));
  }
  protected leave(event: AnimationCallbackEvent): void {
    const glyph = event.target as HTMLElement;
    if (this.counter || still(this.settings) || !glyph.textContent) { event.animationComplete(); return; }
    const to = this.direction() === 'down' ? 1 : -1, end = leaving(event,.26);
    this.motion.add(() => {
      gsap.set(glyph,{position:'absolute',left:0,top:0});
      gsap.to(glyph,{yPercent:to*70,opacity:0,filter:'blur(3px)',duration:.26,ease:'power2.in',onComplete:end});
    });
  }

  /** Counts from the number on screen, a count in progress included, to the new one, when both are one number in one unit. */
  private count(text: string): void {
    const before = this.written;
    this.written = text;
    const from = countable(this.counted() ?? before ?? ''), to = countable(text);
    this.counter?.kill(); this.counter = undefined;
    if (before === undefined || before === text || !from || !to || from.prefix !== to.prefix || from.suffix !== to.suffix || still(this.settings)) {
      this.counted.set(null);
      return;
    }
    const state = {number:from.number};
    this.counted.set(countText(to,from.number));
    this.counter = gsap.to(state,{number:to.number,duration:COUNT.duration,ease:COUNT.ease,
      onUpdate:() => this.counted.set(countText(to,state.number)),
      onComplete:() => { this.counter = undefined; this.counted.set(null); }});
  }
  /** Each change tints every digit at once, green when better, red when worse, then fades back to the number's color. */
  private flash(value: number | null): void {
    const before = this.flashed;
    this.flashed = value;
    if (before === undefined || before === value || this.tone() === 'none' || still(this.settings)) return;
    const digits = this.digits().nativeElement, style = getComputedStyle(this.host.nativeElement);
    const tint = style.getPropertyValue('--tint').trim();
    if (!tint) return;
    this.motion.add(() => gsap.fromTo(digits,{color:tint},{color:style.color,duration:1.2,delay:.45,ease:'power1.inOut',overwrite:true,clearProps:'color'}));
  }
}
