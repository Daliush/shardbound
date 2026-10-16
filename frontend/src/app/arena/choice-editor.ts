import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { ActionView, CardRef, DecisionView, GameView } from '../api/protocol';
import { CardBook } from '../game/card-book';
import { CardComponent } from '../cards/card';
import { Icon } from '../core/icon';

/** A draft only selects an existing server action; it never constructs one. */
export function matchChoice(actions: ActionView[], values: number[], ordered: boolean): ActionView | undefined {
  return actions.find(action => {
    const candidate = ordered ? action.order : action.cards;
    return candidate?.length === values.length && (ordered ? candidate.every((value,index) => value === values[index]) : candidate.every(value => values.includes(value)));
  });
}

@Component({
  selector:'app-choice-editor', imports:[CardComponent,Icon],
  template:`@if (decision().kind === 'choose_cards') {
    <div class="card-choices">@for (card of cards(); track card.id) { <label><input type="checkbox" [checked]="values().includes(card.id)" (change)="toggle(card.id)" /><app-card [cardId]="card.card" mode="thumb" /><span>{{ book.name(card.card) }} #{{ card.id }}</span></label> }</div>
  } @else {
    <p class="muted">Drag attacks into order, or use the arrow buttons.</p>
    <ol>@for (value of values(); track value; let index = $index) {
      <li class="order-item" [attr.data-order]="value" (pointerdown)="dragged.set(value)"><span>{{ orderLabel(value) }}</span><button class="quiet" [disabled]="index === 0" [attr.aria-label]="'Move ' + orderLabel(value) + ' earlier'" (click)="move(index,-1)"><app-icon name="chevron" class="up" /></button><button class="quiet" [disabled]="index === values().length - 1" [attr.aria-label]="'Move ' + orderLabel(value) + ' later'" (click)="move(index,1)"><app-icon name="chevron" class="down" /></button></li>
    }</ol>
  }
  @if (matched(); as action) { <button class="primary confirm-choice" [disabled]="disabled()" (click)="chosen.emit(action)">{{ action.label }}</button> }
  @else { <p role="status" class="muted">Select a combination offered by this decision.</p> }`,
  styles:`:host { display:block; margin-block:1rem 1.5rem; }.card-choices { display:grid; gap:.5rem; }.card-choices label { display:flex; align-items:center; gap:.75rem; min-height:52px; padding:.25rem; border-bottom:1px solid var(--line); }.card-choices app-card { --card-width:32px; }ol { padding-left:1.5rem; }.order-item { touch-action:none; padding:.5rem; display:flex; align-items:center; gap:.3rem; border-bottom:1px solid var(--line); }.order-item span { flex:1; }.up { rotate:-90deg; }.down { rotate:90deg; }.confirm-choice { width:100%; margin-top:1rem; }`,
  host:{'(window:pointerup)':'release($event)','(window:pointercancel)':'dragged.set(null)'},
})
export class ChoiceEditor {
  readonly decision = input.required<DecisionView>(); readonly view = input.required<GameView>(); readonly disabled = input(false); readonly chosen = output<ActionView>();
  protected readonly book = inject(CardBook);
  protected readonly values = linkedSignal({source:() => this.decision().id,computation:() => this.decision().kind === 'choose_order' ? [...(this.decision().actions[0]?.order ?? [])] : [] as number[]});
  protected readonly dragged = signal<number | null>(null);
  protected readonly matched = computed(() => matchChoice(this.decision().actions,this.values(),this.decision().kind === 'choose_order'));
  protected readonly cards = computed(() => {
    const ids = new Set(this.decision().actions.flatMap(action => action.cards ?? []));
    const view = this.view();
    return [...view.you.hand,...view.you.units,...view.you.graveyard,...(view.opponent?.units ?? [])].filter((card:CardRef) => ids.has(card.id));
  });
  protected toggle(id: number): void { this.values.update(values => values.includes(id) ? values.filter(value => value !== id) : [...values,id]); }
  protected move(index: number, by: number): void {
    const values = [...this.values()], next = index + by;
    if (next < 0 || next >= values.length) return;
    [values[index],values[next]] = [values[next],values[index]]; this.values.set(values);
  }
  protected drop(value: number): void { const from = this.values().indexOf(this.dragged() ?? -1), to = this.values().indexOf(value); if (from >= 0 && to >= 0) this.move(from,to-from); this.dragged.set(null); }
  protected release(event: PointerEvent): void { const target = document.elementFromPoint(event.clientX,event.clientY)?.closest<HTMLElement>('[data-order]')?.dataset['order']; if (target !== undefined) this.drop(Number(target)); else this.dragged.set(null); }
  protected orderLabel(value: number): string {
    const first = this.decision().actions[0], names = first?.label.match(/^Replay (.+) first, then (.+)$/)?.slice(1);
    return names?.[first?.order?.indexOf(value) ?? -1] ?? 'Attack ' + (value + 1);
  }
}
