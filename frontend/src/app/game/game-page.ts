import { Component, OnInit, computed, inject, input, linkedSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActionView } from '../api/protocol';
import { SeatStore, inviteLink } from '../api/seat-store';
import { DecisionGroups, ElementKey, Mark, locateIn } from './decision-groups';
import { DecisionPanel } from './decision-panel';
import { GameLog } from './game-log';
import { GameSocketService } from './game-socket.service';
import { HandCardTile } from './hand-card-tile';
import { PlayerPanel } from './player-panel';
import { RelicTile } from './relic-tile';
import { UnitTile } from './unit-tile';

/** The game screen: everything shown comes from the server's view and events. */
@Component({
  selector: 'app-game-page',
  imports: [RouterLink, DecisionPanel, GameLog, HandCardTile, PlayerPanel, RelicTile, UnitTile],
  providers: [GameSocketService],
  templateUrl: './game-page.html',
  styleUrl: './game-page.css',
})
export class GamePage implements OnInit {
  readonly gameId = input.required<string>();

  protected readonly socket = inject(GameSocketService);
  private readonly seats = inject(SeatStore);

  protected readonly token = computed(() => this.seats.token(this.gameId()));
  protected readonly invite = computed(() => {
    const code = this.seats.joinCode(this.gameId());
    return code === null ? null : inviteLink(this.gameId(), code);
  });

  protected readonly decision = computed(() => this.socket.view()?.decision ?? null);
  private readonly groups = computed(
    () => new DecisionGroups(this.decision()?.actions ?? [], locateIn(this.socket.view())),
  );
  private readonly sources = computed(() => this.groups().sources());
  private readonly targets = computed(() => this.groups().targets(this.selected()));

  /** The card or unit clicked first; reset by every new decision. */
  protected readonly selected = linkedSignal<string | undefined, ElementKey | null>({
    source: () => this.decision()?.id,
    computation: () => null,
  });
  /** Several actions share the clicked target (Overcharge, sacrifices): the player picks a button. */
  private readonly candidates = linkedSignal<string | undefined, ActionView[] | null>({
    source: () => this.decision()?.id,
    computation: () => null,
  });
  /** An answer is on its way: no second click until the server replies. */
  protected readonly sent = linkedSignal<unknown, boolean>({
    source: () => [this.decision()?.id, this.socket.rejection()],
    computation: () => false,
  });

  protected readonly buttons = computed(() => this.candidates() ?? this.groups().buttons(this.selected()));
  protected readonly hint = computed(() => {
    if (this.decision() === null || this.sent()) {
      return null;
    }
    if (this.candidates() !== null) {
      return 'Several actions match: pick one.';
    }
    if (this.selected() !== null) {
      return this.targets().size > 0 ? 'Click a highlighted target.' : null;
    }
    if (this.sources().size > 0) {
      return 'Click a highlighted card or unit to see what it can do.';
    }
    return this.targets().size > 0 ? 'Click a highlighted target.' : null;
  });

  ngOnInit(): void {
    const token = this.token();
    if (token !== null) {
      this.socket.connect(socketUrl(this.gameId(), token));
    }
  }

  protected mark(key: ElementKey): Mark {
    if (this.sent()) {
      return null;
    }
    if (this.selected() === key) {
      return 'selected';
    }
    if (this.targets().has(key)) {
      return 'targetable';
    }
    return this.sources().has(key) ? 'selectable' : null;
  }

  /** A target of the selection picks its action; a source becomes the selection, or is deselected. */
  protected click(key: ElementKey): void {
    if (this.targets().has(key)) {
      const picks = this.groups().pick(this.selected(), key);
      if (picks.length === 1) {
        this.choose(picks[0]);
      } else {
        this.candidates.set(picks);
      }
      return;
    }
    if (this.sources().has(key)) {
      this.selected.set(this.selected() === key ? null : key);
      this.candidates.set(null);
    }
  }

  protected cancel(): void {
    this.selected.set(null);
    this.candidates.set(null);
  }

  protected choose(action: ActionView): void {
    const decision = this.decision();
    if (decision !== null) {
      this.sent.set(true);
      this.socket.act(decision.id, action.index);
    }
  }
}

function socketUrl(gameId: string, token: string): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${location.host}/ws/games/${gameId}?token=${encodeURIComponent(token)}`;
}
