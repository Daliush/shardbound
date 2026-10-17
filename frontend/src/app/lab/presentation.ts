import { EvaluationRun, Layers } from '../api/evaluation';
import { atPath, lowerIsBetter, isContextMetric } from './metrics';
import { runKey } from './layer-model';

const NAMES: Record<string, string> = {
  'dense-qwen3-0.6b': 'Qwen3 · 0.6B', bm25: 'BM25', 'bm25-no-stemming': 'BM25 · No stemming',
  'bm25-stop-words-removed': 'BM25 · No stop words', 'bm25-robertson': 'BM25 · Robertson',
  'bm25-atire': 'BM25 · ATIRE', bm25l: 'BM25L', 'bm25+': 'BM25+', random: 'Random baseline',
  'rrf-bm25-dense': 'RRF · BM25 + dense', 'rrf-bm25-dense-expanded': 'RRF + references',
};
export function variantName(value: string | null): string { return value === null ? 'None' : NAMES[value] ?? value; }
export function pipelineName(run: EvaluationRun): string {
  return Object.values(run.layers).filter((value): value is string => value !== null).map(variantName).join(' + ') || 'No layers';
}
export function pipelineKind(run: EvaluationRun): string {
  const retriever = run.layers['retriever'] ?? '';
  if (retriever === 'random') return 'Reference floor';
  if (retriever.endsWith('-expanded')) return 'Reference expansion · awaiting reranking';
  if (retriever.startsWith('rrf')) return 'Hybrid retrieval';
  const kind = retriever.startsWith('dense') ? 'Dense retrieval' : retriever.startsWith('bm25') ? 'Lexical retrieval' : 'Pipeline';
  return run.layers['answer'] ? kind + ' + answer generation' : kind;
}
export function rankedRuns(runs: EvaluationRun[], metric: string) {
  if (isContextMetric(metric)) return runs.filter(run => run.layers['retriever'] !== 'random').map(run => ({run,key:runKey(run.layers),value:atPath(run.scores.overall,metric),rank:null}));
  const direction = lowerIsBetter(metric) ? 1 : -1;
  const sorted = [...runs].filter(run => run.layers['retriever'] !== 'random').sort((a,b) => {
    const av = atPath(a.scores.overall,metric), bv = atPath(b.scores.overall,metric);
    if (av === null) return bv === null ? 0 : 1;
    if (bv === null) return -1;
    return direction * (av - bv);
  });
  return sorted.map((run,index) => ({run, key:runKey(run.layers), value:atPath(run.scores.overall,metric),
    rank: sorted.findIndex(other => atPath(other.scores.overall,metric) === atPath(run.scores.overall,metric)) + 1}));
}
/** The pipelines told apart at a glance, each with its own chart color; the other runs are their variants. */
const MAIN_PIPELINES = ['rrf-bm25-dense-expanded','rrf-bm25-dense','bm25','dense-qwen3-0.6b'];
const VARIANTS = ['bm25-no-stemming','bm25-stop-words-removed','bm25-robertson','bm25-atire','bm25l','bm25+'];
/** A variant of a main pipeline, drawn quieter than it: the BM25 settings, say. The random floor is neither. */
export function isVariant(run: EvaluationRun): boolean {
  const retriever = run.layers['retriever'] ?? '';
  return retriever !== 'random' && !MAIN_PIPELINES.includes(retriever);
}
/**
 * Identity colors never depend on ranking, selected metric, or comparison order. A variant takes its family's
 * color muted, a shade of its own, so the main pipelines stand out and the variants read as one family.
 */
export function runColor(run: EvaluationRun): string {
  const retriever = run.layers['retriever'] ?? '';
  if (retriever === 'random') return 'var(--chart-8)';
  const main = MAIN_PIPELINES.indexOf(retriever);
  if (main >= 0) return 'var(--chart-' + main + ')';
  const family = MAIN_PIPELINES.findIndex(pipeline => retriever.startsWith(pipeline));
  const known = VARIANTS.indexOf(retriever), shade = known >= 0 ? known : [...runKey(run.layers)].reduce((sum,char) => sum+char.charCodeAt(0),0)%VARIANTS.length;
  return 'color-mix(in srgb, var(--chart-' + (family >= 0 ? family : 7) + ') ' + (62-shade*7) + '%, var(--muted))';
}
export function matchingRun(runs: EvaluationRun[], layers: Layers): EvaluationRun | undefined {
  const names = new Set([...Object.keys(layers), ...runs.flatMap(run => Object.keys(run.layers))]);
  return runs.find(run => [...names].every(layer => (run.layers[layer] ?? null) === (layers[layer] ?? null)));
}
