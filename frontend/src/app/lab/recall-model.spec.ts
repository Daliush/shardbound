import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { candidateSummary } from './metrics';
import { MetricCatalog } from './metric-catalog';
import { rankedRuns, runColor } from './presentation';
import { rankAtBudget, recallAt, recallCurve, curvePath } from './recall-model';
import { RecallChart } from './recall-chart';

function run(retriever: string, curve?: number[], count = 191): EvaluationRun {
  return {layers:{retriever,answer:null},scores:{overall:{question_count:7,mean_candidate_count:count,...(curve ? {mean_recall_curve:curve} : {})},by_category:{}},questions:[]};
}

describe('Report metric semantics', () => {
  const catalog = MetricCatalog.fromEvaluation(null);
  it('formats MRR as absolute decimals and counts as context', () => {
    expect(catalog.format('mean_reciprocal_rank',.5667)).toBe('0.567');
    expect(catalog.format('mean_reciprocal_rank',.008)).toBe('0.008');
    expect(catalog.format('reciprocal_rank',1/21)).toBe('0.048');
    expect(catalog.format('mean_candidate_count',41.3)).toBe('41.3');
    expect(catalog.format('candidate_count',50)).toBe('50');
    expect(catalog.isContext('mean_candidate_count')).toBe(true);
  });
  it('reads the ceiling at the end of the curve', () => {
    expect(candidateSummary(run('rrf',[.5,.9],41).scores.overall,catalog)).toBe('90.0% of 41 candidates');
  });
  it('does not rank or reorder runs by candidate count', () => {
    const runs = [run('small',undefined,41),run('all',undefined,191)];
    expect(rankedRuns(runs,'mean_candidate_count',catalog).map(row => [row.run,row.rank])).toEqual([[runs[0],null],[runs[1],null]]);
    expect(runColor(runs[0])).toBe(runColor({...runs[0],scores:{overall:{},by_category:{}}}));
  });
});

describe('Recall at equal budgets', () => {
  it('uses k minus one and clamps at the ceiling', () => {
    expect(recallAt([.1,.4,.7],1)).toBe(.1);
    expect(recallAt([.1,.4,.7],2)).toBe(.4);
    expect(recallAt([.1,.4,.7],191)).toBe(.7);
    expect(recallCurve({mean_recall_curve:[.1,.4,.7]})).toEqual([.1,.4,.7]);
    expect(curvePath([.1,.4,.7],2,k => k,value => value)).toBe('M1.00,0.10 L2.00,0.40');
  });
  it('changes budget and range and draws a dotted extension', async () => {
    const fixture = TestBed.createComponent(RecallChart);
    fixture.componentRef.setInput('runs',[run('rrf-bm25-dense',Array.from({length:191},(_,i) => (i+1)/191)),run('bm25',[.1,.5,.95],3)]);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('input')?.max).toBe('70');
    expect(host.querySelectorAll('.ceiling-line')).toHaveLength(1);
    expect(host.querySelectorAll('.readout-row')[0].textContent).toContain('95.0%');
    (host.querySelectorAll('.range-toggle button')[1] as HTMLButtonElement).click(); await fixture.whenStable();
    expect(host.querySelector('input')?.max).toBe('191');
    const slider = host.querySelector('input')!; slider.value='41'; slider.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(host.querySelector('.readout-heading')?.textContent).toContain('At 41 chunks');
    expect(host.querySelectorAll('.readout-row')[1].textContent).toContain('21.5%');
    (host.querySelectorAll('.readout-row')[1] as HTMLButtonElement).click(); await fixture.whenStable();
    expect(host.querySelectorAll('.readout-row')[1].getAttribute('aria-pressed')).toBe('true');
  });
  it('ranks the readout best first at the budget, sharing tied ranks, with the random floor last', () => {
    const series = [{key:'random',value:.9,random:true},{key:'a',value:.4,random:false},{key:'b',value:.6,random:false},{key:'c',value:.4,random:false}];
    expect(rankAtBudget(series).map(item => [item.key,item.rank])).toEqual([['b',1],['a',2],['c',2],['random',null]]);
  });
  it('reorders the readout when the budget changes', async () => {
    const fixture = TestBed.createComponent(RecallChart);
    fixture.componentRef.setInput('runs',[run('bm25',[.5,.5,.5]),run('dense-qwen3-0.6b',[.1,.2,.9]),run('random',[.05,.1,.15])]);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const order = () => [...host.querySelectorAll('.readout-row')].map(row => row.querySelector('span')?.textContent + ' ' + row.querySelector('.readout-rank')?.textContent);
    expect(order()).toEqual(['Qwen3 · 0.6B 1','BM25 2','Random baseline ']);
    const slider = host.querySelector('input')!; slider.value = '1'; slider.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(order()).toEqual(['BM25 1','Qwen3 · 0.6B 2','Random baseline ']);
  });
  it('uses category curves and counts rather than the overall result', async () => {
    const fixture = TestBed.createComponent(RecallChart), sample = run('bm25',[.9]);
    sample.scores.by_category['interaction'] = {mean_recall_curve:[.2,.4],question_count:7};
    fixture.componentRef.setInput('runs',[sample]); fixture.componentRef.setInput('category','interaction'); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('7 questions');
    expect(fixture.nativeElement.querySelector('.readout-row').textContent).toContain('40.0%');
  });
});
