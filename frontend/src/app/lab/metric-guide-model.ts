import { EvaluationRun, MetricDescription } from '../api/evaluation';
import { isFloor } from './layer-model';
import { MetricCatalog } from './metric-catalog';
import { DECISION_METRIC, atPath } from './metrics';
import { pipelineName, runColor } from './presentation';
import { curvePath, recallCurve } from './recall-model';

/** What a value is for: one decides, some explain the winner, the latency says how long, the rest is context. */
export type MetricRole = 'decides' | 'explains' | 'speed' | 'context';
export interface SampleRow { name: string; color: string; text: string; /** The bar's length, from 0 to 1, longer for a better value. */ length: number; floor: boolean; }
export interface SampleCurve { name: string; color: string; path: string; floor: boolean; }
/** The value in the evaluation open: the top pipeline against the random floor, as bars, one number, or curves. */
export interface MetricSample { kind: 'bars' | 'single' | 'curve'; /** The top pipeline's name, written once. */ leader: string; rows: SampleRow[]; curves: SampleCurve[]; }
export interface GuideEntry { description: MetricDescription; role: MetricRole; anchor: string; better: 'higher' | 'lower' | null; sample: MetricSample | null; }
export interface GuideGroup { role: MetricRole; title: string; summary: string; entries: GuideEntry[]; }

const GROUPS: Record<MetricRole,{title: string; summary: string}> = {
  decides:{title:'Decides',summary:'The one score we use to pick a pipeline. We chose it before running anything.'},
  explains:{title:'Explains',summary:"They help explain why a pipeline wins or loses. They don't pick the winner."},
  speed:{title:'Speed',summary:'How long a question takes. Lower is better.'},
  context:{title:'Context',summary:'Numbers to read next to the scores. They never rank a pipeline.'},
};
const ROLES: MetricRole[] = ['decides','explains','speed','context'];
/** The curve's first chunks drawn in a sample: the prompt and the expansion's start, and what follows them. */
export const CURVE_POINTS = 40;
export const CURVE_WIDTH = 300; export const CURVE_HEIGHT = 96;

/** Every described value of the evaluation, grouped by what it is for, each with its value in this evaluation. */
export function buildGuide(catalog: MetricCatalog, runs: EvaluationRun[]): GuideGroup[] {
  const leader = findLeader(runs), floor = runs.find(isFloor);
  const entries = catalog.descriptions.map(description => {
    const role = findRole(catalog,description,leader);
    return {description,role,anchor:anchorOf(description.path),better:role === 'context' ? null : catalog.lowerIsBetter(description.path) ? 'lower' as const : 'higher' as const,
      sample:sampleOf(catalog,description,leader,floor)};
  });
  return ROLES.flatMap(role => {
    const grouped = entries.filter(entry => entry.role === role);
    return grouped.length ? [{role,...GROUPS[role],entries:grouped}] : [];
  });
}

export function anchorOf(path: string): string { return 'metric-' + path.replace(/[^a-z0-9]+/gi,'-').toLowerCase(); }

/** The best pipeline by the decision metric, the random floor left out. */
export function findLeader(runs: EvaluationRun[]): EvaluationRun | undefined {
  let leader: EvaluationRun | undefined, best = -Infinity;
  for (const run of runs) {
    const value = atPath(run.scores.overall,DECISION_METRIC);
    if (!isFloor(run) && value !== null && value > best) { leader = run; best = value; }
  }
  return leader;
}

function findRole(catalog: MetricCatalog, description: MetricDescription, leader: EvaluationRun | undefined): MetricRole {
  if (description.path === DECISION_METRIC) return 'decides';
  const single = leader === undefined || atPath(leader.scores.overall,description.path) !== null;
  if (catalog.isContext(description.path) || !single) return 'context';
  return catalog.lowerIsBetter(description.path) ? 'speed' : 'explains';
}

function sampleOf(catalog: MetricCatalog, description: MetricDescription, leader: EvaluationRun | undefined, floor: EvaluationRun | undefined): MetricSample | null {
  if (!leader) return null;
  const runs = floor ? [leader,floor] : [leader];
  const values = runs.map(run => atPath(run.scores.overall,description.path));
  if (values.every(value => value === null)) return curveSample(catalog,runs,leader);
  if (values.every(value => value === values[0])) {
    return {kind:'single',leader:pipelineName(leader),rows:[{name:'Every pipeline',color:'var(--lab-accent)',text:catalog.format(description.path,values[0]),length:1,floor:false}],curves:[]};
  }
  const ceiling = catalog.ceiling(description.path,values);
  return {kind:'bars',leader:pipelineName(leader),curves:[],rows:runs.map((run,index) => ({name:rowName(run),color:runColor(run),
    text:catalog.format(description.path,values[index]),length:catalog.barLength(description.path,values[index],ceiling),floor:isFloor(run)}))};
}

function curveSample(catalog: MetricCatalog, runs: EvaluationRun[], leader: EvaluationRun): MetricSample | null {
  const curves = runs.flatMap(run => {
    const curve = recallCurve(run.scores.overall);
    return Array.isArray(curve) && curve.length ? [{run,curve}] : [];
  });
  if (!curves.length) return null;
  const x = (k: number) => (k-1)/(CURVE_POINTS-1)*CURVE_WIDTH, y = (value: number) => CURVE_HEIGHT - value*CURVE_HEIGHT;
  return {kind:'curve',leader:pipelineName(leader),
    curves:curves.map(({run,curve}) => ({name:rowName(run),color:runColor(run),path:curvePath(padCurve(curve),CURVE_POINTS,x,y),floor:isFloor(run)})),
    rows:curves.map(({run,curve}) => ({name:rowName(run),color:runColor(run),
      text:catalog.format('mean_recall_curve',curve[Math.min(curve.length,CURVE_POINTS)-1]) + ' at ' + CURVE_POINTS + ' chunks',length:curve[Math.min(curve.length,CURVE_POINTS)-1],floor:isFloor(run)}))};
}

/** The pipeline Recall@10 ranks first is the top one, not the best at every metric: on the latency, the floor is faster. */
function rowName(run: EvaluationRun): string { return isFloor(run) ? 'Random floor' : 'Top pipeline'; }

/** Past the end of its list, a run keeps the recall its whole list reached. */
function padCurve(curve: number[]): number[] {
  const padded = curve.slice(0,CURVE_POINTS);
  while (padded.length < CURVE_POINTS) padded.push(curve.at(-1) ?? 0);
  return padded;
}
