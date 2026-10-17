import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { CategoryCurves } from './category-curves';

function run(retriever: string, rule: number[], deck: number[]): EvaluationRun {
  return {layers:{retriever,answer:null},scores:{overall:{mean_recall_curve:rule},by_category:{rule:{mean_recall_curve:rule,question_count:9},deck:{mean_recall_curve:deck,question_count:4}}},questions:[]};
}

describe('Category curves', () => {
  it('reads every category at one shared budget and focuses a pipeline in all of them', async () => {
    const fixture = TestBed.createComponent(CategoryCurves);
    fixture.componentRef.setInput('runs',[run('bm25',[.1,.2,.3],[.5,.5,.5]),run('dense-qwen3-0.6b',[.2,.4,.6],[.1,.1,1])]);
    fixture.componentRef.setInput('categories',['deck','rule']);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const readouts = () => [...host.querySelectorAll('.lead-readout')].map(line => [...line.querySelectorAll('span,strong,small')].map(part => part.textContent?.trim()).join(' '));
    expect(host.querySelectorAll('input[type=range]')).toHaveLength(1);
    expect(host.querySelector('.shared-controls strong')?.textContent).toBe('k = 3');
    expect(readouts()).toEqual(['Best: Qwen3 · 0.6B 100.0% k = 3','Best: Qwen3 · 0.6B 60.0% k = 3']);
    const slider = host.querySelector('input')!; slider.value = '1'; slider.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(readouts()).toEqual(['Best: BM25 50.0% k = 1','Best: Qwen3 · 0.6B 20.0% k = 1']);
    (host.querySelector('.shared-legend button') as HTMLButtonElement).click(); await fixture.whenStable();
    expect(readouts()).toEqual(['BM25 50.0% k = 1','BM25 10.0% k = 1']);
    expect(host.querySelectorAll('g.highlighted')).toHaveLength(2);
  });
  it('says when several pipelines tie for best', async () => {
    const fixture = TestBed.createComponent(CategoryCurves);
    fixture.componentRef.setInput('runs',[run('bm25',[1],[1]),run('dense-qwen3-0.6b',[1],[1])]);
    fixture.componentRef.setInput('categories',['rule']);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.lead-readout').textContent).toContain('Best: BM25 and 1 tied');
  });
});
