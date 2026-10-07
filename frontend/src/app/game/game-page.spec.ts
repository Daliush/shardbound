import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FakeWebSocket } from '../testing/fake-web-socket';
import { aView } from '../testing/views';
import { GamePage } from './game-page';
import { WEB_SOCKET } from './game-socket.service';

describe('GamePage', () => {
  beforeEach(() => {
    localStorage.clear();
    FakeWebSocket.reset();
    TestBed.configureTestingModule({
      imports: [GamePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: WEB_SOCKET, useValue: FakeWebSocket },
      ],
    });
  });

  async function open(gameId: string): Promise<ComponentFixture<GamePage>> {
    const fixture = TestBed.createComponent(GamePage);
    fixture.componentRef.setInput('gameId', gameId);
    await fixture.whenStable();
    TestBed.inject(HttpTestingController).match('/api/cards').forEach((request) => request.flush([]));
    return fixture;
  }

  it('says so when this browser holds no seat in the game', async () => {
    const fixture = await open('g-1');

    expect(fixture.nativeElement.textContent).toContain('This browser holds no seat in this game.');
    expect(FakeWebSocket.instances).toHaveLength(0);
  });

  it('connects with the seat token, then shows the board and the decision', async () => {
    localStorage.setItem('shardbound.game.g-1.token', 'secret');
    const fixture = await open('g-1');
    const socket = FakeWebSocket.latest();
    expect(socket.url).toContain('/ws/games/g-1?token=secret');

    socket.open();
    socket.receive({ type: 'state', view: aView(12), history: [{ type: 'turn_started', rules: ['5.2'], text: 'Turn 5: your turn.' }] });
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Your turn: play a card, attack, or end your turn.');
    expect(page.textContent).toContain('[5.2] Turn 5: your turn.');
    expect([...page.querySelectorAll('app-decision-panel button')].map((b) => b.textContent?.trim()))
      .toEqual(['End your turn']);

    (page.querySelector('app-hand-card-tile button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(page.querySelectorAll('.targetable')).toHaveLength(2);

    (page.querySelectorAll('.targetable')[0] as HTMLButtonElement).click();
    expect(socket.sent).toEqual([{ type: 'act', requestId: 'c-1', decisionId: 'd-13', action: 0 }]);
  });
});
