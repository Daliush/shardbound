import { Component, computed, input, signal } from '@angular/core';
import { EvaluationRun } from '../api/evaluation';
import { runKey } from './layer-model';
import { MetricCatalog } from './metric-catalog';
import { pipelineName, runColor } from './presentation';
import { RecallChart } from './recall-chart';
import { budgetFill, recallCurve } from './recall-model';

/** One recall chart per category, read at one shared chunk budget, with one legend that focuses a pipeline in all of them. */
@Component({
  selector:'app-category-curves', imports:[RecallChart],
  template:`
    <div class="shared-controls">
      <label><span>Chunk budget <strong>k = {{ k() }}</strong></span><input type="range" aria-label="Chunk budget for every category" [attr.aria-valuetext]="k() + ' chunks'" min="1" [max]="limit()" [value]="k()" [style.--range-progress]="progress()" (input)="setBudget($event)" /></label>
      <div class="presets">@for (preset of presets; track preset) { <button [attr.aria-pressed]="k() === preset" [disabled]="limit() < preset" (click)="budget.set(preset)">{{ preset }}</button> }</div>
    </div>
    <div class="shared-legend" aria-label="Highlight a pipeline in every category">@for (item of legend(); track item.key) { <button class="quiet" [class.faded]="focused() !== null && focused() !== item.key" [attr.aria-pressed]="focused() === item.key" (click)="toggle(item.key)"><i [style.background]="item.color"></i>{{ item.name }}</button> }</div>
    <div class="curves">@for (category of categories(); track category) { <app-recall-chart [runs]="runs()" [category]="category" [compact]="true" [catalog]="catalog()" [(budget)]="budget" [(focused)]="focused" /> }</div>`,
  styleUrl:'./category-curves.css',
})
export class CategoryCurves {
  readonly runs = input<EvaluationRun[]>([]); readonly categories = input<string[]>([]);
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  protected readonly budget = signal(20);
  protected readonly focused = signal<string | null>(null);
  protected readonly presets = [10,20,41];
  /** The small multiples draw 70 chunks at most, as the overall chart does by default. */
  protected readonly limit = computed(() => Math.min(70,Math.max(1,...this.runs().map(run => recallCurve(run.scores.overall).length))));
  protected readonly k = computed(() => Math.min(this.limit(),this.budget()));
  protected readonly progress = computed(() => budgetFill(this.k(),this.limit()));
  protected readonly legend = computed(() => this.runs().map(run => ({key:runKey(run.layers),name:pipelineName(run),color:runColor(run)})));
  protected setBudget(event: Event): void { this.budget.set(Number((event.target as HTMLInputElement).value)); }
  protected toggle(key: string): void { this.focused.update(current => current === key ? null : key); }
}
