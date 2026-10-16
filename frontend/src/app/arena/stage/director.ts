import { signal } from '@angular/core';
import { EventView, GameView } from '../../api/protocol';
import { Beat, choreograph } from './choreographer';

export interface StagePlayer {
  capture(): unknown;
  play(beat: Beat, old: GameView, next: GameView, signal: AbortSignal): Promise<void>;
  reconcile(capture: unknown, signal: AbortSignal): Promise<void>;
  cancel(): void;
}
export class Director {
  readonly shownView = signal<GameView | null>(null);
  readonly history = signal<EventView[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  private latest: GameView | null = null;
  private latestHistory: EventView[] = [];
  private queue: { view: GameView; events: EventView[] }[] = [];
  private generation = 0;
  private abort = new AbortController();
  constructor(private readonly stage: StagePlayer) {}

  reset(view: GameView | null, history: EventView[]): void {
    this.latest = view;
    this.latestHistory = history;
    this.skip();
  }
  enqueue(view: GameView, events: EventView[]): void {
    this.latest = view;
    this.latestHistory = [...this.latestHistory, ...events];
    if (!this.shownView()) { this.skip(); return; }
    this.queue.push({ view, events });
    if (!this.busy()) void this.playQueue();
  }
  skip(): void {
    this.generation++;
    this.abort.abort();
    this.abort = new AbortController();
    this.stage.cancel();
    this.queue = [];
    this.shownView.set(this.latest);
    this.history.set(this.latestHistory);
    this.busy.set(false);
  }
  private async playQueue(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    const generation = this.generation;
    const signal = this.abort.signal;
    try {
      while (this.queue.length) {
        const next = this.queue.shift()!;
        const old = this.shownView()!;
        const capture = this.stage.capture();
        for (const beat of choreograph(old, next.events, next.view)) {
          await this.stage.play(beat, old, next.view, signal);
          if (signal.aborted || generation !== this.generation) return;
        }
        this.shownView.set(next.view);
        this.history.update(history => [...history, ...next.events]);
        await this.stage.reconcile(capture, signal);
        if (signal.aborted || generation !== this.generation) return;
      }
      this.busy.set(false);
    } catch {
      if (generation === this.generation) {
        this.error.set('Animation interrupted. The board has caught up.');
        this.skip();
      }
    }
  }
}
