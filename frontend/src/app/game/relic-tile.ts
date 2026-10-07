import { Component, computed, inject, input, output } from '@angular/core';
import { RelicView } from '../api/protocol';
import { CardBook } from './card-book';
import { CardTextLines } from './card-text-lines';
import { Mark } from './decision-groups';

/** A relic on the board, with all its lines: it has no attack to list. Clickable when it is a target. */
@Component({
  selector: 'app-relic-tile',
  imports: [CardTextLines],
  template: `
    @let r = relic();
    <button
      type="button"
      class="tile"
      [class.targetable]="mark() === 'targetable'"
      [disabled]="mark() === null"
      (click)="picked.emit()"
    >
      <span class="name">{{ book.name(r.card) }} #{{ r.id }}</span>
      <app-card-text-lines [lines]="text()" />
    </button>
  `,
  styleUrl: './tile.css',
})
export class RelicTile {
  readonly relic = input.required<RelicView>();
  readonly mark = input<Mark>(null);
  readonly picked = output<void>();
  protected readonly book = inject(CardBook);
  protected readonly text = computed(() => this.book.text(this.relic().card));
}
