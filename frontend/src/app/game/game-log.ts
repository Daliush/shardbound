import { Component, ElementRef, afterRenderEffect, input, viewChild } from '@angular/core';
import { EventView } from '../api/protocol';

/** Every event's sentence with its rule IDs, newest at the bottom. */
@Component({
  selector: 'app-game-log',
  template: `
    <h2>Game log</h2>
    <ol #list>
      @for (event of events(); track $index) {
        <li><span class="rules">[{{ event.rules.join(', ') }}]</span> {{ event.text }}</li>
      }
    </ol>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
    h2 {
      margin: 0 0 4px;
      font-size: 1rem;
    }
    ol {
      flex: 1;
      overflow-y: auto;
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: 0.85em;
    }
    .rules {
      color: var(--muted);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class GameLog {
  readonly events = input<EventView[]>([]);
  private readonly list = viewChild.required<ElementRef<HTMLElement>>('list');

  constructor() {
    afterRenderEffect(() => {
      this.events();
      const list = this.list().nativeElement;
      list.scrollTop = list.scrollHeight;
    });
  }
}
