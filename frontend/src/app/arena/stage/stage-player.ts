import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { gsap } from 'gsap';
import { Flip } from 'gsap/Flip';
import { CardRef, GameView } from '../../api/protocol';
import { Settings } from '../../core/settings';
import { Anchors } from './anchors';
import { Beat } from './choreographer';
import { StagePlayer } from './director';
import { Feedback } from './feedback';
import { VfxCanvas } from './vfx';
import { CardBook } from '../../game/card-book';
import { playbackScale } from './timing';

gsap.registerPlugin(Flip);
export interface Ghost { card: CardRef | null; back: boolean; x: number; y: number; width: number; }
export interface StageOverlay { text: string; kind: string; x: number; y: number; number?: string; }
@Injectable()
export class ArenaStage implements StagePlayer, OnDestroy {
  readonly ghost = signal<Ghost | null>(null);
  readonly overlay = signal<StageOverlay | null>(null);
  readonly arrow = signal<{ x1: number; y1: number; x2: number; y2: number } | null>(null);
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
  capture(): unknown { return Flip.getState([...this.anchors.elements.values()]); }

  async play(beat: Beat, old: GameView, next: GameView, signal: AbortSignal): Promise<void> {
    if (signal.aborted || document.hidden || this.settings.preferences().speed === 0) return;
    const calm = this.settings.reducedMotion();
    const element = this.anchors.find(beat.key);
    const rect = element?.getBoundingClientRect();
    const center = rect ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 } : { x: innerWidth / 2, y: innerHeight * .43 };
    const amount = typeof beat.event['amount'] === 'number' ? beat.event['amount'] : 0;
    const number = ['damage', 'heal', 'share'].includes(beat.kind) ? (beat.kind === 'heal' ? '+' : '−') + amount : undefined;
    this.overlay.set({ text: beat.event.text, kind: beat.kind, ...center, number });
    this.feedback.play(beat.kind, amount);
    if (beat.kind === 'attack') {
      this.pendingAttack = { attacker: beat.key!, target: beat.target };
      this.connect(beat.key, beat.target);
    }
    if (['card_played','turn_ended','attack_cancelled'].includes(beat.event.type)) this.pendingAttack = null;
    if (beat.kind === 'intercept' && this.pendingAttack) {
      this.pendingAttack.target = beat.key;
      this.connect(this.pendingAttack.attacker, beat.key);
    }
    if (['link', 'share', 'unlink'].includes(beat.kind)) this.connect(beat.key, beat.target);
    if (['draw', 'play', 'return', 'recall', 'cast', 'fracture', 'echo', 'shatter'].includes(beat.kind)) {
      this.ghost.set({ card: beat.card, back: beat.kind === 'draw' || (beat.kind === 'play' && beat.side === 'opponent'), x: center.x, y: center.y, width: Math.min(innerWidth * .4, beat.kind === 'play' ? 240 : 180) });
    }
    await this.nextPaint(signal);
    if (signal.aborted) return;
    const ghost = document.querySelector<HTMLElement>('[data-stage-ghost]');
    const label = document.querySelector<HTMLElement>('[data-stage-label]');
    const camera = this.anchors.find('camera');
    const duration = beat.duration / 1000;
    const pace = playbackScale(this.settings.preferences().speed);
    await this.timeline(signal, timeline => {
      if (label) timeline.fromTo(label, { opacity: 0, scale: .75, y: 6 }, { opacity: 1, scale: 1, y: 0, duration: duration * .3 }, 0);
      if (!calm) {
        const faction = beat.card ? this.book.card(beat.card.card)?.faction ?? 'neutral' : 'neutral';
        const color = getComputedStyle(document.documentElement).getPropertyValue('--' + (faction === 'neutral' ? 'prism' : faction)).trim();
        if (['heal','freeze','thaw','arrive','summon','cast','anchor','ability'].includes(beat.kind)) this.vfx?.burst(center,color,beat.kind === 'heal' ? 'heal' : faction, pace);
        if (element) { this.touched.add(element); element.style.willChange = 'transform, opacity'; }
        if (element && beat.kind === 'attack') timeline.to(element, { y: 10, scale: 1.09, rotation: -3, duration: duration * .45, ease: 'power2.in' }, 0).to(element, { y: 0, scale: 1, rotation: 0, duration: duration * .55, ease: 'back.out(2)' });
        if (element && beat.kind === 'intercept') timeline.fromTo(element, { x: 0 }, { x: (this.anchors.rect(beat.target)?.left ?? rect!.left) - rect!.left, scale: 1.15, duration: duration * .6 }, 0).to(element, { x: 0, scale: 1, duration: duration * .4 });
        if (beat.kind === 'damage') {
          const attacker = this.anchors.find(this.pendingAttack?.attacker ?? null);
          if (attacker && rect) {
            const from = attacker.getBoundingClientRect();
            this.touched.add(attacker);
            timeline.to(attacker, { x: center.x - from.left - from.width / 2, y: center.y - from.top - from.height / 2, scale: 1.1, duration: duration * .3, ease: 'power3.in' }, 0)
              .to(attacker, { x: 0, y: 0, scale: 1, duration: duration * .45, ease: 'back.out(1.5)' }, duration * .3 + .07);
          }
          if (element) timeline.to(element, { x: 5, scale: .96, opacity: .6, duration: .07 }, duration * .3).to(element, { x: 0, scale: 1, opacity: 1, duration: duration * .45 }, duration * .3 + .07);
          if (camera && amount >= 4) { this.touched.add(camera); timeline.to(camera, { x: Math.min(amount, 8), y: -3, repeat: 1, yoyo: true, duration: .045 }, duration * .3).set(camera, { x: 0, y: 0 }); }
        }
        if (element && ['shatter', 'sacrifice', 'vanish', 'discard'].includes(beat.kind)) {
          const color = getComputedStyle(element).getPropertyValue('--faction').trim() || getComputedStyle(document.documentElement).getPropertyValue('--prism');
          if (beat.kind === 'shatter') this.vfx?.shatter(element, color, 1, pace);
          timeline.to(element, { opacity: 0, scale: beat.kind === 'sacrifice' ? .2 : .9, rotation: beat.kind === 'sacrifice' ? 12 : 0, duration: duration * .5 }, 0);
        }
        if (beat.kind === 'victory') {
          const loser = next.result?.outcome === 'win' ? 'opponent' : 'you';
          const core = this.anchors.find('player:' + loser);
          if (core && next.result?.outcome !== 'draw') {
            this.touched.add(core);
            timeline.to(core, { scale: 1.08, repeat: 3, yoyo: true, duration: .08 }, 0).call(() => this.vfx?.shatter(core, getComputedStyle(document.documentElement).getPropertyValue('--prism'), 2, pace)).to(core, { opacity: 0, duration: .15 });
          }
        }
        if (element && ['heal', 'modify', 'freeze', 'thaw', 'anchor', 'doom', 'ability', 'arrive', 'summon', 'shards'].includes(beat.kind)) timeline.fromTo(element, { scale: 1, opacity: 1 }, { scale: 1.04, opacity: .72, yoyo: true, repeat: 1, duration: duration / 2 }, 0);
      }
      if (ghost) {
        if (beat.kind === 'play' || (beat.kind === 'draw' && beat.side === 'you')) timeline.call(() => this.ghost.update(ghost => ghost ? {...ghost,back:false} : null), [], duration * .35);
        const destination = beat.kind === 'shatter' ? this.anchors.rect('graveyard:' + beat.side) : ['return', 'recall', 'fracture', 'draw'].includes(beat.kind) ? this.anchors.rect('hand:' + beat.side) : null;
        const x = destination ? destination.left + destination.width / 2 : innerWidth / 2;
        const y = destination ? destination.top + destination.height / 2 : innerHeight * .43;
        timeline.fromTo(ghost, { opacity: 0, scale: beat.kind === 'shatter' ? .25 : .65 }, { opacity: 1, scale: 1, x: calm ? 0 : x - center.x, y: calm ? 0 : y - center.y, rotationY: 0, duration: duration * .5, ease: 'back.out(1.2)' }, 0);
        timeline.to(ghost, { opacity: 0, scale: .9, duration: duration * .2 }, duration * .8);
      }
      timeline.call(() => { for (const counter of beat.counters) this.counters.update(values => ({ ...values, [counter.key]: counter.value })); }, [], duration * .7);
      if (label) timeline.to(label, { opacity: 0, y: number ? -20 : 0, duration: duration * .25 }, duration * .75);
      timeline.to({}, { duration }, 0);
      timeline.timeScale(1 / pace);
    });
    if (beat.kind === 'damage') this.pendingAttack = null;
    if (!signal.aborted) { this.ghost.set(null); this.overlay.set(null); }
  }

  async reconcile(capture: unknown, signal: AbortSignal): Promise<void> {
    this.counters.set({});
    if (document.hidden || this.settings.preferences().speed === 0) {
      this.restoreElements(); this.arrow.set(null); return;
    }
    await this.nextPaint(signal);
    if (signal.aborted) return;
    this.restoreElements();
    if (this.settings.preferences().speed && !this.settings.reducedMotion()) {
      await new Promise<void>(resolve => {
        const tween = Flip.from(capture as Flip.FlipState, { duration: .22 * playbackScale(this.settings.preferences().speed), ease: 'power2.out', onComplete: () => { this.timelines.delete(tween); resolve(); } });
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
    this.ghost.set(null); this.overlay.set(null); this.arrow.set(null); this.counters.set({});
    this.pendingAttack = null;
  }
  ngOnDestroy(): void { this.cancel(); this.feedback.close(); }
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

