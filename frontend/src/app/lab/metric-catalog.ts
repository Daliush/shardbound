import { Evaluation, MetricDescription, MetricDisplay } from '../api/evaluation';
import { humanize } from './metrics';

type Known = Pick<MetricDescription,'path' | 'name' | 'display'>;

/**
 * What the lab knows of a value without its description: a question's time, and every value of the evaluations
 * written before each value described itself in its file. Names and displays only, never a text of the lab's own.
 */
const KNOWN_WITHOUT_DESCRIPTION: Known[] = [
  {path:'mean_recall_at.10',name:'Recall@10',display:'share'}, {path:'mean_recall_at.20',name:'Recall@20',display:'share'},
  {path:'mean_reciprocal_rank',name:'MRR',display:'score'}, {path:'question_count',name:'Questions',display:'count'},
  {path:'mean_candidate_count',name:'Candidates',display:'count'}, {path:'mean_recall_curve',name:'Recall curve',display:'share'},
  {path:'latency_p50_seconds',name:'Latency p50',display:'seconds'}, {path:'latency_p95_seconds',name:'Latency p95',display:'seconds'},
  {path:'seconds',name:'Latency',display:'seconds'},
];

/**
 * Every value of one evaluation, as its file describes it: its name, how it is written, and what it does, how and what
 * it represents. Each rule of the lab reads the display: a count is context and never ranks, a time is better lower,
 * a share or a score runs from 0 to 1.
 */
export class MetricCatalog {
  private constructor(private readonly known: ReadonlyMap<string,Known>, readonly descriptions: readonly MetricDescription[]) {}

  static fromEvaluation(report: Evaluation | null | undefined): MetricCatalog {
    const known = new Map<string,Known>();
    for (const value of KNOWN_WITHOUT_DESCRIPTION) known.set(value.path,value);
    const descriptions = report?.metrics ?? [];
    for (const description of descriptions) known.set(description.path,description);
    return new MetricCatalog(known,descriptions);
  }

  name(path: string): string { return this.find(path)?.name ?? humanize(path); }
  display(path: string): MetricDisplay | undefined { return this.find(path)?.display; }
  /** The value's texts, when its file wrote them. */
  description(path: string): MetricDescription | undefined {
    const found = this.find(path);
    return found && 'what' in found ? found as MetricDescription : undefined;
  }
  isShare(path: string): boolean { return this.display(path) === 'share'; }
  isBounded(path: string): boolean { return this.isShare(path) || this.display(path) === 'score'; }
  isContext(path: string): boolean { return this.display(path) === 'count'; }
  /** A time, which depends on the machine it was measured on. */
  isTime(path: string): boolean { return this.display(path) === 'seconds'; }
  lowerIsBetter(path: string): boolean { return this.isTime(path); }
  unit(path: string): string { return this.isShare(path) ? '%' : this.display(path) === 'seconds' ? 's' : 'value'; }

  /** The top of a bar's scale: 1 for a share or a score, the largest value otherwise. */
  ceiling(path: string, values: readonly (number | null)[]): number {
    if (this.isBounded(path)) return 1;
    let largest = 0;
    for (const value of values) if (value !== null && value > largest) largest = value;
    return largest > 0 ? largest : 1;
  }
  /** A value's bar, from 0 to 1 of the scale, always longer for a better value: a time's bar shrinks as it grows. */
  barLength(path: string, value: number | null, ceiling: number): number {
    if (value === null || ceiling <= 0) return 0;
    const share = Math.max(0,Math.min(1,value / ceiling));
    return this.lowerIsBetter(path) ? 1 - share : share;
  }

  format(path: string, value: number | null): string {
    if (value == null) return '—';
    switch (this.display(path)) {
      case 'share': return (value * 100).toFixed(1) + '%';
      case 'score': return value.toFixed(3);
      case 'count': return Number.isInteger(value) ? value.toLocaleString('en') : value.toFixed(1);
      case 'seconds': return formatSeconds(value);
      default: return new Intl.NumberFormat('en',{maximumFractionDigits:3}).format(value);
    }
  }

  /** A value of a group's scores is found at its path, a question's value at the same path without `mean_`. */
  private find(path: string): Known | undefined { return this.known.get(path) ?? this.known.get('mean_' + path); }
}

/** 1.27 s, 780 ms, 0.043 ms: a time under a second in milliseconds, so that a fast one never reads 0.00 s. */
function formatSeconds(value: number): string {
  if (value >= 1 || value === 0) return value.toFixed(2) + ' s';
  const milliseconds = value * 1000;
  return (milliseconds >= 10 ? Math.round(milliseconds).toString() : milliseconds.toPrecision(2)) + ' ms';
}
