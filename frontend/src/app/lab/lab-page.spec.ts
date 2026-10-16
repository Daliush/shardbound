import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LabPage } from './lab-page';
describe('LabPage loading', () => {
  beforeEach(() => TestBed.configureTestingModule({imports:[LabPage],providers:[provideHttpClient(),provideHttpClientTesting(),provideRouter([])]}));
  it('uses test when evaluated and reads the chosen report through existing endpoints', async () => {
    const fixture = TestBed.createComponent(LabPage); await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/evaluations/v1/dev').flush(['old']);
    http.expectOne('/api/evaluations/v1/test').flush(['new']);
    await fixture.whenStable();
    await vi.waitFor(() => http.expectOne('/api/evaluations/v1/test/new').flush({dataset:{version:'v1',split:'test'},evaluated_at:'2026-10-14T22:20:30Z',code:'abc',runs:[]}));
    await fixture.whenStable();
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.textContent).toContain('No combinations in this report.'); });
  });
  it('shows a genuine empty state for an unevaluated split', async () => {
    const fixture = TestBed.createComponent(LabPage); await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/evaluations/v1/dev').flush([]);
    http.expectOne('/api/evaluations/v1/test').flush([]);
    await fixture.whenStable();
    await vi.waitFor(() => expect(fixture.nativeElement.textContent).toContain('Not evaluated yet.'));
  });
  it('shows the actual compared names after asynchronous loading and changes views without requesting the report again', async () => {
    const fixture = TestBed.createComponent(LabPage); await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/evaluations/v1/dev').flush(['new']);
    http.expectOne('/api/evaluations/v1/test').flush([]);
    await fixture.whenStable();
    await vi.waitFor(() => http.expectOne('/api/evaluations/v1/dev/new').flush({dataset:{version:'v1',split:'dev'},evaluated_at:'2026-10-14T22:20:30Z',code:'abc',runs:[
      {layers:{retriever:'random',answer:null},scores:{overall:{mean_recall_at:{'10':.1}},by_category:{}},questions:[]},
      {layers:{retriever:'bm25',answer:null},scores:{overall:{mean_recall_at:{'10':.5}},by_category:{}},questions:[]},
      {layers:{retriever:'dense-qwen3-0.6b',answer:null},scores:{overall:{mean_recall_at:{'10':.8}},by_category:{}},questions:[]},
    ]}));
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.querySelector('.leaderboard')).not.toBeNull(); });
    fixture.nativeElement.querySelectorAll('.lab-nav button')[2].click();
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.querySelector('.comparison-picker')).not.toBeNull(); });
    const selects = fixture.nativeElement.querySelectorAll('.comparison-picker select') as NodeListOf<HTMLSelectElement>;
    expect(selects[0].selectedOptions[0].textContent).toContain('Qwen3');
    expect(selects[1].selectedOptions[0].textContent).toBe('BM25');
    fixture.nativeElement.querySelectorAll('.lab-nav button')[1].click();
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.querySelector('app-metrics-graph')).not.toBeNull(); });
    http.verify();
  });
});


