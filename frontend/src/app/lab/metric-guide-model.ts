import { EvaluationRun, MetricDescription } from '../api/evaluation';
import { isFloor } from './layer-model';
import { MetricCatalog } from './metric-catalog';
import { DECISION_METRIC, atPath } from './metrics';
import { pipelineName, runColor } from './presentation';

/** What a value is for: one decides, some explain the winner, the latency says how long. The rest is context, which the guide leaves out. */
export type MetricRole = 'decides' | 'explains' | 'speed' | 'context';
type ShownRole = Exclude<MetricRole,'context'>;
export interface SampleRow { name: string; color: string; text: string; /** The bar's length, from 0 to 1, longer for a better value. */ length: number; floor: boolean; }
/** The value in the evaluation open: the top pipeline against the random floor, as bars, or one number when they tie. */
export interface MetricSample { kind: 'bars' | 'single'; /** The top pipeline's name, written once. */ leader: string; rows: SampleRow[]; }
export interface GuideEntry { description: MetricDescription; role: ShownRole; anchor: string; better: 'higher' | 'lower'; sample: MetricSample | null; }
export interface GuideGroup { role: ShownRole; title: string; summary: string; entries: GuideEntry[]; }

const GROUPS: Record<ShownRole,{title: string; summary: string}> = {
  decides:{title:'Decides',summary:'The one score we use to pick a pipeline. We chose it before running anything.'},
  explains:{title:'Explains',summary:"They help explain why a pipeline wins or loses. They don't pick the winner."},
  speed:{title:'Speed',summary:'How long a question takes. Lower is better.'},
};
const ROLES: ShownRole[] = ['decides','explains','speed'];

/** Every described score of the evaluation, grouped by what it is for, each with its value in this evaluation; counts and the curve are left out. */
export function buildGuide(catalog: MetricCatalog, runs: EvaluationRun[]): GuideGroup[] {
  const leader = findLeader(runs), floor = runs.find(isFloor);
  const entries = catalog.descriptions.flatMap(description => {
    const role = findRole(catalog,description,leader);
    return role === 'context' ? [] : [{description,role,anchor:anchorOf(description.path),better:catalog.lowerIsBetter(description.path) ? 'lower' as const : 'higher' as const,
      sample:sampleOf(catalog,description,leader,floor)}];
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
  if (values.every(value => value === null)) return null;
  if (values.every(value => value === values[0])) {
    return {kind:'single',leader:pipelineName(leader),rows:[{name:'Every pipeline',color:'var(--lab-accent)',text:catalog.format(description.path,values[0]),length:1,floor:false}]};
  }
  const ceiling = catalog.ceiling(description.path,values);
  return {kind:'bars',leader:pipelineName(leader),rows:runs.map((run,index) => ({name:rowName(run),color:runColor(run),
    text:catalog.format(description.path,values[index]),length:catalog.barLength(description.path,values[index],ceiling),floor:isFloor(run)}))};
}

/** The pipeline Recall@10 ranks first is the top one, not the best at every metric: on the latency, the floor is faster. */
function rowName(run: EvaluationRun): string { return isFloor(run) ? 'Random floor' : 'Top pipeline'; }

