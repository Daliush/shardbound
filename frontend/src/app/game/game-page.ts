import { Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, input, linkedSignal, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ActionView, GameView, PlayerBase, Side } from '../api/protocol';
import { SeatStore, inviteLink } from '../api/seat-store';
import { ArenaBoard } from '../arena/board';
import { ChoiceEditor } from '../arena/choice-editor';
import { resolveGesture } from '../arena/gestures';
import { Anchors } from '../arena/stage/anchors';
import { Director } from '../arena/stage/director';
import { Feedback } from '../arena/stage/feedback';
import { ArenaStage } from '../arena/stage/stage-player';
import { StageOverlay } from '../arena/stage/stage-overlay';
import { CardComponent } from '../cards/card';
import { Inspect } from '../cards/inspect';
import { Icon } from '../core/icon';
import { SettingsPanel } from '../core/settings-panel';
import { ThemeToggle } from '../core/theme-toggle';
import { DecisionGroups, Mark, locateIn, sourceOf } from './decision-groups';
import { GameLog } from './game-log';
import { GameSocketService } from './game-socket.service';
import { pendingCombat } from '../arena/combat';
import { Ambience } from '../core/ambience';
import { RulesText } from '../cards/rules-text';
import { CardPreview } from '../cards/card-preview';

@Component({
  selector: 'app-game-page',
  imports: [RouterLink, ArenaBoard, ChoiceEditor, StageOverlay, CardComponent, CardPreview, RulesText, Icon, SettingsPanel, ThemeToggle, GameLog],
  providers: [GameSocketService, Anchors, ArenaStage, Feedback],
  templateUrl: './game-page.html',
  styleUrls: ['./game-page.css', './game-page-mobile.css'],
  host: { '(window:pointermove)': 'move($event)', '(window:pointerup)': 'release($event)', '(window:pointercancel)': 'abortGesture()', '(window:keydown.escape)': 'cancel()', '(document:visibilitychange)': 'visibility()' },
})
export class GamePage implements OnInit {
  readonly gameId = input.required<string>();
  protected readonly socket = inject(GameSocketService);
  protected readonly stage = inject(ArenaStage);
  protected readonly director = new Director(this.stage);
  private readonly seats = inject(SeatStore);
  private readonly inspect = inject(Inspect);
  private readonly anchors = inject(Anchors);
  private readonly feedback = inject(Feedback);
  private readonly ambience = inject(Ambience);
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
  private readonly actions = computed(() => (this.decision()?.actions ?? []).filter(action => this.attackIndex() === null || action.type !== 'attack' || action.attackIndex === this.attackIndex()));
  private readonly groups = computed(() => new DecisionGroups(this.actions(), locateIn(this.view())));
  protected readonly buttons = computed(() => (this.candidates() ?? this.groups().buttons(this.selected())).filter(action => !['end_turn', 'intercept', 'choose_cards', 'choose_order'].includes(action.type)));
  protected readonly combat = computed(() => pendingCombat(this.view(), this.director.history()));
  protected readonly endTurn = computed(() => this.actions().find(action => action.type === 'end_turn'));
  protected readonly marks = computed(() => {
    const result: Record<string, Mark> = {};
    if (this.sent() || !this.ready()) return result;
    this.groups().sources().forEach(key => result[key] = 'selectable');
    this.groups().targets(this.selected()).forEach(key => result[key] = 'targetable');
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
    for (const key of this.groups().targets(this.selected())) labels[key] = this.groups().pick(this.selected(), key).map(action => action.label).join('; ');
    return labels;
  });
  protected readonly handOpen = signal(false);
  private readonly handSheet = viewChild<ElementRef<HTMLDialogElement>>('handSheet');
  private readonly chronicleSheet = viewChild<ElementRef<HTMLDialogElement>>('chronicleSheet');
  protected openChronicle(): void { this.chronicleSheet()?.nativeElement.showModal(); }
  protected readonly graveSide = signal<Side>('you');
  protected readonly chronicleOpen = signal(false);
  protected readonly copied = signal(false);
  protected readonly drag = signal<{ x: number; y: number; fromX: number; fromY: number } | null>(null);
  protected readonly lastEvent = computed(() => this.director.history().at(-1)?.text ?? 'The Chronicle will record this game.');
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
    effect(() => { if (this.director.busy()) { this.handOpen.set(false); this.inspect.clearPreview(); } });
    effect(() => {
      const dialog = this.handSheet()?.nativeElement;
      if (this.handOpen() && !dialog?.open) dialog?.showModal();
      else if (!this.handOpen() && dialog?.open) dialog.close();
    });
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
    if (key.startsWith('attack:')) {
      const [, id, attack] = key.split(':');
      this.selected.set('unit:' + id); this.attackIndex.set(Number(attack)); this.candidates.set(null);
      const untargeted = this.groups().buttons(this.selected()).filter(action => action.type === 'attack');
      if (untargeted.length) this.pick(untargeted);
      return;
    }
    if (this.groups().targets(this.selected()).has(key)) this.pick(this.groups().pick(this.selected(), key));
    else if (this.groups().sources().has(key)) {
      this.selected.set(this.selected() === key ? null : key); this.attackIndex.set(null); this.candidates.set(null);
    }
  }
  private pick(actions: ActionView[]): void {
    if (actions.length === 1) this.choose(actions[0]);
    else if (actions.length) this.candidates.set(actions);
  }
  protected choose(action: ActionView): void {
    const decision = this.decision();
    if (!decision || this.sent() || !decision.actions.some(legal => legal.index === action.index)) return;
    this.feedback.unlock(); this.sent.set(true); this.handOpen.set(false);
    this.socket.act(decision.id, action.index);
  }
  protected cancel(): void { this.inspect.clearPreview(); this.handOpen.set(false); this.chronicleOpen.set(false); this.selected.set(null); this.candidates.set(null); this.attackIndex.set(null); this.abortGesture(); }
  protected handActions(id: number): ActionView[] { return this.actions().filter(action => sourceOf(action) === 'card:' + id); }
  protected selectFromHand(key: string): void { this.click(key); this.handOpen.set(false); }
  protected press(input: { key: string; event: PointerEvent; attack?: number }): void {
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
    pointer.moving = true;
    this.inspect.clearPreview();
    this.selected.set(pointer.key); this.attackIndex.set(pointer.attack);
    this.handOpen.set(false);
    this.drag.set({ x: event.clientX, y: event.clientY, fromX: pointer.x, fromY: pointer.y });
  }
  protected release(event: PointerEvent): void {
    const pointer = this.pointer;
    if (pointer?.moving && this.view()) {
      const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-key]')?.dataset['key'];
      this.suppressClickUntil = Date.now() + 600;
      if (target) this.pick(resolveGesture(this.view()!, pointer.key, target, pointer.attack, this.combat()?.target ?? null));
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
}
function socketUrl(gameId: string, token: string): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  return scheme + '://' + location.host + '/ws/games/' + gameId + '?token=' + encodeURIComponent(token);
}

