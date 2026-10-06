import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { JoinPage } from './join-page';

describe('JoinPage', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [JoinPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('joins with the code of the invite link, then opens the game', async () => {
    const fixture = TestBed.createComponent(JoinPage);
    fixture.componentRef.setInput('gameId', 'g-1');
    fixture.componentRef.setInput('code', 'join-code');
    const http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne('/api/decks').flush([
      { id: 'ember-starter', name: 'Ember Starter', faction: 'ember', cards: [] },
      { id: 'root-starter', name: 'Root Starter', faction: 'root', cards: [] },
    ]);
    await fixture.whenStable();

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();

    const join = http.expectOne('/api/games/g-1/join');
    expect(join.request.body).toEqual({ joinCode: 'join-code', deck: 'root-starter' });
    join.flush({ gameId: 'g-1', playerToken: 'second', websocketPath: '/ws/games/g-1' });
    expect(localStorage.getItem('shardbound.game.g-1.token')).toBe('second');
    expect(navigate).toHaveBeenCalledWith(['/games', 'g-1']);
  });

  it('cannot join without a code', async () => {
    const fixture = TestBed.createComponent(JoinPage);
    fixture.componentRef.setInput('gameId', 'g-1');
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('This link has no join code.');
  });
});
