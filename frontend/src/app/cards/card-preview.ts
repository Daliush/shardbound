import { Directive, OnDestroy, effect, inject, input } from '@angular/core';
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
  /** No preview while the player aims: it would cover the targets. */
  readonly appCardPreviewOff = input(false);
  private readonly inspect = inject(Inspect);
  private readonly owner = {};
  constructor() { effect(() => { if (this.appCardPreviewOff()) this.leave(); }); }
  enter(event: PointerEvent): void { if (event.pointerType !== 'touch') this.focus(); }
  focus(): void { if (!this.appCardPreviewOff()) this.inspect.previewAfter(this.owner, this.appCardPreview()); }
  leave(): void { this.inspect.clearPreview(this.owner); }
  ngOnDestroy(): void { this.leave(); }
}
