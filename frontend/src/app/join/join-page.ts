import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { ApiClient } from '../api/api-client';
import { Deck } from '../api/protocol';
import { SeatStore } from '../api/seat-store';

/** The invite link's page: choose a deck and take the second seat. */
@Component({
  selector: 'app-join-page',
  templateUrl: './join-page.html',
  styleUrl: '../pages.css',
})
export class JoinPage {
  readonly gameId = input.required<string>();
  readonly code = input<string>('');

  private readonly api = inject(ApiClient);
  private readonly seats = inject(SeatStore);
  private readonly router = inject(Router);

  protected readonly decks = toSignal(this.api.decks(), { initialValue: [] as Deck[] });
  protected readonly deck = linkedSignal(() => (this.decks()[1] ?? this.decks()[0])?.id ?? '');
  protected readonly error = signal<string | null>(null);

  protected join(): void {
    this.error.set(null);
    this.api.joinGame(this.gameId(), this.code(), this.deck()).subscribe({
      next: (access) => {
        this.seats.saveToken(access.gameId, access.playerToken);
        this.router.navigate(['/games', access.gameId]);
      },
      error: (failure: HttpErrorResponse) => this.error.set(failure.error?.detail ?? failure.message),
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }
}
