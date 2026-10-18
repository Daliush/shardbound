import { EvaluationRun, Layers } from '../api/evaluation';

export function runKey(layers: Layers): string {
  const sorted = Object.fromEntries(Object.keys(layers).sort().map(key => [key, layers[key]]));
  return btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(sorted)))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
}
export function decodeRun(key: string): Layers | null {
  try {
    const json = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(key.replaceAll('-','+').replaceAll('_','/')), char => char.charCodeAt(0))));
    return json && !Array.isArray(json) && typeof json === 'object' && Object.values(json).every(value => value === null || typeof value === 'string') ? json : null;
  } catch { return null; }
}
export function layerNames(runs: EvaluationRun[]): string[] { return [...new Set(runs.flatMap(run => Object.keys(run.layers)))]; }
export function variants(runs: EvaluationRun[], layer: string): (string | null)[] { return [...new Set(runs.map(run => run.layers[layer] ?? null))]; }
/** The random floor: the random retriever alone. Followed by a reranker, it is a pipeline like the others. */
export function isFloor(run: EvaluationRun): boolean {
  return Object.entries(run.layers).every(([layer,value]) => layer === 'retriever' ? value === 'random' : value === null);
}
export function runName(run: EvaluationRun): string {
  return Object.entries(run.layers).map(([layer,value]) => value === null ? layer + ': none' : value).join(' / ');
}

