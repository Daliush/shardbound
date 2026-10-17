import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { Evaluations } from './evaluations';

describe('Evaluation datasets', () => {
  beforeEach(() => TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]}));
  it('reads a split as written, one question per line, and asks the server once', async () => {
    const evaluations = TestBed.inject(Evaluations), http = TestBed.inject(HttpTestingController);
    const questions = firstValueFrom(evaluations.dataset('v2','dev'));
    const again = firstValueFrom(evaluations.dataset('v2','dev'));
    http.expectOne('/api/datasets/v2/dev').flush('{"id":"a","question":"Line one\\nline two"}\n\n{"id":"b","question":"B?"}\n');
    expect((await questions).map(question => [question.id,question.question])).toEqual([['a','Line one\nline two'],['b','B?']]);
    expect(await again).toEqual(await questions);
    http.verify();
  });
});
