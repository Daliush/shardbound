import { Component, inject, input, output } from '@angular/core';
import { ActionView } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { Inspect } from '../cards/inspect';
import { RulesText } from '../cards/rules-text';
import { Icon } from '../core/icon';

/** What the battle line says: the engine's prompt or the selection in progress, and what a click would do. */
export interface Intent {
  tone: 'turn' | 'select' | 'target' | 'threat' | 'wait' | 'resolve' | 'done';
  title: string;
  detail?: string | null;
  card?: string | null;
}

export interface IntentOption { label: string; detail: string; value: number; active: boolean; }

@Component({
  selector: 'app-intent-bar',
  imports: [CardComponent, RulesText, Icon],
  template: `@let now = intent();
    <div class="say" [attr.data-tone]="now.tone" aria-live="polite">
      @if (now.card) { <button class="thumb quiet" aria-label="Inspect the card" (click)="inspect.open({ id: now.card })"><app-card [cardId]="now.card" mode="thumb" [interactiveTilt]="false" /></button> }
      @else { <span class="glyph"><app-icon [name]="glyph()" /></span> }
      <div class="words">
        <p class="title">{{ now.title }}</p>
        @if (now.detail) { <p class="detail"><app-rules-text [text]="now.detail" /></p> }
      </div>
    </div>
    @if (options().length || buttons().length || cancellable() || endTurn()) {
      <div class="act">
        @if (options().length) {
          <div class="options" role="radiogroup" aria-label="Attack">@for (option of options(); track option.value) {
            <button role="radio" [attr.aria-checked]="option.active" [class.active]="option.active" [disabled]="disabled()" (click)="option.active || pickOption.emit(option.value)"><app-icon name="sword" />{{ option.label }}<small><app-rules-text [text]="option.detail" /></small></button>
          }</div>
        }
        @for (action of buttons(); track action.index) {
          @let primary = $first && action.type !== 'decline_intercept';
          <button class="action" [class.primary]="primary" [disabled]="disabled()" (click)="choose.emit(action)">@if (primary) { {{ action.label }} } @else { <app-rules-text [text]="action.label" /> }</button>
        }
        @if (cancellable()) { <button class="quiet cancel" (click)="cancel.emit()">Cancel</button> }
        @if (endTurn(); as action) {
          <button class="end-turn" [class.hot]="hot()" [disabled]="disabled()" (click)="choose.emit(action)"><span>{{ action.label }}</span><app-icon name="chevron" /></button>
        }
      </div>
    }`,
  styleUrl: './intent-bar.css',
})
export class IntentBar {
  protected readonly inspect = inject(Inspect);
  readonly intent = input.required<Intent>();
  readonly buttons = input<ActionView[]>([]);
  readonly options = input<IntentOption[]>([]);
  readonly cancellable = input(false);
  readonly endTurn = input<ActionView | null>(null);
  readonly hot = input(false);
  readonly disabled = input(false);
  readonly choose = output<ActionView>();
  readonly pickOption = output<number>();
  readonly cancel = output<void>();
  protected glyph(): string {
    return ({ turn: 'shard', select: 'shard', target: 'sword', threat: 'shield', wait: 'hourglass', resolve: 'hourglass', done: 'check' } as const)[this.intent().tone];
  }
}
