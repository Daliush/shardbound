import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HomePage } from './home-page';

describe('HomePage', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('offers the decks and bots, and opens a game against a bot once created', async () => {
    const fixture = TestBed.createComponent(HomePage);
    const http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne('/api/decks').flush([
      { id: 'ember-starter', name: 'Ember Starter', faction: 'ember', cards: [] },
      { id: 'root-starter', name: 'Root Starter', faction: 'root', cards: [] },
    ]);
    http.expectOne('/api/bots').flush(['random']);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('select[name=deck] option')).toHaveLength(2);
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
