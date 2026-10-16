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
export function combination(runs: EvaluationRun[], selected: Layers, layer: string, value: string | null): EvaluationRun | undefined {
  return runs.find(run => layerNames(runs).every(name => (run.layers[name] ?? null) === (name === layer ? value : selected[name] ?? null)));
}
export function runName(run: EvaluationRun): string {
  return Object.entries(run.layers).map(([layer,value]) => value === null ? layer + ': none' : value).join(' / ');
}

