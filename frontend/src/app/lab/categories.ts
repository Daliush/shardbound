import { Component, computed, input, output } from '@angular/core';
import { scaleLinear } from 'd3-scale';
import { EvaluationRun } from '../api/evaluation';
import { isFloor, runKey, runName } from './layer-model';
import { atPath, formatMetric, humanize, isContextMetric, isRatio, metricName } from './metrics';
import { ScoreChart } from './score-chart';
@Component({
  selector:'app-categories',imports:[ScoreChart],
  template:`<div class="view-toggle"><p>{{ metricName(metric()) }} by category</p><button [attr.aria-pressed]="!multiples()" (click)="modeChanged.emit(false)">Heatmap</button><button [attr.aria-pressed]="multiples()" (click)="modeChanged.emit(true)">Small multiples</button></div>
  @if (multiples()) { <div class="multiples">@for (category of categories(); track category.name) { <section><h3>{{ humanize(category.name) }} <small>{{ category.count }} questions</small></h3>@if (category.count < 10) { <p class="noise">{{ noise(category.count) }}</p> }<app-score-chart [runs]="category.runs" [metric]="metric()" [pins]="pins()" (selected)="selected.emit($event)" (pinned)="pinned.emit($event)" /></section> }</div> }
  @else { <div class="table-scroll"><table><caption class="sr-only">{{ metricName(metric()) }} for each run and category</caption><thead><tr><th scope="col">Pipeline</th>@for (category of categories(); track category.name) { <th scope="col">{{ humanize(category.name) }}<small>{{ category.count }} questions</small>@if (category.count < 10) { <span class="noise" [title]="noise(category.count)">Small sample</span> }</th> }</tr></thead><tbody>@for (run of runs(); track key(run.layers)) { <tr><th scope="row"><button class="quiet" (click)="selected.emit(key(run.layers))">{{ name(run) }}@if (floor(run)) { <small>floor</small> }</button></th>@for (category of categories(); track category.name) { <td [style.background]="fill(run,category.name)">{{ format(metric(),value(run,category.name)) }}</td> }</tr> }</tbody></table></div> }
  <p class="note">Values are the means in the evaluation file. Small categories move sharply when a single question changes.</p>`,
  styles:`.view-toggle { display: flex; align-items: center; gap: .5rem; margin-bottom: 1.5rem; }.view-toggle p { margin: 0 auto 0 0; color: var(--muted); font-size: .8rem; }.view-toggle button { font-size: .72rem; }.view-toggle button[aria-pressed='true'] { border-color: var(--prism); }.table-scroll { overflow: auto; }table { width: 100%; border-collapse: separate; border-spacing: 3px; font-size: .75rem; }th { font-weight: 500; text-align: left; padding: .7rem; min-width: 130px; }th:first-child { position: sticky; left: 0; z-index: 2; background: var(--obsidian-950); min-width: 230px; }th button { text-align: left; justify-content: flex-start; padding: .3rem; font-size: .73rem; width: 100%; }th small { display: block; color: var(--muted); font-weight: 400; margin-top: .3rem; }td { text-align: center; font-family: var(--font-number); color: var(--text); }.noise { display: block; font-size: .64rem; color: var(--contender-1); margin-top: .3rem; }.note { color: var(--muted); font-size: .74rem; margin-top: 1.5rem; }.multiples { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 2.5rem; }.multiples h3 small { display: block; font-size: .7rem; font-weight: 400; margin-top: .3rem; }@media(max-width:700px) { .multiples { grid-template-columns: 1fr; }.view-toggle { flex-wrap: wrap; }.view-toggle p { width: 100%; }th:first-child { min-width: 160px; max-width: 180px; } }`,
})
export class Categories {
  readonly runs = input<EvaluationRun[]>([]); readonly metric = input('mean_recall_at.10'); readonly pins = input<string[]>([]);
  readonly selected = output<string>(); readonly pinned = output<string>();
  readonly multiples = input(false); readonly modeChanged = output<boolean>(); protected readonly format = formatMetric; protected readonly humanize = humanize; protected readonly metricName = metricName; protected readonly key = runKey; protected readonly name = runName; protected readonly floor = isFloor;
  protected readonly categories = computed(() => [...new Set(this.runs().flatMap(run => Object.keys(run.scores.by_category)))].map(name => ({name,count:Math.max(...this.runs().map(run => Number(run.scores.by_category[name]?.['question_count'] ?? 0))),runs:this.runs().filter(run => !!run.scores.by_category[name]).map(run => ({...run,scores:{...run.scores,overall:run.scores.by_category[name]}}))})));
  protected value(run: EvaluationRun, category: string): number | null { return atPath(run.scores.by_category[category],this.metric()); }
  protected fill(run: EvaluationRun, category: string): string {
    if (isContextMetric(this.metric())) return 'var(--lab-surface)';
    const value = this.value(run,category);
    const ceiling = isRatio(this.metric()) ? 1 : Math.max(1,...this.runs().map(run => this.value(run,category) ?? 0));
    const percent = scaleLinear().domain([0,ceiling]).range([2,40]).clamp(true)(value ?? 0);
    return 'color-mix(in srgb, var(--tide) ' + percent + '%, var(--obsidian-950))';
  }
  protected noise(count: number): string { return count + ' questions: one question moves this by ' + Math.round(100 / Math.max(1,count)) + ' points'; }
}

