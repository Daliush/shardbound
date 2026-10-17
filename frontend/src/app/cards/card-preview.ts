import { Directive, OnDestroy, inject, input } from '@angular/core';
import { Inspect, InspectedCard } from './inspect';

@Directive({
  selector: '[appCardPreview]',
  host: {
    '(pointerenter)': 'enter($event)', '(pointerleave)': 'leave()',
    '(pointerdown)': 'leave()', '(focusin)': 'focus()', '(focusout)': 'leave()',
  },
})
export class CardPreview implements OnDestroy {
  readonly appCardPreview = input.required<InspectedCard>();
  private readonly inspect = inject(Inspect);
  private readonly owner = {};
  enter(event: PointerEvent): void { if (event.pointerType !== 'touch') this.focus(); }
  focus(): void { this.inspect.previewAfter(this.owner, this.appCardPreview()); }
  leave(): void { this.inspect.clearPreview(this.owner); }
  ngOnDestroy(): void { this.leave(); }
}
