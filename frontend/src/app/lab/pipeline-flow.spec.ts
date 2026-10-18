import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { CardBook } from '../game/card-book';
import { PipelineFlow } from './pipeline-flow';
import { citedChunks, columnSummary, pipelineColumns } from './pipeline-model';

function run(layers: EvaluationRun['layers'], top: string[], ranks: Record<string,number | null>, count: number): EvaluationRun {
  return {layers,scores:{overall:{},by_category:{}},questions:[{question_id:'q',category:'interaction',top_chunk_ids:top,recall_at:{'10':0,'20':0},reciprocal_rank:0,relevant_ranks:ranks,candidate_count:count}]};
}
const top = Array.from({length:20},(_,index) => index === 6 ? 'ember.ash-warden' : 'chunk-' + index);
const bm25Top = top.map((chunk,index) => index === 2 ? '8.5' : chunk);
const runs = [
  run({retriever:'rrf-bm25-dense',expand:'refs'},['ember.ash-warden',...top.filter(chunk => chunk !== 'ember.ash-warden')],{'11.1.1':21,'8.5':24,'8.18':null},25),
  run({retriever:'bm25'},bm25Top,{'11.1.1':29,'8.5':3,'8.18':155},156),
  run({retriever:'random'},top,{'11.1.1':1,'8.5':2,'8.18':3},191),
];
const reranked = run({retriever:'rrf-bm25-dense',expand:'refs',rerank:'minilm-l6'},top,{'11.1.1':2,'8.5':23,'8.18':null},25);

describe('Pipeline stages of a question', () => {
  it('orders the stages, leaves out the ones not evaluated, and draws the top 20 or every candidate', () => {
    const columns = pipelineColumns([...runs,reranked],'q',['ember.ash-warden']);
    expect(columns.map(column => [column.stage.label,column.slots.length,column.total])).toEqual([['BM25',20,156],['RRF + refs',25,25],['MiniLM',25,25]]);
    expect(columns[0].slots[6]).toEqual({rank:7,chunk:'ember.ash-warden',kind:'card'});
    expect(columns[0].slots[2]).toEqual({rank:3,chunk:'8.5',kind:'cited'});
    expect(columns[0].slots[3]).toEqual({rank:4,chunk:'chunk-3',kind:'other'});
  });
  it('names the cited chunks past the 20 ids a report keeps, and only them', () => {
    const [, expanded] = pipelineColumns(runs,'q',[]);
    expect(expanded.slots[20]).toEqual({rank:21,chunk:'11.1.1',kind:'cited'});
    expect(expanded.slots[21]).toEqual({rank:22,chunk:null,kind:'other'});
    expect(expanded.slots[23]).toEqual({rank:24,chunk:'8.5',kind:'cited'});
  });
  it('says how many cited chunks a stage shows and where the others are', () => {
    const [bm25, expanded] = pipelineColumns(runs,'q',[]);
    expect(columnSummary(bm25)).toEqual(['1 of 3 cited','ranks 29–155']);
    expect(columnSummary(expanded)).toEqual(['2 of 3 cited','8.18 missing']);
    expect(citedChunks([bm25,expanded])).toEqual(['11.1.1','8.5','8.18']);
  });
  it('leaves out a question no stage evaluated', () => {
    expect(pipelineColumns(runs,'unknown',[])).toEqual([]);
  });
});

describe('Pipeline flow', () => {
  beforeEach(() => TestBed.configureTestingModule({providers:[{provide:CardBook,useValue:{cards:signal([]),card:() => undefined,name:(id:string) => id,text:() => []}}]}));
  it('joins each cited chunk from stage to stage and tables its ranks', async () => {
    const fixture = TestBed.createComponent(PipelineFlow);
    fixture.componentRef.setInput('id','q'); fixture.componentRef.setInput('runs',runs); fixture.componentRef.setInput('cards',['ember.ash-warden']);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('[data-flow-stage]')).toHaveLength(2);
    expect(host.querySelectorAll('[data-flow-link]')).toHaveLength(2);
    expect(host.querySelectorAll('rect.cited')).toHaveLength(3);
    expect(host.querySelectorAll('rect.card')).toHaveLength(2);
    const rows = [...host.querySelectorAll('.rank-table tbody tr')].map(row => [...row.querySelectorAll('td')].map(cell => cell.textContent?.trim()));
    expect(rows).toEqual([['#29','#21'],['#3','#24'],['#155','—']]);
    expect(host.querySelector('.rank-table tbody tr td.in-prompt')?.textContent).toBe('#3');
  });
  it('explains that an unanswerable question cites nothing', async () => {
    const fixture = TestBed.createComponent(PipelineFlow);
    fixture.componentRef.setInput('id','q'); fixture.componentRef.setInput('runs',[run({retriever:'bm25'},top,{},156)]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.rank-table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('This question cites no source');
  });
});
