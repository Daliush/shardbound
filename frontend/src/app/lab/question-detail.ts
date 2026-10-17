import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DatasetQuestion, EvaluationRun } from '../api/evaluation';
import { CardBook } from '../game/card-book';
import { ChunkChip } from './chunk-chip';
import { PipelineFlow } from './pipeline-flow';
import { firstHit, formatMetric, generic, humanize, relevantRanks, questionCandidates, atPath } from './metrics';
import { pipelineName, runColor } from './presentation';
import { runKey } from './layer-model';
@Component({
  selector:'app-question-detail',imports:[ChunkChip,PipelineFlow],
  templateUrl:'./question-detail.html',styleUrl:'./question-detail.css',
})
export class QuestionDetail {
  readonly id = input.required<string>(); readonly runs = input<EvaluationRun[]>([]);
  /** The question as the dataset words it, with its answer key; absent while the dataset loads or when it can't be read. */
  readonly question = input<DatasetQuestion>();
  /** Every run of the report, for the stages of the pipeline. */
  readonly pipeline = input<EvaluationRun[]>([]);
  readonly closed = output<void>();
  private readonly book = inject(CardBook);
  protected readonly hovered = signal<string | null>(null);
  protected readonly format = formatMetric; protected readonly humanize = humanize; protected readonly generic = generic;
  protected readonly candidates = questionCandidates; protected readonly at = atPath;
  protected readonly columns = computed(() => this.runs().map(run => { const question = run.questions.find(question => question.question_id === this.id()); const ranks = relevantRanks(question); return {key:runKey(run.layers),name:pipelineName(run),color:runColor(run),question,hit:firstHit(question),ranks:ranks === null ? null : Object.entries(ranks).sort(([a],[b]) => a.localeCompare(b)),extras:question ? Object.entries(question).filter(([key]) => !['question_id','category','top_chunk_ids','recall_at','reciprocal_rank','relevant_ranks','candidate_count','candidate_recall'].includes(key)) : []}; }));
  protected readonly category = computed(() => this.columns().find(column => column.question)?.question?.category ?? '');
  protected readonly knownRelevant = computed(() => new Set(this.columns().flatMap(column => {
    const explicit = column.question?.['relevant_chunk_ids'];
    return [...Object.keys(relevantRanks(column.question) ?? {}),...(column.hit ? [column.hit.chunk] : []),...(Array.isArray(explicit) ? explicit.filter((id): id is string => typeof id === 'string') : [])];
  })));
  protected readonly cardsInId = computed(() => this.book.cards().filter(card => this.id().includes(card.id)));
  protected ranks(id: string, key: string): string {
    return this.columns().filter(column => column.key !== key).map((column,index) => { const rank = column.question?.top_chunk_ids.indexOf(id) ?? -1; return String.fromCharCode(65 + this.columns().indexOf(column)) + ': ' + (rank >= 0 ? '#' + (rank+1) : '—'); }).join(' · ');
  }
}

