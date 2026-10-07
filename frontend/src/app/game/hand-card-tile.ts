import { Component, computed, inject, input, output } from '@angular/core';
import { HandCardView } from '../api/protocol';
import { CardBook } from './card-book';
import { Mark } from './decision-groups';

/** A card in your hand. Its rules text comes with slice 3. */
@Component({
  selector: 'app-hand-card-tile',
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
