import { Component, input, output } from '@angular/core';
import { PlayerBase } from '../api/protocol';
import { Mark } from './decision-groups';

/** A player's counters. Clickable when the player is a target. */
@Component({
  selector: 'app-player-panel',
  template: `
    @let p = player();
    <button
      type="button"
      class="panel"
      [class.targetable]="mark() === 'targetable'"
      [disabled]="mark() === null"
      (click)="picked.emit()"
    >
      <strong>{{ title() }}</strong>
      <span>{{ p.faction }}</span>
      <span>HP {{ p.hp }} / {{ p.maxHp }}</span>
      <span>
        Shards {{ p.shards }} / {{ p.maxShards }}
        @if (p.lockedNextTurn > 0) {
          ({{ p.lockedNextTurn }} locked)
        }
      </span>
      <span>Hand {{ handCount() }}</span>
      <span>Deck {{ p.deckCount }}</span>
      <span>Graveyard {{ p.graveyard.length }}</span>
      @if (p.fatigue > 0) {
        <span>Fatigue {{ p.fatigue }}</span>
      }
    </button>
  `,
  styles: `
    .panel {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      width: 100%;
      padding: 6px 8px;
      border: 1px solid var(--line);
      border-radius: 4px;
      background: var(--panel);
      color: inherit;
      font: inherit;
      text-align: left;
    }
    .panel:disabled {
      cursor: default;
    }
  `,
})
export class PlayerPanel {
  readonly title = input.required<string>();
  readonly player = input.required<PlayerBase>();
  readonly handCount = input.required<number>();
  readonly mark = input<Mark>(null);
  readonly picked = output<void>();
}
