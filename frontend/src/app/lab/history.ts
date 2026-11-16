import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { scaleLinear } from 'd3-scale';
import { line } from 'd3-shape';
import { Evaluation, EvaluationRun } from '../api/evaluation';
import { Evaluations } from './evaluations';
import { runKey } from './layer-model';
import { deviceName, pipelineName, runColor } from './presentation';
import { atPath } from './metrics';
import { MetricCatalog } from './metric-catalog';
@Component({
  selector:'app-lab-history',
  template:`@if (error()) { <p class="error" role="alert">{{ error() }}</p> }
  @else if (loading()) { <p role="status">Loading evaluation history…</p> }
  @else { <div class="report-history">@for (report of reports().slice().reverse(); track report.evaluated_at) { <button class="history-entry quiet" (click)="opened.emit(timestamp(report.evaluated_at))"><span class="history-date">{{ date(report.evaluated_at) }}<small>{{ report.runs.length }} pipelines · {{ report.code.slice(0,7) }} · {{ device(report) }}</small></span><span class="history-description">{{ report.description || 'No description recorded for this evaluation.' }}</span><span class="open-report">Open report</span></button> }</div><p class="muted">{{ metricName(metric()) }} over time · identical layer configurations are one series{{ time() ? ' per device, since a time depends on the machine' : '' }}.{{ lower() ? ' Lower is better, so the axis is flipped: up is better.' : '' }} The best first.</p>@for (series of shown(); track series.key) { <section class="history-series"><h3>{{ series.name }}</h3><svg viewBox="0 0 800 160" role="img" [attr.aria-label]="series.name + ': ' + metricName(metric())"><line x1="35" x2="780" y1="130" y2="130" /><path [attr.d]="series.path" [style.stroke]="series.color" />@for (point of series.points; track point.index) { <circle [style.fill]="series.color" [attr.cx]="point.x" [attr.cy]="point.y" r="4"><title>{{ point.date }} · {{ point.code }} · {{ point.device }} · {{ format(metric(),point.value) }}</title></circle> }<text x="0" y="134">{{ lower() ? format(metric(),ceiling()) : '0' }}</text><text x="0" y="18">{{ lower() ? '0' : format(metric(),ceiling()) }}</text></svg><div class="history-points">@for (point of series.points; track point.index) { <span>{{ point.date }}<small>{{ point.code }} · {{ point.device }} · {{ format(metric(),point.value) }}</small></span> }</div></section> }@if (hidden()) { <button class="show-all" (click)="all.set(true)">Show the {{ hidden() }} other series</button> } }`,
  styles:`.report-history { margin-bottom: 2.5rem; }.history-entry { width: 100%; display: grid; grid-template-columns: 170px 1fr 90px; gap: 1.5rem; text-align: left; padding: 1.2rem .4rem; border: 0; border-bottom: 1px solid var(--line); border-radius: 0; font-size: .86rem; font-weight: 400; }.history-entry:hover:not(:disabled) { background: color-mix(in srgb, var(--text) 4%, transparent); border-color: var(--line); }.history-date { font-weight: 500; }.history-date small { display: block; font-size: .74rem; font-weight: 400; color: var(--muted); margin-top: .4rem; }.history-description { line-height: 1.7; }.open-report { justify-self: end; color: var(--muted); font-size: .8rem; }@media(max-width:650px) { .history-entry { grid-template-columns: 1fr; gap: .7rem; } }.history-series { border-top: 1px solid var(--line); padding-top: 1.5rem; margin-top: 2rem; }.history-series h3 { font-size: .92rem; font-weight: 500; }svg { width: 100%; max-height: 200px; }path { fill: none; stroke-width: 2; }line { stroke: var(--line); }text { fill: var(--muted); font: 11px var(--font-ui); }.history-points { display: flex; justify-content: space-between; gap: 1rem; overflow: auto; font-size: .76rem; }.history-points small { display: block; margin-top: .3rem; color: var(--muted); }.show-all { margin-top: 1.6rem; min-height: 38px; border-radius: 8px; font-size: .84rem; }`,
})
export class LabHistory {
  readonly opened = output<string>();
  readonly version = input.required<string>(); readonly split = input.required<string>(); readonly timestamps = input<string[]>([]); readonly metric = input('mean_recall_at.10');
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  private readonly evaluations = inject(Evaluations); private generation = 0;
  protected readonly reports = signal<Evaluation[]>([]);
  protected readonly loading = signal(false); protected readonly error = signal('');
  protected readonly ceiling = computed(() => this.catalog().ceiling(this.metric(),this.reports().flatMap(report => report.runs.map(run => atPath(run.scores.overall,this.metric())))));
  /** A time or a cost is better lower: its axis is flipped so that up is always better. */
  protected readonly lower = computed(() => this.catalog().lowerIsBetter(this.metric()));
  /** A time depends on the machine it was measured on: its series are drawn per device. */
  protected readonly time = computed(() => this.catalog().isTime(this.metric()));
  protected readonly series = computed(() => {
    const reports = this.reports(), time = this.time(), x = scaleLinear().domain([0,Math.max(1,reports.length-1)]).range([35,780]), y = scaleLinear().domain([0,this.ceiling()]).range(this.lower() ? [15,130] : [130,15]);
    const keyOf = (report: Evaluation, run: EvaluationRun) => runKey(run.layers) + (time ? ' @ ' + deviceName(report) : '');
    const keys = [...new Set(reports.flatMap(report => report.runs.map(run => keyOf(report,run))))];
    return keys.map(key => {
      let name = '', color = 'var(--lab-accent)';
      const points = reports.flatMap((report,index) => { const run = report.runs.find(run => keyOf(report,run) === key); if (!run) return []; name = pipelineName(run) + (time ? ' · ' + deviceName(report) : ''); color = runColor(run); const value = atPath(run.scores.overall,this.metric()); return value === null ? [] : [{index,x:x(index),y:y(value),value,date:new Date(report.evaluated_at).toLocaleDateString('en-GB'),code:report.code,device:deviceName(report)}]; });
      return {key,name,color,points,path:line<typeof points[number]>().x(point => point.x).y(point => point.y)(points)};
    });
  });
  /** Every pipeline has a series: the six best on the latest value first, the rest on demand. */
  protected readonly all = signal(false);
  private readonly ranked = computed(() => [...this.series()].sort((a,b) => {
    const last = (series: typeof a) => series.points.at(-1)?.value ?? null, left = last(a), right = last(b);
    if (left === null || right === null) return left === null ? 1 : -1;
    return this.lower() ? left - right : right - left;
  }));
  protected readonly shown = computed(() => this.all() ? this.ranked() : this.ranked().slice(0,6));
  protected readonly hidden = computed(() => this.ranked().length - this.shown().length);
  constructor() { effect(() => { const version = this.version(), split = this.split(), timestamps = this.timestamps(); void this.load(version,split,timestamps); }); }
  protected format(metric: string, value: number | null): string { return this.catalog().format(metric,value); }
  protected device(report: Evaluation): string { return deviceName(report); }
  protected metricName(metric: string): string { return this.catalog().name(metric); }
  protected timestamp(date: string): string { return date.replace(/T(\d{2}):(\d{2}):(\d{2})Z/, 'T$1-$2-$3Z'); }
  protected date(value: string): string { return new Date(value.replace(/T(\d{2})-(\d{2})-(\d{2})Z/, 'T$1:$2:$3Z')).toLocaleString('en-GB',{dateStyle:'medium',timeStyle:'short'}); }
  private async load(version: string, split: string, timestamps: string[]): Promise<void> {
    const generation = ++this.generation;
    if (!timestamps.length) { this.reports.set([]); return; }
    this.loading.set(true); this.error.set('');
    try { const reports = await Promise.all(timestamps.map(timestamp => firstValueFrom(this.evaluations.report(version,split,timestamp)))); if (generation === this.generation) this.reports.set(reports); }
    catch { if (generation === this.generation) this.error.set('History could not be loaded. Try reopening this view.'); }
    finally { if (generation === this.generation) this.loading.set(false); }
  }
}

