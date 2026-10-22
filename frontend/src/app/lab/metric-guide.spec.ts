import { TestBed } from '@angular/core/testing';
import { Evaluation, EvaluationRun, MetricDescription } from '../api/evaluation';
import { MetricCatalog } from './metric-catalog';
import { MetricGuide } from './metric-guide';
import { anchorOf, buildGuide, findLeader } from './metric-guide-model';

function described(path: string, name: string, display: MetricDescription['display']): MetricDescription {
  return {path,name,display,what:name + ' does this.',how:name + ' is computed so.',represents:name + ' stands for that.'};
}
const METRICS = [described('mean_recall_at.10','Recall@10','share'),described('mean_binary_ndcg_at.10','Binary nDCG@10','score'),
  described('latency_p95_seconds','Latency p95','seconds'),described('question_count','Questions','count'),
  described('mean_candidate_count','Candidates','count'),described('mean_recall_curve','Recall curve','share')];
function run(retriever: string, recall: number, ndcg: number, candidates: number, seconds: number, rerank: string | null = null): EvaluationRun {
  return {layers:{retriever,rerank,answer:null},questions:[],scores:{by_category:{},overall:{mean_recall_at:{'10':recall},mean_binary_ndcg_at:{'10':ndcg},
    latency_p95_seconds:seconds,question_count:61,mean_candidate_count:candidates,mean_recall_curve:[recall/2,recall,recall]}}};
}
const RUNS = [run('random',.089,.056,191,0),run('bm25',.533,.5,191,.01),run('rrf-bm25-dense',.727,.623,41.3,1.27,'minilm-l6')];
function evaluation(metrics?: MetricDescription[]): Evaluation {
  return {dataset:{version:'v2',split:'dev'},evaluated_at:'2026-10-22T12:49:42Z',code:'6b39e1f',...(metrics ? {metrics} : {}),runs:RUNS};
}

describe('Metric guide model', () => {
  it('groups each described value by what it is for: one decides, the others explain, time, or give context', () => {
    const groups = buildGuide(MetricCatalog.fromEvaluation(evaluation(METRICS)),RUNS);

    expect(groups.map(group => [group.role,group.entries.map(entry => entry.description.name)])).toEqual([
      ['decides',['Recall@10']],['explains',['Binary nDCG@10']],['speed',['Latency p95']],['context',['Questions','Candidates','Recall curve']]]);
  });
  it('samples each value on the top pipeline by Recall@10 against the random floor, a longer bar always better', () => {
    const [decides,explains,speed,context] = buildGuide(MetricCatalog.fromEvaluation(evaluation(METRICS)),RUNS);

    expect(findLeader(RUNS)?.layers['rerank']).toBe('minilm-l6');
    expect(decides.entries[0].sample?.rows.map(row => [row.name,row.text,row.length])).toEqual([['Top pipeline','72.7%',.727],['Random floor','8.9%',.089]]);
    expect(explains.entries[0].sample?.leader).toBe('RRF · BM25 + dense + MiniLM-L6 cross-encoder');
    expect([speed.entries[0].better,context.entries[0].better]).toEqual(['lower',null]);
    expect(speed.entries[0].sample?.rows.map(row => [row.text,row.length])).toEqual([['1.27 s',0],['0.00 s',1]]);
    expect(context.entries[0].sample?.kind).toBe('single');
    expect(context.entries[1].sample?.rows.map(row => row.length)).toEqual([41.3/191,1]);
    expect(context.entries[2].sample?.kind).toBe('curve');
    expect(context.entries[2].sample?.curves).toHaveLength(2);
  });
  it('describes nothing for an evaluation written before its values described themselves', () => {
    expect(buildGuide(MetricCatalog.fromEvaluation(evaluation()),RUNS)).toEqual([]);
  });
  it('anchors each card on its path', () => {
    expect(anchorOf('mean_binary_ndcg_at.10')).toBe('metric-mean-binary-ndcg-at-10');
  });
});

describe('Metric guide', () => {
  it('shows each metric with what it does, how it is computed and what it represents, and indexes them', async () => {
    const fixture = TestBed.createComponent(MetricGuide);
    fixture.componentRef.setInput('catalog',MetricCatalog.fromEvaluation(evaluation(METRICS)));
    fixture.componentRef.setInput('runs',RUNS);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;

    expect([...host.querySelectorAll('.metric-card h3')].map(title => title.textContent)).toEqual(
      ['Recall@10','Binary nDCG@10','Latency p95','Questions','Candidates','Recall curve']);
    const ndcg = host.querySelector('#metric-mean-binary-ndcg-at-10')!;
    expect(ndcg.querySelector('.card-what')?.textContent).toBe('Binary nDCG@10 does this.');
    expect([...ndcg.querySelectorAll('.facet p')].map(text => text.textContent)).toEqual(['Binary nDCG@10 is computed so.','Binary nDCG@10 stands for that.']);
    expect(ndcg.querySelector('.display-badge')?.textContent).toBe('Score · 0 to 1');
    expect(host.querySelectorAll('.rail-item')).toHaveLength(6);
  });
  it('offers the latest evaluation when the open one describes nothing', async () => {
    const fixture = TestBed.createComponent(MetricGuide);
    fixture.componentRef.setInput('catalog',MetricCatalog.fromEvaluation(evaluation()));
    let asked = false;
    fixture.componentInstance.latest.subscribe(() => asked = true);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.guide-empty button')!;

    button.click();

    expect(asked).toBe(true);
  });
});
