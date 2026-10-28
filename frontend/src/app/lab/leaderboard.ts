import { Component, computed, input, output, signal } from '@angular/core';
import { EvaluationRun } from '../api/evaluation';
import { Icon } from '../core/icon';
import { isFloor } from './layer-model';
import { atPath, leadMetrics } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { Pager } from './pager';
import { pageOf, windowOf } from './pages';
import { pipelineKind, pipelineName, rankedRuns, runColor, stagesOf } from './presentation';
import { RollingNumber } from './rolling-number';
import { RowMotion } from './row-motion';

/**
 * The lab's first page: the best pipeline by the metric chosen, then every pipeline ranked ten at a time. Ranking by
 * another metric keeps the page, so that its rows move: a row that climbs out of it leaves through the top, the one
 * that falls into it comes in from above.
 */
@Component({
  selector:'app-leaderboard', imports:[Icon,Pager,RollingNumber,RowMotion],
  templateUrl:'./leaderboard.html', styleUrls:['./leaderboard.css','./leaderboard-board.css','./leaderboard-mobile.css'],
})
export class Leaderboard {
  readonly runs = input<EvaluationRun[]>([]);
  readonly catalog = input(MetricCatalog.fromEvaluation(null));
  /** The scores a pipeline can be ranked by. */
  readonly scores = input<string[]>([]);
  readonly metric = input.required<string>();
  readonly ranked = output<string>();
  readonly built = output<string>();
  readonly compared = output<string>();
  protected readonly page = signal(0);
  protected readonly search = signal('');
  protected readonly name = pipelineName; protected readonly kind = pipelineKind; protected readonly color = runColor; protected readonly stages = stagesOf;
  protected readonly rows = computed(() => rankedRuns(this.runs(),this.metric(),this.catalog()));
  protected readonly found = computed(() => {
    const query = this.search().trim().toLowerCase();
    return query ? this.rows().filter(row => pipelineName(row.run).toLowerCase().includes(query) || JSON.stringify(row.run.layers).toLowerCase().includes(query)) : this.rows();
  });
  protected readonly shown = computed(() => pageOf(this.found(),this.page()));
  protected readonly window = computed(() => windowOf(this.found().map(row => row.key),this.page()));
  protected readonly columns = computed(() => leadMetrics(this.scores(),this.metric()));
  protected readonly champion = computed(() => this.rows()[0]);
  protected readonly floor = computed(() => this.runs().find(isFloor));
  /** How far the best leads the second: "+0.4 pts ahead of #2", or "Tied with #2". */
  protected readonly lead = computed(() => {
    const [first,second] = this.rows();
    if (!first || !second || first.value === null || second.value === null) return null;
    const gap = Math.abs(first.value - second.value);
    return {runnerUp:second,text:gap < 1e-10 ? 'Tied with #2' : this.gap(gap) + (this.lower() ? ' faster than #2' : ' ahead of #2')};
  });
  /** "8.0× the random floor", for a score the floor has. */
  protected readonly multiple = computed(() => {
    const best = this.champion()?.value ?? null, floor = atPath(this.floor()?.scores.overall,this.metric());
    return best === null || floor === null || floor <= 0 || this.lower() ? null : (best/floor).toFixed(1) + '× the random floor';
  });

  protected metricName(metric: string): string { return this.catalog().name(metric); }
  protected format(metric: string, value: number | null): string { return this.catalog().format(metric,value); }
  protected lower(metric = this.metric()): boolean { return this.catalog().lowerIsBetter(metric); }
  protected at(run: EvaluationRun, metric: string): number | null { return atPath(run.scores.overall,metric); }
  protected description(metric: string): string { return this.catalog().description(metric)?.what ?? ''; }
  /** A bar always longer for a better value. */
  protected width(run: EvaluationRun, metric: string): number {
    const ceiling = this.catalog().ceiling(metric,this.runs().map(other => atPath(other.scores.overall,metric)));
    return this.catalog().barLength(metric,atPath(run.scores.overall,metric),ceiling) * 100;
  }
  protected rankText(rank: number | null): string { return rank === null ? '—' : rank.toString().padStart(2,'0'); }
  protected find(event: Event): void { this.search.set((event.target as HTMLInputElement).value); this.page.set(0); }
  protected rankBy(event: Event): void { this.ranked.emit((event.target as HTMLSelectElement).value); }
  private gap(value: number): string { return this.catalog().isShare(this.metric()) ? '+' + (value*100).toFixed(1) + ' pts' : this.format(this.metric(),value); }
}
