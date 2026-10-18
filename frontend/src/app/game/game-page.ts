import { Component, DestroyRef, OnInit, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ActionView, DecisionView, GameView, PlayerBase, Side } from '../api/protocol';
import { SeatStore, inviteLink } from '../api/seat-store';
import { ArenaBoard, Press } from '../arena/board';
import { ChoiceEditor } from '../arena/choice-editor';
import { Projection, attackOf, interceptable, pendingCombat, projectAttack, unitOf } from '../arena/combat';
import { resolveGesture } from '../arena/gestures';
import { Intent, IntentBar, IntentOption } from '../arena/intent-bar';
import { Aim } from '../arena/links';
import { Anchors } from '../arena/stage/anchors';
import { Director } from '../arena/stage/director';
import { Feedback } from '../arena/stage/feedback';
import { ArenaStage } from '../arena/stage/stage-player';
import { StageOverlay } from '../arena/stage/stage-overlay';
import { CardComponent } from '../cards/card';
import { CardPreview } from '../cards/card-preview';
import { Inspect } from '../cards/inspect';
import { Ambience } from '../core/ambience';
import { Icon } from '../core/icon';
import { SettingsPanel } from '../core/settings-panel';
import { ThemeToggle } from '../core/theme-toggle';
import { CardBook } from './card-book';
import { DecisionGroups, Mark, locateIn, sourceOf } from './decision-groups';
import { GameLog } from './game-log';
import { GameSocketService } from './game-socket.service';

/** Actions answered somewhere else than the battle line: on the board, in the opening hand or in the choice tray. */
const ELSEWHERE = ['end_turn', 'choose_cards', 'choose_order', 'keep_hand', 'mulligan'];

@Component({
  selector: 'app-game-page',
  imports: [RouterLink, ArenaBoard, ChoiceEditor, IntentBar, StageOverlay, CardComponent, CardPreview, Icon, SettingsPanel, ThemeToggle, GameLog],
  providers: [GameSocketService, Anchors, ArenaStage, Feedback],
  templateUrl: './game-page.html',
  styleUrls: ['./game-page.css', './game-page-overlays.css', './game-page-mobile.css'],
  host: { '(window:pointermove)': 'move($event)', '(window:pointerup)': 'release($event)', '(window:pointercancel)': 'abortGesture()', '(window:keydown.escape)': 'cancel()', '(document:visibilitychange)': 'visibility()' },
})
export class GamePage implements OnInit {
  readonly gameId = input.required<string>();
  protected readonly socket = inject(GameSocketService);
  protected readonly stage = inject(ArenaStage);
  protected readonly director = new Director(this.stage);
  private readonly seats = inject(SeatStore);
  private readonly inspect = inject(Inspect);
  private readonly feedback = inject(Feedback);
  private readonly ambience = inject(Ambience);
  private readonly book = inject(CardBook);
  private readonly destroy = inject(DestroyRef);
  protected readonly token = computed(() => this.seats.token(this.gameId()));
  protected readonly invite = computed(() => {
    const code = this.seats.joinCode(this.gameId());
    return code === null ? null : inviteLink(this.gameId(), code);
  });
  protected readonly view = computed(() => {
    const view = this.director.shownView();
    if (!view) return null;
    const counters = this.stage.counters();
    const player = <T extends PlayerBase>(p: T, side: Side): T => ({
      ...p, hp: counters['hp:' + side] ?? p.hp, shards: counters['shards:' + side] ?? p.shards,
      deckCount: counters['deck:' + side] ?? p.deckCount,
      units: p.units.map(unit => ({ ...unit, defense: counters['defense:' + unit.id] ?? unit.defense })),
    });
    return { ...view, you: player(view.you, 'you'), opponent: view.opponent && player(view.opponent, 'opponent') };
  });
  protected readonly ready = computed(() => !this.director.busy() && this.socket.synchronized() && this.socket.status() === 'open' && this.view()?.version === this.socket.view()?.version);
  protected readonly decision = computed(() => this.ready() ? this.view()?.decision ?? null : null);
  protected readonly selected = linkedSignal<string | undefined, string | null>({ source: () => this.decision()?.id, computation: () => null });
  protected readonly candidates = linkedSignal<string | undefined, ActionView[] | null>({ source: () => this.decision()?.id, computation: () => null });
  protected readonly attackIndex = linkedSignal<string | undefined, number | null>({ source: () => this.decision()?.id, computation: () => null });
  protected readonly sent = linkedSignal<unknown, boolean>({ source: () => [this.decision()?.id, this.socket.rejection()], computation: () => false });
  protected readonly hovered = signal<string | null>(null);
  private readonly actions = computed(() => (this.decision()?.actions ?? []).filter(action => this.attackIndex() === null || action.type !== 'attack' || action.attackIndex === this.attackIndex()));
  private readonly groups = computed(() => new DecisionGroups(this.actions(), locateIn(this.view())));
  private readonly targets = computed(() => this.groups().targets(this.selected()));
  protected readonly buttons = computed(() => (this.candidates() ?? this.groups().buttons(this.selected())).filter(action => !ELSEWHERE.includes(action.type)));
  protected readonly combat = computed(() => pendingCombat(this.view(), this.director.history()));
  protected readonly endTurn = computed(() => this.actions().find(action => action.type === 'end_turn') ?? null);
  protected readonly opening = computed(() => this.decision()?.kind === 'mulligan' ? this.decision()!.actions : []);
  /** Nothing left to play or attack with: End turn is the only move, so it calls for attention. */
  protected readonly idle = computed(() => this.decision()?.kind === 'main' && !this.selected() && this.groups().sources().size === 0);
  protected readonly marks = computed(() => {
    const result: Record<string, Mark> = {};
    if (this.sent() || !this.ready()) return result;
    this.groups().sources().forEach(key => result[key] = 'selectable');
    this.targets().forEach(key => result[key] = 'targetable');
    if (this.decision()?.kind === 'intercept' && this.selected() && this.combat()) result[this.combat()!.target] = 'targetable';
    if (this.selected()) result[this.selected()!] = 'selected';
    return result;
  });
  protected readonly labels = computed(() => {
    const labels: Record<string, string> = {};
    for (const action of this.actions()) {
      const key = sourceOf(action);
      if (key) labels[key] = action.label;
    }
    for (const key of this.targets()) labels[key] = this.groups().pick(this.selected(), key).map(action => action.label).join('; ');
    return labels;
  });
  /** While the player aims, everything that is not part of the choice steps back. */
  protected readonly focus = computed(() => {
    const kind = this.decision()?.kind;
    return !this.sent() && (kind === 'intercept' || kind === 'choose_target' || (this.selected() !== null && this.targets().size > 0));
  });
  protected readonly projections = computed<Record<string, Projection>>(() => {
    const view = this.view();
    if (!view || this.director.busy()) return {};
    const combat = this.combat();
    if (combat) {
      const interceptors = (view.decision?.actions ?? []).filter(action => action.type === 'intercept').map(action => 'unit:' + action.interceptor);
      return projectAttack(view, combat.attacker, combat.attackIndex, [combat.target, ...interceptors]);
    }
    const selected = this.selected();
    return selected?.startsWith('unit:') ? projectAttack(view, selected, this.attackIndex(), this.targets()) : {};
  });
  protected readonly aim = computed<Aim | null>(() => {
    const from = this.selected(), to = this.hovered();
    return from && to && this.targets().has(to) ? { from, to } : null;
  });
  /** The attacks a selected unit may choose between, when it has more than one. */
  protected readonly options = computed<IntentOption[]>(() => {
    const view = this.view(), selected = this.selected();
    if (!view || !selected?.startsWith('unit:') || this.decision()?.kind !== 'main') return [];
    const ready = new Set(this.decision()!.actions.filter(action => action.type === 'attack' && sourceOf(action) === selected).map(action => action.attackIndex));
    if (ready.size < 2) return [];
    return (unitOf(view, selected)?.attacks ?? []).filter(attack => ready.has(attack.index)).map(attack => ({
      label: attack.name ?? 'Attack ' + (attack.index + 1), value: attack.index, active: this.attackIndex() === attack.index,
      detail: (attack.damage === null ? '' : attack.damage + ' damage · ') + attack.cost + (attack.cost === 1 ? ' Shard' : ' Shards'),
    }));
  });
  protected readonly intent = computed<Intent>(() => {
    const view = this.view();
    if (!view) return { tone: 'wait', title: 'Finding your seat at the table…' };
    if (this.director.busy()) return { tone: 'resolve', title: this.stage.caption() || 'Resolving…' };
    if (view.result) return { tone: 'done', title: 'The game is over.' };
    if (view.status === 'waiting_for_opponent') return { tone: 'wait', title: 'Waiting for an opponent to join.', detail: 'Share the invite link to start.' };
    const decision = this.decision();
    if (decision) return this.decide(view, decision);
    const combat = this.combat();
    if (view.waitingFor?.kind === 'intercept' && combat) return { tone: 'wait', title: 'Your opponent may intercept.', detail: this.book.name(combat.attackerCard) + ' attacks ' + this.book.name(combat.targetCard) + '.' };
    if (view.waitingFor) return { tone: 'wait', title: view.yourTurn ? 'Your opponent is choosing…' : 'Opponent’s turn', detail: view.yourTurn ? null : 'Watch their moves here; you may be asked to intercept.' };
    return { tone: 'wait', title: this.socket.status() === 'open' ? 'Catching up with the table…' : 'Connecting…' };
  });
  protected readonly chronicleOpen = signal(typeof matchMedia === 'function' && matchMedia('(min-width: 1280px)').matches);
  protected readonly graveSide = signal<Side>('you');
  protected readonly copied = signal(false);
  protected readonly drag = signal<{ x: number; y: number; fromX: number; fromY: number } | null>(null);
  private pointer: { key: string; x: number; y: number; attack: number | null; moving: boolean; timer: ReturnType<typeof setTimeout> } | null = null;
  private suppressClickUntil = 0;

  constructor() {
    this.socket.frames.pipe(takeUntilDestroyed()).subscribe(frame => {
      this.abortGesture();
      if (frame.type === 'update') this.director.enqueue(frame.view, frame.events);
      else if (frame.type === 'state') this.director.reset(frame.view, frame.history);
      else this.director.reset(this.socket.view(), this.socket.log());
    });
    this.destroy.onDestroy(() => { this.abortGesture(); this.director.skip(); this.inspect.clearPreview(); this.ambience.faction.set('neutral'); });
    effect(() => this.ambience.faction.set(this.view()?.you.faction ?? 'neutral'));
    effect(() => { if (this.director.busy()) this.inspect.clearPreview(); });
  }
  ngOnInit(): void {
    const token = this.token();
    if (token !== null) this.socket.connect(socketUrl(this.gameId(), token));
  }
  protected click(key: string): void {
    if (Date.now() < this.suppressClickUntil || !this.ready() || this.sent()) return;
    this.feedback.unlock();
    this.inspect.clearPreview();
    if (this.decision()?.kind === 'intercept' && key === this.combat()?.target && this.selected()) {
      this.pick(resolveGesture(this.view()!, this.selected(), key, null, this.combat()!.target)); return;
    }
    if (this.targets().has(key)) this.pick(this.groups().pick(this.selected(), key));
    else if (this.groups().sources().has(key)) this.select(this.selected() === key ? null : key);
  }
  protected select(key: string | null, attack: number | null = null): void {
    this.selected.set(key); this.candidates.set(null); this.hovered.set(null);
    const first = (this.decision()?.actions ?? []).find(action => action.type === 'attack' && sourceOf(action) === key);
    this.attackIndex.set(attack ?? first?.attackIndex ?? null);
  }
  protected chooseAttack(index: number): void { this.attackIndex.set(index); this.candidates.set(null); }
  private pick(actions: ActionView[]): void {
    if (actions.length === 1) this.choose(actions[0]);
    else if (actions.length) this.candidates.set(actions);
  }
  protected choose(action: ActionView): void {
    const decision = this.decision();
    if (!decision || this.sent() || !decision.actions.some(legal => legal.index === action.index)) return;
    this.feedback.unlock(); this.sent.set(true); this.hovered.set(null);
    this.socket.act(decision.id, action.index);
  }
  protected cancel(): void { this.inspect.clearPreview(); this.select(null); this.abortGesture(); }
  protected press(input: Press): void {
    if (input.event.button !== 0 || !this.ready() || this.sent()) return;
    this.inspect.clearPreview();
    this.abortGesture();
    const timer = setTimeout(() => {
      if (!this.pointer?.moving) { this.inspectKey(input.key); this.suppressClickUntil = Date.now() + 600; this.abortGesture(); }
    }, 500);
    this.pointer = { key: input.key, x: input.event.clientX, y: input.event.clientY, attack: input.attack ?? null, moving: false, timer };
  }
  protected move(event: PointerEvent): void {
    const pointer = this.pointer;
    if (!pointer || this.sent()) return;
    if (Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y) < 9 && !pointer.moving) return;
    clearTimeout(pointer.timer);
    if (!this.groups().sources().has(pointer.key)) return;
    if (!pointer.moving) this.select(pointer.key, pointer.attack ?? (this.selected() === pointer.key ? this.attackIndex() : null));
    pointer.moving = true;
    this.inspect.clearPreview();
    const over = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-key]')?.dataset['key'] ?? null;
    this.hovered.set(over);
    this.drag.set({ x: event.clientX, y: event.clientY, fromX: pointer.x, fromY: pointer.y });
  }
  protected release(event: PointerEvent): void {
    const pointer = this.pointer;
    if (pointer?.moving && this.view()) {
      const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-key]')?.dataset['key'];
      this.suppressClickUntil = Date.now() + 600;
      if (target) this.pick(resolveGesture(this.view()!, pointer.key, target, this.attackIndex(), this.combat()?.target ?? null));
    }
    this.abortGesture();
  }
  protected abortGesture(): void { if (this.pointer) clearTimeout(this.pointer.timer); this.pointer = null; this.drag.set(null); }
  protected inspectKey(key: string): void {
    const view = this.view();
    const unit = [...(view?.you.units ?? []), ...(view?.opponent?.units ?? [])].find(unit => 'unit:' + unit.id === key);
    const hand = view?.you.hand.find(card => 'card:' + card.id === key);
    const relic = [...(view?.you.relics ?? []), ...(view?.opponent?.relics ?? [])].find(card => 'relic:' + card.id === key);
    const card = unit ?? hand ?? relic;
    if (card) this.inspect.open({ id: card.card, unit, hand });
  }
  protected visibility(): void { if (document.hidden) { this.abortGesture(); this.director.skip(); } }
  protected async copyInvite(): Promise<void> {
    try { await navigator.clipboard.writeText(this.invite()!); this.copied.set(true); } catch { this.copied.set(false); }
  }

  /** The battle line for a decision of this seat: the engine's prompt, or the selection in progress. */
  private decide(view: GameView, decision: DecisionView): Intent {
    if (this.sent()) return { tone: 'resolve', title: 'Sent. Waiting for the table…' };
    const selected = this.selected();
    switch (decision.kind) {
      case 'intercept': {
        if (!selected) return { tone: 'threat', title: decision.prompt, detail: 'Tap a glowing ally to intercept, or let the attack through.' };
        return { tone: 'threat', title: 'Intercept with ' + this.name(view, selected) + '?', detail: this.outcome(this.name(view, selected), this.projections()[selected]) + ' Confirm, or tap the defender.' };
      }
      case 'choose_target': return { tone: 'target', title: decision.prompt, detail: 'Tap a highlighted target.' };
      case 'mulligan': case 'choose_cards': case 'choose_order': return { tone: 'select', title: decision.prompt };
      default: return this.mainIntent(view, decision);
    }
  }
  private mainIntent(view: GameView, decision: DecisionView): Intent {
    const selected = this.selected(), hovered = this.hovered(), targets = this.targets();
    if (this.candidates()) return { tone: 'select', title: 'Choose how', detail: 'This target can be chosen in several ways.' };
    if (selected?.startsWith('card:')) {
      const card = view.you.hand.find(hand => 'card:' + hand.id === selected)!;
      const text = this.book.text(card.card).map(line => line.text).join(' ');
      if (hovered && targets.has(hovered)) return { tone: 'target', title: this.groups().pick(selected, hovered)[0]?.label ?? '', detail: text, card: card.card };
      if (targets.size) return { tone: 'target', title: this.book.name(card.card) + ': choose a target', detail: text, card: card.card };
      return { tone: 'select', title: this.book.name(card.card), detail: text || 'Play it from the buttons, or drag it onto your side.', card: card.card };
    }
    if (selected?.startsWith('unit:')) {
      const unit = unitOf(view, selected)!, attack = attackOf(view, selected, this.attackIndex());
      const projection = hovered ? this.projections()[hovered] : undefined;
      if (hovered && projection) {
        const label = this.groups().pick(selected, hovered)[0]?.label ?? '';
        const warning = hovered.startsWith('unit:') && interceptable(view, hovered) ? ' Your opponent may intercept it.' : '';
        return { tone: 'target', title: label, detail: this.outcome(this.name(view, hovered), projection) + warning };
      }
      const what = attack ? (attack.name ?? 'Attack') + (attack.damage === null ? '' : ', ' + attack.damage + ' damage') + ' for ' + attack.cost + (attack.cost === 1 ? ' Shard' : ' Shards') : 'Attack';
      if (targets.size) return { tone: 'target', title: this.book.name(unit.card) + ': choose a target', detail: what + '. The outcome is written on each target.' };
      return { tone: 'select', title: this.book.name(unit.card), detail: what + '.' };
    }
    const sources = [...this.groups().sources()];
    const cards = sources.filter(key => key.startsWith('card:')).length, units = sources.filter(key => key.startsWith('unit:')).length;
    if (!cards && !units) return { tone: 'done', title: 'Nothing left to play.', detail: 'End your turn when you are ready.' };
    const counts = [cards ? plural(cards, 'card', 'cards') + ' to play' : '', units ? plural(units, 'unit', 'units') + ' ready' : ''].filter(Boolean).join(' · ');
    return { tone: 'turn', title: decision.prompt, detail: counts + '. Everything you can use glows.' };
  }
  private outcome(name: string, projection: Projection | undefined): string {
    if (!projection) return '';
    switch (projection.outcome) {
      case 'lethal': return name + (name === 'You' || name === 'Your opponent' ? ' drops to 0 HP: lethal.' : ' is destroyed: lethal.');
      case 'anchored': return name + ' drops to 0 but its Anchor keeps it on the board, doomed.';
      case 'linked': return name + ' shares the ' + projection.amount + ' damage with its link.';
      case 'effect': return 'This attack applies its effect to ' + name + '.';
      default: return name + ' takes ' + projection.amount + ' damage: ' + projection.before + ' → ' + projection.after + '.';
    }
  }
  private name(view: GameView, key: string): string {
    if (key === 'player:you') return 'You';
    if (key === 'player:opponent') return 'Your opponent';
    const unit = unitOf(view, key);
    return unit ? this.book.name(unit.card) : key;
  }
}
function plural(count: number, one: string, many: string): string { return count + ' ' + (count === 1 ? one : many); }
function socketUrl(gameId: string, token: string): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  return scheme + '://' + location.host + '/ws/games/' + gameId + '?token=' + encodeURIComponent(token);
}
