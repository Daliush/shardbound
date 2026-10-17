import { Component, ElementRef, afterNextRender, inject, viewChild } from '@angular/core';
import { CardComponent } from '../../cards/card';
import { ArenaStage } from './stage-player';

@Component({
  selector: 'app-stage-overlay',
  imports: [CardComponent],
  template: `<canvas #canvas aria-hidden="true"></canvas>
    @if (stage.arrow(); as arrow) { <svg class="arrows" aria-hidden="true"><defs><marker id="stage-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto"><path d="M0 0 6 3 0 6" fill="none" stroke="currentColor" /></marker></defs><path [attr.d]="'M' + arrow.x1 + ',' + arrow.y1 + ' Q' + ((arrow.x1 + arrow.x2) / 2 + 40) + ',' + (Math.min(arrow.y1, arrow.y2) - 40) + ' ' + arrow.x2 + ',' + arrow.y2" marker-end="url(#stage-arrow)" /></svg> }
    @if (stage.ghost(); as ghost) { <div class="ghost" data-stage-ghost [style.left.px]="ghost.x" [style.top.px]="ghost.y"><app-card [cardId]="ghost.card?.card ?? ''" [back]="ghost.back" [style.--card-width]="ghost.width + 'px'" /></div> }
    @if (stage.overlay(); as overlay) { <div data-stage-label class="beat-label" [class.number]="overlay.number" [class.heal]="overlay.kind === 'heal'" [class.banner]="!overlay.number" [style.left.px]="overlay.number ? overlay.x : null" [style.top.px]="overlay.number ? overlay.y : null">{{ overlay.number ?? overlay.text }}</div> }
  `,
  styles: `:host { position: fixed; inset: 0; pointer-events: none; z-index: 35; } canvas,.arrows { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; } .arrows { color: var(--target); } .arrows > path { fill: none; stroke: currentColor; stroke-width: 3; stroke-dasharray: 9 5; } .ghost { position: absolute; translate: -50% -50%; perspective: 800px; } .beat-label { position: absolute; translate: -50% -50%; text-align: center; } .beat-label.number { font-size: 3rem; font-weight: 600; color: var(--damage); text-shadow: 0 3px 3px var(--black); } .beat-label.heal { color: var(--heal); } .banner { top: 43%; left: 50%; max-width: min(86vw, 680px); padding: .75rem 1.5rem; color: var(--text); background: var(--obsidian-950); border-block: 1px solid var(--prism-deep); font: 600 clamp(1.3rem, 3vw, 2rem)/1.15 var(--font-title); }`,
})
export class StageOverlay {
  protected readonly stage = inject(ArenaStage);
  protected readonly Math = Math;
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  constructor() { afterNextRender(() => this.stage.attach(this.canvas().nativeElement)); }
}
