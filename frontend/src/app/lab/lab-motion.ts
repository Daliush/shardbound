import { Directive, ElementRef, OnDestroy, afterRenderEffect, inject, input } from '@angular/core';
import { gsap } from 'gsap';
import { Settings } from '../core/settings';

/** Animates a user's changed view; the content is visible without JavaScript motion. */
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
      if (this.settings.reducedMotion() || this.settings.preferences().speed === 0) return;
      const host = this.element.nativeElement;
      this.animation = gsap.context(() => {
        gsap.from(host, { opacity: .5, y: 5, duration: .24, ease: 'power2.out', clearProps: 'opacity,transform' });
        const bars = host.querySelectorAll('[data-score-bar]');
        if (bars.length) gsap.from(bars, { scaleX: 0, transformOrigin: 'left center', duration: .55, stagger: .025, ease: 'power3.out', clearProps: 'transform' });
        const paths = host.querySelectorAll<SVGPathElement>('[data-score-line]');
        paths.forEach(path => {
          const length = path.getTotalLength?.() ?? 0;
          if (length) gsap.fromTo(path, { strokeDasharray: length, strokeDashoffset: length }, { strokeDashoffset: 0, duration: .65, ease: 'power2.out', clearProps: 'strokeDasharray,strokeDashoffset' });
        });
      }, host);
    });
  }
  ngOnDestroy(): void { this.animation?.revert(); }
}
