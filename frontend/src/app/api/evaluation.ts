export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type Fields = { [key: string]: JsonValue };
export type Layers = Record<string, string | null>;
export interface EvaluationQuestion extends Fields {
  question_id: string;
  category: string;
  top_chunk_ids: string[];
  recall_at: { [key: string]: number };
  reciprocal_rank: number;
  /** Where each cited chunk landed in the run's whole list, null when the run never returned it. */
  relevant_ranks: Record<string, number | null>;
  candidate_count: number;
}
export interface EvaluationRun {
  description?: string;
  layers: Layers;
  scores: { overall: Fields; by_category: Record<string, Fields>; [key: string]: unknown };
  questions: EvaluationQuestion[];
  [key: string]: unknown;
}
/** One line of a dataset split, as the dataset wrote it: the question, worded, and its answer key. */
export interface DatasetQuestion extends Fields {
  id: string;
  category: string;
  question: string;
  answerable: boolean;
  key_facts: string[];
  citations: string[];
  cards: string[];
}
/** How a value is written: a share (72.7%), a score from 0 to 1 (0.663), a count (61, 41.3), a time (1.27 s, 780 ms). */
export type MetricDisplay = 'share' | 'score' | 'count' | 'seconds';
/** One value of the scores, as the code that computes it describes it (ai/rag/evaluation/metric_description.py). */
export interface MetricDescription {
  /** Where the value is in the scores of a group: `mean_recall_at.10`, `question_count`. */
  path: string;
  name: string;
  display: MetricDisplay;
  what: string;
  how: string;
  represents: string;
}
export interface Evaluation {
  description?: string;
  dataset: { version: string; split: string };
  evaluated_at: string;
  code: string;
  /** Every value of the scores, described; absent from the files written before each value described itself. */
  metrics?: MetricDescription[];
  runs: EvaluationRun[];
  [key: string]: unknown;
}


