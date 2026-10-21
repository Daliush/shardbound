import { Component, DestroyRef, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { CardBook } from '../game/card-book';
import { CardComponent, Pose } from './card';
import { Inspect } from './inspect';
import { ShowcaseMotion } from './showcase-motion';

/** Specks of light rising behind the card: place, size, delay and duration, the same at every opening. */
const MOTES = Array.from({ length: 18 }, (_, index) => ({
  left: (index * 37 + 11) % 100, size: 2 + (index * 7) % 4, delay: -((index * 1.7) % 9), duration: 7 + (index * 3) % 6, drift: ((index * 29) % 60) - 30,
}));

/**
 * The inspected card, alone and large over the blurred page, in 3D: it spins in when opened, leans toward the
 * pointer or the tilted phone, and turns under a drag to show its back. Escape, the close button or a click beside
 * it puts it away. The board's hover preview stays a flat card: it must not move under the eye.
 */
@Component({
  selector: 'app-inspect-panel',
  imports: [CardComponent, Icon],
  templateUrl: './inspect-panel.html',
  styleUrl: './inspect-panel.css',
  host: { '(window:keydown.escape)': 'inspect.clearPreview()' },
})
export class InspectPanel {
  protected readonly inspect = inject(Inspect);
  private readonly book = inject(CardBook);
  private readonly settings = inject(Settings);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly motion = new ShowcaseMotion();
  protected readonly motes = MOTES;
  protected readonly pose = signal<Pose>({ x: 0, y: 0 });
  /** How far the card is turned from the face it shows, -90 to 90 degrees: its shadow slides the other way. */
  protected readonly turn = computed(() => ((this.pose().y % 180) + 270) % 180 - 90);
  protected readonly leaving = signal(false);
  protected readonly touchOnly = typeof matchMedia === 'function' && matchMedia('(hover: none)').matches;
  protected readonly faction = computed(() => {
    const card = this.inspect.card();
    return card ? this.book.card(card.id)?.faction ?? card.id.split('.')[0] : 'neutral';
  });
  private frame = 0;
  private last = 0;
  private drag: { pointer: number; x: number; y: number } | null = null;

  constructor() {
    effect(() => {
      const dialog = this.dialog().nativeElement, card = this.inspect.card();
      if (card) untracked(() => { this.leaving.set(false); if (!dialog.open) dialog.showModal(); this.enter(); });
      else if (dialog.open) dialog.close();
    });
    effect(() => {
      const tilt = this.settings.tilt();
      if (this.settings.preferences().gyroscope && this.inspect.card()) this.motion.leanTo(tilt.y / 8, tilt.x / 8);
    });
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
  }

  /** Closes after the card's way out; Escape comes here too, through the dialog's cancel. */
  protected dismiss(event?: Event): void {
    event?.preventDefault();
    if (this.leaving()) return;
    if (this.settings.reducedMotion()) { this.inspect.close(); return; }
    this.leaving.set(true);
    setTimeout(() => { if (this.leaving()) this.inspect.close(); }, 180);
  }
  protected backdrop(event: MouseEvent): void {
    if (!(event.target as Element).closest('.stage, .close')) this.dismiss();
  }
  protected grab(event: PointerEvent): void {
    if (event.button !== 0) return;
    (event.currentTarget as Element).setPointerCapture(event.pointerId);
    this.drag = { pointer: event.pointerId, x: event.clientX, y: event.clientY };
    this.motion.grab();
  }
  protected point(event: PointerEvent): void {
    if (this.drag?.pointer === event.pointerId) {
      this.motion.turn(event.clientX - this.drag.x, event.clientY - this.drag.y);
      this.drag.x = event.clientX; this.drag.y = event.clientY;
      this.pose.set({ ...this.motion.pose });
      return;
    }
    const card = this.dialog().nativeElement.querySelector('.stage app-card')?.getBoundingClientRect();
    if (event.pointerType === 'touch' || !card || this.settings.reducedMotion()) return;
    this.motion.leanTo((event.clientX - card.left - card.width / 2) / (card.width / 2), (event.clientY - card.top - card.height / 2) / (card.height / 2));
  }
  protected letGo(event: PointerEvent): void {
    if (this.drag?.pointer !== event.pointerId) return;
    this.drag = null;
    this.motion.release();
  }
  protected rest(): void { this.motion.rest(); }

  private enter(): void {
    cancelAnimationFrame(this.frame);
    this.motion.enter();
    if (this.settings.reducedMotion()) Object.assign(this.motion.pose, { x: 0, y: 0 });
    this.last = performance.now();
    const tick = (now: number) => {
      if (!this.inspect.card()) return;
      this.pose.set({ ...this.motion.step(now - this.last, !this.settings.reducedMotion()) });
      this.last = now;
      this.frame = requestAnimationFrame(tick);
    };
    this.frame = requestAnimationFrame(tick);
  }
}
