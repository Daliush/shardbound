import { TestBed } from '@angular/core/testing';
import { FakeWebSocket } from '../testing/fake-web-socket';
import { aView } from '../testing/views';
import { GameSocketService, REPLACED, UPDATE_PACE_MS, WEB_SOCKET } from './game-socket.service';

describe('GameSocketService', () => {
  let service: GameSocketService;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.reset();
    TestBed.configureTestingModule({
      providers: [GameSocketService, { provide: WEB_SOCKET, useValue: FakeWebSocket }],
    });
    service = TestBed.inject(GameSocketService);
    service.connect('ws://test/ws/games/g?token=t');
    FakeWebSocket.latest().open();
  });

  afterEach(() => {
    service.disconnect();
    vi.useRealTimers();
  });

  const event = (text: string) => ({ type: 'turn_started', rules: ['5.2'], text });

  it('shows the state, then applies each update and appends its events to the log', () => {
    FakeWebSocket.latest().receive({ type: 'state', view: aView(12), history: [event('Turn 5: your turn.')] });
    FakeWebSocket.latest().receive({ type: 'update', view: aView(13), events: [event('You play Spark Dart.')] });

    expect(service.status()).toBe('open');
    expect(service.view()?.version).toBe(13);
    expect(service.log().map((e) => e.text)).toEqual(['Turn 5: your turn.', 'You play Spark Dart.']);
  });

  it('shows queued updates one by one, about 400 ms apart', () => {
    const socket = FakeWebSocket.latest();
    socket.receive({ type: 'state', view: aView(12), history: [] });
    socket.receive({ type: 'update', view: aView(13), events: [] });
    socket.receive({ type: 'update', view: aView(14), events: [] });
    socket.receive({ type: 'update', view: aView(15), events: [] });

    expect(service.view()?.version).toBe(13);
    vi.advanceTimersByTime(UPDATE_PACE_MS);
    expect(service.view()?.version).toBe(14);
    vi.advanceTimersByTime(UPDATE_PACE_MS);
    expect(service.view()?.version).toBe(15);
  });

  it('asks for the full state when a version is missing', () => {
    const socket = FakeWebSocket.latest();
    socket.receive({ type: 'state', view: aView(12), history: [] });
    socket.receive({ type: 'update', view: aView(14), events: [] });

    expect(service.view()?.version).toBe(12);
    expect(socket.sent).toEqual([{ type: 'sync' }]);

    socket.receive({ type: 'state', view: aView(14), history: [] });
    expect(service.view()?.version).toBe(14);
  });

  it('sends an answer by index, and shows a rejection until the next update', () => {
    const socket = FakeWebSocket.latest();
    socket.receive({ type: 'state', view: aView(12), history: [] });

    service.act('d-13', 6);
    socket.receive({ type: 'rejected', requestId: 'c-1', reason: 'stale_decision', message: 'Decision d-13 is no longer pending.' });

    expect(socket.sent).toEqual([{ type: 'act', requestId: 'c-1', decisionId: 'd-13', action: 6 }]);
    expect(service.rejection()?.reason).toBe('stale_decision');
    socket.receive({ type: 'update', view: aView(13), events: [] });
    expect(service.rejection()).toBeNull();
  });

  it('reconnects with a backoff when the connection drops, and takes the new state', () => {
    FakeWebSocket.latest().receive({ type: 'state', view: aView(12), history: [] });

    FakeWebSocket.latest().drop();
    expect(service.status()).toBe('reconnecting');
    expect(FakeWebSocket.instances).toHaveLength(1);

    vi.advanceTimersByTime(1000);
    expect(FakeWebSocket.instances).toHaveLength(2);
    FakeWebSocket.latest().open();
    FakeWebSocket.latest().receive({ type: 'state', view: aView(20), history: [] });

    expect(service.status()).toBe('open');
    expect(service.view()?.version).toBe(20);
  });

  it('stays closed when a newer connection replaced this one', () => {
    FakeWebSocket.latest().drop(REPLACED, true);

    vi.advanceTimersByTime(60_000);

    expect(service.status()).toBe('replaced');
    expect(FakeWebSocket.instances).toHaveLength(1);
  });
});
