import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { gsap } from 'gsap';
import { Flip } from 'gsap/Flip';
import { CardRef, GameView } from '../../api/protocol';
import { Settings } from '../../core/settings';
import { CardBook } from '../../game/card-book';
import { Anchors } from './anchors';
import { Beat } from './choreographer';
import { StagePlayer } from './director';
import { Feedback } from './feedback';
import { playbackScale } from './timing';
import { VfxCanvas } from './vfx';

gsap.registerPlugin(Flip);
export interface Ghost { card: CardRef | null; back: boolean; x: number; y: number; width: number; }
export interface FloatingNumber { text: string; kind: 'damage' | 'heal'; x: number; y: number; }
export interface Banner { text: string; tone: 'you' | 'opponent'; y: number; }
export interface Arrow { x1: number; y1: number; x2: number; y2: number; }

/** Game pieces that move between updates; containers keep their layout and are never tweened by Flip. */
const PIECES = /^(unit|card|relic):/;
const GHOSTED = ['draw', 'play', 'return', 'recall', 'fracture', 'shatter'];

/**
 * Plays one beat at a time over the board the player still sees, then lets the new board slide into place.
 * The narration goes to the battle line (caption); numbers, the turn banner and travelling cards are drawn by the
 * stage overlay; impacts move the real elements, and every touched element is restored afterwards.
 */
@Injectable()
export class ArenaStage implements StagePlayer, OnDestroy {
  readonly ghost = signal<Ghost | null>(null);
  readonly number = signal<FloatingNumber | null>(null);
  readonly banner = signal<Banner | null>(null);
  readonly caption = signal('');
  readonly arrow = signal<Arrow | null>(null);
  readonly counters = signal<Record<string, number>>({});
  readonly anchors = inject(Anchors);
  private readonly settings = inject(Settings);
  private readonly feedback = inject(Feedback);
  private readonly book = inject(CardBook);
  private timelines = new Set<gsap.core.Timeline | gsap.core.Tween>();
  private touched = new Set<HTMLElement>();
  private vfx?: VfxCanvas;
  private pendingAttack: { attacker: string; target: string | null } | null = null;

  attach(canvas: HTMLCanvasElement): void { this.vfx = new VfxCanvas(canvas); }
  capture(): unknown { return Flip.getState(this.pieces()); }

  async play(beat: Beat, old: GameView, next: GameView, signal: AbortSignal): Promise<void> {
    this.caption.set(beat.event.text);
    if (signal.aborted || document.hidden || this.settings.preferences().speed === 0) return;
    const calm = this.settings.reducedMotion();
    const element = this.anchors.find(beat.key);
    const center = this.centerOf(beat, element);
    const amount = typeof beat.event['amount'] === 'number' ? beat.event['amount'] : 0;
    if (['damage', 'heal', 'share'].includes(beat.kind) && amount) this.number.set({ text: (beat.kind === 'heal' ? '+' : '−') + amount, kind: beat.kind === 'heal' ? 'heal' : 'damage', ...center });
    if (beat.kind === 'turn') this.banner.set({ text: beat.side === 'you' ? 'Your turn' : 'Opponent’s turn', tone: beat.side === 'opponent' ? 'opponent' : 'you', y: this.middle() });
    this.feedback.play(beat.kind, amount);
    if (beat.kind === 'attack') { this.pendingAttack = { attacker: beat.key!, target: beat.target }; this.connect(beat.key, beat.target); }
    if (['card_played', 'turn_ended', 'attack_cancelled'].includes(beat.event.type)) this.pendingAttack = null;
    if (beat.kind === 'intercept' && this.pendingAttack) { this.pendingAttack.target = beat.key; this.connect(this.pendingAttack.attacker, beat.key); }
    if (['link', 'share', 'unlink'].includes(beat.kind)) this.connect(beat.key, beat.target);
    const ghost = GHOSTED.includes(beat.kind) ? this.ghostFor(beat, next, center) : null;
    if (ghost) this.ghost.set(ghost.start);
    await this.nextPaint(signal);
    if (signal.aborted) return;
    const ghostElement = document.querySelector<HTMLElement>('[data-stage-ghost]');
    const numberElement = document.querySelector<HTMLElement>('[data-stage-number]');
    const bannerElement = document.querySelector<HTMLElement>('[data-stage-banner]');
    const duration = beat.duration / 1000;
    const pace = playbackScale(this.settings.preferences().speed);
    await this.timeline(signal, timeline => {
      const color = this.color(beat);
      if (numberElement) {
        timeline.fromTo(numberElement, { opacity: 0, scale: .4, y: 0 }, { opacity: 1, scale: 1.25, duration: duration * .22, ease: 'back.out(3)' }, duration * .3)
          .to(numberElement, { scale: 1, y: -30, duration: duration * .5, ease: 'power2.out' }, duration * .52)
          .to(numberElement, { opacity: 0, duration: duration * .2 }, duration * .8);
      }
      if (bannerElement) {
        timeline.fromTo(bannerElement, { clipPath: 'inset(0 100% 0 0)', x: -24 }, { clipPath: 'inset(0 0% 0 0)', x: 0, duration: duration * .32, ease: 'expo.out' }, 0)
          .to(bannerElement, { clipPath: 'inset(0 0 0 100%)', x: 24, duration: duration * .24, ease: 'power2.in' }, duration * .76);
      }
      if (!calm) {
        if (['heal', 'freeze', 'thaw', 'arrive', 'summon', 'cast', 'echo', 'anchor', 'ability'].includes(beat.kind)) this.vfx?.burst(center, beat.kind === 'heal' ? this.token('heal') : color, beat.kind === 'heal' ? 'heal' : this.faction(beat), pace);
        if (element) this.touch(element);
        if (element && beat.kind === 'attack') this.windUp(timeline, element, beat.target, duration);
        if (element && beat.kind === 'play' && ghost) timeline.set(element, { opacity: 0 }, 0);
        if (element && beat.kind === 'intercept') {
          const to = this.anchors.rect(beat.target), from = element.getBoundingClientRect();
          if (to) timeline.to(element, { x: (to.left - from.left) * .55, y: (to.top - from.top) * .55, scale: 1.12, duration: duration * .5, ease: 'power3.out' }, 0).to(element, { x: 0, y: 0, scale: 1, duration: duration * .4, ease: 'power2.inOut' });
        }
        if (beat.kind === 'damage' || beat.kind === 'share') this.impact(timeline, element, center, amount, duration, color, pace);
        if (element && ['shatter', 'sacrifice', 'vanish', 'discard'].includes(beat.kind)) {
          if (beat.kind === 'shatter' || beat.kind === 'sacrifice') this.vfx?.shatter(element, getComputedStyle(element).getPropertyValue('--faction').trim() || color, 1, pace);
          timeline.to(element, { opacity: 0, scale: beat.kind === 'sacrifice' ? .3 : .9, rotation: beat.kind === 'sacrifice' ? 10 : 0, duration: duration * .45, ease: 'power2.in' }, 0);
        }
        if (beat.kind === 'victory') {
          const loser = this.anchors.find('player:' + (next.result?.outcome === 'win' ? 'opponent' : 'you'));
          if (loser && next.result?.outcome !== 'draw') {
            this.touch(loser);
            timeline.to(loser, { x: 6, repeat: 5, yoyo: true, duration: .05 }, 0).call(() => this.vfx?.shatter(loser, this.token('prism'), 2, pace)).to(loser, { opacity: .25, duration: .2 });
          }
        }
        if (element && ['heal', 'modify', 'freeze', 'thaw', 'anchor', 'doom', 'ability', 'shards', 'link', 'unlink'].includes(beat.kind)) {
          timeline.fromTo(element, { scale: 1, filter: 'brightness(1)' }, { scale: 1.05, filter: 'brightness(1.35)', yoyo: true, repeat: 1, duration: duration / 2, ease: 'sine.inOut' }, 0);
        }
      }
      if (ghost && ghostElement) this.travel(timeline, ghostElement, ghost, beat, duration, calm);
      timeline.call(() => { for (const counter of beat.counters) this.counters.update(values => ({ ...values, [counter.key]: counter.value })); }, [], duration * .62);
      timeline.to({}, { duration }, 0);
      timeline.timeScale(1 / pace);
    });
    if (beat.kind === 'damage') this.pendingAttack = null;
    if (!signal.aborted) { this.ghost.set(null); this.number.set(null); this.banner.set(null); }
  }

  async reconcile(capture: unknown, signal: AbortSignal): Promise<void> {
    this.counters.set({});
    if (document.hidden || this.settings.preferences().speed === 0) { this.restoreElements(); this.arrow.set(null); return; }
    await this.nextPaint(signal);
    if (signal.aborted) return;
    this.restoreElements();
    if (!this.settings.reducedMotion()) {
      const pace = playbackScale(this.settings.preferences().speed);
      await new Promise<void>(resolve => {
        const tween = Flip.from(capture as Flip.FlipState, {
          targets: this.pieces(), duration: .26 * pace, ease: 'power3.out', simple: true,
          onEnter: entering => gsap.fromTo(entering, { opacity: 0, scale: .7, y: 14 }, { opacity: 1, scale: 1, y: 0, duration: .32 * pace, ease: 'back.out(1.7)', clearProps: 'opacity,scale,transform' }),
          onComplete: () => { this.timelines.delete(tween); resolve(); },
        });
        this.timelines.add(tween);
        signal.addEventListener('abort', () => { tween.kill(); resolve(); }, { once: true });
      });
    }
    this.arrow.set(null);
  }
  cancel(): void {
    for (const timeline of this.timelines) timeline.kill();
    this.timelines.clear();
    this.restoreElements();
    this.vfx?.clear();
    this.ghost.set(null); this.number.set(null); this.banner.set(null); this.arrow.set(null); this.counters.set({});
    this.pendingAttack = null;
  }
  ngOnDestroy(): void { this.cancel(); this.feedback.close(); }

  /** The attacker gathers itself away from its target before the strike. */
  private windUp(timeline: gsap.core.Timeline, element: HTMLElement, target: string | null, duration: number): void {
    const from = element.getBoundingClientRect(), to = this.anchors.rect(target);
    const away = to ? Math.sign(from.top - to.top) || 1 : 1;
    timeline.to(element, { y: away * 10, scale: 1.08, duration: duration * .5, ease: 'power2.out' }, 0)
      .to(element, { y: 0, scale: 1.04, duration: duration * .5, ease: 'power2.in' });
  }
  /** The attacker lunges, the target flashes and shakes, the table trembles on a heavy hit. */
  private impact(timeline: gsap.core.Timeline, element: HTMLElement | undefined, center: { x: number; y: number }, amount: number, duration: number, color: string, pace: number): void {
    const hit = duration * .3;
    const attacker = this.anchors.find(this.pendingAttack?.attacker ?? null);
    if (attacker && attacker !== element) {
      const from = attacker.getBoundingClientRect();
      this.touch(attacker);
      timeline.to(attacker, { x: (center.x - from.left - from.width / 2) * .78, y: (center.y - from.top - from.height / 2) * .78, scale: 1.1, duration: hit, ease: 'power3.in' }, 0)
        .to(attacker, { x: 0, y: 0, scale: 1, duration: duration * .5, ease: 'power3.out' }, hit + .05);
    }
    timeline.call(() => this.vfx?.burst(center, color, 'impact', pace), [], hit);
    if (element) {
      timeline.fromTo(element, { filter: 'brightness(2.2) saturate(1.3)' }, { filter: 'brightness(1) saturate(1)', duration: duration * .5, ease: 'power2.out' }, hit)
        .to(element, { keyframes: { x: [0, -7, 6, -4, 2, 0] }, duration: duration * .4, ease: 'none' }, hit);
    }
    const camera = this.anchors.find('camera');
    if (camera && amount >= 4) { this.touch(camera); timeline.to(camera, { keyframes: { x: [0, Math.min(amount, 9), -Math.min(amount, 9) * .6, 0], y: [0, -3, 2, 0] }, duration: .2, ease: 'none' }, hit); }
  }
  /** A card travelling between zones: drawn from a deck, played from a hand, sent to a graveyard. */
  private travel(timeline: gsap.core.Timeline, ghost: HTMLElement, path: { start: Ghost; end: { x: number; y: number } | null; hold: boolean }, beat: Beat, duration: number, calm: boolean): void {
    const center = { x: innerWidth / 2, y: this.middle() };
    const dx = (point: { x: number }) => calm ? 0 : point.x - path.start.x, dy = (point: { y: number }) => calm ? 0 : point.y - path.start.y;
    if (path.start.back && beat.kind === 'play') timeline.call(() => this.ghost.update(ghost => ghost ? { ...ghost, back: false } : null), [], duration * .18);
    if (path.hold) {
      timeline.fromTo(ghost, { opacity: 0, scale: .45, x: 0, y: 0 }, { opacity: 1, scale: 1, x: dx(center), y: dy(center), duration: duration * .3, ease: 'expo.out' }, 0);
      if (path.end) timeline.to(ghost, { x: dx(path.end), y: dy(path.end), scale: .35, opacity: 0, duration: duration * .22, ease: 'power2.in' }, duration * .78);
      else timeline.to(ghost, { opacity: 0, scale: .9, duration: duration * .18 }, duration * .82);
    } else {
      const end = path.end ?? center;
      timeline.fromTo(ghost, { opacity: 0, scale: .8, x: 0, y: 0 }, { opacity: 1, scale: 1, duration: duration * .2, ease: 'power2.out' }, 0)
        .to(ghost, { x: dx(end), y: dy(end), scale: .6, duration: duration * .6, ease: 'power2.inOut' }, duration * .15)
        .to(ghost, { opacity: 0, duration: duration * .2 }, duration * .78);
    }
  }
  private ghostFor(beat: Beat, next: GameView, center: { x: number; y: number }): { start: Ghost; end: { x: number; y: number } | null; hold: boolean } {
    const side = beat.side ?? 'you';
    const from = beat.kind === 'draw' ? this.rectCenter('deck:' + side) : beat.kind === 'play' && !this.anchors.find(beat.key) ? this.rectCenter('hand:' + side) : null;
    const start = from ?? center;
    const back = side === 'opponent' && (beat.kind === 'draw' || beat.kind === 'play');
    if (beat.kind === 'play') {
      const unit = [...next.you.units, ...(next.opponent?.units ?? [])].some(unit => unit.id === beat.card?.id);
      const relic = [...next.you.relics, ...(next.opponent?.relics ?? [])].some(relic => relic.id === beat.card?.id);
      const end = unit ? this.rectCenter('board:' + side) : relic ? this.rectCenter('player:' + side) : null;
      return { start: { card: beat.card, back, ...start, width: Math.min(innerWidth * .42, 220) }, end, hold: true };
    }
    const destination = beat.kind === 'shatter' ? 'graveyard:' + side : beat.kind === 'draw' && side === 'opponent' ? 'hand:opponent' : 'hand:' + side;
    return { start: { card: beat.card, back, ...start, width: beat.kind === 'draw' ? Math.min(innerWidth * .26, 120) : Math.min(innerWidth * .28, 130) }, end: this.rectCenter(destination), hold: false };
  }
  private centerOf(beat: Beat, element: HTMLElement | undefined): { x: number; y: number } {
    const rect = element?.getBoundingClientRect() ?? (['arrive', 'summon'].includes(beat.kind) && beat.side ? this.anchors.rect('board:' + beat.side) : undefined);
    return rect ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 } : { x: innerWidth / 2, y: this.middle() };
  }
  private rectCenter(key: string): { x: number; y: number } | null {
    const rect = this.anchors.rect(key);
    return rect ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 } : null;
  }
  private middle(): number { const battle = this.anchors.rect('battle'); return battle ? battle.top + battle.height / 2 : innerHeight * .45; }
  private faction(beat: Beat): string { return beat.card ? this.book.card(beat.card.card)?.faction ?? 'neutral' : 'neutral'; }
  private color(beat: Beat): string { const faction = this.faction(beat); return this.token(faction === 'neutral' ? 'prism' : faction); }
  private token(name: string): string { return getComputedStyle(document.documentElement).getPropertyValue('--' + name).trim(); }
  private pieces(): HTMLElement[] { return [...this.anchors.elements.entries()].filter(([key]) => PIECES.test(key)).map(([, element]) => element); }
  private touch(element: HTMLElement): void { this.touched.add(element); element.style.willChange = 'transform, filter'; }
  private restoreElements(): void {
    for (const element of this.touched) gsap.set(element, { clearProps: 'transform,opacity,filter,willChange' });
    this.touched.clear();
  }
  private connect(from: string | null, to: string | null): void {
    const a = this.anchors.rect(from), b = this.anchors.rect(to);
    if (a && b) this.arrow.set({ x1: a.left + a.width / 2, y1: a.top + a.height / 2, x2: b.left + b.width / 2, y2: b.top + b.height / 2 });
  }
  private timeline(signal: AbortSignal, build: (timeline: gsap.core.Timeline) => void): Promise<void> {
    return new Promise(resolve => {
      let complete = false;
      const finish = () => { if (complete) return; complete = true; signal.removeEventListener('abort', cancel); this.timelines.delete(timeline); resolve(); };
      const timeline = gsap.timeline({ onComplete: finish });
      const cancel = () => { timeline.kill(); finish(); };
      this.timelines.add(timeline);
      signal.addEventListener('abort', cancel, { once: true });
      build(timeline);
    });
  }
  private nextPaint(signal: AbortSignal): Promise<void> {
    return new Promise(resolve => {
      const finish = () => { signal.removeEventListener('abort', finish); resolve(); };
      if (signal.aborted) { finish(); return; }
      signal.addEventListener('abort', finish, { once: true });
      requestAnimationFrame(() => requestAnimationFrame(finish));
    });
  }
}
