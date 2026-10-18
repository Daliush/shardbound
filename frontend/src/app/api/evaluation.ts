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
export interface Evaluation {
  description?: string;
  dataset: { version: string; split: string };
  evaluated_at: string;
  code: string;
  runs: EvaluationRun[];
  [key: string]: unknown;
}


