import { Injectable, InjectionToken, OnDestroy, inject, signal } from '@angular/core';
import { EMPTY, Observable, Subscription, repeat, retry, timer } from 'rxjs';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { ClientMessage, EventView, GameView, ServerMessage } from '../api/protocol';

/** The WebSocket constructor, replaced by a fake in tests. */
export const WEB_SOCKET = new InjectionToken<typeof WebSocket>('WEB_SOCKET', {
  factory: () => WebSocket,
});

/** Time between two queued updates, so a bot's moves can be followed one by one (spec §14). */
export const UPDATE_PACE_MS = 400;

/** Spec §13.1: another connection took this seat. */
export const REPLACED = 4409;

const MAX_BACKOFF_MS = 10_000;

export type ConnectionStatus = 'connecting' | 'open' | 'reconnecting' | 'replaced' | 'closed';

type Update = Extract<ServerMessage, { type: 'update' }>;

/**
 * One seat's connection to a game (spec §13.2). It renders nothing and computes no rule: it keeps the latest
 * view and the log the server sent, applies updates in version order, and asks for the full state on a gap.
 */
@Injectable()
export class GameSocketService implements OnDestroy {
  private readonly webSocketCtor = inject(WEB_SOCKET);

  readonly view = signal<GameView | null>(null);
  readonly log = signal<EventView[]>([]);
  readonly rejection = signal<{ reason: string; message: string } | null>(null);
  readonly status = signal<ConnectionStatus>('connecting');

  private socket?: WebSocketSubject<ServerMessage | ClientMessage>;
  private subscription?: Subscription;
  private queue: Update[] = [];
  private pacing?: ReturnType<typeof setTimeout>;
  private failures = 0;
  private requests = 0;

  connect(url: string): void {
    this.socket = webSocket<ServerMessage | ClientMessage>({
      url,
      WebSocketCtor: this.webSocketCtor,
      openObserver: { next: () => this.opened() },
      closeObserver: { next: (event) => this.closed(event) },
    });
    this.subscription = this.socket
      .pipe(
        retry({ delay: () => this.reconnectDelay() }),
        repeat({ delay: () => this.reconnectDelay() }),
      )
      .subscribe((message) => this.receive(message as ServerMessage));
  }

  /** Answers the pending decision with the index of one of its actions: the client never builds an action. */
  act(decisionId: string, action: number): void {
    this.send({ type: 'act', requestId: `c-${++this.requests}`, decisionId, action });
  }

  disconnect(): void {
    this.status.set('closed');
    this.subscription?.unsubscribe();
    clearTimeout(this.pacing);
    this.pacing = undefined;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private receive(message: ServerMessage): void {
    switch (message.type) {
      case 'state':
        this.queue = [];
        this.view.set(message.view);
        this.log.set(message.history);
        this.rejection.set(null);
        break;
      case 'update':
        this.queue.push(message);
        if (this.pacing === undefined) {
          this.applyNext();
        }
        break;
      case 'rejected':
        this.rejection.set({ reason: message.reason, message: message.message });
        break;
    }
  }

  /** One queued update at a time; a version that is not the next one means a message was missed. */
  private applyNext(): void {
    const next = this.queue.shift();
    if (next === undefined) {
      this.pacing = undefined;
      return;
    }
    const current = this.view()?.version ?? -1;
    if (next.view.version <= current) {
      this.applyNext();
      return;
    }
    if (next.view.version > current + 1) {
      this.queue = [];
      this.pacing = undefined;
      this.send({ type: 'sync' });
      return;
    }
    this.view.set(next.view);
    this.log.update((log) => [...log, ...next.events]);
    this.rejection.set(null);
    this.pacing = setTimeout(() => this.applyNext(), UPDATE_PACE_MS);
  }

  private send(message: ClientMessage): void {
    this.socket?.next(message);
  }

  /** The server sends the full state on every connection, so a reconnection resyncs by itself. */
  private opened(): void {
    this.failures = 0;
    this.status.set('open');
  }

  private closed(event: CloseEvent): void {
    if (event.code === REPLACED) {
      this.status.set('replaced');
    }
  }

  private reconnectDelay(): Observable<unknown> {
    if (this.status() === 'replaced' || this.status() === 'closed') {
      return EMPTY;
    }
    this.status.set('reconnecting');
    this.failures++;
    return timer(Math.min(500 * 2 ** this.failures, MAX_BACKOFF_MS));
  }
}
