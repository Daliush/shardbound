import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { scaleLinear } from 'd3-scale';
import { line } from 'd3-shape';
import { Evaluation } from '../api/evaluation';
import { Evaluations } from './evaluations';
import { runKey, runName } from './layer-model';
import { atPath, formatMetric, isRatio, metricName } from './metrics';
@Component({
  selector:'app-lab-history',
  template:`@if (timestamps().length < 2) { <div class="empty-state"><h2>The first measurement.</h2><p>This split has one evaluation. Trends will appear when another evaluation is published.</p></div> }
  @else if (error()) { <p class="error" role="alert">{{ error() }}</p> }
  @else if (loading()) { <p role="status">Loading evaluation history…</p> }
  @else { <p class="muted">{{ metricName(metric()) }} over time · identical layer configurations are one series.</p>@for (series of series(); track series.key) { <section class="history-series"><h3>{{ series.name }}</h3><svg viewBox="0 0 800 160" role="img" [attr.aria-label]="series.name + ': ' + metricName(metric())"><line x1="35" x2="780" y1="130" y2="130" /><path [attr.d]="series.path" />@for (point of series.points; track point.index) { <circle [attr.cx]="point.x" [attr.cy]="point.y" r="4"><title>{{ point.date }} · {{ point.code }} · {{ format(metric(),point.value) }}</title></circle> }<text x="0" y="134">0</text><text x="0" y="18">{{ format(metric(),ceiling()) }}</text></svg><div class="history-points">@for (point of series.points; track point.index) { <span>{{ point.date }}<small>{{ point.code }} · {{ format(metric(),point.value) }}</small></span> }</div></section> } }`,
  styles:`.history-series { border-top: 1px solid var(--line); padding-top: 1.5rem; margin-top: 2rem; }.history-series h3 { font-size: .8rem; }svg { width: 100%; max-height: 200px; }path { fill: none; stroke: var(--prism); stroke-width: 2; }line { stroke: var(--line); }circle { fill: var(--prism-light); }text { fill: var(--muted); font: 10px var(--font-number); }.history-points { display: flex; justify-content: space-between; gap: 1rem; overflow: auto; font-size: .7rem; }.history-points small { display: block; font-family: var(--font-number); margin-top: .3rem; }`,
})
export class LabHistory {
  readonly version = input.required<string>(); readonly split = input.required<string>(); readonly timestamps = input<string[]>([]); readonly metric = input('mean_recall_at.10');
  private readonly evaluations = inject(Evaluations); private generation = 0;
  private readonly reports = signal<Evaluation[]>([]);
  protected readonly loading = signal(false); protected readonly error = signal('');
  protected readonly format = formatMetric; protected readonly metricName = metricName;
  protected readonly ceiling = computed(() => isRatio(this.metric()) ? 1 : Math.max(1,...this.reports().flatMap(report => report.runs.map(run => atPath(run.scores.overall,this.metric()) ?? 0))));
  protected readonly series = computed(() => {
    const reports = this.reports(), x = scaleLinear().domain([0,Math.max(1,reports.length-1)]).range([35,780]), y = scaleLinear().domain([0,this.ceiling()]).range([130,15]);
    const keys = [...new Set(reports.flatMap(report => report.runs.map(run => runKey(run.layers))))];
    return keys.map(key => {
      let name = '';
      const points = reports.flatMap((report,index) => { const run = report.runs.find(run => runKey(run.layers) === key); if (!run) return []; name = runName(run); const value = atPath(run.scores.overall,this.metric()); return value === null ? [] : [{index,x:x(index),y:y(value),value,date:new Date(report.evaluated_at).toLocaleDateString('en-GB'),code:report.code}]; });
      return {key,name,points,path:line<typeof points[number]>().x(point => point.x).y(point => point.y)(points)};
    });
  });
  constructor() { effect(() => { const version = this.version(), split = this.split(), timestamps = this.timestamps(); void this.load(version,split,timestamps); }); }
  private async load(version: string, split: string, timestamps: string[]): Promise<void> {
    const generation = ++this.generation;
    if (timestamps.length < 2) { this.reports.set([]); return; }
    this.loading.set(true); this.error.set('');
    try { const reports = await Promise.all(timestamps.map(timestamp => firstValueFrom(this.evaluations.report(version,split,timestamp)))); if (generation === this.generation) this.reports.set(reports); }
    catch { if (generation === this.generation) this.error.set('History could not be loaded. Try reopening this view.'); }
    finally { if (generation === this.generation) this.loading.set(false); }
  }
}

