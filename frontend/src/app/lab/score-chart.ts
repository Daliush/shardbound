import { Component, computed, input, output } from '@angular/core';
import { max } from 'd3-array';
import { scaleLinear } from 'd3-scale';
import { EvaluationRun } from '../api/evaluation';
import { runKey, runName } from './layer-model';
import { atPath, formatMetric, isRatio, metricName, metricUnit } from './metrics';
@Component({
  selector:'app-score-chart',
  template:`<div class="axis"><span>{{ metricName(metric()) }} · {{ metricUnit(metric()) }}</span><span>0 — {{ format(metric(), ceiling()) }}</span></div>
    @for (row of rows(); track row.key) {
      <div class="score-row" [class.pinned]="pins().includes(row.key)" [style.--contender]="color(row.key)">
        <button class="run-label quiet" (click)="selected.emit(row.key)" [title]="row.name">{{ row.name }}</button>
        <svg viewBox="0 0 600 28" preserveAspectRatio="none" role="img" [attr.aria-label]="row.name + ': ' + format(metric(),row.value) + ' ' + metricName(metric())">
          <rect class="track" x="0" y="9" width="600" height="10" rx="2" />
          <rect class="bar" x="0" y="9" [attr.width]="width(row.value)" height="10" rx="2" />
          @if (floor() !== null) { <line class="floor" [attr.x1]="width(floor())" [attr.x2]="width(floor())" y1="2" y2="26" /> }
        </svg>
        <span class="score number">{{ format(metric(),row.value) }}</span><button class="pin quiet" [attr.aria-label]="(pins().includes(row.key) ? 'Unpin ' : 'Pin ') + row.name" [attr.aria-pressed]="pins().includes(row.key)" [disabled]="!pins().includes(row.key) && pins().length >= 4" (click)="pinned.emit(row.key)">{{ pins().includes(row.key) ? '−' : '+' }}</button>
      </div>
    }
    @if (floor() !== null) { <p class="floor-note">Dashed line: random retrieval floor · {{ format(metric(),floor()) }} {{ metricName(metric()) }}</p> }`,
  styles:`.axis { display: flex; justify-content: space-between; color: var(--muted); font-size: .68rem; padding: .4rem 0 1rem; }.score-row { display: grid; grid-template-columns: minmax(170px, 1.25fr) minmax(120px, 2fr) 80px 44px; gap: .8rem; align-items: center; border-top: 1px solid var(--line); min-height: 62px; }.run-label { font-size: .78rem; font-weight: 400; padding-inline: 0; justify-content: flex-start; text-align: left; overflow-wrap: anywhere; }.score { text-align: right; font-size: .82rem; }.score-row svg { width: 100%; height: 28px; }.track { fill: var(--obsidian-800); }.bar { fill: var(--prism-deep); transition: width 300ms var(--ease-out); }.pinned .bar { fill: var(--contender); }.floor { stroke: var(--text); stroke-width: 1.5; stroke-dasharray: 3 3; }.pin { padding: .3rem; font-size: 1.2rem; }.pinned .pin { color: var(--contender); }.floor-note { margin: 1rem 0 0; color: var(--muted); font-size: .72rem; }@media(max-width:650px) { .score-row { grid-template-columns: 1fr 65px 36px; gap: .2rem .5rem; padding: .4rem 0; }.run-label { grid-column: 1; min-height: 32px; font-size: .7rem; }.score-row svg { grid-row: 2; grid-column: 1 / 3; height: 16px; }.score { font-size: .75rem; }.pin { grid-column: 3; grid-row: 1 / 3; }.axis { font-size: .6rem; } }`,
})
export class ScoreChart {
  readonly runs = input<EvaluationRun[]>([]); readonly metric = input('mean_recall_at.10'); readonly pins = input<string[]>([]);
  readonly selected = output<string>(); readonly pinned = output<string>();
  protected readonly format = formatMetric; protected readonly metricName = metricName; protected readonly metricUnit = metricUnit;
  protected readonly rows = computed(() => this.runs().filter(run => run.layers['retriever'] !== 'random').map(run => ({key:runKey(run.layers),name:runName(run),value:atPath(run.scores.overall,this.metric())})).sort((a,b) => (b.value ?? -Infinity)-(a.value ?? -Infinity)));
  protected readonly floor = computed(() => atPath(this.runs().find(run => run.layers['retriever'] === 'random')?.scores.overall,this.metric()));
  protected readonly ceiling = computed(() => isRatio(this.metric()) ? 1 : Math.max(1,max(this.rows(),row => row.value ?? 0) ?? 1,this.floor() ?? 0));
  protected width(value: number | null): number { return scaleLinear().domain([0,this.ceiling()]).range([0,600]).clamp(true)(value ?? 0); }
  protected color(key: string): string { return 'var(--contender-' + Math.max(0,this.pins().indexOf(key)) + ')'; }
}

