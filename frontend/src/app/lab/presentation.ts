import { EvaluationRun, Layers } from '../api/evaluation';
import { atPath, lowerIsBetter } from './metrics';
import { runKey } from './layer-model';

const NAMES: Record<string, string> = {
  'dense-qwen3-0.6b': 'Qwen3 · 0.6B', bm25: 'BM25', 'bm25-no-stemming': 'BM25 · No stemming',
  'bm25-stop-words-removed': 'BM25 · No stop words', 'bm25-robertson': 'BM25 · Robertson',
  'bm25-atire': 'BM25 · ATIRE', bm25l: 'BM25L', 'bm25+': 'BM25+', random: 'Random baseline',
};
export function variantName(value: string | null): string { return value === null ? 'None' : NAMES[value] ?? value; }
export function pipelineName(run: EvaluationRun): string {
  return Object.values(run.layers).filter((value): value is string => value !== null).map(variantName).join(' + ') || 'No layers';
}
export function pipelineKind(run: EvaluationRun): string {
  const retriever = run.layers['retriever'] ?? '';
  if (retriever === 'random') return 'Reference floor';
  const kind = retriever.startsWith('dense') ? 'Dense retrieval' : retriever.startsWith('bm25') ? 'Lexical retrieval' : 'Pipeline';
  return run.layers['answer'] ? kind + ' + answer generation' : kind;
}
export function rankedRuns(runs: EvaluationRun[], metric: string) {
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
export function matchingRun(runs: EvaluationRun[], layers: Layers): EvaluationRun | undefined {
  const names = new Set([...Object.keys(layers), ...runs.flatMap(run => Object.keys(run.layers))]);
  return runs.find(run => [...names].every(layer => (run.layers[layer] ?? null) === (layers[layer] ?? null)));
}
