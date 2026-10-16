import { Params } from '@angular/router';
export const DATASET_VERSIONS = ['v1'];
export const SPLITS = ['dev','test'];
export const LAB_TABS = ['leaderboard','metrics','compare','builder','categories','head-to-head','questions','history'] as const;
export interface LabState { version: string; split: string; timestamp: string; run: string; pins: string[]; metric: string; tab: string; question: string; category: string; search: string; sort: string; multiples: boolean; }
export function readState(params: Params): LabState {
  const text = (key: string) => typeof params[key] === 'string' ? params[key] : '';
  return {version:DATASET_VERSIONS.includes(text('v')) ? text('v') : DATASET_VERSIONS[0],split:SPLITS.includes(text('split')) ? text('split') : '',timestamp:text('at'),run:text('run'),pins:[...new Set(text('pins').split('.').filter(Boolean))].slice(0,4),metric:text('metric') || 'mean_recall_at.10',tab:LAB_TABS.includes(text('tab') as typeof LAB_TABS[number]) ? text('tab') : 'leaderboard',multiples:text('categoryView') === 'multiples',question:text('q'),category:text('category'),search:text('search'),sort:text('sort') === 'disagreement' ? 'disagreement' : 'id'};
}
export function writeState(state: LabState): Params {
  return {v:state.version,split:state.split,at:state.timestamp,run:state.run,pins:state.pins.join('.'),metric:state.metric,tab:state.tab,...(state.multiples ? {categoryView:'multiples'} : {}),...(state.question ? {q:state.question} : {}),...(state.category ? {category:state.category} : {}),...(state.search ? {search:state.search} : {}),...(state.sort === 'disagreement' ? {sort:state.sort} : {})};
}


