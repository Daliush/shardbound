import { Component, ElementRef, afterNextRender, inject, viewChild } from '@angular/core';
import { CardComponent } from '../../cards/card';
import { ArenaStage } from './stage-player';

/** What the stage draws above the table: particles, the strike's beam, travelling cards, numbers, the turn banner. */
@Component({
  selector: 'app-stage-overlay',
  imports: [CardComponent],
  template: `<canvas #canvas aria-hidden="true"></canvas>
    @if (stage.arrow(); as arrow) {
      @let d = 'M' + arrow.x1 + ',' + arrow.y1 + ' Q' + ((arrow.x1 + arrow.x2) / 2 + (arrow.x2 - arrow.x1) * .2) + ',' + ((arrow.y1 + arrow.y2) / 2) + ' ' + arrow.x2 + ',' + arrow.y2;
      <svg class="beam" aria-hidden="true"><path class="glow" [attr.d]="d" /><path class="line" [attr.d]="d" /><circle [attr.cx]="arrow.x2" [attr.cy]="arrow.y2" r="11" /></svg>
    }
    @if (stage.ghost(); as ghost) { <div class="ghost" data-stage-ghost [style.left.px]="ghost.x" [style.top.px]="ghost.y"><app-card [cardId]="ghost.card?.card ?? ''" [back]="ghost.back" [interactiveTilt]="false" [style.--card-width]="ghost.width + 'px'" /></div> }
    @if (stage.number(); as number) { <div data-stage-number class="number" [class.heal]="number.kind === 'heal'" [style.left.px]="number.x" [style.top.px]="number.y">{{ number.text }}</div> }
    @if (stage.banner(); as banner) { <div data-stage-banner class="banner" [class.theirs]="banner.tone === 'opponent'" [style.top.px]="banner.y" role="presentation"><span>{{ banner.text }}</span></div> }
  `,
  styles: `
    :host { position: fixed; inset: 0; pointer-events: none; z-index: 35; }
    canvas, .beam { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
    .beam path { fill: none; stroke-linecap: round; }
    .beam .glow { stroke: var(--game-attack); stroke-width: 12; opacity: .2; filter: blur(4px); }
    .beam .line { stroke: var(--game-attack); stroke-width: 3; stroke-dasharray: 12 8; animation: flow .7s linear infinite; }
    .beam circle { fill: none; stroke: var(--game-attack); stroke-width: 2.5; }
    .ghost { position: absolute; translate: -50% -50%; perspective: 900px; filter: drop-shadow(0 18px 30px color-mix(in srgb, var(--black) 60%, transparent)); }
    .number { position: absolute; translate: -50% -50%; font: 800 clamp(2.4rem, 5vw, 3.4rem)/1 var(--font-ui); font-variant-numeric: tabular-nums; color: var(--damage); -webkit-text-stroke: 1px color-mix(in srgb, var(--black) 40%, transparent); text-shadow: 0 4px 14px color-mix(in srgb, var(--black) 75%, transparent); }
    .number.heal { color: var(--heal); }
    .banner { position: absolute; left: 0; right: 0; translate: 0 -50%; display: grid; place-items: center; padding: 14px 0; background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--obsidian-950) 97%, transparent) 12% 88%, transparent); }
    .banner span { font: 600 clamp(2.2rem, 6vw, 3.6rem)/1 var(--font-title); letter-spacing: -.01em; color: var(--faction-light); text-shadow: 0 0 34px color-mix(in srgb, var(--faction) 55%, transparent); }
    .banner.theirs span { color: var(--text); text-shadow: 0 0 30px color-mix(in srgb, var(--text) 25%, transparent); }
    @keyframes flow { to { stroke-dashoffset: -20; } }
  `,
})
export class StageOverlay {
  protected readonly stage = inject(ArenaStage);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  constructor() { afterNextRender(() => this.stage.attach(this.canvas().nativeElement)); }
}
