import { EvaluationQuestion, EvaluationRun } from '../api/evaluation';
import { decodeRun, isFloor, layerNames, runKey, variants } from './layer-model';
import { countDifferences, differences, metrics } from './metrics';
import { readState, writeState } from './url-state';
function question(id: string, value: number): EvaluationQuestion { return {question_id:id,category:'rule',top_chunk_ids:['1.1','ember.ash-warden'],recall_at:{'10':value},reciprocal_rank:.5,relevant_ranks:{},candidate_count:2}; }
function run(layers: EvaluationRun['layers'], questions: EvaluationQuestion[] = []): EvaluationRun { return {layers,scores:{overall:{mean_recall_at:{'10':.5},latency_ms:12.5,quality:{custom_metric:.3}},by_category:{}},questions}; }
describe('Lab layer model', () => {
  const runs = [run({retriever:'dense',answer:null,reranker:'cross'}),run({retriever:'bm25',answer:null,reranker:null}),run({retriever:'dense',answer:'small',reranker:'cross'})];
  it('keeps layers in document order and null as its own variant', () => {
    expect(layerNames(runs)).toEqual(['retriever','answer','reranker']); expect(variants(runs,'answer')).toEqual([null,'small']);
  });
  it('encodes layer order independently and preserves Unicode and null', () => {
    const layers = {retriever:'déjà-vu',answer:null};
    expect(decodeRun(runKey(layers))).toEqual(layers);
    expect(runKey(layers)).toBe(runKey({answer:null,retriever:'déjà-vu'}));
    expect(decodeRun('garbage!')).toBeNull();
  });
  it('takes the random retriever alone for the floor, and once reranked for a pipeline', () => {
    expect(isFloor(run({retriever:'random',answer:null}))).toBe(true);
    expect(isFloor(run({retriever:'random',rerank:null,answer:null}))).toBe(true);
    expect(isFloor(run({retriever:'random',rerank:'minilm-l6',answer:null}))).toBe(false);
    expect(isFloor(run({retriever:'bm25',rerank:null,answer:null}))).toBe(false);
  });
});
describe('Lab metrics', () => {
  it('discovers future numeric fields without changing the UI', () => { expect(metrics([run({retriever:'x'})])).toEqual(['mean_recall_at.10','latency_ms','quality.custom_metric']); });
  it('compares only matching questions with both values and counts ties', () => {
    const rows = differences(run({},[question('a',.8),question('b',.2),question('c',.4),question('missing',1)]),run({},[question('a',.3),question('b',.7),question('c',.4)]),'mean_recall_at.10');
    expect(rows.map(row => row.id)).toEqual(['a','c','b']); expect(countDifferences(rows)).toEqual({a:1,tie:1,b:1});
  });
});
describe('Lab URL', () => {
  it('round trips all chosen state including null layers, filters and an open question', () => {
    const state = readState({v:'v2',split:'dev',at:'2026-10-14T22-20-30Z',run:runKey({retriever:'dense',answer:null}),pins:runKey({answer:null,retriever:'bm25'}),metric:'latency_ms',tab:'questions',q:'echo/ember.ash-warden',search:'6.5',category:'interaction',sort:'disagreement'});
    expect(readState(writeState(state))).toEqual(state);
    expect(readState(writeState({...state,multiples:true})).multiples).toBe(true);
  });
  it('rejects unknown version, split and tab and caps pins to four', () => {
    const state = readState({v:'v900',split:'secret',tab:'surprise',pins:'a.b.c.d.e.a'}); expect(state.version).toBe('v2'); expect(state.split).toBe(''); expect(state.tab).toBe('leaderboard'); expect(state.pins).toEqual(['a','b','c','d']);
  });
});

