import { Fields } from '../api/evaluation';

export function recallCurve(fields: Fields | undefined): number[] {
  const value = fields?.['mean_recall_curve'];
  return Array.isArray(value) && value.length > 0 && value.every(point => typeof point === 'number' && Number.isFinite(point) && point >= 0 && point <= 1) ? value as number[] : [];
}
/** Once a run exhausts its candidates, recall stays at its reported ceiling. */
export function recallAt(curve: number[], k: number): number | null {
  return curve.length ? curve[Math.min(curve.length,Math.max(1,Math.round(k)))-1] : null;
}
/** The readout at a budget: best first, tied values share a rank; curves missing, then the random floor, come last, unranked. */
export function rankAtBudget<T extends { value: number | null; random: boolean }>(series: T[]): (T & { rank: number | null })[] {
  const scored = series.filter(item => !item.random && item.value !== null).sort((a,b) => b.value!-a.value!);
  const unranked = series.filter(item => item.random || item.value === null).sort((a,b) => Number(a.random)-Number(b.random));
  return [...scored.map(item => ({...item,rank:scored.findIndex(other => other.value === item.value)+1})),...unranked.map(item => ({...item,rank:null}))];
}
export function curvePath(curve: number[], end: number, x: (k:number) => number, y: (value:number) => number): string {
  return curve.slice(0,end).map((value,index) => (index ? 'L' : 'M') + x(index+1).toFixed(2) + ',' + y(value).toFixed(2)).join(' ');
}
