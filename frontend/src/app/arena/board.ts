import { BoardLinks } from './links';
import { Component, computed, inject, input, output } from '@angular/core';
import { ActionView, GameView, Side, UnitView } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { Inspect } from '../cards/inspect';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';
import { Mark } from '../game/decision-groups';
import { PlayerHud } from './player-hud';
import { AnchorDirective } from './stage/anchors';
import { CardPreview } from '../cards/card-preview';
import { PendingCombat } from './combat';

@Component({
  selector: 'app-arena-board',
  imports: [CardComponent, CardPreview, Icon, PlayerHud, AnchorDirective, BoardLinks],
  templateUrl: './board.html',
  styleUrls: ['./board.css', './board-mobile.css', './board-combat.css'],
})
export class ArenaBoard {
  readonly view = input.required<GameView>();
  readonly marks = input<Record<string, Mark>>({});
  readonly labels = input<Record<string, string>>({});
  readonly selected = input<string | null>(null);
  readonly locked = input(false);
  readonly combat = input<PendingCombat | null>(null);
  readonly action = output<string>();
  readonly press = output<{ key: string; event: PointerEvent; attack?: number }>();
  readonly graveyard = output<Side>();
  readonly handOpened = output<void>();
  protected readonly inspect = inject(Inspect);
  protected readonly book = inject(CardBook);
  protected readonly rows = computed(() => [
    { side: 'opponent' as const, player: this.view().opponent },
    { side: 'you' as const, player: this.view().you },
  ]);
  protected readonly opponentHand = computed(() => Array.from({ length: this.view().opponent?.handCount ?? 0 }, (_, index) => index));
  protected readonly actions = computed(() => this.locked() ? [] : this.view().decision?.actions ?? []);
  protected mark(key: string): Mark { return this.locked() ? null : this.marks()[key] ?? null; }
  protected pick(key: string, cardId?: string): void {
    if (this.mark(key)) this.action.emit(key);
    else if (cardId && !this.locked()) this.inspect.open({ id: cardId, unit: [...this.view().you.units, ...(this.view().opponent?.units ?? [])].find(unit => 'unit:' + unit.id === key), hand: this.view().you.hand.find(card => 'card:' + card.id === key) });
  }
  protected attacks(unit: UnitView): ActionView[] {
    return this.actions().filter(action => action.type === 'attack' && action.attacker === unit.id).filter((action, index, actions) => actions.findIndex(other => other.attackIndex === action.attackIndex) === index);
  }
  protected attackLabel(unit: UnitView, index: number | undefined): string { return unit.attacks.find(attack => attack.index === index)?.name ?? 'Attack'; }
  protected attackDetails(unit: UnitView, index: number | undefined) { return unit.attacks.find(attack => attack.index === index); }
  protected readyAttacks(unit: UnitView): number[] { return this.attacks(unit).map(action => action.attackIndex!); }
  protected canIntercept(unit: UnitView): boolean { return this.actions().some(action => action.type === 'intercept' && action.interceptor === unit.id); }
  protected isAttacker(unit: UnitView): boolean { return this.combat()?.attacker === 'unit:' + unit.id; }
  protected isDefender(unit: UnitView): boolean { return this.combat()?.target === 'unit:' + unit.id; }
}


