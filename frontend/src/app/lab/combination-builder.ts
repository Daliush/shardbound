import { Component, computed, effect, input, output, signal } from '@angular/core';
import { EvaluationRun, Layers } from '../api/evaluation';
import { Icon } from '../core/icon';
import { layerNames, runKey, variants } from './layer-model';
import { atPath, formatMetric, metricDescription, metricName, metrics } from './metrics';
import { matchingRun, pipelineName, variantName } from './presentation';
import { LabMotion } from './lab-motion';

@Component({
  selector: 'app-combination-builder', imports: [Icon, LabMotion],
  template: `
    <div class="builder-layout">
      <div class="builder-editor">
        <div class="pipeline-preview" aria-label="Your pipeline">
          <span class="source-node"><app-icon name="search" />Question</span>
          @for (layer of layers(); track layer) { <app-icon name="arrow" /><span class="pipeline-node" [class.inactive]="draft()[layer] === null"><small>{{ layerLabel(layer) }}</small>{{ variant(draft()[layer] ?? null) }}</span> }
        </div>
        @for (layer of layers(); track layer; let i = $index) {
          <fieldset><legend><span class="step">{{ i + 1 }}</span>{{ layerLabel(layer) }}</legend><p class="step-description">{{ layerDescription(layer) }}</p>
            <div class="options" role="radiogroup" [attr.aria-label]="layerLabel(layer)">
              @for (option of options(layer); track option) {
                <label class="option" [class.chosen]="(draft()[layer] ?? null) === option">
                  <input type="radio" [name]="'builder-' + layer" [value]="option ?? ''" [checked]="(draft()[layer] ?? null) === option" (change)="choose(layer,option)" />
                  <span><strong>{{ variant(option) }}</strong><small>{{ optionDescription(option,layer) }}</small></span>
                  @if ((draft()[layer] ?? null) === option) { <app-icon name="check" /> }
                </label>
              }
            </div>
          </fieldset>
        }
        <p class="builder-note"><app-icon name="info" />Options come from recorded evaluations. New experiments are run offline.</p>
      </div>
      <aside class="results" aria-labelledby="results-title">
        <div class="results-heading"><app-icon name="chart" /><h3 id="results-title">Your combination</h3></div>
        <div class="recipe">@for (layer of layers(); track layer) { <div><span>{{ layerLabel(layer) }}</span><strong>{{ variant(draft()[layer] ?? null) }}</strong></div> }</div>
        @if (revealed() && match(); as run) {
          <div class="result-scores" [labMotion]="key(run.layers)" aria-live="polite"><p class="result-status"><app-icon name="check" />Recorded results</p>@if (run.description) { <p class="sample-size" [title]="run.description">{{ run.description }}</p> }
            @for (metric of metrics(); track metric) { <div class="result-metric"><div><span>{{ metricName(metric) }}</span><strong>{{ format(metric,at(run.scores.overall,metric)) }}</strong></div><p>{{ description(metric) }}</p></div> }
            <p class="sample-size">Measured on {{ run.scores.overall['question_count'] ?? run.questions.length }} questions.</p>
            <button class="primary" (click)="compared.emit(key(run.layers))"><app-icon name="compare" />Compare this combination</button>
          </div>
        } @else {
          <div class="result-placeholder"><app-icon name="chart" /><h4>{{ match() ? 'Ready when you are.' : 'No recorded result.' }}</h4><p>{{ match() ? 'Choose your components, then reveal how this combination scored.' : 'This combination has not been evaluated. Choose another setup to see recorded scores.' }}</p></div>
          <button class="primary reveal-button" [disabled]="!match()" (click)="revealed.set(true)">View scores<app-icon name="arrow" /></button>
        }
        <p class="precomputed">Precomputed benchmark. No model is run here.</p>
      </aside>
    </div>
  `,
  styleUrls: ['./combination-builder.css', './combination-builder-mobile.css'],
})
export class CombinationBuilder {
  readonly runs = input<EvaluationRun[]>([]);
  readonly initial = input<Layers>({});
  readonly changed = output<Layers>();
  readonly compared = output<string>();
  protected readonly draft = signal<Layers>({});
  protected readonly revealed = signal(false);
  protected readonly layers = computed(() => layerNames(this.runs()));
  protected readonly metrics = computed(() => metrics(this.runs()));
  protected readonly match = computed(() => matchingRun(this.runs(),this.draft()));
  protected readonly variant = variantName;
  protected readonly name = pipelineName;
  protected readonly key = runKey;
  protected readonly metricName = metricName;
  protected readonly format = formatMetric;
  protected readonly at = atPath;
  protected readonly description = metricDescription;
  constructor() { effect(() => { this.draft.set({...this.initial()}); this.revealed.set(false); }); }
  protected options(layer: string) { return variants(this.runs(),layer); }
  protected choose(layer: string, value: string | null): void {
    const draft = {...this.draft(),[layer]:value};
    this.draft.set(draft); this.revealed.set(false); this.changed.emit(draft);
  }
  protected layerLabel(layer: string): string { return layer === 'answer' ? 'Answer model' : layer === 'rerank' ? 'Reranker' : layer === 'expand' ? 'Expansion' : layer[0].toUpperCase() + layer.slice(1); }
  protected layerDescription(layer: string): string {
    if (layer === 'retriever') return 'How should your pipeline find the relevant rules?';
    if (layer === 'expand') return 'Should your pipeline add the cards the question names and the rules they refer to?';
    if (layer === 'rerank') return 'How should your pipeline reorder the candidates it found?';
    if (layer === 'answer') return 'The model that answers using the retrieved sources.';
    return 'Choose the ' + layer + ' used in your combination.';
  }
  protected optionDescription(value: string | null, layer: string): string {
    if (value === null) return layer === 'answer' ? 'Retrieval only · no answer generation' : layer === 'rerank' ? 'No reranking · in retrieval order' : layer === 'expand' ? 'No expansion · the retrieved chunks only' : 'Skip this component';
    if (value === 'random') return 'Random selection · reference floor';
    if (value === 'minilm-l6') return 'Cross-encoder · reads the question with each candidate';
    if (value === 'refs') return 'The cards the question names and the rules every chunk refers to';
    if (value.startsWith('rrf')) return 'BM25 and dense retrieval fused by rank';
    if (value.startsWith('dense')) return 'Semantic search with embeddings';
    if (value === 'bm25') return 'Keyword search · Lucene scoring';
    if (value === 'bm25l') return 'Keyword search · BM25L scoring';
    if (value === 'bm25+') return 'Keyword search · BM25+ scoring';
    if (value.startsWith('bm25')) return 'Keyword search · ' + (value.replace(/^bm25-?/, '') || 'default').replaceAll('-', ' ');
    return value;
  }
}
