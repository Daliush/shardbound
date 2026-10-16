import { EvaluationQuestion, EvaluationRun, Fields, JsonValue } from '../api/evaluation';
export const DECISION_METRIC = 'mean_recall_at.10';
export function humanize(key: string): string { return key.replaceAll('_',' ').replaceAll('.',' · ').replace(/\b\w/g, char => char.toUpperCase()); }
export function metricName(key: string): string {
  if (/^(mean_)?recall_at\./.test(key)) return 'Recall@' + key.split('.').at(-1);
  if (key === 'mean_reciprocal_rank') return 'Mean reciprocal rank';
  if (key === 'reciprocal_rank') return 'Reciprocal rank';
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
export function metrics(runs: EvaluationRun[]): string[] { return [...new Set(runs.flatMap(run => numericFields(run.scores.overall)))].filter(key => key !== 'question_count'); }
export function isRatio(key: string): boolean { return /(^|\.)(mean_)?(recall_at|reciprocal_rank|accuracy|precision|recall|f1|abstention_rate)(\.|$)/.test(key); }
export function formatMetric(key: string, value: number | null): string {
  if (value == null) return '—';
  if (isRatio(key)) return (value * 100).toFixed(1) + '%';
  return new Intl.NumberFormat('en', {maximumFractionDigits:3}).format(value);
}
export function metricUnit(key: string): string {
  return isRatio(key) ? '%' : /(^|_)ms($|\.)/.test(key) ? 'ms' : /(^|_)(usd|cost)($|\.)/.test(key) ? 'USD' : 'value';
}
export function lowerIsBetter(key: string): boolean { return /(^|[._])(latency|duration|cost|error_rate|hallucination_rate)([._]|$)/.test(key); }
export function metricDescription(key: string): string {
  if (/recall_at\./.test(key)) return 'Relevant sources found in the first ' + key.split('.').at(-1) + ' results.';
  if (/reciprocal_rank/.test(key)) return 'How early the first relevant source appears.';
  return lowerIsBetter(key) ? 'Lower values are better.' : 'Reported evaluation score.';
}
export function questionMetric(question: EvaluationQuestion | undefined, key: string): number | null {
  return atPath(question, key.startsWith('mean_') ? key.slice(5) : key);
}
export function firstHit(question: EvaluationQuestion | undefined): { rank: number; chunk: string } | null {
  if (!question || question.reciprocal_rank < .05 || question.reciprocal_rank > 1) return null;
  const rank = Math.round(1 / question.reciprocal_rank);
  const chunk = question.top_chunk_ids[rank - 1];
  return rank >= 1 && rank <= 20 && chunk ? {rank,chunk} : null;
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


