import { EvaluationRun } from '../api/evaluation';
import { matchingRun, rankedRuns } from './presentation';

function run(retriever: string, value: number | null, answer: string | null = null): EvaluationRun {
  return {layers:{retriever,answer},scores:{overall:{accuracy:value,latency_ms:value},by_category:{}},questions:[]};
}
describe('Benchmark ranking', () => {
  it('ranks ties equally, leaves missing scores last, and keeps the reference floor out of the ranking', () => {
    const rows = rankedRuns([run('missing',null),run('first',.8),run('tie',.8),run('last',.3),run('random',1)],'accuracy');
    expect(rows.map(row => [row.run.layers['retriever'],row.rank])).toEqual([['first',1],['tie',1],['last',3],['missing',4]]);
  });
  it('ranks latency from lowest to highest without treating a missing value as zero', () => {
    expect(rankedRuns([run('slow',120),run('missing',null),run('fast',30)],'latency_ms').map(row => row.run.layers['retriever'])).toEqual(['fast','slow','missing']);
  });
  it('does not manufacture scores for a combination whose components exist separately', () => {
    const runs = [run('dense',.8,'model-a'),run('bm25',.5,null)];
    expect(matchingRun(runs,{retriever:'bm25',answer:'model-a'})).toBeUndefined();
    expect(matchingRun(runs,{retriever:'bm25',answer:null})).toBe(runs[1]);
  });
});
