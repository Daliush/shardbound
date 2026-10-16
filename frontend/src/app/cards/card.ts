import { Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { HandCardView, UnitView } from '../api/protocol';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { CardBook } from '../game/card-book';
import { Mark } from '../game/decision-groups';
import { CardArt } from './card-art';
import { RulesText } from './rules-text';

@Component({
  selector: 'app-card',
  imports: [Icon, RulesText],
  templateUrl: './card.html',
  styleUrls: ['./card.css', './card-states.css', './card-modes.css', './card-back.css'],
  host: { '[attr.data-mode]': 'mode()', '(pointermove)': 'point($event)', '(pointerleave)': 'resetTilt()', '(pointercancel)': 'resetTilt()' },
})
export class CardComponent {
  readonly cardId = input('');
  readonly hand = input<HandCardView | null>(null);
  readonly unit = input<UnitView | null>(null);
  readonly mode = input<'hand' | 'board' | 'inspect' | 'thumb'>('hand');
  readonly back = input(false);
  readonly mark = input<Mark>(null);
  readonly readyAttacks = input<number[]>([]);
  protected readonly book = inject(CardBook);
  protected readonly art = inject(CardArt);
  protected readonly settings = inject(Settings);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly failedArt = signal('');
  protected readonly pointer = signal({ x: 0, y: 0, glare: 50 });
  readonly definition = computed(() => this.book.card(this.cardId()));
  protected readonly displayLines = computed(() => (this.definition()?.text ?? []).map(line => {
    if (this.mode() !== 'board' || line.kind !== 'attack') return line;
    return { ...line, text: line.text.replace(/^.+?\((\d+ Shards?)\):/, '$1:').replace(/Deal (\d+) damage to the target\./g, '$1 damage.') };
  }));
  protected readonly faction = computed(() => this.definition()?.faction ?? this.cardId().split('.')[0] ?? 'neutral');
  protected readonly artSource = computed(() => this.failedArt() === this.cardId() ? this.art.placeholder(this.cardId(), this.faction()) : this.art.source(this.cardId(), this.faction()));
  protected readonly printedCost = computed(() => this.definition()?.fracture?.find(step => step.step === (this.hand()?.fractureStep ?? 1))?.cost ?? this.definition()?.cost);
  protected readonly cost = computed(() => this.hand()?.cost ?? this.printedCost());
  protected readonly transform = computed(() => {
    if (this.settings.reducedMotion() || this.mode() !== 'thumb') return this.back() ? 'rotateY(180deg)' : '';
    const tilt = this.settings.preferences().gyroscope ? this.settings.tilt() : this.pointer();
    return 'rotateX(' + -tilt.x + 'deg) rotateY(' + (tilt.y + (this.back() ? 180 : 0)) + 'deg)';
  });
  protected readonly states = computed(() => {
    const u = this.unit();
    if (!u) return [];
    return [
      ...(u.arrivedThisTurn ? [{ icon: 'hourglass', label: 'Arrived this turn' }] : []),
      ...(u.hasAttackedThisTurn ? [{ icon: 'sword', label: 'Already attacked' }] : []),
      ...(u.hasInterceptedThisTurn ? [{ icon: 'shield', label: 'Already intercepted' }] : []),
      ...(u.frozen ? [{ icon: 'frozen', label: 'Frozen' }] : []),
      ...(u.anchorProtected ? [{ icon: 'anchor', label: 'Anchor protected' }] : []),
      ...(u.doomed ? [{ icon: 'doom', label: 'Doomed' }] : []),
      ...(u.linkedTo !== null ? [{ icon: 'link', label: 'Linked to #' + u.linkedTo }] : []),
    ];
  });
  protected readonly accessibleText = computed(() => {
    const card = this.definition();
    if (this.back()) return 'Face-down card';
    return [card?.name ?? this.cardId(), card?.token ? 'Token' : '', card?.type, this.cost() === undefined ? '' : this.cost() + ' Shards', ...(card?.text.map(line => line.text) ?? [])].filter(Boolean).join('. ');
  });
  protected point(event: PointerEvent): void {
    if (this.mode() === 'inspect' || this.settings.reducedMotion()) return;
    const rect = this.element.nativeElement.getBoundingClientRect();
    this.pointer.set({ x: ((event.clientY - rect.top) / rect.height - .5) * 20, y: ((event.clientX - rect.left) / rect.width - .5) * 24, glare: (event.clientX - rect.left) / rect.width * 100 });
  }
  protected resetTilt(): void { this.pointer.set({ x: 0, y: 0, glare: 50 }); }
  protected signed(value: number): string { return (value < 0 ? '' : '+') + value; }
}


