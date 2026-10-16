import { TestBed } from '@angular/core/testing';
import { EvaluationRun } from '../api/evaluation';
import { CombinationBuilder } from './combination-builder';
import { Settings } from '../core/settings';

const runs: EvaluationRun[] = [
  {layers:{retriever:'dense',answer:'model'},scores:{overall:{accuracy:.8,question_count:10},by_category:{}},questions:[]},
  {layers:{retriever:'bm25',answer:null},scores:{overall:{accuracy:.5,question_count:10},by_category:{}},questions:[]},
];
describe('Combination builder', () => {
  beforeEach(() => { TestBed.configureTestingModule({imports:[CombinationBuilder]}); TestBed.inject(Settings).update({motion:'on'}); });
  it('reveals recorded scores and clears them when a component changes', async () => {
    const fixture = TestBed.createComponent(CombinationBuilder);
    fixture.componentRef.setInput('runs',runs); fixture.componentRef.setInput('initial',runs[0].layers);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.reveal-button') as HTMLButtonElement).click(); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('80.0%');
    const choice = fixture.nativeElement.querySelector('input[value="bm25"]') as HTMLInputElement;
    choice.click(); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No recorded result.');
    expect(fixture.nativeElement.textContent).not.toContain('80.0%');
    expect(fixture.nativeElement.querySelector('.reveal-button').disabled).toBe(true);
    const none = fixture.nativeElement.querySelector('input[name="builder-answer"][value=""]') as HTMLInputElement;
    none.click(); await fixture.whenStable();
    fixture.nativeElement.querySelector('.reveal-button').click(); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('50.0%');
  });
});
