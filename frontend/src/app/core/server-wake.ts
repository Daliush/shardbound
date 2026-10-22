import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { finalize, tap } from 'rxjs';

/** A warm server answers well under this; a request still waiting is waiting for an instance to start. */
export const SLOW_AFTER_MS = 3000;
/** Past this, the wait is longer than a cold start should take. */
export const LONG_AFTER_S = 30;
/** How long the client says the server is back before it hides the message. */
export const AWAKE_FOR_MS = 2500;

export type WakeState = 'idle' | 'waking' | 'awake';

/** One wait for the server, from a request (or a socket) to its answer. Ending it twice does nothing. */
export interface ServerWait {
  end(answered: boolean): void;
}

/**
 * The game server runs on Cloud Run with no instance kept warm, so the first request after a quiet
 * spell waits for one to start. Counts the waits in flight and says when they have lasted too long.
 */
@Injectable({ providedIn: 'root' })
export class ServerWake implements OnDestroy {
  readonly state = signal<WakeState>('idle');
  readonly waitedSeconds = signal(0);
  readonly takingLong = computed(() => this.waitedSeconds() >= LONG_AFTER_S);

  private pending = 0;
  private startedAt = 0;
  private slowTimer?: ReturnType<typeof setTimeout>;
  private ticker?: ReturnType<typeof setInterval>;
  private awakeTimer?: ReturnType<typeof setTimeout>;

  startWait(): ServerWait {
    if (this.pending++ === 0) this.startEpisode();
    let ended = false;
    return {
      end: (answered) => {
        if (ended) return;
        ended = true;
        this.endWait(answered);
      },
    };
  }

  dismiss(): void {
    this.stopTimers();
    this.state.set('idle');
  }

  ngOnDestroy(): void {
    this.stopTimers();
  }

  private startEpisode(): void {
    this.startedAt = Date.now();
    this.slowTimer = setTimeout(() => this.showWaking(), SLOW_AFTER_MS);
  }

  private showWaking(): void {
    clearTimeout(this.awakeTimer);
    this.state.set('waking');
    this.countSeconds();
    this.ticker = setInterval(() => this.countSeconds(), 1000);
  }

  private countSeconds(): void {
    this.waitedSeconds.set(Math.floor((Date.now() - this.startedAt) / 1000));
  }

  private endWait(answered: boolean): void {
    this.pending--;
    const waking = this.state() === 'waking';
    if (waking && answered) this.showAwake();
    if (this.pending > 0) return;
    clearTimeout(this.slowTimer);
    clearInterval(this.ticker);
    if (waking && !answered) this.state.set('idle');
  }

  private showAwake(): void {
    clearInterval(this.ticker);
    this.state.set('awake');
    this.awakeTimer = setTimeout(() => this.state.set('idle'), AWAKE_FOR_MS);
  }

  private stopTimers(): void {
    clearTimeout(this.slowTimer);
    clearInterval(this.ticker);
    clearTimeout(this.awakeTimer);
  }
}

/** Times every request to the game server. A gateway error or no answer at all means it is not up yet. */
export const watchServerWake: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) return next(request);
  const wait = inject(ServerWake).startWait();
  return next(request).pipe(
    tap({
      next: (event) => { if (event.type === HttpEventType.Response) wait.end(true); },
      error: (error: unknown) => wait.end(serverAnswered(error)),
    }),
    finalize(() => wait.end(false)),
  );
};

function serverAnswered(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status > 0 && error.status < 500;
}
