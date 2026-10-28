import { Directive, ElementRef, OnDestroy, afterRenderEffect, inject, input } from '@angular/core';
import { gsap } from 'gsap';
import { Settings } from '../core/settings';
import { SPRINGS, still } from './springs';

/** Brings a changed view in on springs: it rises, its bars grow, its dots pop, its lines draw. The content is visible without motion. */
@Directive({ selector: '[labMotion]' })
export class LabMotion implements OnDestroy {
  readonly labMotion = input<unknown>();
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly settings = inject(Settings);
  private animation?: gsap.Context;
  constructor() {
    afterRenderEffect(() => {
      this.labMotion();
      this.animation?.revert();
      if (still(this.settings)) return;
      const host = this.element.nativeElement;
      this.animation = gsap.context(() => {
        gsap.from(host, { opacity: 0, y: 12, duration: SPRINGS.gentle.duration, ease: SPRINGS.gentle.ease, clearProps: 'opacity,transform' });
        const bars = host.querySelectorAll('[data-score-bar]');
        if (bars.length) gsap.from(bars, { scaleX: 0, transformOrigin: 'left center', duration: SPRINGS.bouncy.duration, stagger: .025, ease: SPRINGS.bouncy.ease, clearProps: 'transform' });
        const dots = host.querySelectorAll('[data-score-dot]');
        if (dots.length) gsap.from(dots, { scale: 0, opacity: 0, duration: SPRINGS.bouncy.duration, stagger: .012, ease: SPRINGS.bouncy.ease, clearProps: 'transform,opacity' });
        const paths = host.querySelectorAll<SVGPathElement>('[data-score-line]');
        paths.forEach(path => {
          const length = path.getTotalLength?.() ?? 0;
          if (length) gsap.fromTo(path, { strokeDasharray: length, strokeDashoffset: length }, { strokeDashoffset: 0, duration: .7, ease: 'power2.out', clearProps: 'strokeDasharray,strokeDashoffset' });
        });
      }, host);
    });
  }
  ngOnDestroy(): void { this.animation?.revert(); }
}
