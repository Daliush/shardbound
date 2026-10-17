import { Component, computed, inject, input, output } from '@angular/core';
import { Deck } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { Inspect } from '../cards/inspect';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';

@Component({
  selector: 'app-deck-picker',
  imports: [CardComponent, Icon],
  template: `<div class="deck-options" role="group" [attr.aria-label]="label()">
    @for (deck of decks(); track deck.id) {
      <button type="button" class="deck-box" [attr.data-faction]="deck.faction" [class.chosen]="selected() === deck.id" [attr.aria-pressed]="selected() === deck.id" (click)="chosen.emit(deck.id)">
        <div class="deck-art"><app-card [cardId]="face(deck)" mode="thumb" /><app-icon [name]="deck.faction" /></div>
        <span class="deck-name">{{ deck.name }}</span><span class="deck-meta">{{ count(deck) }} cards <app-icon [name]="selected() === deck.id ? 'check' : 'chevron'" /></span>
      </button>
    }
  </div>
  @if (current(); as deck) { <div class="deck-detail"><p>{{ deck.description }}</p><details><summary>Explore the deck</summary><div class="card-list">@for (entry of deck.cards; track entry.card) { <button type="button" class="quiet" (click)="inspect.open({id: entry.card})"><span class="number">{{ entry.count }}×</span>{{ book.name(entry.card) }}<app-icon name="eye" /></button> }</div></details></div> }`,
  styleUrl: './deck-picker.css',
})
export class DeckPicker {
  readonly decks = input<Deck[]>([]);
  readonly selected = input('');
  readonly label = input('Your deck');
  readonly chosen = output<string>();
  protected readonly book = inject(CardBook);
  protected readonly inspect = inject(Inspect);
  protected readonly current = computed(() => this.decks().find(deck => deck.id === this.selected()));
  protected face(deck: Deck): string { return deck.cards.find(entry => entry.card.startsWith(deck.faction + '.'))?.card ?? ''; }
  protected count(deck: Deck): number { return deck.cards.reduce((sum, entry) => sum + entry.count, 0); }
}

