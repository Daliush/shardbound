import { Component, computed, inject, input, output } from '@angular/core';
import { GameView, Side, UnitView } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { CardPreview } from '../cards/card-preview';
import { Inspect } from '../cards/inspect';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';
import { Mark } from '../game/decision-groups';
import { PendingCombat, Projection } from './combat';
import { Aim, BoardLinks } from './links';
import { PlayerHud } from './player-hud';
import { AnchorDirective } from './stage/anchors';
import { CombatRole, UnitToken } from './unit-token';

export interface Press { key: string; event: PointerEvent; attack?: number; }

/**
 * The table, top to bottom: the opponent, their units, the battle line (projected by the page), your units, you,
 * your hand. It draws what the page decided; a click on something marked is an action, anything else inspects.
 */
@Component({
  selector: 'app-arena-board',
  imports: [CardComponent, CardPreview, Icon, PlayerHud, AnchorDirective, BoardLinks, UnitToken],
  templateUrl: './board.html',
  styleUrls: ['./board.css', './board-hand.css', './board-mobile.css'],
})
export class ArenaBoard {
  readonly view = input.required<GameView>();
  readonly marks = input<Record<string, Mark>>({});
  readonly labels = input<Record<string, string>>({});
  readonly projections = input<Record<string, Projection>>({});
  readonly selected = input<string | null>(null);
  readonly attackIndex = input<number | null>(null);
  readonly locked = input(false);
  readonly focus = input(false);
  readonly combat = input<PendingCombat | null>(null);
  readonly aim = input<Aim | null>(null);
  readonly action = output<string>();
  readonly press = output<Press>();
  readonly hover = output<string | null>();
  readonly graveyard = output<Side>();
  protected readonly inspect = inject(Inspect);
  protected readonly book = inject(CardBook);
  protected readonly lockedMarks = computed(() => this.locked() ? {} : this.marks());
  protected readonly yourTurn = computed(() => !this.locked() && this.view().decision?.kind === 'main');
  protected readonly interceptors = computed(() => new Set((this.view().decision?.actions ?? []).filter(action => action.type === 'intercept').map(action => 'unit:' + action.interceptor)));
  protected mark(key: string): Mark { return this.lockedMarks()[key] ?? null; }
  protected pick(key: string, cardId?: string): void {
    if (this.mark(key)) this.action.emit(key);
    else if (cardId && !this.locked()) this.inspect.open({ id: cardId, unit: this.unit(key), hand: this.view().you.hand.find(card => 'card:' + card.id === key) });
  }
  protected role(unit: UnitView): CombatRole {
    const key = 'unit:' + unit.id, combat = this.combat();
    if (combat?.attacker === key) return 'attacker';
    if (combat?.target === key) return 'defender';
    return !this.locked() && this.interceptors().has(key) ? 'interceptor' : null;
  }
  /** While the player aims, what is neither a target nor the selection steps back (it stays clickable). */
  protected dim(key: string, unit?: UnitView): boolean {
    const mark = this.mark(key);
    return this.focus() && mark !== 'targetable' && mark !== 'selected' && !(unit && this.role(unit));
  }
  protected rest(key: string): boolean { return this.yourTurn() && !this.mark(key) && !this.focus(); }
  protected label(key: string, fallback: string): string { return this.labels()[key] || fallback; }
  private unit(key: string): UnitView | undefined { return [...this.view().you.units, ...(this.view().opponent?.units ?? [])].find(unit => 'unit:' + unit.id === key); }
}
