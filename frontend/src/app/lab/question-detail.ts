import { Component, computed, inject, input, output, signal } from '@angular/core';
import { linkHorizontal } from 'd3-shape';
import { EvaluationRun } from '../api/evaluation';
import { CardBook } from '../game/card-book';
import { ChunkChip } from './chunk-chip';
import { firstHit, formatMetric, generic, humanize } from './metrics';
import { runKey, runName } from './layer-model';
@Component({
  selector:'app-question-detail',imports:[ChunkChip],
  templateUrl:'./question-detail.html',styleUrl:'./question-detail.css',
})
export class QuestionDetail {
  readonly id = input.required<string>(); readonly runs = input<EvaluationRun[]>([]);
  readonly closed = output<void>();
  private readonly book = inject(CardBook);
  protected readonly hovered = signal<string | null>(null);
  protected readonly format = formatMetric; protected readonly humanize = humanize; protected readonly generic = generic;
  protected readonly columns = computed(() => this.runs().map(run => { const question = run.questions.find(question => question.question_id === this.id()); return {key:runKey(run.layers),name:runName(run),question,hit:firstHit(question),extras:question ? Object.entries(question).filter(([key]) => !['question_id','category','top_chunk_ids','recall_at','reciprocal_rank'].includes(key)) : []}; }));
  protected readonly category = computed(() => this.columns().find(column => column.question)?.question?.category ?? '');
  protected readonly knownRelevant = computed(() => new Set(this.columns().flatMap(column => {
    const explicit = column.question?.['relevant_chunk_ids'];
    return [...(column.hit ? [column.hit.chunk] : []),...(Array.isArray(explicit) ? explicit.filter((id): id is string => typeof id === 'string') : [])];
  })));
  protected readonly cardsInId = computed(() => this.book.cards().filter(card => this.id().includes(card.id)));
  protected readonly paths = computed(() => {
    const columns = this.columns(), width = 1000 / Math.max(1,columns.length);
    return columns.flatMap((column,index) => index === columns.length - 1 ? [] : (column.question?.top_chunk_ids.slice(0,20) ?? []).flatMap((id,rank) => {
      const nextRank = columns[index+1].question?.top_chunk_ids.indexOf(id) ?? -1;
      if (nextRank < 0 || nextRank >= 20) return [];
      const y = (rank: number) => 144 + rank * 48 + (rank >= 10 ? 32 : 0) + 24;
      const path = linkHorizontal()({source:[(index+1)*width-12,y(rank)],target:[(index+1)*width+12,y(nextRank)]});
      return [{id,path}];
    }));
  });
  protected ranks(id: string, key: string): string {
    return this.columns().filter(column => column.key !== key).map((column,index) => { const rank = column.question?.top_chunk_ids.indexOf(id) ?? -1; return String.fromCharCode(65 + this.columns().indexOf(column)) + ': ' + (rank >= 0 ? '#' + (rank+1) : '—'); }).join(' · ');
  }
}

