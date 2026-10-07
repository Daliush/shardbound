import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Card } from '../api/protocol';
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

  async function open(gameId: string, cards: Card[] = []): Promise<ComponentFixture<GamePage>> {
    const fixture = TestBed.createComponent(GamePage);
    fixture.componentRef.setInput('gameId', gameId);
    await fixture.whenStable();
    TestBed.inject(HttpTestingController).match('/api/cards').forEach((request) => request.flush(cards));
    return fixture;
  }

  async function playing(view = aView(12), cards: Card[] = []): Promise<HTMLElement> {
    localStorage.setItem('shardbound.game.g-1.token', 'secret');
    const fixture = await open('g-1', cards);
    FakeWebSocket.latest().open();
    FakeWebSocket.latest().receive({ type: 'state', view, history: [] });
    await fixture.whenStable();
    TestBed.inject(HttpTestingController).match('/api/cards').forEach((request) => request.flush(cards));
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
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

  it('shows the whole text of a card in hand and a relic, and the abilities of a unit', async () => {
    const base = aView(12);
    const view = aView(12, { you: { ...base.you, relics: [{ id: 7, card: 'tide.coral-font', controller: 'you' }] } });
    const page = await playing(view, [
      card('ember.spark-dart', 'Spark Dart', [{ kind: 'effect', text: 'Deal 3 damage to an enemy unit.' }]),
      card('ember.cinderling', 'Cinderling', [
        { kind: 'attack', text: 'Flick (1 Shard): Deal 3 damage to the target.' },
        { kind: 'ability', text: 'Death: Deal 2 damage to your opponent.' },
      ]),
      card('tide.coral-font', 'Coral Font', [{ kind: 'ability', text: 'All your units have +1/+2.' }]),
    ]);

    const unit = [...page.querySelectorAll('app-unit-tile')].find((tile) => tile.textContent?.includes('Cinderling #1'))!;
    expect(page.querySelector('app-hand-card-tile')?.textContent).toContain('Deal 3 damage to an enemy unit.');
    expect(unit.textContent).toContain('Death: Deal 2 damage to your opponent.');
    expect(unit.textContent).not.toContain('Deal 3 damage to the target.');
    expect(unit.querySelector('li')?.getAttribute('title')).toBe('Flick (1 Shard): Deal 3 damage to the target.');
    expect(page.querySelector('app-relic-tile')?.textContent).toContain('Coral Font #7');
    expect(page.querySelector('app-relic-tile')?.textContent).toContain('All your units have +1/+2.');
  });

  it('shows the modifiers of a unit as badges', async () => {
    const base = aView(12);
    const cinderling = {
      ...base.you.units[0],
      modifiers: [
        { attackDamage: 3, defense: 0, duration: 'end_of_turn' as const },
        { attackDamage: -2, defense: 0, duration: 'permanent' as const },
      ],
    };
    const page = await playing(aView(12, { you: { ...base.you, units: [cinderling] } }));

    const badges = [...page.querySelectorAll('app-unit-tile .badge')].map((b) => b.textContent?.trim());
    expect(badges).toEqual(['+3/+0 this turn', '-2/+0']);
  });

  it('answers a discard by clicking the card to discard', async () => {
    const base = aView(20);
    const view = aView(20, {
      yourTurn: false,
      you: { ...base.you, hand: [...base.you.hand, { id: 4, card: 'neutral.shardling', cost: 1, fractureStep: null }] },
      decision: {
        id: 'd-21',
        kind: 'choose_cards',
        prompt: "Choose 1 card to discard for your opponent's Mind Rot #9.",
        actions: [
          { index: 0, type: 'choose_cards', label: 'Discard Spark Dart #3', cards: [3] },
          { index: 1, type: 'choose_cards', label: 'Discard Shardling #4', cards: [4] },
        ],
      },
    });
    const page = await playing(view);

    const hand = [...page.querySelectorAll('app-hand-card-tile button')] as HTMLButtonElement[];
    expect(hand.map((tile) => tile.classList.contains('targetable'))).toEqual([true, true]);
    hand[1].click();

    expect(FakeWebSocket.latest().sent).toEqual([{ type: 'act', requestId: 'c-1', decisionId: 'd-21', action: 1 }]);
  });
});

function card(id: string, name: string, text: Card['text']): Card {
  return { id, name, faction: 'neutral', type: 'unit', token: false, keywords: [], text };
}
