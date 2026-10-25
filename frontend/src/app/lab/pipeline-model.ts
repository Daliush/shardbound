import { EvaluationRun, Layers } from '../api/evaluation';
import { matchingRun } from './presentation';

/** A stage of the retrieval pipeline, a run of the report found by its layers. `depth` is how many ranks it hands on, or every candidate the next layer reads. */
export interface PipelineStage { layers: Layers; label: string; role: string; depth: number | 'all'; }
/**
 * The stages in the order chunks flow through them: a reranker draws every candidate it reordered, the prompt reading its
 * first 10. Qwen3 reorders the same candidates as MiniLM, the baseline: it comes last, as the other reranker.
 */
export const PIPELINE_STAGES: PipelineStage[] = [
  {layers:{retriever:'bm25'},label:'BM25',role:'Words',depth:20},
  {layers:{retriever:'dense-qwen3-0.6b'},label:'Dense',role:'Meaning',depth:20},
  {layers:{retriever:'rrf-bm25-dense'},label:'RRF',role:'Both fused',depth:20},
  {layers:{retriever:'rrf-bm25-dense',expand:'refs'},label:'RRF + refs',role:'Candidates',depth:'all'},
  {layers:{retriever:'rrf-bm25-dense',expand:'refs',rerank:'minilm-l6'},label:'MiniLM',role:'Reranked',depth:'all'},
  {layers:{retriever:'rrf-bm25-dense',expand:'refs',rerank:'qwen3-reranker-0.6b'},label:'Qwen3',role:'Other reranker',depth:'all'},
];
export type SlotKind = 'cited' | 'card' | 'other';
/** A rank of a stage. Past the 20 ids a report keeps, only cited chunks are known by name. */
export interface PipelineSlot { rank: number; chunk: string | null; kind: SlotKind; }
export interface PipelineColumn {
  stage: PipelineStage; total: number; slots: PipelineSlot[];
  /** Where each cited chunk landed in the stage's whole list; null when the stage never returned it. */
  ranks: Record<string, number | null>;
}

/** The stages the report evaluated for this question, each with its slots; a stage without cited ranks is left out. */
export function pipelineColumns(runs: EvaluationRun[], id: string, cards: readonly string[]): PipelineColumn[] {
  const named = new Set(cards);
  return PIPELINE_STAGES.flatMap(stage => {
    const question = matchingRun(runs,stage.layers)?.questions.find(question => question.question_id === id);
    if (!question) return [];
    const ranks = question.relevant_ranks, total = question.candidate_count;
    const depth = stage.depth === 'all' ? total : Math.min(total,stage.depth);
    const citedAt = new Map(Object.entries(ranks).flatMap(([chunk,rank]) => rank === null ? [] : [[rank,chunk] as const]));
    const slots = Array.from({length:depth},(_,index): PipelineSlot => {
      const chunk = question.top_chunk_ids[index] ?? citedAt.get(index+1) ?? null;
      return {rank:index+1,chunk,kind:chunk === null ? 'other' : Object.hasOwn(ranks,chunk) ? 'cited' : named.has(chunk) ? 'card' : 'other'};
    });
    return [{stage,total,slots,ranks}];
  });
}
/** "2 of 7 cited", then where the others are, one line each: "ranks 29–155", "8.18 missing". */
export function columnSummary(column: PipelineColumn): string[] {
  const ranks = Object.entries(column.ranks), depth = column.slots.length;
  const shown = ranks.filter(([,rank]) => rank !== null && rank <= depth).length;
  const beyond = ranks.flatMap(([,rank]) => rank !== null && rank > depth ? [rank] : []).sort((a,b) => a-b);
  const missing = ranks.filter(([,rank]) => rank === null).map(([chunk]) => chunk);
  return [shown + ' of ' + ranks.length + ' cited',
    ...(beyond.length ? [beyond.length === 1 ? 'rank ' + beyond[0] : 'ranks ' + beyond[0] + '–' + beyond.at(-1)] : []),
    ...(missing.length ? [missing.length === 1 ? missing[0] + ' missing' : missing.length + ' missing'] : [])];
}
/** The cited chunks, the ones the last stage ranks first on top, then those it never returned. */
export function citedChunks(columns: PipelineColumn[]): string[] {
  const last = columns.at(-1)?.ranks ?? {};
  return Object.keys(last).sort((a,b) => (last[a] ?? Infinity)-(last[b] ?? Infinity) || a.localeCompare(b));
}
