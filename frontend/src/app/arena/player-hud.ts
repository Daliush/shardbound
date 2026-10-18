import { Component, computed, inject, input, output } from '@angular/core';
import { PlayerBase, Side } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { CardPreview } from '../cards/card-preview';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';
import { Mark } from '../game/decision-groups';
import { Projection } from './combat';
import { AnchorDirective } from './stage/anchors';

/** One player's state at a glance: who, HP, Shards, the piles, the relics. The portrait is the player as a target. */
@Component({
  selector: 'app-player-hud',
  imports: [Icon, AnchorDirective, CardComponent, CardPreview],
  templateUrl: './player-hud.html',
  styleUrls: ['./player-hud.css', './player-hud-resources.css', './player-hud-mobile.css'],
  host: { '[attr.data-faction]': 'player().faction', '[attr.data-side]': 'side()', '[class.active]': 'active()' },
})
export class PlayerHud {
  readonly player = input.required<PlayerBase>();
  readonly side = input.required<Side>();
  readonly handCount = input(0);
  readonly active = input(false);
  readonly marks = input<Record<string, Mark>>({});
  readonly labels = input<Record<string, string>>({});
  readonly projections = input<Record<string, Projection>>({});
  readonly pick = output<string>();
  readonly graveyard = output<void>();
  protected readonly book = inject(CardBook);
  protected readonly key = computed(() => 'player:' + this.side());
  protected readonly mark = computed(() => this.marks()[this.key()] ?? null);
  protected readonly projection = computed(() => this.projections()[this.key()] ?? null);
  protected readonly name = computed(() => this.side() === 'you' ? 'You' : 'Opponent');
  protected readonly health = computed(() => Math.max(0, Math.min(1, this.player().hp / Math.max(1, this.player().maxHp))));
  protected readonly after = computed(() => {
    const projection = this.projection();
    return projection?.after === null || projection?.after === undefined ? null : projection.after / Math.max(1, this.player().maxHp);
  });
  protected readonly pips = computed(() => Array.from({ length: this.player().maxShards }, (_, index) => index < this.player().shards));
}
