import { TestBed } from '@angular/core/testing';
import { EvaluationQuestion, EvaluationRun } from '../api/evaluation';
import { candidateSummary, firstHit, formatMetric, isContextMetric, metrics, questionCandidates, relevantRanks } from './metrics';
import { rankedRuns, runColor } from './presentation';
import { recallAt, recallCurve, curvePath } from './recall-model';
import { RecallChart } from './recall-chart';

function run(retriever: string, curve?: number[], count = 191): EvaluationRun {
  return {layers:{retriever,answer:null},scores:{overall:{question_count:7,mean_candidate_count:count,...(curve ? {mean_recall_curve:curve} : {})},by_category:{}},questions:[]};
}

describe('Report metric semantics', () => {
  it('formats MRR as absolute decimals and counts as context', () => {
    expect(formatMetric('mean_reciprocal_rank',.5667)).toBe('0.567');
    expect(formatMetric('mean_reciprocal_rank',.008)).toBe('0.008');
    expect(formatMetric('reciprocal_rank',1/21)).toBe('0.048');
    expect(formatMetric('mean_candidate_count',41)).toBe('41.0');
    expect(formatMetric('candidate_count',50)).toBe('50');
    expect(isContextMetric('mean_candidate_count')).toBe(true);
  });
  it('hides the candidate recall of older reports and reads the ceiling at the end of the curve', () => {
    const legacy = run('rrf',[.5,.9],41);
    legacy.scores.overall['mean_candidate_recall'] = .9;
    expect(metrics([legacy])).not.toContain('mean_candidate_recall');
    expect(candidateSummary(legacy.scores.overall)).toBe('90.0% of 41.0 candidates');
    expect(candidateSummary(run('old',undefined,41).scores.overall)).toBe('41.0 candidates');
  });
  it('does not rank or reorder runs by candidate count', () => {
    const runs = [run('small',undefined,41),run('all',undefined,191)];
    expect(rankedRuns(runs,'mean_candidate_count').map(row => [row.run,row.rank])).toEqual([[runs[0],null],[runs[1],null]]);
    expect(runColor(runs[0])).toBe(runColor({...runs[0],scores:{overall:{},by_category:{}}}));
  });
  it('finds the actual first cited rank beyond twenty and keeps missing citations', () => {
    const question: EvaluationQuestion = {question_id:'q',category:'interaction',top_chunk_ids:['card'],recall_at:{'10':0,'20':0},reciprocal_rank:1/21,relevant_ranks:{'11.1.1':21,'8.5':33,'8.18':null},candidate_count:50};
    expect(firstHit(question)).toEqual({rank:21,chunk:'11.1.1'});
    expect(relevantRanks(question)).toEqual({'11.1.1':21,'8.5':33,'8.18':null});
    expect(questionCandidates(question)).toBe('2 of 3 cited chunks among 50 candidates');
    expect(firstHit({...question,relevant_ranks:{'8.18':null}})).toBeNull();
  });
});

describe('Recall at equal budgets', () => {
  it('uses k minus one, clamps at the ceiling, and never invents a missing curve', () => {
    expect(recallAt([.1,.4,.7],1)).toBe(.1);
    expect(recallAt([.1,.4,.7],2)).toBe(.4);
    expect(recallAt([.1,.4,.7],191)).toBe(.7);
    expect(recallAt([],20)).toBeNull();
    expect(recallCurve({})).toEqual([]);
    expect(recallCurve({mean_recall_curve:[.1,null,.5]})).toEqual([]);
    expect(curvePath([.1,.4,.7],2,k => k,value => value)).toBe('M1.00,0.10 L2.00,0.40');
  });
  it('changes budget and range, draws a dotted extension, and marks old runs missing', async () => {
    const fixture = TestBed.createComponent(RecallChart);
    fixture.componentRef.setInput('runs',[run('rrf-bm25-dense',Array.from({length:191},(_,i) => (i+1)/191)),run('rrf-bm25-dense-expanded',[.1,.5,.95],3),run('old')]);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('input')?.max).toBe('70');
    expect(host.querySelectorAll('.ceiling-line')).toHaveLength(1);
    expect(host.querySelectorAll('.readout-row')[1].textContent).toContain('95.0%');
    expect(host.querySelectorAll('.readout-row')[2].textContent).toContain('—');
    (host.querySelectorAll('.range-toggle button')[1] as HTMLButtonElement).click(); await fixture.whenStable();
    expect(host.querySelector('input')?.max).toBe('191');
    const slider = host.querySelector('input')!; slider.value='41'; slider.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(host.querySelector('.readout-heading')?.textContent).toContain('At 41 chunks');
    expect(host.querySelectorAll('.readout-row')[0].textContent).toContain('21.5%');
    (host.querySelectorAll('.readout-row')[1] as HTMLButtonElement).click(); await fixture.whenStable();
    expect(host.querySelectorAll('.readout-row')[1].getAttribute('aria-pressed')).toBe('true');
  });
  it('uses category curves and counts rather than the overall result', async () => {
    const fixture = TestBed.createComponent(RecallChart), sample = run('bm25',[.9]);
    sample.scores.by_category['interaction'] = {mean_recall_curve:[.2,.4],question_count:7};
    fixture.componentRef.setInput('runs',[sample]); fixture.componentRef.setInput('category','interaction'); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('7 questions');
    expect(fixture.nativeElement.querySelector('.readout-row').textContent).toContain('40.0%');
  });
});
