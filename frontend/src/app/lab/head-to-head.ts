import { Component, computed, input, output } from '@angular/core';
import { scaleLinear } from 'd3-scale';
import { EvaluationRun } from '../api/evaluation';
import { countDifferences, differences } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { runName } from './layer-model';
@Component({
  selector:'app-head-to-head',
  template:`@if (runs().length >= 2) {
    <div class="versus"><p><b>A</b>{{ name(runs()[0]) }}</p><p><b>B</b>{{ name(runs()[1]) }}</p></div>
    <div class="counts"><span><b>{{ counts().a }}</b> A higher</span><span><b>{{ counts().tie }}</b> tied</span><span><b>{{ counts().b }}</b> B higher</span></div>
    <p class="note">{{ metricName(metric()) }} difference per question · A − B{{ ratio() ? ' in percentage points' : '' }}. {{ context(metric()) ? 'Candidate counts are context only; more is not better.' : 'Percentage deltas use points; score deltas are absolute.' }}</p>
    @for (summary of categoryCounts(); track summary.category) { <span class="category-count">{{ summary.category }}: {{ summary.count.a }} / {{ summary.count.tie }} / {{ summary.count.b }}</span> }
    <div class="delta-axis"><span>B higher</span><span>0</span><span>A higher</span></div>
    <div class="delta-list">@for (row of rows(); track row.id) { <button class="delta-row quiet" (click)="opened.emit(row.id)" [attr.aria-label]="row.id + ': ' + deltaText(row.delta) + ' ' + metricName(metric())"><span class="question-id">{{ row.id }}</span><svg viewBox="0 0 400 22" preserveAspectRatio="none" aria-hidden="true"><line x1="200" x2="200" y1="0" y2="22" /><rect [attr.x]="Math.min(200,position(row.delta))" [attr.width]="Math.max(1,Math.abs(position(row.delta)-200))" y="5" height="12" [class.b]="row.delta < 0" /></svg><span class="number">{{ deltaText(row.delta) }}</span></button> } @empty { <p class="empty-state">This metric has no comparable per-question values in these runs.</p> }</div>
  } @else { <p class="empty-state">Pin two pipelines to compare them question by question.</p> }`,
  styles:`.versus { display: flex; justify-content: space-between; gap: 2rem; }.versus p { font-size: .76rem; overflow-wrap: anywhere; }.versus b { display: inline-block; margin-right: .6rem; color: var(--contender-0); }.versus p:last-child b { color: var(--contender-1); }.counts { display: flex; gap: 2rem; margin: 1rem 0; font-size: .78rem; }.counts b { font: 1.3rem var(--font-number); margin-right: .3rem; }.note { font-size: .72rem; color: var(--muted); }.category-count { display: inline-block; font-size: .65rem; margin: 0 1rem .5rem 0; color: var(--muted); }.delta-axis { display: flex; justify-content: space-between; margin: 1.5rem 80px .5rem 40%; font-size: .65rem; color: var(--muted); }.delta-list { max-height: 620px; overflow: auto; }.delta-row { width: 100%; display: grid; grid-template-columns: minmax(140px, 2fr) minmax(100px, 3fr) 70px; gap: 1rem; border-radius: 0; border-bottom: 1px solid var(--line); padding: .4rem 0; font-size: .68rem; text-align: left; }.question-id { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.delta-row svg { width: 100%; height: 22px; }.delta-row line { stroke: var(--line); }.delta-row rect { fill: var(--contender-0); }.delta-row rect.b { fill: var(--contender-1); }.number { text-align: right; }@media(max-width:600px) { .counts { gap: .75rem; font-size: .65rem; }.counts b { font-size: 1rem; }.delta-row { grid-template-columns: 1fr 58px; gap: .25rem; }.question-id { grid-column: 1 / -1; }.delta-axis { margin-left: 0; margin-right: 58px; }.versus { gap: 1rem; } }`,
})
export class HeadToHead {
  readonly runs = input<EvaluationRun[]>([]); readonly metric = input('mean_recall_at.10'); readonly category = input('');
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  readonly opened = output<string>();
  protected readonly Math = Math; protected readonly name = runName; protected readonly ratio = computed(() => this.catalog().isShare(this.metric()));
  private readonly allRows = computed(() => this.runs().length >= 2 ? differences(this.runs()[0],this.runs()[1],this.metric()) : []);
  protected readonly rows = computed(() => this.allRows().filter(row => !this.category() || row.category === this.category()));
  protected readonly counts = computed(() => countDifferences(this.rows()));
  protected readonly categoryCounts = computed(() => [...new Set(this.allRows().map(row => row.category))].map(category => ({category,count:countDifferences(this.allRows().filter(row => row.category === category))})));
  protected position(value: number): number { const extent = this.ratio() ? 1 : Math.max(1,...this.rows().map(row => Math.abs(row.delta))); return scaleLinear().domain([-extent,extent]).range([0,400])(value); }
  protected deltaText(value: number): string { return (value > 0 ? '+' : '') + (this.ratio() ? (value * 100).toFixed(1) + ' pp' : this.catalog().format(this.metric(),value)); }
  protected context(metric: string): boolean { return this.catalog().isContext(metric); }
  protected metricName(metric: string): string { return this.catalog().name(metric); }
}

