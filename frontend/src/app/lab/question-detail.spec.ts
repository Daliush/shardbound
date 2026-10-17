import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { CardBook } from '../game/card-book';
import { QuestionDetail } from './question-detail';

describe('Question citation evidence', () => {
  beforeEach(() => TestBed.configureTestingModule({providers:[{provide:CardBook,useValue:{cards:signal([]),card:() => undefined,name:(id:string) => id,text:() => []}}]}));
  it('renders ranks beyond twenty, missing citations, candidate context, and every relevant top-list marker', async () => {
    const fixture = TestBed.createComponent(QuestionDetail);
    const run: EvaluationRun = {layers:{retriever:'bm25'},scores:{overall:{},by_category:{}},questions:[{question_id:'q',category:'rule',top_chunk_ids:['1.1','1.2','1.3'],recall_at:{'10':.5,'20':.5},reciprocal_rank:1,relevant_ranks:{'1.1':1,'1.2':2,'8.5':33,'8.18':null},candidate_count:50}]};
    fixture.componentRef.setInput('id','q'); fixture.componentRef.setInput('runs',[run]); await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.rank-slot.relevant')).toHaveLength(2);
    expect(host.querySelector('.cited-evidence')?.textContent).toContain('#33');
    expect(host.querySelector('.cited-evidence')?.textContent).toContain('Not retrieved');
    expect(host.querySelector('.cited-evidence')?.textContent).toContain('3 of 4 cited chunks among 50 candidates');
    expect(host.querySelector('.future-fields')).toBeNull();
  });
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
  it('explains unknown ranks in legacy reports without inventing candidate values', async () => {
    const fixture = TestBed.createComponent(QuestionDetail);
    fixture.componentRef.setInput('id','old'); fixture.componentRef.setInput('runs',[{layers:{retriever:'bm25'},scores:{overall:{},by_category:{}},questions:[{question_id:'old',category:'rule',top_chunk_ids:['1.1'],recall_at:{'10':0,'20':0},reciprocal_rank:.02}]}]); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Ranks beyond the top 20 are unknown');
    expect(fixture.nativeElement.textContent).toContain('— candidates');
    expect(fixture.nativeElement.querySelectorAll('.rank-slot.relevant')).toHaveLength(0);
  });
});
