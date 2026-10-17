import { Component, computed, input, output } from '@angular/core';
import { DatasetQuestion, EvaluationRun } from '../api/evaluation';
import { humanize } from './metrics';
import { PipelineFlow } from './pipeline-flow';
@Component({
  selector:'app-question-detail',imports:[PipelineFlow],
  templateUrl:'./question-detail.html',styleUrl:'./question-detail.css',
})
export class QuestionDetail {
  readonly id = input.required<string>();
  /** The question as the dataset words it, with its answer key; absent while the dataset loads or when it can't be read. */
  readonly question = input<DatasetQuestion>();
  /** Every run of the report, for the stages of the pipeline. */
  readonly runs = input<EvaluationRun[]>([]);
  readonly closed = output<void>();
  protected readonly humanize = humanize;
  protected readonly category = computed(() => this.question()?.category ?? this.runs().flatMap(run => run.questions).find(question => question.question_id === this.id())?.category ?? '');
}
