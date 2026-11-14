import { Evaluation, EvaluationQuestion, EvaluationRun, Fields, JsonValue } from '../api/evaluation';
import type { MetricCatalog } from './metric-catalog';
import { recallCurve } from './recall-model';
/** Context recall: the share of the cited chunks the prompt would hold, Recall@10 for a run that ends with a ranking. */
export const DECISION_METRIC = 'mean_context_recall';
/** The number of chunks a run returns: after a context, what the prompt holds, which a context's recall is read with. */
export const SIZE_METRIC = 'mean_candidate_count';
/** The scores a leaderboard shows at a glance; the others are one click away, on the metrics page. */
const LEAD_METRICS = [DECISION_METRIC,'mean_recall_at.20','mean_binary_ndcg_at.10','mean_reciprocal_rank','latency_p50_seconds'];
/**
 * An evaluation recorded before context recall, given it. Every run of such a file ends with a ranking, whose context
 * recall is its Recall@10 by definition: the prompt would hold its first 10 chunks. So the lab ranks every file by the
 * same metric. Its description is the file's own of Recall@10, under the name context recall, so that the lab says
 * what the value is and writes no text of its own. A file that records it is left as written.
 */
export function withContextRecall(evaluation: Evaluation): Evaluation {
  if (evaluation.runs.some(run => DECISION_METRIC in run.scores.overall)) return evaluation;
  const fill = (fields: Fields): Fields => { const recall = atPath(fields,'mean_recall_at.10'); return recall === null ? fields : {[DECISION_METRIC]:recall,...fields}; };
  const recall = evaluation.metrics?.find(description => description.path === 'mean_recall_at.10');
  return {...evaluation,...(evaluation.metrics && recall ? {metrics:[{...recall,path:DECISION_METRIC,name:'Context recall'},...evaluation.metrics]} : {}),runs:evaluation.runs.map(run => ({...run,
    scores:{...run.scores,overall:fill(run.scores.overall),by_category:Object.fromEntries(Object.entries(run.scores.by_category).map(([category,fields]) => [category,fill(fields)]))},
    questions:run.questions.map(question => question.recall_at?.['10'] === undefined ? question : {context_recall:question.recall_at['10'],...question})}))};
}
/** The leaderboard's columns, in the file's order: the scores shown at a glance, and the one it ranks by. */
export function leadMetrics(scores: readonly string[], ranking: string): string[] {
  const lead = scores.filter(metric => LEAD_METRICS.includes(metric));
  const shown = new Set(lead.length ? lead : scores.slice(0,5));
  return scores.filter(metric => shown.has(metric) || metric === ranking);
}
export function humanize(key: string): string { return key.replaceAll('_',' ').replaceAll('.',' · ').replace(/\b\w/g, char => char.toUpperCase()); }
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
export function questionMetric(question: EvaluationQuestion | undefined, key: string): number | null {
  return atPath(question, key.startsWith('mean_') ? key.slice(5) : key);
}
/**
 * "95.3% of 41.3 candidates": the end of the recall curve, the share of the cited chunks among every chunk the run
 * ranked. A context has no curve: "84.9% of 19.7 chunks in the prompt", its context recall.
 */
export function candidateSummary(fields: Fields, catalog: MetricCatalog): string {
  const curve = recallCurve(fields), size = catalog.format(SIZE_METRIC,atPath(fields,SIZE_METRIC));
  if (!curve.length) return catalog.format(DECISION_METRIC,atPath(fields,DECISION_METRIC)) + ' of ' + size + ' chunks in the prompt';
  return (curve.at(-1)! * 100).toFixed(1) + '% of ' + size + ' candidates';
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
