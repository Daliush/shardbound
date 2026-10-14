import { Component, computed, inject, signal } from '@angular/core';
import { CardComponent } from '../../cards/card';
import { Inspect } from '../../cards/inspect';
import { CardBook } from '../../game/card-book';
import { UnitView } from '../../api/protocol';
import { aView } from '../../testing/views';

@Component({
  selector: 'app-sandbox',
  imports: [CardComponent],
  template: `<section class="page"><h1>The stage</h1><p class="muted">Development sandbox · synthetic board states</p>
    <div class="controls"><label>Card <select (change)="cardId.set(value($event))">@for (card of book.cards(); track card.id) { <option [value]="card.id" [selected]="card.id === cardId()">{{ card.name }}</option> }</select></label><button (click)="back.set(!back())">Flip card</button><button (click)="inspect.open({ id: currentId() })">Inspect</button></div>
    <div class="gallery"><app-card [cardId]="currentId()" mode="inspect" [back]="back()" /><div class="states">@for (unit of examples(); track unit.id) { <div><app-card [cardId]="unit.card" [unit]="unit" mode="board" [mark]="unit.id === 8 ? 'selectable' : null" /><p>{{ names[unit.id - 1] }}</p></div> }</div></div>
  </section>`,
  styles: `.controls { display: flex; flex-wrap: wrap; gap: 1rem; margin-bottom: 2rem; } label { display: flex; align-items: center; gap: .5rem; } .gallery { display: flex; flex-wrap: wrap; gap: 3rem; align-items: start; } .states { display: grid; grid-template-columns: repeat(3, 1fr); gap: 2rem; } .states p { margin: .8rem 0; color: var(--muted); } @media(max-width:600px) { .states { gap: 1rem; --unit-width: 95px; } }`,
})
export class Sandbox {
  protected readonly book = inject(CardBook);
  protected readonly inspect = inject(Inspect);
  protected readonly cardId = signal('ember.ash-warden');
  protected readonly currentId = computed(() => this.book.card(this.cardId())?.id ?? this.book.cards()[0]?.id ?? 'ember.ash-warden');
  protected readonly back = signal(false);
  protected readonly names = ['Frozen', 'Doomed', 'Anchored', 'Linked', 'Modified', 'Token', 'Arrived', 'Playable', 'Attacked'];
  protected readonly examples = computed(() => {
    const base = aView(0).you.units[0];
    const changes: Partial<UnitView>[] = [{ frozen: true }, { doomed: true, defense: 0 }, { anchorProtected: true }, { linkedTo: 3 }, { modifiers: [{ attackDamage: 2, defense: 0, duration: 'end_of_turn' }] }, { token: true, card: 'root.sprout' }, { arrivedThisTurn: true }, {}, { hasAttackedThisTurn: true }];
    return changes.map((change, index) => ({ ...base, card: this.currentId(), id: index + 1, ...change }));
  });
  protected value(event: Event): string { return (event.target as HTMLSelectElement).value; }
}
