import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { ApiClient } from '../api/api-client';
import { Deck } from '../api/protocol';
import { SeatStore } from '../api/seat-store';
import { DeckPicker } from '../play/deck-picker';

@Component({
  selector: 'app-join-page', imports: [DeckPicker],
  template: `<section class="page"><span class="muted">An invitation to the Arena</span><h1>Your opponent awaits.</h1><p>Choose your deck and take the second seat.</p><form (submit)="$event.preventDefault(); join()"><app-deck-picker [decks]="decks()" [selected]="deck()" (chosen)="deck.set($event)" /><button class="primary" type="submit" [disabled]="!deck() || !code() || joining()">{{ joining() ? 'Taking your seat…' : 'Join' }}</button></form>@if (!code()) { <p class="error">This link has no join code.</p> }@if (error(); as message) { <p role="alert" class="error">{{ message }}</p> }</section>`,
  styles: `.page { max-width: 920px; padding-top: 4rem; } h1 { margin-top: 1rem; } p { color: var(--muted); } form { margin-top: 2rem; } form > button { min-width: 180px; margin-top: 1rem; }`,
})
export class JoinPage {
  readonly gameId = input.required<string>();
  readonly code = input<string>('');
  private readonly api = inject(ApiClient);
  private readonly seats = inject(SeatStore);
  private readonly router = inject(Router);
  protected readonly error = signal<string | null>(null);
  protected readonly joining = signal(false);
  protected readonly decks = toSignal(this.api.decks().pipe(catchError(() => { this.error.set('The decks could not be loaded. Try refreshing the page.'); return of([]); })), { initialValue: [] as Deck[] });
  protected readonly deck = linkedSignal(() => (this.decks()[1] ?? this.decks()[0])?.id ?? '');
  protected join(): void {
    if (this.joining() || !this.code() || !this.deck()) return;
    this.error.set(null); this.joining.set(true);
    this.api.joinGame(this.gameId(), this.code(), this.deck()).pipe(finalize(() => this.joining.set(false))).subscribe({
      next: access => { this.seats.saveToken(access.gameId, access.playerToken); void this.router.navigate(['/games', access.gameId]); },
      error: (failure: HttpErrorResponse) => this.error.set(failure.error?.detail ?? failure.message),
    });
  }
}

