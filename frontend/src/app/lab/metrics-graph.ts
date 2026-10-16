import { Component, computed, input, signal } from '@angular/core';
import { EvaluationRun } from '../api/evaluation';
import { atPath, formatMetric, isRatio, metricDescription, metricName, metrics, lowerIsBetter } from './metrics';
import { pipelineName } from './presentation';
import { runKey } from './layer-model';
import { LabMotion } from './lab-motion';

@Component({
  selector: 'app-metrics-graph', imports: [LabMotion],
  template: `
    <div class="graph-toolbar"><p>Follow a combination across every metric.</p><label>Question category<select [value]="category()" (change)="category.set(value($event))"><option value="">All questions</option>@for (item of categories(); track item) { <option [value]="item">{{ item.replaceAll('_', ' ') }}</option> }</select></label></div>
    <div class="plot" [labMotion]="category()">
      <svg viewBox="0 0 960 390" role="img" aria-labelledby="plot-title plot-description">
        <title id="plot-title">Scores across metrics</title><desc id="plot-description">Each line is a combination. Each axis is labelled with its scale. Exact values are in the table below.</desc>
        @for (tick of ticks; track tick) { <line x1="95" x2="865" [attr.y1]="y(tick)" [attr.y2]="y(tick)" class="grid" /> }
        @for (axis of axes(); track axis.key; let i = $index) {
          <line [attr.x1]="x(i)" [attr.x2]="x(i)" y1="45" y2="315" class="axis" />
          <text [attr.x]="x(i)" y="24" class="axis-title">{{ metricName(axis.key) }}</text>
          <text [attr.x]="x(i)" y="350" class="axis-caption">{{ lower(axis.key) ? 'Lower is better' : 'Higher is better' }}</text>
          @for (tick of ticks; track tick) { <text [attr.x]="x(i) - 12" [attr.y]="y(tick) + 4" class="tick">{{ format(axis.key, tick * axis.max) }}</text> }
        }
        @for (series of series(); track series.key; let i = $index) {
          <g [style.color]="color(i)" [class.faded]="focused() !== null && focused() !== series.key" [class.highlighted]="focused() === series.key">
            <path [attr.d]="series.path" data-score-line />
            @for (point of series.points; track point.metric) { @if (point.value !== null) {
              <circle [attr.cx]="point.x" [attr.cy]="point.y" r="4.5"><title>{{ series.name }} — {{ metricName(point.metric) }}: {{ format(point.metric,point.value) }}</title></circle>
            } }
          </g>
        }
      </svg>
    </div>
    <div class="legend" aria-label="Highlight a combination">@for (series of series(); track series.key; let i = $index) {
      <button class="quiet" [attr.aria-pressed]="focused() === series.key" [class.dimmed]="focused() !== null && focused() !== series.key" (click)="focused.set(focused() === series.key ? null : series.key)"><i [style.background]="color(i)"></i>{{ series.name }}</button>
    }</div>
    <div class="score-table-wrap"><table><caption>Exact scores{{ category() ? ' · ' + category().replaceAll('_',' ') : '' }}</caption><thead><tr><th scope="col">Combination</th>@for (axis of axes(); track axis.key) { <th scope="col" [title]="description(axis.key)">{{ metricName(axis.key) }}</th> }</tr></thead><tbody>
      @for (series of series(); track series.key; let i = $index) { <tr [class.dimmed]="focused() !== null && focused() !== series.key"><th scope="row"><i [style.background]="color(i)"></i>{{ series.name }}</th>@for (point of series.points; track point.metric) { <td>{{ format(point.metric,point.value) }}</td> }</tr> }
    </tbody></table></div>
    <p class="scale-note">@if (sharedScale()) { All axes use the same 0–100% scale. } @else { Each metric uses its own labelled scale; compare values within an axis. } Missing scores are left blank. Random is the reference floor.</p>
  `,
  styleUrl: './metrics-graph.css',
})
export class MetricsGraph {
  readonly runs = input<EvaluationRun[]>([]);
  protected readonly focused = signal<string | null>(null);
  protected readonly category = signal('');
  protected readonly ticks = [0,.25,.5,.75,1];
  protected readonly format = formatMetric;
  protected readonly metricName = metricName;
  protected readonly description = metricDescription;
  protected readonly lower = lowerIsBetter;
  protected readonly categories = computed(() => [...new Set(this.runs().flatMap(run => Object.keys(run.scores.by_category)))]);
  protected readonly axes = computed(() => metrics(this.runs()).map(key => ({ key, max: isRatio(key) ? 1 : Math.max(1,...this.runs().map(run => atPath(this.category() ? run.scores.by_category[this.category()] : run.scores.overall,key) ?? 0)) })));
  protected readonly sharedScale = computed(() => this.axes().every(axis => isRatio(axis.key)));
  protected readonly series = computed(() => this.runs().map(run => {
    const points = this.axes().map((axis,index) => { const value = atPath(this.category() ? run.scores.by_category[this.category()] : run.scores.overall,axis.key); return {metric:axis.key,value,x:this.x(index),y:this.y((value ?? 0)/axis.max)}; });
    let connected = false;
    const path = points.map(point => { if (point.value === null) { connected = false; return ''; } const command = connected ? 'L' : 'M'; connected = true; return command + point.x + ',' + point.y; }).join(' ');
    return {key:runKey(run.layers),name:pipelineName(run),points,path};
  }));
  protected x(index: number): number { return 95 + index * (770 / Math.max(1,this.axes().length-1)); }
  protected y(value: number): number { return 315 - Math.max(0,Math.min(1,value)) * 270; }
  protected color(index: number): string { return 'var(--chart-' + index % 9 + ')'; }
  protected value(event: Event): string { return (event.target as HTMLSelectElement).value; }
}
