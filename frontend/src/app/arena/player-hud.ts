import { Component, computed, input, output } from '@angular/core';
import { PlayerBase, Side } from '../api/protocol';
import { Icon } from '../core/icon';
import { Mark } from '../game/decision-groups';
import { AnchorDirective } from './stage/anchors';

@Component({
  selector: 'app-player-hud',
  imports: [Icon, AnchorDirective],
  template: `@let p = player();
    <div class="identity"><app-icon [name]="p.faction" /><div><strong>{{ side() === 'you' ? 'You' : 'Opponent' }}</strong><span>{{ p.faction }}<span class="hand-count"> · {{ handCount() }} in hand</span></span></div></div>
    <button class="health" [class.targetable]="mark() === 'targetable'" [class.low]="p.hp <= p.maxHp * .3" [appAnchor]="'player:' + side()" [attr.data-key]="'player:' + side()" [attr.aria-label]="label() || (side() === 'you' ? 'Your' : 'Opponent') + ' health: ' + p.hp + ' of ' + p.maxHp" (click)="picked.emit()">
      <div class="health-numbers"><app-icon name="heart" /><strong>{{ p.hp }}</strong><span>/ {{ p.maxHp }} <b>HP</b></span></div>
      <span class="health-track"><span [style.transform]="'scaleX(' + healthPercent() / 100 + ')' "></span></span>
    </button>
    <div class="resources" [appAnchor]="'shards:' + side()" [attr.aria-label]="p.shards + ' of ' + p.maxShards + ' Shards; ' + p.lockedNextTurn + ' locked next turn'">
      <div class="shard-numbers"><app-icon name="shard" /><strong>{{ p.shards }}</strong><span>/ {{ p.maxShards }} <b>Shards</b></span>@if (p.lockedNextTurn) { <span class="lock-count"><app-icon name="lock" />{{ p.lockedNextTurn }} next turn</span> }</div>
      <div class="shard-track">@for (pip of pips(); track pip) { <span [class.lit]="pip < p.shards"></span> }</div>
    </div>`,
  styleUrl: './player-hud.css',
  host: { '[attr.data-faction]': 'player().faction', '[class.active]': 'active()' },
})
export class PlayerHud {
  readonly player = input.required<PlayerBase>();
  readonly side = input.required<Side>();
  readonly handCount = input(0);
  readonly active = input(false);
  readonly mark = input<Mark>(null);
  readonly label = input('');
  readonly picked = output<void>();
  protected readonly pips = computed(() => Array.from({ length: this.player().maxShards }, (_, i) => i));
  protected readonly healthPercent = computed(() => Math.max(0, Math.min(100, this.player().hp / Math.max(1, this.player().maxHp) * 100)));
}
