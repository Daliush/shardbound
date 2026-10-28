import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { MetricsGraph, zoomedAxis } from './metrics-graph';

function run(retriever: string, recall: number): EvaluationRun {
  return {layers:{retriever,answer:null},scores:{overall:{mean_recall_at:{'10':recall},question_count:61},by_category:{}},questions:[]};
}

describe('Zoomed dot plot axis', () => {
  it('zooms on the values so that close scores stay apart', () => {
    const axis = zoomedAxis([.533,.565,.623],true);
    expect(axis.domain).toEqual([.52,.64]);
    expect(axis.x(.52)).toBe(0);
    expect(axis.x(.64)).toBe(100);
    expect(axis.ticks.length).toBeGreaterThan(2);
  });
  it('keeps a ratio within 0–1 and pads a single value', () => {
    expect(zoomedAxis([.98,1],true).domain[1]).toBe(1);
    const single = zoomedAxis([.5],true);
    expect(single.domain[0]).toBeLessThan(.5);
    expect(single.domain[1]).toBeGreaterThan(.5);
  });
});

describe('Metrics dot plots', () => {
  it('ranks the pipelines best first and draws the random floor as a reference, not a row', async () => {
    const fixture = TestBed.createComponent(MetricsGraph);
    fixture.componentRef.setInput('runs',[run('bm25',.533),run('random',.089),run('dense-qwen3-0.6b',.623),run('bm25l',.552)]);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect([...host.querySelectorAll('.dot-row .name-text')].map(name => name.textContent?.trim())).toEqual(['Qwen3 · 0.6B','BM25L','BM25']);
    expect(host.querySelectorAll('.dot-row.variant')).toHaveLength(1);
    expect(host.querySelector('.axis-note')?.textContent).toContain('Random floor: 8.9%, off this axis');
    expect(host.querySelector('.grid-lines .floor')).toBeNull();
  });
});
