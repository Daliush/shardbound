import { Component, ElementRef, OnDestroy, afterRenderEffect, inject, input, signal } from '@angular/core';
import { GameView } from '../api/protocol';
import { Anchors } from './stage/anchors';
import { PendingCombat } from './combat';

export interface Aim { from: string; to: string; }
interface Beam { d: string; x: number; y: number; }

/** Lines drawn over the table: the attack waiting for an intercept, the aim of a selection, and linked units. */
@Component({
  selector: 'app-board-links',
  template: `<svg aria-hidden="true">
    @for (path of links(); track $index) { <path class="link" [attr.d]="path" /> }
    @if (attack(); as beam) { <path class="beam glow" [attr.d]="beam.d" /><path class="beam" [attr.d]="beam.d" /><circle class="reticle" [attr.cx]="beam.x" [attr.cy]="beam.y" r="9" /> }
    @if (aimed(); as beam) { <path class="aim" [attr.d]="beam.d" /><circle class="aim-end" [attr.cx]="beam.x" [attr.cy]="beam.y" r="5" /> }
  </svg>`,
  styles: `
    :host { position: absolute; inset: 0; pointer-events: none; z-index: 8; }
    svg { width: 100%; height: 100%; overflow: visible; }
    path { fill: none; }
    .link { stroke: var(--tide); stroke-width: 1.5; stroke-dasharray: 4 5; opacity: .65; }
    .beam { stroke: var(--game-attack); stroke-width: 3; stroke-linecap: round; stroke-dasharray: 10 8; animation: flow .8s linear infinite; }
    .beam.glow { stroke-width: 10; opacity: .18; stroke-dasharray: none; animation: none; filter: blur(3px); }
    .reticle { fill: none; stroke: var(--game-attack); stroke-width: 2.5; animation: lock 1.2s var(--ease-out) infinite; transform-box: fill-box; transform-origin: center; }
    .aim { stroke: var(--target); stroke-width: 2; stroke-dasharray: 6 6; opacity: .85; animation: flow .9s linear infinite; }
    .aim-end { fill: var(--target); }
    @keyframes flow { to { stroke-dashoffset: -18; } }
    @keyframes lock { from { scale: 1.6; opacity: 0; } 40% { scale: 1; opacity: 1; } }
  `,
})
export class BoardLinks implements OnDestroy {
  readonly view = input.required<GameView>();
  readonly combat = input<PendingCombat | null>(null);
  readonly aim = input<Aim | null>(null);
  protected readonly attack = signal<Beam | null>(null);
  protected readonly aimed = signal<Beam | null>(null);
  protected readonly links = signal<string[]>([]);
  private readonly anchors = inject(Anchors);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly resize = () => this.measure();
  private frame = 0;
  constructor() {
    window.addEventListener('resize', this.resize);
    window.addEventListener('scroll', this.resize, true);
    afterRenderEffect(() => { this.view(); this.combat(); this.aim(); cancelAnimationFrame(this.frame); this.frame = requestAnimationFrame(() => this.measure()); });
  }
  private measure(): void {
    const bounds = this.host.nativeElement.getBoundingClientRect(), view = this.view(), combat = this.combat(), aim = this.aim();
    this.attack.set(combat ? this.beam(bounds, combat.attacker, combat.target) : null);
    this.aimed.set(aim ? this.beam(bounds, aim.from, aim.to) : null);
    this.links.set([...view.you.units, ...(view.opponent?.units ?? [])].filter(unit => unit.linkedTo !== null && unit.id < unit.linkedTo).flatMap(unit => {
      const a = this.anchors.rect('unit:' + unit.id), b = this.anchors.rect('unit:' + unit.linkedTo);
      if (!a || !b) return [];
      const ax = a.left + a.width / 2 - bounds.left, ay = a.top + a.height / 2 - bounds.top, bx = b.left + b.width / 2 - bounds.left, by = b.top + b.height / 2 - bounds.top;
      return ['M' + ax + ',' + ay + ' Q' + (ax + bx) / 2 + ',' + (Math.min(ay, by) - 35) + ' ' + bx + ',' + by];
    }));
  }
  /** A curve from the edge of one element to the edge of the other, facing each other. */
  private beam(bounds: DOMRect, from: string, to: string): Beam | null {
    const a = this.anchors.rect(from), b = this.anchors.rect(to);
    if (!a || !b) return null;
    const down = a.top < b.top;
    const ax = a.left + a.width / 2 - bounds.left, ay = (down ? a.bottom - a.height * .2 : a.top + a.height * .2) - bounds.top;
    const bx = b.left + b.width / 2 - bounds.left, by = (down ? b.top + b.height * .35 : b.bottom - b.height * .35) - bounds.top;
    const bend = (bx - ax) * .25;
    return { d: 'M' + ax + ',' + ay + ' C' + (ax + bend) + ',' + (ay + by) / 2 + ' ' + (bx - bend) + ',' + (ay + by) / 2 + ' ' + bx + ',' + by, x: bx, y: by };
  }
  ngOnDestroy(): void { window.removeEventListener('resize', this.resize); window.removeEventListener('scroll', this.resize, true); cancelAnimationFrame(this.frame); }
}
