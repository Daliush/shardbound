import { EvaluationRun, Layers } from '../api/evaluation';
import { atPath } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { isFloor, runKey } from './layer-model';

const NAMES: Record<string, string> = {
  'dense-qwen3-0.6b': 'Qwen3 · 0.6B', bm25: 'BM25', 'bm25-no-stemming': 'BM25 · No stemming',
  'bm25-stop-words-removed': 'BM25 · No stop words', 'bm25-robertson': 'BM25 · Robertson',
  'bm25-atire': 'BM25 · ATIRE', bm25l: 'BM25L', 'bm25+': 'BM25+', random: 'Random baseline',
  'rrf-bm25-dense': 'RRF · BM25 + dense', refs: 'References',
  'minilm-l6': 'MiniLM-L6 cross-encoder', 'qwen3-reranker-0.6b': 'Qwen3-Reranker · 0.6B',
};
export function variantName(value: string | null): string { return value === null ? 'None' : NAMES[value] ?? value; }
export function pipelineName(run: EvaluationRun): string {
  return Object.values(run.layers).filter((value): value is string => value !== null).map(variantName).join(' + ') || 'No layers';
}
export function pipelineKind(run: EvaluationRun): string {
  if (isFloor(run)) return 'Reference floor';
  const retriever = run.layers['retriever'] ?? '';
  const kind = retriever === 'random' ? 'Random order' : retriever.startsWith('rrf') ? 'Hybrid retrieval'
    : retriever.startsWith('dense') ? 'Dense retrieval' : retriever.startsWith('bm25') ? 'Lexical retrieval' : 'Pipeline';
  return kind + (run.layers['expand'] ? ' + references' : '') + (run.layers['rerank'] ? ' + reranking' : '') + (run.layers['answer'] ? ' + answer generation' : '');
}
export function rankedRuns(runs: EvaluationRun[], metric: string, catalog: MetricCatalog) {
  if (catalog.isContext(metric)) return runs.filter(run => !isFloor(run)).map(run => ({run,key:runKey(run.layers),value:atPath(run.scores.overall,metric),rank:null}));
  const direction = catalog.lowerIsBetter(metric) ? 1 : -1;
  const sorted = [...runs].filter(run => !isFloor(run)).sort((a,b) => {
    const av = atPath(a.scores.overall,metric), bv = atPath(b.scores.overall,metric);
    if (av === null) return bv === null ? 0 : 1;
    if (bv === null) return -1;
    return direction * (av - bv);
  });
  return sorted.map((run,index) => ({run, key:runKey(run.layers), value:atPath(run.scores.overall,metric),
    rank: sorted.findIndex(other => atPath(other.scores.overall,metric) === atPath(run.scores.overall,metric)) + 1}));
}
/** The pipelines told apart at a glance, each with its own chart color; the other runs are their variants. */
const MAIN_PIPELINES: Layers[] = [
  {retriever:'rrf-bm25-dense',expand:'refs',rerank:'minilm-l6'}, {retriever:'rrf-bm25-dense',expand:'refs'},
  {retriever:'rrf-bm25-dense'}, {retriever:'bm25'}, {retriever:'dense-qwen3-0.6b'},
  {retriever:'rrf-bm25-dense',expand:'refs',rerank:'qwen3-reranker-0.6b'},
];
const VARIANTS = ['bm25-no-stemming','bm25-stop-words-removed','bm25-robertson','bm25-atire','bm25l','bm25+'];
/** A variant of a main pipeline, drawn quieter than it: the BM25 settings, or BM25 expanded, say. The random floor is neither. */
export function isVariant(run: EvaluationRun): boolean {
  return !isFloor(run) && !MAIN_PIPELINES.some(layers => hasLayers(run,layers));
}
/**
 * Identity colors never depend on ranking, selected metric, or comparison order. A variant takes its family's
 * color muted, a shade of its own, so the main pipelines stand out and the variants read as one family.
 */
export function runColor(run: EvaluationRun): string {
  if (isFloor(run)) return 'var(--chart-8)';
  const main = MAIN_PIPELINES.findIndex(layers => hasLayers(run,layers));
  if (main >= 0) return 'var(--chart-' + main + ')';
  const retriever = run.layers['retriever'] ?? '';
  const family = MAIN_PIPELINES.findIndex(pipeline => retriever.startsWith(pipeline['retriever'] ?? ''));
  const known = hasLayers(run,{retriever}) ? VARIANTS.indexOf(retriever) : -1;
  const shade = known >= 0 ? known : [...runKey(run.layers)].reduce((sum,char) => sum+char.charCodeAt(0),0)%VARIANTS.length;
  return 'color-mix(in srgb, var(--chart-' + (family >= 0 ? family : 7) + ') ' + (62-shade*7) + '%, var(--muted))';
}
export function matchingRun(runs: EvaluationRun[], layers: Layers): EvaluationRun | undefined {
  return runs.find(run => hasLayers(run,layers));
}
/** Whether the run is exactly this pipeline: a layer either leaves out counts as skipped. */
function hasLayers(run: EvaluationRun, layers: Layers): boolean {
  const names = new Set([...Object.keys(layers), ...Object.keys(run.layers)]);
  return [...names].every(layer => (run.layers[layer] ?? null) === (layers[layer] ?? null));
}
