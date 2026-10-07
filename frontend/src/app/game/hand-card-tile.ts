import { Component, computed, inject, input, output } from '@angular/core';
import { HandCardView } from '../api/protocol';
import { CardBook } from './card-book';
import { CardTextLines } from './card-text-lines';
import { Mark } from './decision-groups';

/** A card in your hand, with its full rules text. */
@Component({
  selector: 'app-hand-card-tile',
  imports: [CardTextLines],
  template: `
    @let c = card();
    <button
      type="button"
      class="tile"
      [class.selectable]="mark() === 'selectable'"
      [class.selected]="mark() === 'selected'"
      [class.targetable]="mark() === 'targetable'"
      [disabled]="mark() === null"
      (click)="picked.emit()"
    >
      <span class="name">{{ book.name(c.card) }}</span>
      <span class="cost">{{ c.cost }} {{ c.cost === 1 ? 'Shard' : 'Shards' }}</span>
      <span class="type">{{ details()?.type }}{{ details()?.defense ? ' · ' + details()?.defense + ' def' : '' }}</span>
      @if (c.fractureStep !== null) {
        <span class="badge">Fracture step {{ c.fractureStep }}</span>
      }
      @for (keyword of details()?.keywords ?? []; track keyword) {
        <span class="badge">{{ keyword }}</span>
      }
      <app-card-text-lines [lines]="details()?.text ?? []" />
    </button>
  `,
  styleUrl: './tile.css',
})
export class HandCardTile {
  readonly card = input.required<HandCardView>();
  readonly mark = input<Mark>(null);
  readonly picked = output<void>();
  protected readonly book = inject(CardBook);
  protected readonly details = computed(() => this.book.card(this.card().card));
}
