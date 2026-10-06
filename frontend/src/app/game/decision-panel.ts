import { Component, input, output } from '@angular/core';
import { ActionView, DecisionView, GameView } from '../api/protocol';

/** The engine's prompt, the buttons left once the board's clicks are taken out, and what is being waited for. */
@Component({
  selector: 'app-decision-panel',
  template: `
    @if (decision(); as decision) {
      <p class="prompt">{{ decision.prompt }}</p>
      @if (hint()) {
        <p class="hint">{{ hint() }}</p>
      }
      <div class="buttons">
        @for (action of buttons(); track action.index) {
          <button type="button" [disabled]="busy()" (click)="chosen.emit(action)">{{ action.label }}</button>
        }
        @if (canCancel()) {
          <button type="button" class="cancel" (click)="cancelled.emit()">Cancel</button>
        }
      </div>
    } @else if (waitingFor(); as waiting) {
      <p class="prompt">Waiting for your opponent ({{ waiting.kind.replace('_', ' ') }})…</p>
    }
    @if (rejection(); as rejection) {
      <p class="rejected">{{ rejection.message }}</p>
    }
  `,
  styles: `
    .buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .hint {
      color: var(--muted);
    }
    .rejected {
      color: var(--alert);
    }
  `,
})
export class DecisionPanel {
  readonly decision = input<DecisionView | null>(null);
  readonly waitingFor = input<GameView['waitingFor']>(null);
  readonly buttons = input<ActionView[]>([]);
  readonly hint = input<string | null>(null);
  readonly canCancel = input(false);
  readonly busy = input(false);
  readonly rejection = input<{ message: string } | null>(null);
  readonly chosen = output<ActionView>();
  readonly cancelled = output<void>();
}
