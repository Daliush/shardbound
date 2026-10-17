import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { CardBook } from '../game/card-book';
import { QuestionDetail } from './question-detail';

function run(retriever: string, ranks?: Record<string,number | null>): EvaluationRun {
  return {layers:{retriever,answer:null},scores:{overall:{},by_category:{}},questions:[{question_id:'q',category:'simple-rule',top_chunk_ids:['1.1'],recall_at:{'10':1,'20':1},reciprocal_rank:1,...(ranks ? {relevant_ranks:ranks} : {})}]};
}

describe('Question detail', () => {
  beforeEach(() => TestBed.configureTestingModule({providers:[{provide:CardBook,useValue:{cards:signal([]),card:() => undefined,name:(id:string) => id,text:() => []}}]}));
  it('shows the question as the dataset words it, with its answer key, or says it is unanswerable', async () => {
    const fixture = TestBed.createComponent(QuestionDetail);
    fixture.componentRef.setInput('id','q');
    fixture.componentRef.setInput('question',{id:'q',category:'rule',question:'How many HP does a player start with?',answerable:true,key_facts:['Each player starts with 20 HP.'],citations:['1.1'],cards:[]});
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('h2')?.textContent).toBe('How many HP does a player start with?');
    expect(host.querySelector('.question-id')?.textContent).toBe('q');
    expect(host.querySelector('.answer-key li')?.textContent).toBe('Each player starts with 20 HP.');
    fixture.componentRef.setInput('question',{id:'q',category:'unanswerable',question:'What does Rampage do?',answerable:false,key_facts:[],citations:[],cards:[]});
    await fixture.whenStable();
    expect(host.querySelector('.answer-key')?.textContent).toContain('Unanswerable');
  });
  it('falls back on the id and the report category without the dataset, and opens on the pipeline flow', async () => {
    const fixture = TestBed.createComponent(QuestionDetail);
    fixture.componentRef.setInput('id','q'); fixture.componentRef.setInput('runs',[run('bm25',{'1.1':1})]);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('h2')?.textContent).toBe('q');
    expect(host.querySelector('header p')?.textContent).toBe('Simple-Rule');
    expect(host.querySelector('.answer-key')).toBeNull();
    expect(host.querySelectorAll('[data-flow-stage]')).toHaveLength(1);
  });
  it('says when an older report keeps no cited ranks', async () => {
    const fixture = TestBed.createComponent(QuestionDetail);
    fixture.componentRef.setInput('id','q'); fixture.componentRef.setInput('runs',[run('bm25')]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.no-ranks')?.textContent).toContain('This report keeps no cited ranks');
  });
});
