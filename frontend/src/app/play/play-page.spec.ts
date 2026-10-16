import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Ambience } from '../core/ambience';
import { PlayPage } from './play-page';

describe('PlayPage', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [PlayPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('preselects the faction from the home page and changes the whole ambience on selection', async () => {
    const params = new BehaviorSubject(convertToParamMap({ faction: 'tide' }));
    TestBed.overrideProvider(ActivatedRoute, {useValue: {queryParamMap: params, snapshot: {queryParamMap: params.value}}});
    const fixture = TestBed.createComponent(PlayPage), http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/decks').flush([
      {id: 'ember-starter', name: 'Ember Starter', faction: 'ember', cards: []},
      {id: 'tide-starter', name: 'Tide Starter', faction: 'tide', cards: []},
    ]);
    http.expectOne('/api/bots').flush(['random']);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.chosen').textContent).toContain('Tide Starter');
    expect(TestBed.inject(Ambience).faction()).toBe('tide');
    fixture.nativeElement.querySelector('[data-faction="ember"].deck-box').click();
    await fixture.whenStable();
    expect(TestBed.inject(Ambience).faction()).toBe('ember');
    params.next(convertToParamMap({faction: 'unknown'}));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.chosen').textContent).toContain('Ember Starter');
  });

  it('offers the decks and bots, and opens a game against a bot once created', async () => {
    const fixture = TestBed.createComponent(PlayPage);
    const http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne('/api/decks').flush([
      { id: 'ember-starter', name: 'Ember Starter', faction: 'ember', cards: [] },
      { id: 'root-starter', name: 'Root Starter', faction: 'root', cards: [] },
    ]);
    http.expectOne('/api/bots').flush(['random']);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('.deck-box')).toHaveLength(2);
    (page.querySelector('button[type=submit]') as HTMLButtonElement).click();

    const create = http.expectOne('/api/games');
    expect(create.request.body).toEqual({
      deck: 'ember-starter',
      opponent: { type: 'bot', bot: 'random', deck: 'root-starter' },
    });
    create.flush({ gameId: 'g-1', playerToken: 'secret', websocketPath: '/ws/games/g-1' });
    expect(localStorage.getItem('shardbound.game.g-1.token')).toBe('secret');
    expect(navigate).toHaveBeenCalledWith(['/games', 'g-1']);
  });
});

