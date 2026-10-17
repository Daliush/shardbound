import { DestroyRef, Directive, ElementRef, inject, input, output } from '@angular/core';

/** A horizontal gesture emits exactly one step on release; vertical scrolling stays native. */
@Directive({
  selector: '[stepSwipe]',
  host: {
    '(pointerdown)': 'start($event)', '(pointermove)': 'move($event)',
    '(pointerup)': 'finish($event)', '(pointercancel)': 'cancel($event)',
    '(lostpointercapture)': 'lostCapture($event)',
  },
})
export class StepSwipe {
  readonly enabled = input(false, { alias: 'stepSwipe' });
  readonly stepped = output<-1 | 1>();
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private gesture?: { id: number; x: number; y: number; horizontal: boolean };
  private blockClickUntil = 0;

  constructor() {
    // A swipe must not also activate the card underneath the release point.
    const suppressClick = (event: MouseEvent) => {
      if (event.detail !== 0 && performance.now() < this.blockClickUntil) {
        event.preventDefault(); event.stopImmediatePropagation();
      }
    };
    this.element.addEventListener('click', suppressClick, true);
    inject(DestroyRef).onDestroy(() => this.element.removeEventListener('click', suppressClick, true));
  }

  protected start(event: PointerEvent): void {
    if (!this.enabled() || event.button !== 0 || event.isPrimary === false || this.gesture) return;
    this.blockClickUntil = 0;
    this.gesture = { id: event.pointerId, x: event.clientX, y: event.clientY, horizontal: false };
  }

  protected move(event: PointerEvent): void {
    const gesture = this.gesture;
    if (!gesture || gesture.id !== event.pointerId) return;
    const dx = Math.abs(event.clientX - gesture.x), dy = Math.abs(event.clientY - gesture.y);
    if (!gesture.horizontal) {
      if (dy > 10 && dy >= dx) { this.gesture = undefined; return; }
      if (dx < 10 || dx < dy * 1.2) return;
      gesture.horizontal = true;
      this.element.setPointerCapture?.(event.pointerId);
    }
    event.preventDefault();
  }

  protected finish(event: PointerEvent): void {
    const gesture = this.gesture;
    if (!gesture || gesture.id !== event.pointerId) return;
    const dx = event.clientX - gesture.x, dy = event.clientY - gesture.y;
    this.cancel(event);
    if (gesture.horizontal) this.blockClickUntil = performance.now() + 400;
    if (!this.enabled() || Math.abs(dx) < 40 || Math.abs(dx) < Math.abs(dy) * 1.2) return;
    this.blockClickUntil = performance.now() + 400;
    event.preventDefault();
    this.stepped.emit(dx < 0 ? 1 : -1);
  }

  protected lostCapture(event: PointerEvent): void {
    // Touch starts with implicit capture on a child. Its loss bubbles here when
    // move() transfers capture to the carousel; that transfer must keep the swipe alive.
    if (event.target === this.element) this.cancel(event);
  }

  protected cancel(event: PointerEvent): void {
    if (this.gesture?.id !== event.pointerId) return;
    this.gesture = undefined;
    if (this.element.hasPointerCapture?.(event.pointerId)) this.element.releasePointerCapture(event.pointerId);
  }
}
