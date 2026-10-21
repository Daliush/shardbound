import { Component, DestroyRef, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { Inspect } from '../cards/inspect';
import { CardComponent } from '../cards/card';
import { Ambience } from '../core/ambience';
import { FACTIONS } from '../core/factions';
import { CardBook } from '../game/card-book';
import type { ElementalScene, ElementKind } from './elemental-scene';
import { StepSwipe } from './step-swipe';

@Component({
  selector: 'app-home-page',
  imports: [Icon, CardComponent, StepSwipe],
  templateUrl: './home-page.html',
  styleUrls: ['./home-page.css', './home-portals.css', './home-auras.css', './home-carousel.css', './home-doors.css'],
  host: { '[class.motion-off]': 'motionOff()', '[class.scene-paused]': 'paused()', '[attr.data-faction]': 'active().id', '[style.--slide-duration]': 'slideDuration() + "ms"' },
})
export class HomePage {
  protected readonly factions = FACTIONS;
  protected readonly book = inject(CardBook);
  protected readonly selection = signal<number | null>(null);
  protected readonly lastHovered = signal(0);
  protected readonly center = computed(() => this.selection() ?? this.lastHovered());
  protected readonly selected = computed(() => this.factions[this.center()]);
  protected readonly compact = signal(false);
  protected readonly sliding = signal(false);
  protected readonly recycling = signal<number | null>(null);
  protected readonly previousFaction = computed(() => this.factions[(this.center() + 2) % 3]);
  protected readonly nextFaction = computed(() => this.factions[(this.center() + 1) % 3]);
  protected readonly hovered = signal<number | null>(null);
  protected readonly active = computed(() => this.factions[this.hovered() ?? this.selection() ?? this.lastHovered()]);
  protected readonly paused = signal(false);
  protected readonly departing = signal<'arena' | 'lab' | null>(null);
  protected readonly settings = inject(Settings);
  protected readonly motionOff = computed(() => this.settings.reducedMotion() || this.settings.preferences().speed === 0);
  protected readonly slideDuration = computed(() => this.motionOff() ? 0 : 480 / this.settings.preferences().speed);
  private readonly inspect = inject(Inspect);
  private readonly ambience = inject(Ambience);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('elements');
  private scene?: ElementalScene;
  private loading?: Promise<void>;
  private pointer?: { kind: ElementKind; x: number; y: number };
  private transition?: Animation;
  private slideTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    const mobile = typeof matchMedia === 'function' ? matchMedia('(max-width: 800px)') : null;
    const resize = () => { this.compact.set(mobile?.matches ?? false); this.hovered.set(null); this.finishSlide(); };
    resize();
    mobile?.addEventListener('change', resize);
    this.destroyRef.onDestroy(() => { mobile?.removeEventListener('change', resize); clearTimeout(this.slideTimer); });
    effect(() => this.ambience.faction.set(this.active().id));
    effect(() => { if (this.motionOff()) { this.pointer = undefined; this.scene?.stop(); this.finishSlide(); } });
    let visible = true;
    const pause = () => { this.paused.set(document.hidden || !visible); if (this.paused()) this.scene?.stop(); };
    const observer = typeof IntersectionObserver === 'undefined' ? null : new IntersectionObserver(entries => { visible = entries[0].isIntersecting; pause(); });
    observer?.observe(this.element.nativeElement);
    document.addEventListener('visibilitychange', pause);
    this.destroyRef.onDestroy(() => { observer?.disconnect(); document.removeEventListener('visibilitychange', pause); this.pointer = undefined; this.scene?.dispose(); this.transition?.cancel(); this.ambience.faction.set('neutral'); });
  }

  protected choose(index: number): void {
    if (this.sliding()) return;
    if (this.compact() && index !== this.center() && !this.motionOff()) {
      const direction = (index - this.center() + 3) % 3 === 1 ? 1 : -1;
      // The third card changes sides behind the focal pair, never sweeping over the main card.
      this.recycling.set((this.center() - direction + 3) % 3);
      this.sliding.set(true);
      this.slideTimer = setTimeout(() => this.finishSlide(), this.slideDuration());
    }
    this.selection.set(index);
    this.hovered.set(null);
  }

  protected step(direction: -1 | 1): void { this.choose((this.center() + direction + 3) % 3); }
  protected slot(index: number): number { return ((index - this.center() + 4) % 3) - 1; }
  private finishSlide(): void { clearTimeout(this.slideTimer); this.sliding.set(false); this.recycling.set(null); }
  protected preview(index: number): void { if (!this.compact()) { this.lastHovered.set(index); this.hovered.set(index); } }
  protected unpreview(): void { this.hovered.set(null); }

  protected key(event: KeyboardEvent, index: number): void {
    const current = this.compact() ? this.center() : index;
    const next = event.key === 'ArrowRight' ? (current + 1) % 3 : event.key === 'ArrowLeft' ? (current + 2) % 3 : event.key === 'Home' ? 0 : event.key === 'End' ? 2 : null;
    if (next === null) return;
    event.preventDefault();
    if (this.sliding()) return;
    this.choose(next);
    (event.currentTarget as HTMLElement).closest('.portals')?.querySelectorAll<HTMLButtonElement>('.portal')[next]?.focus();
  }

  protected point(event: PointerEvent, kind: ElementKind): void {
    if (this.compact() || event.pointerType === 'touch' || this.motionOff() || this.departing() || !matchMedia('(hover: hover) and (pointer: fine)').matches) return;
    const tile = event.currentTarget as HTMLElement;
    const rect = tile.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width, y = (event.clientY - rect.top) / rect.height;
    tile.style.setProperty('--rx', (-(y - .5) * 5) + 'deg');
    tile.style.setProperty('--ry', ((x - .5) * 7) + 'deg');
    tile.style.setProperty('--mx', x * 100 + '%');
    tile.style.setProperty('--my', y * 100 + '%');
    this.pointer = { kind, x: event.clientX, y: event.clientY };
    if (this.scene) this.scene.point(kind, event.clientX, event.clientY);
    else this.loadScene();
  }

  protected leave(event: PointerEvent): void {
    const tile = event.currentTarget as HTMLElement;
    tile.style.removeProperty('--rx'); tile.style.removeProperty('--ry');
    this.pointer = undefined;
    this.scene?.leave();
  }

  private loadScene(): void {
    if (this.loading) return;
    this.loading = import('./elemental-scene').then(({ ElementalScene }) => {
      const canvas = this.canvas()?.nativeElement;
      if (!canvas || this.destroyRef.destroyed || this.motionOff() || !this.pointer) { this.loading = undefined; return; }
      this.scene = new ElementalScene(canvas);
      this.scene.point(this.pointer.kind, this.pointer.x, this.pointer.y);
    }).catch(() => { /* Card selection and the cinematic backdrop remain usable without WebGL. */ });
  }

  protected inspectCard(): void { this.inspect.open({ id: this.selected().card }); }
  protected reloadCards(): void { window.location.reload(); }

  protected async enter(event: MouseEvent, destination: 'arena' | 'lab'): Promise<void> {
    // Preserve native open-in-new-tab and copy-link behavior.
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (this.departing()) return;
    const navigate = () => this.router.navigate([destination === 'arena' ? '/play' : '/lab'], destination === 'arena' ? { queryParams: { faction: this.selected().id } } : {});
    if (this.motionOff()) { await navigate(); return; }
    this.departing.set(destination);
    this.scene?.leave();
    const veil = (event.currentTarget as HTMLElement).closest('.home')?.querySelector('.passage');
    if (veil) {
      this.transition = veil.animate([
        { opacity: 0, clipPath: 'circle(0% at 50% 85%)' },
        { opacity: 1, clipPath: 'circle(150% at 50% 85%)' },
      ], { duration: 480 / this.settings.preferences().speed, easing: 'cubic-bezier(.16,1,.3,1)', fill: 'forwards' });
      try { await this.transition.finished; } catch { return; }
    }
    if (this.destroyRef.destroyed) return;
    try { await navigate(); } finally {
      if (!this.destroyRef.destroyed) { this.transition?.cancel(); this.departing.set(null); }
    }
  }
}

