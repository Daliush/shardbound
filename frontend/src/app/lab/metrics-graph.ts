import { Component, computed, input, signal } from '@angular/core';
import { scaleLinear } from 'd3-scale';
import { EvaluationRun } from '../api/evaluation';
import { atPath, formatMetric, humanize, isRatio, isContextMetric, metricDescription, metricName, metrics, lowerIsBetter } from './metrics';
import { isVariant, pipelineName, runColor } from './presentation';
import { isFloor, runKey } from './layer-model';
import { LabMotion } from './lab-motion';

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

@Component({
  selector:'app-metrics-graph', imports:[LabMotion],
  template:`
    <div class="graph-toolbar"><p>One question at a time, one metric at a time. No combined score.</p><label>Question category<select [value]="category()" (change)="category.set(value($event))"><option value="">All questions</option>@for (item of categories(); track item) { <option [value]="item">{{ humanize(item) }}</option> }</select></label></div>
    <div class="metric-plots" [labMotion]="category()">
      @for (metric of axes(); track metric.key) {
        <section class="metric-plot">
          <header><h2>{{ metricName(metric.key) }}</h2><span>{{ metric.key === 'mean_recall_at.10' ? 'Decision metric' : 'Explains performance' }}</span></header>
          <p class="metric-description">{{ description(metric.key) }} {{ lower(metric.key) ? 'Lower is better.' : 'Higher is better.' }}</p>
          <div class="dot-plot">
            <div class="axis-row" aria-hidden="true"><span></span><div class="axis-ticks">@for (tick of metric.ticks; track tick.value) { <span [style.left.%]="tick.x">{{ tickLabel(metric.key,tick.value) }}</span> }</div><span></span></div>
            <div class="grid-lines" aria-hidden="true">@for (tick of metric.ticks; track tick.value) { <b [style.left.%]="tick.x"></b> }@if (metric.floorX !== null) { <b class="floor" [style.left.%]="metric.floorX"></b> }</div>
            @for (row of metric.rows; track row.key) {
              <div class="dot-row" [class.variant]="row.variant"><span class="row-name" [title]="row.description || row.name"><i [style.background]="row.color"></i>{{ row.name }}</span><div class="dot-track">@if (row.x !== null) { <i class="dot" data-score-dot [style.left.%]="row.x" [style.background]="row.color"></i> }</div><strong>{{ format(metric.key,row.value) }}</strong></div>
            }
          </div>
          <p class="axis-note">Axis zoomed on {{ format(metric.key,metric.domain[0]) }}–{{ format(metric.key,metric.domain[1]) }} to keep close scores apart.@if (metric.floor !== null) { Random floor: {{ format(metric.key,metric.floor) }}{{ metric.floorX === null ? ', off this axis' : ', dashed' }}. }</p>
        </section>
      }
    </div>
    <p class="scale-note">Each plot has its own axis, zoomed on its values: compare positions within a plot, not across plots. Candidate counts describe the retrieval budget and never determine a winner. Returning the entire corpus puts the end of the recall curve at 100% by construction.</p>
    <details class="exact-scores"><summary>All reported values</summary><div class="score-table-wrap"><table><caption>Exact scores · {{ category() ? humanize(category()) : 'All questions' }}</caption><thead><tr><th scope="col">Pipeline</th>@for (metric of allMetrics(); track metric) { <th scope="col">{{ metricName(metric) }}</th> }<th scope="col">Questions</th></tr></thead><tbody>@for (row of rows(); track row.key) { <tr><th scope="row" [title]="row.description || row.name"><i [style.background]="row.color"></i>{{ row.name }}</th>@for (metric of allMetrics(); track metric) { <td>{{ format(metric,at(row.scores,metric)) }}</td> }<td>{{ format('question_count',at(row.scores,'question_count')) }}</td></tr> }</tbody></table></div></details>
  `,
  styleUrl:'./metrics-graph.css',
})
export class MetricsGraph {
  readonly runs = input<EvaluationRun[]>([]);
  protected readonly category = signal('');
  protected readonly format = formatMetric; protected readonly metricName = metricName; protected readonly description = metricDescription;
  protected readonly humanize = humanize; protected readonly lower = lowerIsBetter; protected readonly at = atPath;
  protected readonly categories = computed(() => [...new Set(this.runs().flatMap(run => Object.keys(run.scores.by_category)))]);
  protected readonly allMetrics = computed(() => metrics(this.runs()));
  protected readonly rows = computed(() => this.runs().map(run => ({key:runKey(run.layers),name:pipelineName(run),color:runColor(run),description:run.description,variant:isVariant(run),random:isFloor(run),scores:this.category() ? run.scores.by_category[this.category()] : run.scores.overall})));
  /** One dot plot per score, best first; the random floor is a reference line, not a row. */
  protected readonly axes = computed(() => this.allMetrics().filter(key => !isContextMetric(key)).flatMap(key => {
    const pipelines = this.rows().filter(row => !row.random).map(row => ({...row,value:atPath(row.scores,key)}));
    const values = pipelines.flatMap(row => row.value === null ? [] : [row.value]);
    if (!values.length) return [];
    const axis = zoomedAxis(values,isRatio(key) || key === 'mean_reciprocal_rank'), direction = lowerIsBetter(key) ? 1 : -1;
    const floor = atPath(this.rows().find(row => row.random)?.scores,key);
    const inside = floor !== null && floor >= axis.domain[0] && floor <= axis.domain[1];
    const rows = pipelines.sort((a,b) => a.value === null ? 1 : b.value === null ? -1 : direction*(a.value-b.value)).map(row => ({...row,x:row.value === null ? null : axis.x(row.value)}));
    return [{key,rows,domain:axis.domain,ticks:axis.ticks,floor,floorX:inside ? axis.x(floor) : null}];
  }));
  /** Ticks are round numbers: 52%, 0.45. */
  protected tickLabel(key: string, value: number): string { return isRatio(key) ? +(value*100).toFixed(1) + '%' : String(+value.toFixed(3)); }
  protected value(event: Event): string { return (event.target as HTMLSelectElement).value; }
}
