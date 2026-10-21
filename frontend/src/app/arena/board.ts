import { Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { GameView, Side, UnitView } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { CardPreview } from '../cards/card-preview';
import { Inspect } from '../cards/inspect';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';
import { Mark } from '../game/decision-groups';
import { PendingCombat, Projection } from './combat';
import { Aim, BoardLinks } from './links';
import { PlayerHud } from './player-hud';
import { AnchorDirective } from './stage/anchors';
import { CombatRole, UnitToken } from './unit-token';

export interface Press { key: string; event: PointerEvent; attack?: number; }
/** Where the big copy of a hand card stands above the hand, in pixels from the hand's left edge. */
interface PeekPlace { key: string; left: number; width: number; fromX: number; fromScale: number; }

/**
 * The table, top to bottom: the opponent, their units, the battle line (projected by the page), your units, you,
 * your hand. It draws what the page decided; a click on something marked is an action, anything else inspects.
 */
@Component({
  selector: 'app-arena-board',
  imports: [CardComponent, CardPreview, Icon, PlayerHud, AnchorDirective, BoardLinks, UnitToken],
  templateUrl: './board.html',
  styleUrls: ['./board.css', './board-hand.css', './board-mobile.css'],
})
export class ArenaBoard {
  readonly view = input.required<GameView>();
  readonly marks = input<Record<string, Mark>>({});
  readonly labels = input<Record<string, string>>({});
  readonly projections = input<Record<string, Projection>>({});
  readonly selected = input<string | null>(null);
  readonly attackIndex = input<number | null>(null);
  readonly locked = input(false);
  readonly focus = input(false);
  readonly combat = input<PendingCombat | null>(null);
  readonly aim = input<Aim | null>(null);
  readonly action = output<string>();
  readonly press = output<Press>();
  readonly hover = output<string | null>();
  readonly graveyard = output<Side>();
  protected readonly inspect = inject(Inspect);
  protected readonly book = inject(CardBook);
  private readonly hand = viewChild.required<ElementRef<HTMLElement>>('hand');
  private readonly peekPlace = signal<PeekPlace | null>(null);
  /** The hand card being read: under the mouse, under a finger pressed on the hand, or focused from the keyboard. */
  protected readonly peek = computed(() => {
    const place = this.peekPlace(), card = place && this.view().you.hand.find(card => 'card:' + card.id === place.key);
    return place && card ? { ...place, card } : null;
  });
  private touching = false;
  protected readonly lockedMarks = computed(() => this.locked() ? {} : this.marks());
  protected readonly yourTurn = computed(() => !this.locked() && this.view().decision?.kind === 'main');
  protected readonly interceptors = computed(() => new Set((this.view().decision?.actions ?? []).filter(action => action.type === 'intercept').map(action => 'unit:' + action.interceptor)));
  constructor() {
    // Nothing covers the targets while the player aims, nor stays under an inspected card.
    effect(() => { if (this.focus() || this.inspect.card()) this.peekPlace.set(null); });
  }
  protected mark(key: string): Mark { return this.lockedMarks()[key] ?? null; }
  protected hoverIn(event: PointerEvent, key: string): void { if (event.pointerType !== 'touch') this.peekAt(key); }
  protected hoverOut(event: PointerEvent, key: string): void { if (event.pointerType !== 'touch') this.unpeek(key); }
  protected focusIn(event: FocusEvent, key: string): void { if ((event.target as HTMLElement).matches(':focus-visible')) this.peekAt(key); }
  protected focusOut(key: string): void { if (!this.touching) this.unpeek(key); }
  /** A finger on a hand card reads it; sliding along the hand reads the card under the finger, until it lifts. */
  protected touch(event: PointerEvent, key: string): void {
    if (event.pointerType !== 'touch') return;
    this.touching = true;
    this.peekAt(key);
  }
  protected slide(event: PointerEvent): void {
    if (!this.touching) return;
    const under = document.elementFromPoint(event.clientX, event.clientY);
    const key = under?.closest('.hand') ? under.closest<HTMLElement>('.hand-card')?.dataset['key'] : null;
    if (key === null) this.peekPlace.set(null);
    else if (key && key !== this.peekPlace()?.key) this.peekAt(key);
  }
  protected release(event: PointerEvent): void {
    if (event.pointerType !== 'touch') return;
    this.touching = false;
    this.peekPlace.set(null);
  }
  protected pick(key: string, cardId?: string): void {
    if (this.mark(key)) this.action.emit(key);
    else if (cardId && !this.locked()) this.inspect.open({ id: cardId, unit: this.unit(key), hand: this.view().you.hand.find(card => 'card:' + card.id === key) });
  }
  protected role(unit: UnitView): CombatRole {
    const key = 'unit:' + unit.id, combat = this.combat();
    if (combat?.attacker === key) return 'attacker';
    if (combat?.target === key) return 'defender';
    return !this.locked() && this.interceptors().has(key) ? 'interceptor' : null;
  }
  /** While the player aims, what is neither a target nor the selection steps back (it stays clickable). */
  protected dim(key: string, unit?: UnitView): boolean {
    const mark = this.mark(key);
    return this.focus() && mark !== 'targetable' && mark !== 'selected' && !(unit && this.role(unit));
  }
  protected rest(key: string): boolean { return this.yourTurn() && !this.mark(key) && !this.focus(); }
  protected label(key: string, fallback: string): string { return this.labels()[key] || fallback; }
  /**
   * Stands the card's big copy right above the hand, over its own place but inside the hand's width, as large as
   * the table leaves room for: it covers the board for a moment, never another card of the hand.
   */
  private peekAt(key: string): void {
    const hand = this.hand().nativeElement, slot = hand.querySelector<HTMLElement>('[data-key="' + key + '"]');
    if (!slot || this.focus() || this.inspect.card()) { this.peekPlace.set(null); return; }
    const box = hand.getBoundingClientRect(), rect = slot.getBoundingClientRect();
    const room = (box.top - (hand.parentElement ?? hand).getBoundingClientRect().top - 32) * 5 / 7;
    const width = Math.round(Math.max(160, Math.min(300, innerWidth * .66, room, Math.max(210, rect.width * 1.8))));
    const center = rect.left + rect.width / 2 - box.left;
    const left = Math.max(0, Math.min(box.width - width, center - width / 2));
    this.peekPlace.set({ key, left, width, fromX: center - left - width / 2, fromScale: rect.width / width });
  }
  private unpeek(key: string): void { if (this.peekPlace()?.key === key) this.peekPlace.set(null); }
  private unit(key: string): UnitView | undefined { return [...this.view().you.units, ...(this.view().opponent?.units ?? [])].find(unit => 'unit:' + unit.id === key); }
}
