import { Fields } from '../api/evaluation';

/** Recall@k for every k from 1 to the longest list of the run, as the report keeps it. */
export function recallCurve(fields: Fields): number[] {
  return fields['mean_recall_curve'] as number[];
}
/** Once a run exhausts its candidates, recall stays at its reported ceiling. */
export function recallAt(curve: number[], k: number): number {
  return curve[Math.min(curve.length,Math.max(1,Math.round(k)))-1];
}
/** The readout at a budget: best first, tied values share a rank; the random floor comes last, unranked. */
export function rankAtBudget<T extends { value: number; random: boolean }>(series: T[]): (T & { rank: number | null })[] {
  const scored = series.filter(item => !item.random).sort((a,b) => b.value-a.value);
  const floor = series.filter(item => item.random);
  return [...scored.map(item => ({...item,rank:scored.findIndex(other => other.value === item.value)+1})),...floor.map(item => ({...item,rank:null}))];
}
export function curvePath(curve: number[], end: number, x: (k:number) => number, y: (value:number) => number): string {
  return curve.slice(0,end).map((value,index) => (index ? 'L' : 'M') + x(index+1).toFixed(2) + ',' + y(value).toFixed(2)).join(' ');
}
