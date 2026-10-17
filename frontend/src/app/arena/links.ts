import { Component, ElementRef, OnDestroy, afterRenderEffect, inject, input, signal } from '@angular/core';
import { GameView } from '../api/protocol';
import { Anchors } from './stage/anchors';
import { PendingCombat } from './combat';
@Component({
  selector:'app-board-links',
  template:`<svg aria-hidden="true"><defs><marker id="combat-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0 8 4 0 8Z" class="arrowhead" /></marker></defs>@for (path of paths(); track $index) { <path [attr.d]="path" /> }@if (attackPath()) { <path class="attack-path" [attr.d]="attackPath()" marker-end="url(#combat-arrow)" /> }</svg>`,
  styles:`:host { position: absolute; inset: 0; pointer-events: none; z-index: 8; }svg { width: 100%; height: 100%; overflow: visible; }path { fill: none; stroke: var(--tide); stroke-width: 1.5; stroke-dasharray: 4 5; opacity: .6; }.attack-path { stroke: var(--game-attack); stroke-width: 3; stroke-dasharray: 0; opacity: 1; filter: drop-shadow(0 2px 4px #000); }.arrowhead { fill: var(--game-attack); stroke: none; opacity: 1; }`,
})
export class BoardLinks implements OnDestroy {
  readonly view = input.required<GameView>();
  readonly combat = input<PendingCombat | null>(null);
  protected readonly attackPath = signal<string | null>(null);
  protected readonly paths = signal<string[]>([]);
  private readonly anchors = inject(Anchors);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly resize = () => this.measure();
  private frame = 0;
  constructor() {
    window.addEventListener('resize',this.resize);
    window.addEventListener('scroll', this.resize, true);
    afterRenderEffect(() => { this.view(); this.combat(); cancelAnimationFrame(this.frame); this.frame = requestAnimationFrame(() => this.measure()); });
  }
  private measure(): void {
    const bounds = this.host.nativeElement.getBoundingClientRect(), view = this.view();
    const combat = this.combat(), a = this.anchors.rect(combat?.attacker ?? null), b = this.anchors.rect(combat?.target ?? null);
    if (a && b) {
      const down = a.top < b.top;
      const ax = a.left + a.width / 2 - bounds.left, ay = (down ? a.bottom : a.top) - bounds.top;
      const bx = b.left + b.width / 2 - bounds.left, by = (down ? b.top - 12 : b.bottom + 12) - bounds.top;
      this.attackPath.set('M' + ax + ',' + ay + ' C' + ax + ',' + (ay+by)/2 + ' ' + bx + ',' + (ay+by)/2 + ' ' + bx + ',' + by);
    } else this.attackPath.set(null);
    this.paths.set([...view.you.units,...(view.opponent?.units ?? [])].filter(unit => unit.linkedTo !== null && unit.id < unit.linkedTo).flatMap(unit => {
      const a = this.anchors.rect('unit:' + unit.id), b = this.anchors.rect('unit:' + unit.linkedTo);
      if (!a || !b) return [];
      const ax = a.left + a.width / 2 - bounds.left, ay = a.top + a.height / 2 - bounds.top, bx = b.left + b.width / 2 - bounds.left, by = b.top + b.height / 2 - bounds.top;
      return ['M' + ax + ',' + ay + ' Q' + (ax + bx) / 2 + ',' + (Math.min(ay,by)-35) + ' ' + bx + ',' + by];
    }));
  }
  ngOnDestroy(): void { window.removeEventListener('resize',this.resize); window.removeEventListener('scroll',this.resize,true); cancelAnimationFrame(this.frame); }
}

