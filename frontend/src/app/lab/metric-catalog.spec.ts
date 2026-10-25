import { Evaluation, MetricDescription } from '../api/evaluation';
import { MetricCatalog } from './metric-catalog';

const NDCG: MetricDescription = {path:'mean_binary_ndcg_at.10',name:'Binary nDCG@10',display:'score',what:'How high the cited chunks sit.',
  how:'Every cited chunk gains 1.',represents:'The order inside the prompt.'};
function evaluation(metrics?: MetricDescription[]): Evaluation {
  return {dataset:{version:'v2',split:'dev'},evaluated_at:'2026-10-22T12:49:42Z',code:'6b39e1f',...(metrics ? {metrics} : {}),runs:[]};
}

describe('Metric catalog', () => {
  it('names, writes and describes a value as its evaluation file says', () => {
    const catalog = MetricCatalog.fromEvaluation(evaluation([NDCG]));

    expect(catalog.name('mean_binary_ndcg_at.10')).toBe('Binary nDCG@10');
    expect(catalog.format('mean_binary_ndcg_at.10',.62290)).toBe('0.623');
    expect(catalog.description('mean_binary_ndcg_at.10')?.how).toBe('Every cited chunk gains 1.');
    expect(catalog.descriptions).toEqual([NDCG]);
  });
  it('finds a question\'s value at the path of its group without mean_', () => {
    const catalog = MetricCatalog.fromEvaluation(evaluation([NDCG]));

    expect(catalog.name('binary_ndcg_at.10')).toBe('Binary nDCG@10');
    expect(catalog.format('reciprocal_rank',1/21)).toBe('0.048');
  });
  it('reads every rule from the display: a count is context, a time is better lower, a share or a score is bounded', () => {
    const catalog = MetricCatalog.fromEvaluation(evaluation());

    expect([catalog.isContext('mean_candidate_count'),catalog.isContext('question_count'),catalog.isContext('mean_recall_at.10')]).toEqual([true,true,false]);
    expect([catalog.lowerIsBetter('latency_p95_seconds'),catalog.lowerIsBetter('mean_reciprocal_rank')]).toEqual([true,false]);
    expect([catalog.isTime('latency_p95_seconds'),catalog.isTime('mean_recall_at.10')]).toEqual([true,false]);
    expect([catalog.isBounded('mean_recall_at.20'),catalog.isBounded('mean_reciprocal_rank'),catalog.isBounded('mean_candidate_count')]).toEqual([true,true,false]);
    expect([catalog.unit('mean_recall_at.10'),catalog.unit('latency_p50_seconds'),catalog.unit('mean_reciprocal_rank')]).toEqual(['%','s','value']);
  });
  it('writes a share in percent, a score with three decimals, a count whole or with one decimal, a time in seconds', () => {
    const catalog = MetricCatalog.fromEvaluation(evaluation());

    expect(catalog.format('mean_recall_at.10',.72732)).toBe('72.7%');
    expect(catalog.format('mean_reciprocal_rank',.008)).toBe('0.008');
    expect([catalog.format('question_count',61),catalog.format('mean_candidate_count',41.3279),catalog.format('candidate_count',50)]).toEqual(['61','41.3','50']);
    expect([catalog.format('latency_p95_seconds',1.2234),catalog.format('seconds',.78),catalog.format('seconds',.0000427)]).toEqual(['1.22 s','780 ms','0.043 ms']);
    expect(catalog.format('mean_recall_at.10',null)).toBe('—');
  });
  it('knows the names of a file written before its values described themselves, and no text for them', () => {
    const catalog = MetricCatalog.fromEvaluation(evaluation());

    expect([catalog.name('mean_recall_at.10'),catalog.name('latency_p95_seconds'),catalog.name('seconds')]).toEqual(['Recall@10','Latency p95','Latency']);
    expect(catalog.description('mean_recall_at.10')).toBeUndefined();
    expect(catalog.descriptions).toEqual([]);
  });
  it('draws a longer bar for a better value: a share against 1, a time reversed against the slowest', () => {
    const catalog = MetricCatalog.fromEvaluation(evaluation());

    expect(catalog.barLength('mean_recall_at.10',.727,catalog.ceiling('mean_recall_at.10',[.727,.089]))).toBe(.727);
    const times = [1.27,.0001,null];
    const ceiling = catalog.ceiling('latency_p50_seconds',times);
    expect(ceiling).toBe(1.27);
    expect([catalog.barLength('latency_p50_seconds',1.27,ceiling),catalog.barLength('latency_p50_seconds',.0001,ceiling)]).toEqual([0,1 - .0001/1.27]);
    expect(catalog.barLength('latency_p50_seconds',null,ceiling)).toBe(0);
  });
  it('names an unknown value from its path and writes it without inventing a percentage', () => {
    const catalog = MetricCatalog.fromEvaluation(null);

    expect(catalog.name('quality.custom_metric')).toBe('Quality · Custom Metric');
    expect(catalog.format('quality.custom_metric',.3)).toBe('0.3');
    expect([catalog.isContext('quality.custom_metric'),catalog.lowerIsBetter('quality.custom_metric')]).toEqual([false,false]);
  });
});
