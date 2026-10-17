import { Fields } from '../api/evaluation';

export function recallCurve(fields: Fields | undefined): number[] {
  const value = fields?.['mean_recall_curve'];
  return Array.isArray(value) && value.length > 0 && value.every(point => typeof point === 'number' && Number.isFinite(point) && point >= 0 && point <= 1) ? value as number[] : [];
}
/** Once a run exhausts its candidates, recall stays at its reported ceiling. */
export function recallAt(curve: number[], k: number): number | null {
  return curve.length ? curve[Math.min(curve.length,Math.max(1,Math.round(k)))-1] : null;
}
export function curvePath(curve: number[], end: number, x: (k:number) => number, y: (value:number) => number): string {
  return curve.slice(0,end).map((value,index) => (index ? 'L' : 'M') + x(index+1).toFixed(2) + ',' + y(value).toFixed(2)).join(' ');
}
