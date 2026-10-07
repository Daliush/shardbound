import { Component, input } from '@angular/core';
import { TextLine } from '../api/protocol';

/** Lines of a card's rules text, as the engine wrote them (spec §9). */
@Component({
  selector: 'app-card-text-lines',
  template: `
    @for (line of lines(); track $index) {
      <span class="line">{{ line.text }}</span>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 2px;
      font-size: 0.85em;
    }
  `,
})
export class CardTextLines {
  readonly lines = input.required<TextLine[]>();
}
