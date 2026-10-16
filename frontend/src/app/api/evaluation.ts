export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type Fields = { [key: string]: JsonValue };
export type Layers = Record<string, string | null>;
export interface EvaluationQuestion extends Fields {
  question_id: string;
  category: string;
  top_chunk_ids: string[];
  recall_at: { [key: string]: number };
  reciprocal_rank: number;
}
export interface EvaluationRun {
  layers: Layers;
  scores: { overall: Fields; by_category: Record<string, Fields>; [key: string]: unknown };
  questions: EvaluationQuestion[];
  [key: string]: unknown;
}
export interface Evaluation {
  dataset: { version: string; split: string };
  evaluated_at: string;
  code: string;
  runs: EvaluationRun[];
  [key: string]: unknown;
}


