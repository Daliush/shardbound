import { Component, computed, input, signal } from '@angular/core';
import { scaleLinear } from 'd3-scale';
import { EvaluationRun } from '../api/evaluation';
import { DECISION_METRIC, atPath, humanize, metrics } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { isVariant, pipelineName, runColor } from './presentation';
import { Icon } from '../core/icon';
import { isFloor, runKey } from './layer-model';
import { anchorOf } from './metric-guide-model';
import { Pager } from './pager';
import { pageOf, windowOf } from './pages';
import { RecallChart } from './recall-chart';
import { recallCurve } from './recall-model';
import { RollingNumber } from './rolling-number';
import { RowMotion } from './row-motion';

/**
 * The axis of a dot plot, zoomed on the values so that close scores stay apart: a nice domain around them, kept
 * within 0–1 for a ratio. Positions are percentages of the track.
 */
export function zoomedAxis(values: number[], bounded: boolean) {
  const low = Math.min(...values), high = Math.max(...values), pad = high > low ? 0 : Math.max(.05,Math.abs(high)*.1);
  const scale = scaleLinear().domain([low-pad,high+pad]).nice(4);
  if (bounded) scale.domain(scale.domain().map(value => Math.max(0,Math.min(1,value)))).nice(4);
  scale.range([0,100]);
  return {domain:scale.domain() as [number,number],ticks:scale.ticks(4).map(value => ({value,x:scale(value)})),x:(value: number) => scale(value)};
}

/** The recall curve's section, which folds like a metric's. */
const CONTEXT = 'context';

/**
 * Every metric on its own: first how much context each pipeline needs, the recall curve, then one dot plot per score,
 * each folding on itself and showing its best pipeline when folded, its pipelines ten at a time when open.
 */
@Component({
  selector:'app-metrics-graph', imports:[Icon,Pager,RecallChart,RollingNumber,RowMotion],
  templateUrl:'./metrics-graph.html', styleUrls:['./metrics-graph.css','./metrics-graph-fold.css'],
})
export class MetricsGraph {
  readonly runs = input<EvaluationRun[]>([]);
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  protected readonly category = signal('');
  protected readonly CONTEXT = CONTEXT;
  /** The sections open: the recall curve and the decision metric, on arrival. */
  private readonly open = signal<ReadonlySet<string>>(new Set([CONTEXT,DECISION_METRIC]));
  private readonly pages = signal<Readonly<Record<string,number>>>({});
  protected readonly decision = DECISION_METRIC; protected readonly humanize = humanize; protected readonly at = atPath;
  protected readonly categories = computed(() => [...new Set(this.runs().flatMap(run => Object.keys(run.scores.by_category)))]);
  protected readonly allMetrics = computed(() => metrics(this.runs()));
  protected readonly rows = computed(() => this.runs().map(run => ({key:runKey(run.layers),name:pipelineName(run),color:runColor(run),description:run.description,variant:isVariant(run),random:isFloor(run),scores:this.category() ? run.scores.by_category[this.category()] : run.scores.overall})));
  /** One dot plot per score, best first; the random floor is a reference line, not a row. */
  protected readonly axes = computed(() => this.allMetrics().filter(key => !this.catalog().isContext(key)).flatMap(key => {
    const pipelines = this.rows().filter(row => !row.random).map(row => ({...row,value:atPath(row.scores,key)}));
    const values = pipelines.flatMap(row => row.value === null ? [] : [row.value]);
    if (!values.length) return [];
    const axis = zoomedAxis(values,this.catalog().isBounded(key)), direction = this.lower(key) ? 1 : -1;
    const floor = atPath(this.rows().find(row => row.random)?.scores,key);
    const inside = floor !== null && floor >= axis.domain[0] && floor <= axis.domain[1];
    const sorted = pipelines.sort((a,b) => a.value === null ? 1 : b.value === null ? -1 : direction*(a.value-b.value));
    const rows = sorted.map(row => ({...row,x:row.value === null ? null : axis.x(row.value),rank:sorted.findIndex(other => other.value === row.value)+1}));
    const page = this.pageFor(key);
    return [{key,anchor:anchorOf(key),rows,shown:pageOf(rows,page),window:windowOf(rows.map(row => row.key),page),domain:axis.domain,ticks:axis.ticks,floor,floorX:inside ? axis.x(floor) : null}];
  }));
  /** The recall curve is drawn when the runs kept it. */
  protected readonly curves = computed(() => this.runs().some(run => Array.isArray(recallCurve(run.scores.overall))));
  private readonly sections = computed(() => [...(this.curves() ? [CONTEXT] : []),...this.axes().map(axis => axis.key)]);
  protected readonly allOpen = computed(() => this.sections().every(section => this.open().has(section)));
  protected isOpen(section: string): boolean { return this.open().has(section); }
  protected toggle(section: string): void {
    this.open.update(open => { const next = new Set(open); if (!next.delete(section)) next.add(section); return next; });
  }
  protected toggleAll(): void { this.open.set(new Set(this.allOpen() ? [] : this.sections())); }
  protected pageFor(metric: string): number { return this.pages()[metric] ?? 0; }
  protected turn(metric: string, page: number): void { this.pages.update(pages => ({...pages,[metric]:page})); }
  /** Ticks are round numbers: 52%, 0.45. */
  protected format(metric: string, value: number | null): string { return this.catalog().format(metric,value); }
  protected metricName(metric: string): string { return this.catalog().name(metric); }
  protected lower(metric: string): boolean { return this.catalog().lowerIsBetter(metric); }
  protected tickLabel(key: string, value: number): string { return this.catalog().isShare(key) ? +(value*100).toFixed(1) + '%' : String(+value.toFixed(3)); }
  protected value(event: Event): string { return (event.target as HTMLSelectElement).value; }
}
