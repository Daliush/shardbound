import { Component, inject, input, output } from '@angular/core';
import { UnitView } from '../api/protocol';
import { CardBook } from './card-book';
import { Mark } from './decision-groups';

@Component({
  selector: 'app-unit-tile',
  template: `
    @let u = unit();
    <button
      type="button"
      class="tile"
      [class.selectable]="mark() === 'selectable'"
      [class.selected]="mark() === 'selected'"
      [class.targetable]="mark() === 'targetable'"
      [disabled]="mark() === null"
      (click)="picked.emit()"
    >
      <span class="name">{{ book.name(u.card) }} #{{ u.id }}</span>
      <span class="defense">{{ u.defense }} / {{ u.maxDefense }}</span>
      <ul class="attacks">
        @for (attack of u.attacks; track attack.index) {
          <li>
            {{ attack.name ?? 'Attack' }} ({{ attack.cost }})
            @if (attack.damage !== null) {
              · {{ attack.damage }} dmg
            }
            @if (attack.echo !== null) {
              · Echo {{ attack.echo }}
            }
          </li>
        }
      </ul>
      <span class="badges">
        @if (u.token) {
          <span class="badge">token</span>
        }
        @if (u.arrivedThisTurn) {
          <span class="badge">arrived</span>
        }
        @if (u.hasAttackedThisTurn) {
          <span class="badge">attacked</span>
        }
        @if (u.hasInterceptedThisTurn) {
          <span class="badge">intercepted</span>
        }
        @if (u.frozen) {
          <span class="badge">frozen</span>
        }
        @if (u.anchorProtected) {
          <span class="badge">anchored</span>
        }
        @if (u.doomed) {
          <span class="badge">doomed</span>
        }
        @if (u.linkedTo !== null) {
          <span class="badge">linked to #{{ u.linkedTo }}</span>
        }
      </span>
    </button>
  `,
  styleUrl: './tile.css',
})
export class UnitTile {
  readonly unit = input.required<UnitView>();
  readonly mark = input<Mark>(null);
  readonly picked = output<void>();
  protected readonly book = inject(CardBook);
}
