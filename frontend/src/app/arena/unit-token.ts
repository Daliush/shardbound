import { Component, computed, inject, input, signal } from '@angular/core';
import { UnitView } from '../api/protocol';
import { CardArt } from '../cards/card-art';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';
import { Mark } from '../game/decision-groups';
import { Projection } from './combat';

export type CombatRole = 'attacker' | 'defender' | 'interceptor' | null;

/** A unit on the board: its art, name, attacks and defense; its full text is one hover or long press away. */
@Component({
  selector: 'app-unit-token',
  imports: [Icon],
  templateUrl: './unit-token.html',
  styleUrls: ['./unit-token.css', './unit-token-overlay.css', './unit-token-marks.css'],
  host: {
    '[attr.data-faction]': 'faction()', '[attr.data-mark]': 'mark()', '[attr.data-role]': 'role()',
    '[class.dim]': 'dim()', '[class.frozen]': 'unit().frozen', '[class.doomed]': 'unit().doomed',
    '[class.spent]': 'spent()', '[class.fresh]': 'unit().arrivedThisTurn',
  },
})
export class UnitToken {
  readonly unit = input.required<UnitView>();
  readonly mark = input<Mark>(null);
  readonly projection = input<Projection | null>(null);
  readonly role = input<CombatRole>(null);
  readonly attackIndex = input<number | null>(null);
  readonly dim = input(false);
  protected readonly book = inject(CardBook);
  private readonly art = inject(CardArt);
  private readonly failedArt = signal('');
  protected readonly faction = computed(() => this.book.card(this.unit().card)?.faction ?? this.unit().card.split('.')[0]);
  protected readonly artSource = computed(() => this.failedArt() === this.unit().card ? this.art.placeholder(this.unit().card, this.faction()) : this.art.source(this.unit().card, this.faction()));
  protected readonly spent = computed(() => this.unit().hasAttackedThisTurn && this.unit().controller === 'you');
  protected readonly damaged = computed(() => this.unit().defense < this.unit().maxDefense);
  protected readonly states = computed(() => {
    const u = this.unit();
    return [
      ...(u.arrivedThisTurn ? [{ icon: 'hourglass', label: 'Arrived this turn: it attacks next turn' }] : []),
      ...(u.frozen ? [{ icon: 'frozen', label: 'Frozen: it can neither attack nor intercept' }] : []),
      ...(u.anchorProtected ? [{ icon: 'anchor', label: 'Anchor: it cannot leave the board' }] : []),
      ...(u.doomed ? [{ icon: 'doom', label: 'Doomed: destroyed at end of turn unless healed' }] : []),
      ...(u.linkedTo !== null ? [{ icon: 'link', label: 'Linked to #' + u.linkedTo + ': damage is shared' }] : []),
      ...(u.hasInterceptedThisTurn ? [{ icon: 'shield', label: 'Already intercepted this turn' }] : []),
    ];
  });
  protected readonly modifier = computed(() => {
    const total = this.unit().modifiers.reduce((sum, m) => ({ attack: sum.attack + m.attackDamage, defense: sum.defense + m.defense, temporary: sum.temporary || m.duration === 'end_of_turn' }), { attack: 0, defense: 0, temporary: false });
    return total.attack || total.defense ? { text: signed(total.attack) + '/' + signed(total.defense), temporary: total.temporary } : null;
  });
  protected readonly roleLabel = computed(() => ({ attacker: 'Attacking', defender: 'Under attack', interceptor: 'Can intercept' } as Record<string, string>)[this.role() ?? ''] ?? null);
  protected failed(): void { this.failedArt.set(this.unit().card); }
}

function signed(value: number): string { return (value < 0 ? '−' + -value : '+' + value); }
