import { EvaluationRun, Layers } from '../api/evaluation';
import { isFloor, layerNames, variants } from './layer-model';
import { DECISION_METRIC, atPath } from './metrics';
import { familyOf, layerName, matchingRun } from './presentation';

/** A choice of a step: the variant it picks, and the decision score the pipeline gets with it, when that run was recorded. */
export interface StageChoice { value: string | null; label: string; score: number | null; chosen: boolean; }
/** A family of variants drawn as one choice, BM25's settings as its members; a variant alone is a family of one. */
export interface StageFamily { family: string; description: string; chosen: boolean; members: StageChoice[]; /** What a click on the family picks: its chosen member, else its first. */ pick: StageChoice; }
export interface StageNode { layer: string; name: string; role: string; families: StageFamily[]; skipped: boolean; }
/** A step evaluated with one variant only, the answer model so far: drawn as what comes next, never as a choice. */
export interface FixedStage { layer: string; name: string; value: string | null; }

const ROLES: Record<string,string> = {retriever:'Finds the candidates', expand:'Adds what the question names', rerank:'Reorders the candidates', answer:'Answers from the sources'};

/** The steps a pipeline goes through, each with every variant it was evaluated with, scored with the other steps as drafted. */
export function stageNodes(runs: EvaluationRun[], draft: Layers): StageNode[] {
  return layerNames(runs).filter(layer => variants(runs,layer).length > 1).map(layer => {
    const choices = variants(runs,layer).map(value => {
      const run = matchingRun(runs,{...draft,[layer]:value});
      return {value,label:familyOf(value).member,score:run ? atPath(run.scores.overall,DECISION_METRIC) : null,chosen:(draft[layer] ?? null) === value};
    });
    const families = [...new Set(choices.map(choice => familyOf(choice.value).family))].map(family => {
      const members = choices.filter(choice => familyOf(choice.value).family === family);
      return {family,description:describe(members[0].value,layer),chosen:members.some(member => member.chosen),members,pick:members.find(member => member.chosen) ?? members[0]};
    });
    return {layer,name:layerName(layer),role:ROLES[layer] ?? '',families,skipped:(draft[layer] ?? null) === null};
  });
}

export function fixedStages(runs: EvaluationRun[]): FixedStage[] {
  return layerNames(runs).flatMap(layer => {
    const only = variants(runs,layer);
    return only.length === 1 ? [{layer,name:layerName(layer),value:only[0]}] : [];
  });
}

/**
 * How many chunks leave each step, on average over the questions: the candidates of the pipeline cut after that step,
 * as recorded. A reranker without the expansion reads the retriever's first 20.
 */
export function flowCounts(runs: EvaluationRun[], draft: Layers, layers: string[]): (number | null)[] {
  const prefix: Layers = {};
  return layers.map(layer => {
    prefix[layer] = draft[layer] ?? null;
    return atPath(matchingRun(runs,prefix)?.scores.overall,'mean_candidate_count');
  });
}

/** The run's rank among the pipelines by a metric, best first, ties sharing a rank; the random floor is not ranked. */
export function rankOf(runs: EvaluationRun[], run: EvaluationRun, metric: string, lowerIsBetter: boolean): number | null {
  const value = atPath(run.scores.overall,metric);
  if (value === null || isFloor(run)) return null;
  return runs.filter(other => !isFloor(other)).filter(other => {
    const score = atPath(other.scores.overall,metric);
    return score !== null && (lowerIsBetter ? score < value : score > value);
  }).length + 1;
}

/** The best pipeline by the decision metric, the one the builder starts from. */
export function bestRun(runs: EvaluationRun[]): EvaluationRun | undefined {
  return runs.filter(run => !isFloor(run)).reduce<EvaluationRun | undefined>((best,run) =>
    (atPath(run.scores.overall,DECISION_METRIC) ?? -1) > (atPath(best?.scores.overall,DECISION_METRIC) ?? -1) ? run : best,undefined);
}

function describe(value: string | null, layer: string): string {
  if (value === null) return layer === 'rerank' ? 'Keeps the retrieval order' : layer === 'expand' ? 'The retrieved chunks only' : 'Skips this step';
  if (value === 'random') return 'Random order · the floor';
  if (value === 'minilm-l6') return 'Cross-encoder · reads the question with each candidate';
  if (value === 'qwen3-reranker-0.6b') return 'LLM cross-encoder · told the task';
  if (value === 'refs') return 'The cards named, the rules each chunk cites';
  if (value.startsWith('rrf')) return 'BM25 and dense, fused by rank';
  if (value.startsWith('dense')) return 'Meaning · embeddings';
  if (value.startsWith('bm25')) return 'Words · keyword scoring';
  return value;
}
