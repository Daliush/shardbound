import { EvaluationQuestion, EvaluationRun, Fields, JsonValue } from '../api/evaluation';
import { recallCurve } from './recall-model';
export const DECISION_METRIC = 'mean_recall_at.10';
export function humanize(key: string): string { return key.replaceAll('_',' ').replaceAll('.',' · ').replace(/\b\w/g, char => char.toUpperCase()); }
export function metricName(key: string): string {
  if (/^(mean_)?recall_at\./.test(key)) return 'Recall@' + key.split('.').at(-1);
  if (key === 'mean_reciprocal_rank') return 'MRR';
  if (key === 'reciprocal_rank') return 'Reciprocal rank';
  if (/^(mean_)?candidate_count$/.test(key)) return 'Candidates';
  if (key === 'question_count') return 'Questions';
  return humanize(key);
}
export function numericFields(fields: Fields, prefix = ''): string[] {
  return Object.entries(fields).flatMap(([key,value]) => {
    const path = prefix ? prefix + '.' + key : key;
    return typeof value === 'number' && Number.isFinite(value) ? [path] : value && typeof value === 'object' && !Array.isArray(value) ? numericFields(value,path) : [];
  });
}
export function atPath(fields: Fields | undefined, path: string): number | null {
  let value: JsonValue | undefined = fields;
  for (const key of path.split('.')) value = value && typeof value === 'object' && !Array.isArray(value) ? value[key] : undefined;
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}
/** Fields older reports still hold but the lab no longer shows: the candidate recall is the recall curve's last value. */
const DROPPED_METRICS = new Set(['mean_candidate_recall']);
export function metrics(runs: EvaluationRun[]): string[] { return [...new Set(runs.flatMap(run => numericFields(run.scores.overall)))].filter(key => key !== 'question_count' && !DROPPED_METRICS.has(key)); }
export function isContextMetric(key: string): boolean { return /^(mean_)?candidate_count$|^question_count$/.test(key); }
export function isRatio(key: string): boolean { return /(^|\.)(mean_)?(recall_at|accuracy|precision|recall|f1|abstention_rate)(\.|$)/.test(key); }
export function formatMetric(key: string, value: number | null): string {
  if (value == null) return '—';
  if (isRatio(key)) return (value * 100).toFixed(1) + '%';
  if (/^(mean_)?reciprocal_rank$/.test(key)) return value.toFixed(3);
  if (key === 'mean_candidate_count') return value.toFixed(1);
  if (key === 'question_count' || key === 'candidate_count') return Math.round(value).toLocaleString('en');
  return new Intl.NumberFormat('en', {maximumFractionDigits:3}).format(value);
}
export function metricUnit(key: string): string {
  return isRatio(key) ? '%' : /(^|_)ms($|\.)/.test(key) ? 'ms' : /(^|_)(usd|cost)($|\.)/.test(key) ? 'USD' : 'value';
}
export function lowerIsBetter(key: string): boolean { return /(^|[._])(latency|duration|cost|error_rate|hallucination_rate)([._]|$)/.test(key); }
export function metricDescription(key: string): string {
  if (/recall_at\.10$/.test(key)) return 'Share of cited chunks in the first 10. The prompt budget and decision metric.';
  if (/recall_at\.20$/.test(key)) return 'Share of cited chunks in the first 20, where reference expansion starts.';
  if (/recall_at\./.test(key)) return 'Share of cited chunks in the first ' + key.split('.').at(-1) + ' results.';
  if (/reciprocal_rank/.test(key)) return 'Average of 1 / first cited rank. A higher value puts a useful chunk earlier.';
  if (/candidate_count/.test(key)) return 'Average chunks returned. Context, not a score; read alongside the end of the recall curve.';
  if (key === 'question_count') return 'Number of evaluated questions. Small categories are more sensitive to each question.';
  return lowerIsBetter(key) ? 'Lower values are better.' : 'Reported evaluation score.';
}
export function questionMetric(question: EvaluationQuestion | undefined, key: string): number | null {
  return atPath(question, key.startsWith('mean_') ? key.slice(5) : key);
}
export function relevantRanks(question: EvaluationQuestion | undefined): Record<string,number | null> | null {
  const value = question?.['relevant_ranks'];
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return Object.fromEntries(Object.entries(value).filter((entry): entry is [string,number | null] => entry[1] === null || (typeof entry[1] === 'number' && Number.isInteger(entry[1]) && entry[1] > 0)));
}
/** "95.3% of 41.3 candidates": the end of the recall curve, the most a reranker could keep, over the candidates it reads. */
export function candidateSummary(fields: Fields | undefined): string {
  const candidates = formatMetric('mean_candidate_count',atPath(fields,'mean_candidate_count')) + ' candidates', ceiling = recallCurve(fields).at(-1);
  return ceiling === undefined ? candidates : (ceiling * 100).toFixed(1) + '% of ' + candidates;
}
export interface Delta { id: string; category: string; a: number; b: number; delta: number; }
export function differences(a: EvaluationRun, b: EvaluationRun, metric: string): Delta[] {
  const questions = new Map(b.questions.map(question => [question.question_id,question]));
  return a.questions.flatMap(question => {
    const av = questionMetric(question,metric), bv = questionMetric(questions.get(question.question_id),metric);
    return av === null || bv === null ? [] : [{id:question.question_id,category:question.category,a:av,b:bv,delta:av-bv}];
  }).sort((a,b) => b.delta-a.delta || a.id.localeCompare(b.id));
}
export function countDifferences(rows: Delta[]): { a: number; tie: number; b: number } {
  return {a:rows.filter(row => row.delta > 1e-10).length,tie:rows.filter(row => Math.abs(row.delta) <= 1e-10).length,b:rows.filter(row => row.delta < -1e-10).length};
}
export function generic(value: unknown): string {
  if (value == null) return 'none';
  return typeof value === 'object' ? JSON.stringify(value,null,2) : String(value);
}


