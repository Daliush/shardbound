import { AnimationCallbackEvent, Directive, ElementRef, OnDestroy, afterRenderEffect, inject, input, linkedSignal } from '@angular/core';
import { gsap } from 'gsap';
import { Settings } from '../core/settings';
import { RowWindow, arrivalOf } from './pages';
import { SPRINGS, leaving, still } from './springs';

/**
 * The rows of a page of a ranking, each a direct child with `data-row-key`, moved on springs: a row the new order
 * moves glides to its place, a row that crosses the page's edge comes in or leaves through it, and a page turned
 * slides in from its side. The rows call `enter` and `leave` from `animate.enter` and `animate.leave`.
 */
@Directive({selector:'[labRows]', exportAs:'labRows', host:{'style':'position: relative'}})
export class RowMotion implements OnDestroy {
  readonly labRows = input.required<RowWindow>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly settings = inject(Settings);
  private readonly motion = gsap.context(() => {});
  /** Where each row sat after the last render, so that a row the new order moves glides from there. */
  private readonly tops = new WeakMap<HTMLElement,number>();
  private readonly entering = new WeakSet<HTMLElement>();
  private readonly history = linkedSignal<RowWindow,{now: RowWindow; before: RowWindow | null}>({
    source:this.labRows, computation:(now,previous) => ({now,before:previous?.source ?? null}),
  });
  private queued = false;

  constructor() { afterRenderEffect(() => { this.history(); this.glide(); }); }
  ngOnDestroy(): void { this.motion.revert(); }

  enter(event: AnimationCallbackEvent, key: string): void {
    const row = event.target as HTMLElement;
    if (still(this.settings)) return;
    const {now,before} = this.history(), arrival = arrivalOf(key,now,before,false);
    const delay = arrival.kind === 'rank' ? 0 : arrival.index * (arrival.kind === 'intro' ? .035 : .022);
    const from = arrival.kind === 'page' ? {x:arrival.side*36} : arrival.kind === 'rank' ? {yPercent:arrival.side*80} : {y:arrival.kind === 'intro' ? 14 : 0,scale:arrival.kind === 'fade' ? .97 : 1};
    this.entering.add(row);
    this.motion.add(() => gsap.fromTo(row,{...from,opacity:0},{x:0,y:0,yPercent:0,scale:1,opacity:1,delay,duration:SPRINGS.bouncy.duration,ease:SPRINGS.bouncy.ease,
      clearProps:'transform,opacity',onComplete:() => this.entering.delete(row)}));
  }

  leave(event: AnimationCallbackEvent, key: string): void {
    const row = event.target as HTMLElement;
    if (still(this.settings)) { event.animationComplete(); return; }
    const {now,before} = this.history(), arrival = arrivalOf(key,now,before,true);
    row.dataset['leaving'] = 'true';
    const to = arrival.kind === 'page' ? {x:arrival.side*36} : arrival.kind === 'rank' ? {yPercent:arrival.side*80} : {scale:.97};
    const end = leaving(event,.3);
    this.motion.add(() => {
      gsap.set(row,{position:'absolute',top:this.tops.get(row) ?? row.offsetTop,left:0,right:0,zIndex:0,pointerEvents:'none'});
      gsap.to(row,{...to,opacity:0,duration:.3,ease:'power2.in',onComplete:end});
    });
    this.queueGlide();
  }

  /** A leaving row stops holding its place, so the rows after it may move: glide them once every row has left. */
  private queueGlide(): void {
    if (this.queued) return;
    this.queued = true;
    queueMicrotask(() => { this.queued = false; this.glide(); });
  }

  /** Each row the new order moves glides from where it was seen, a glide in progress included, to its new place. */
  private glide(): void {
    const animate = !still(this.settings);
    for (const row of this.host.querySelectorAll<HTMLElement>(':scope > [data-row-key]')) {
      if (row.dataset['leaving']) continue;
      const top = row.offsetTop, before = this.tops.get(row);
      this.tops.set(row,top);
      if (!animate || before === undefined || before === top || this.entering.has(row)) continue;
      const from = before + Number(gsap.getProperty(row,'y')) - top;
      this.motion.add(() => gsap.fromTo(row,{y:from,zIndex:1},{y:0,duration:SPRINGS.snappy.duration,ease:SPRINGS.snappy.ease,overwrite:'auto',clearProps:'transform,zIndex'}));
    }
  }
}
