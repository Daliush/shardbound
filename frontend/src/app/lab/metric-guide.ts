import { Component, ElementRef, OnDestroy, afterRenderEffect, computed, inject, input, output, signal, untracked } from '@angular/core';
import { gsap } from 'gsap';
import { EvaluationRun, MetricDisplay } from '../api/evaluation';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { MetricCatalog } from './metric-catalog';
import { SPRINGS } from './springs';
import { buildGuide } from './metric-guide-model';

const DISPLAY_LABELS: Record<MetricDisplay,string> = {share:'Share · %',score:'Score · 0 to 1',count:'Count',seconds:'Seconds'};

/**
 * Every metric of the evaluation open, as its file describes it: what it does, how it is computed, what it represents,
 * and its value for the best pipeline and the random floor. The texts are the evaluation's, never the lab's.
 */
@Component({
  selector:'app-metric-guide', imports:[Icon],
  templateUrl:'./metric-guide.html', styleUrls:['./metric-guide.css','./metric-guide-card.css','./metric-guide-mobile.css'],
})
export class MetricGuide implements OnDestroy {
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  readonly runs = input<EvaluationRun[]>([]);
  /** Asked from an evaluation recorded before its metrics described themselves. */
  readonly latest = output<void>();
  protected readonly groups = computed(() => buildGuide(this.catalog(),this.runs()));
  /** The card read now, which the index marks. */
  protected readonly active = signal('');
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly settings = inject(Settings);
  private motion?: gsap.Context;
  private reveals?: IntersectionObserver;
  private spy?: IntersectionObserver;

  constructor() {
    afterRenderEffect(() => { this.groups(); this.watch(); });
    afterRenderEffect(() => this.mark(this.active()));
  }
  ngOnDestroy(): void { this.stop(); }

  protected displayLabel(display: MetricDisplay): string { return DISPLAY_LABELS[display]; }
  protected jump(anchor: string): void {
    this.active.set(anchor);
    this.host.nativeElement.querySelector('#' + anchor)?.scrollIntoView({behavior:this.still() ? 'auto' : 'smooth',block:'start'});
  }

  private still(): boolean { return this.settings.reducedMotion() || this.settings.preferences().speed === 0; }
  private stop(): void { this.motion?.revert(); this.reveals?.disconnect(); this.spy?.disconnect(); }

  /** Marks the card read, and reveals each card as it scrolls in: its parts rise, its gauges fill. */
  private watch(): void {
    this.stop();
    const host = this.host.nativeElement;
    const cards = [...host.querySelectorAll<HTMLElement>('[data-guide-card]')];
    if (!cards.length || typeof IntersectionObserver === 'undefined') return;
    if (!untracked(() => cards.some(card => card.id === this.active()))) this.active.set(cards[0].id);
    this.spy = new IntersectionObserver(entries => {
      const shown = entries.filter(entry => entry.isIntersecting).sort((a,b) => a.boundingClientRect.top-b.boundingClientRect.top);
      if (shown.length) this.active.set(shown[0].target.id);
    },{rootMargin:'-18% 0px -62% 0px'});
    for (const card of cards) this.spy.observe(card);
    if (this.still()) return;
    this.motion = gsap.context(() => {
      gsap.from(host.querySelectorAll('[data-guide-intro] > *'),{opacity:0,y:10,duration:.45,stagger:.07,ease:'power2.out',clearProps:'opacity,transform'});
      gsap.set(cards,{opacity:0,y:24});
      gsap.set(host.querySelectorAll('[data-guide-fill]'),{scaleX:0,transformOrigin:'left center'});
    },host);
    this.reveals = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        this.reveals?.unobserve(entry.target);
        this.reveal(entry.target as HTMLElement);
      }
    },{rootMargin:'0px 0px -8% 0px',threshold:.1});
    for (const card of cards) this.reveals.observe(card);
  }

  private reveal(card: HTMLElement): void {
    this.motion?.add(() => {
      gsap.timeline({defaults:{ease:'power3.out'}})
        .to(card,{opacity:1,y:0,duration:SPRINGS.gentle.duration,ease:SPRINGS.gentle.ease,clearProps:'opacity,transform'})
        .from(card.querySelectorAll('[data-guide-part]'),{opacity:0,y:10,duration:.45,stagger:.08,clearProps:'opacity,transform'},'-=.4')
        .to(card.querySelectorAll('[data-guide-fill]'),{scaleX:1,duration:SPRINGS.bouncy.duration,stagger:.1,ease:SPRINGS.bouncy.ease,clearProps:'transform'},'-=.25');
    });
  }

  /** Slides the index's marker to the card read, and keeps its chip in sight on a phone. */
  private mark(anchor: string): void {
    const host = this.host.nativeElement;
    const item = host.querySelector<HTMLElement>('[data-anchor="' + anchor + '"]');
    const marker = host.querySelector<HTMLElement>('[data-guide-marker]');
    if (!item || !marker) return;
    gsap.to(marker,{y:item.offsetTop,height:item.offsetHeight,opacity:1,duration:this.still() ? 0 : .35,ease:'power3.out'});
    const rail = item.parentElement;
    if (rail && rail.scrollWidth > rail.clientWidth) {
      rail.scrollTo({left:item.offsetLeft-(rail.clientWidth-item.offsetWidth)/2,behavior:this.still() ? 'auto' : 'smooth'});
    }
  }
}
