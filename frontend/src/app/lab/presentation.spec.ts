import { EvaluationRun } from '../api/evaluation';
import { isVariant, matchingRun, rankedRuns, runColor } from './presentation';

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
  it('gives each main pipeline its own color and each variant a muted shade of its family', () => {
    expect(['rrf-bm25-dense-expanded','rrf-bm25-dense','bm25','dense-qwen3-0.6b','random'].map(name => runColor(run(name,0)))).toEqual(['var(--chart-0)','var(--chart-1)','var(--chart-2)','var(--chart-3)','var(--chart-8)']);
    const variants = ['bm25-no-stemming','bm25l','bm25+'].map(name => runColor(run(name,0)));
    expect(variants.every(color => color.startsWith('color-mix(in srgb, var(--chart-2) '))).toBe(true);
    expect(new Set(variants).size).toBe(3);
    expect([run('bm25l',0),run('bm25',0),run('random',0)].map(isVariant)).toEqual([true,false,false]);
  });
  it('does not manufacture scores for a combination whose components exist separately', () => {
    const runs = [run('dense',.8,'model-a'),run('bm25',.5,null)];
    expect(matchingRun(runs,{retriever:'bm25',answer:'model-a'})).toBeUndefined();
    expect(matchingRun(runs,{retriever:'bm25',answer:null})).toBe(runs[1]);
  });
});
