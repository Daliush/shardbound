import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { Settings } from '../core/settings';
import { Leaderboard } from './leaderboard';

/** Pipeline n scores n% at Recall@10 and the reverse at Recall@20, so ranking by the other metric turns the board over. */
function run(n: number): EvaluationRun {
  return {layers:{retriever:'bm25-' + n,answer:null},questions:[],scores:{overall:{mean_recall_at:{'10':n/100,'20':(30-n)/100},question_count:61},by_category:{}}};
}
const FLOOR: EvaluationRun = {layers:{retriever:'random',answer:null},questions:[],scores:{overall:{mean_recall_at:{'10':.01,'20':.02}},by_category:{}}};
const RUNS = [FLOOR,...Array.from({length:23},(_,index) => run(index+1))];

describe('Leaderboard', () => {
  beforeEach(() => { TestBed.configureTestingModule({imports:[Leaderboard]}); TestBed.inject(Settings).update({motion:'on'}); });
  async function board(metric: string) {
    const fixture = TestBed.createComponent(Leaderboard);
    fixture.componentRef.setInput('runs',RUNS); fixture.componentRef.setInput('scores',['mean_recall_at.10','mean_recall_at.20']); fixture.componentRef.setInput('metric',metric);
    await fixture.whenStable();
    return {fixture,host:fixture.nativeElement as HTMLElement};
  }
  const names = (host: HTMLElement) => [...host.querySelectorAll('.board-row .pipeline-name')].map(name => name.textContent?.trim());

  it('leads with the best pipeline, its lead over the second and its multiple of the random floor', async () => {
    const {host} = await board('mean_recall_at.10');

    expect(host.querySelector('#champion-title')?.textContent).toBe('bm25-23');
    expect(host.querySelector('.champion .score .sr-only')?.textContent).toBe('23.0%');
    expect(host.querySelector('.lead-line b')?.textContent).toBe('+1.0 pts ahead of #2');
    expect(host.querySelector('.floor-line')?.textContent).toBe('23.0× the random floor');
  });
  it('shows ten pipelines at a time, the random floor apart, and the next ten one arrow away', async () => {
    const {fixture,host} = await board('mean_recall_at.10');

    expect(names(host)).toHaveLength(10);
    expect(names(host)[0]).toBe('bm25-23');
    expect(host.querySelector('.floor-row')?.textContent).toContain('1.0%');
    host.querySelector<HTMLButtonElement>('app-pager button[aria-label="Next 10"]')!.click(); await fixture.whenStable();

    expect(names(host)).toEqual(Array.from({length:10},(_,index) => 'bm25-' + (13-index)));
    expect(host.querySelector('app-pager .range .sr-only')?.textContent).toBe('11–20');
  });
  it('shows the chunks of each pipeline after its scores, a column it never ranks by, when the file has them', async () => {
    const sized = RUNS.map(each => ({...each,scores:{...each.scores,overall:{...each.scores.overall,mean_candidate_count:12.2}}}));
    const fixture = TestBed.createComponent(Leaderboard);
    fixture.componentRef.setInput('runs',sized); fixture.componentRef.setInput('scores',['mean_recall_at.10']); fixture.componentRef.setInput('metric','mean_recall_at.10');
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.board-head .size-cell')?.textContent).toBe('Chunks');
    expect(host.querySelector('.board-head .size-cell button')).toBeNull();
    expect(host.querySelector('.board-row .size-cell')?.textContent?.trim()).toBe('12.2');
    expect((await board('mean_recall_at.10')).host.querySelector('.size-cell')).toBeNull();
  });
  it('says a context that leads is read with its size, since the answers choose the context', async () => {
    const context: EvaluationRun = {layers:{retriever:'rrf',context:'top10-plus-named-cards-refs',answer:null},questions:[],
      scores:{overall:{mean_recall_at:{'10':.5,'20':.5},mean_candidate_count:19.7},by_category:{}}};
    const fixture = TestBed.createComponent(Leaderboard);
    fixture.componentRef.setInput('runs',[...RUNS,context]); fixture.componentRef.setInput('scores',['mean_recall_at.10']); fixture.componentRef.setInput('metric','mean_recall_at.10');
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.context-note')?.textContent).toBe('19.7 chunks in the prompt. A bigger context holds more by its size alone: the answers on dev choose the context.');
    expect((await board('mean_recall_at.10')).host.querySelector('.context-note')).toBeNull();
  });
  it('keeps the page open when the ranking metric changes, so the pipelines that cross its edge come and go', async () => {
    const {fixture,host} = await board('mean_recall_at.10');
    host.querySelector<HTMLButtonElement>('app-pager button[aria-label="Next 10"]')!.click(); await fixture.whenStable();

    fixture.componentRef.setInput('metric','mean_recall_at.20'); await fixture.whenStable();

    expect(names(host)).toEqual(Array.from({length:10},(_,index) => 'bm25-' + (11+index)));
    expect(host.querySelector('#champion-title')?.textContent).toBe('bm25-1');
  });
});
