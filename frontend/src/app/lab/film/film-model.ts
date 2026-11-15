import { DatasetQuestion, EvaluationQuestion, EvaluationRun, Layers } from '../../api/evaluation';
import { bestRun } from '../builder-model';
import { isFloor } from '../layer-model';
import { DECISION_METRIC, atPath } from '../metrics';
import { matchingRun } from '../presentation';

export type ChunkKind = 'rule' | 'card' | 'term';

/** A chunk of the film: one rule, one card or one term. An anonymous chunk is one no recorded ranking names. */
export interface FilmChunk { key: string; id: string | null; kind: ChunkKind; faction: string | null; needed: boolean; }

/**
 * A step's ranking for the question: the chunk at each rank, null where the report keeps no name (past its 20 ids),
 * and the needed chunks it does not hand on at all.
 */
export interface FilmRanking { layer: string; value: string; known: (string | null)[]; cut: string[]; }

/** The scores of one question's ranking, as recorded. */
export interface FilmScores { recallAt10: number | null; ndcgAt10: number | null; reciprocalRank: number | null; seconds: number | null; }

/**
 * Everything the film shows, from one recorded evaluation: a question, the chunks its answer key cites, the best
 * pipeline's ranking after each of its steps, what its prompt holds, and the same question through dense retrieval
 * alone. Nothing is estimated: every rank is one the report recorded, and a chunk it does not name stays anonymous.
 */
export interface FilmExample {
  questionId: string;
  question: string;
  needed: string[];
  /** Every chunk the retrievers rank: the rulebook's and the cards'. */
  corpus: number;
  steps: FilmRanking[];
  /** The chunks the prompt holds: the best pipeline's context, or the first 10 of its ranking. */
  prompt: string[];
  best: { layers: Layers; score: number | null; held: number };
  dense: { value: string; top: string[]; held: number; score: number | null; ranks: Record<string, number | null> };
  /** The scores of the last ranking, the one the prompt is cut from. */
  scores: FilmScores;
  questionCount: number;
  pipelineCount: number;
}

/** Past this many needed chunks, a question is too busy to follow on screen. */
const MAX_NEEDED = 8, MIN_NEEDED = 3, MAX_LENGTH = 150;

/** The example the film tells, or null when the evaluation lacks a run it needs: the best pipeline, its steps, dense retrieval alone. */
export function filmExample(runs: EvaluationRun[], dataset: ReadonlyMap<string, DatasetQuestion>): FilmExample | null {
  const best = bestRun(runs), dense = runs.find(run => (run.layers['retriever'] ?? '').startsWith('dense') && Object.entries(run.layers).every(([layer,value]) => layer === 'retriever' || value === null));
  if (!best || !dense || !best.layers['retriever']) return null;
  const order = ['retriever','expand','rerank'].filter(layer => best.layers[layer]);
  const stepRuns = order.map((layer,index) => {
    const prefix: Layers = Object.fromEntries(order.slice(0,index+1).map(name => [name,best.layers[name]]));
    return {layer,value:best.layers[layer] as string,run:matchingRun(runs,prefix)};
  });
  if (stepRuns.some(step => !step.run)) return null;
  const last = stepRuns.at(-1)!.run!;
  const question = chooseQuestion(runs,best,dense,stepRuns.map(step => step.run!),dataset);
  if (!question) return null;
  const at = (run: EvaluationRun) => run.questions.find(row => row.question_id === question.id)!;
  const ending = at(best), ranking = at(last), alone = at(dense);
  const needed = Object.keys(ending.relevant_ranks);
  const prompt = (best.layers['context'] ?? null) !== null ? ending.top_chunk_ids : ranking.top_chunk_ids.slice(0,10);
  return {
    questionId: question.id, question: question.question, needed,
    corpus: Math.max(...runs.flatMap(run => run.questions.map(row => row.candidate_count))),
    steps: stepRuns.map(step => { const row = at(step.run!); return {layer:step.layer,value:step.value,known:known(row),cut:Object.keys(row.relevant_ranks).filter(id => row.relevant_ranks[id] === null)}; }),
    prompt,
    best: {layers:best.layers,score:atPath(best.scores.overall,DECISION_METRIC),held:prompt.filter(id => needed.includes(id)).length},
    dense: {value:dense.layers['retriever'] as string,top:alone.top_chunk_ids.slice(0,10),held:alone.top_chunk_ids.slice(0,10).filter(id => needed.includes(id)).length,score:atPath(dense.scores.overall,DECISION_METRIC),ranks:alone.relevant_ranks},
    scores: {recallAt10:atPath(ranking,'recall_at.10'),ndcgAt10:atPath(ranking,'binary_ndcg_at.10'),reciprocalRank:atPath(ranking,'reciprocal_rank'),seconds:atPath(ranking,'seconds')},
    questionCount: best.questions.length,
    pipelineCount: runs.filter(run => !isFloor(run)).length,
  };
}

/**
 * The question that shows the pipeline best: a short one whose answer key cites a few chunks, that the best pipeline
 * serves far better than dense retrieval alone, and whose share of needed chunks in the first 10 grows step by step.
 */
export function chooseQuestion(runs: EvaluationRun[], best: EvaluationRun, dense: EvaluationRun, steps: EvaluationRun[], dataset: ReadonlyMap<string, DatasetQuestion>): DatasetQuestion | null {
  const byId = (run: EvaluationRun) => new Map(run.questions.map(row => [row.question_id,row]));
  const ending = byId(best), alone = byId(dense), stages = steps.map(byId);
  let chosen: { question: DatasetQuestion; score: number } | null = null;
  for (const [id,row] of ending) {
    const question = dataset.get(id), needed = Object.keys(row.relevant_ranks).length;
    if (!question || !question.answerable || needed < MIN_NEEDED || needed > MAX_NEEDED || question.question.length > MAX_LENGTH || question.question.includes('\n')) continue;
    const reached = [...stages.map(stage => recall(stage.get(id))), contextRecall(row)];
    const climbs = reached.slice(1).filter((value,index) => value > reached[index] + 1e-9).length;
    const score = contextRecall(row) - contextRecall(alone.get(id)) + .2*climbs - question.question.length/10000;
    if (!chosen || score > chosen.score) chosen = {question,score};
  }
  return chosen?.question ?? null;
}

/** The chunks of a recorded question's ranking at their ranks: its first ids, then each cited chunk where it landed. */
export function known(question: EvaluationQuestion): (string | null)[] {
  const ranks: (string | null)[] = Array.from({length:question.candidate_count},(_,index) => question.top_chunk_ids[index] ?? null);
  for (const [chunk,rank] of Object.entries(question.relevant_ranks)) if (rank !== null && rank <= ranks.length) ranks[rank-1] = chunk;
  return ranks;
}

/** Every chunk the film draws: the ones a ranking or the prompt names, then anonymous ones up to the whole corpus. */
export function filmChunks(example: FilmExample): FilmChunk[] {
  const named = [...new Set([...example.needed,...example.steps.flatMap(step => step.known.filter((id): id is string => id !== null)),...example.prompt])];
  const chunks = named.map(id => describe(id,example.needed.includes(id)));
  for (let index = chunks.length; index < Math.max(example.corpus,named.length); index++) chunks.push({key:'#' + index,id:null,kind:'rule',faction:null,needed:false});
  return chunks;
}

/**
 * Each step's full order, as chunk keys: the recorded chunks at their ranks, the unnamed ranks filled with chunks the
 * next step or the prompt names (they come from these candidates), then with chunks the step before kept. A needed
 * chunk the step cut never fills a rank.
 * Only a named chunk's place is shown by name: the others are drawn alike, so filling them invents nothing.
 */
export function stepOrders(example: FilmExample, chunks: FilmChunk[]): string[][] {
  const orders: string[][] = [];
  example.steps.forEach((step,index) => {
    const placed = new Set([...step.known.filter((id): id is string => id !== null),...step.cut]);
    const next = (example.steps[index+1]?.known ?? example.prompt).filter((id): id is string => id !== null && !placed.has(id));
    const previous = orders[index-1] ?? chunks.map(chunk => chunk.key);
    const fill = [...new Set([...next,...previous])].filter(key => !placed.has(key));
    let cursor = 0;
    orders.push(step.known.map(id => id ?? fill[cursor++]));
  });
  return orders;
}

function describe(id: string, needed: boolean): FilmChunk {
  const faction = /^(ember|tide|root|neutral)\./.exec(id)?.[1] ?? null;
  return {key:id,id,kind:faction ? 'card' : id.startsWith('vocabulary.') ? 'term' : 'rule',faction,needed};
}
function recall(row: EvaluationQuestion | undefined): number { return atPath(row,'recall_at.10') ?? 0; }
function contextRecall(row: EvaluationQuestion | undefined): number { return atPath(row,'context_recall') ?? recall(row); }
