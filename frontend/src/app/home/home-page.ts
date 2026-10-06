import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { ApiClient } from '../api/api-client';
import { Deck, Opponent } from '../api/protocol';
import { SeatStore, inviteLink } from '../api/seat-store';

/** Choose a deck and an opponent, then create the game. */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  templateUrl: './home-page.html',
  styleUrl: '../pages.css',
})
export class HomePage {
  private readonly api = inject(ApiClient);
  private readonly seats = inject(SeatStore);
  private readonly router = inject(Router);

  protected readonly decks = toSignal(this.api.decks(), { initialValue: [] as Deck[] });
  protected readonly bots = toSignal(this.api.bots(), { initialValue: [] as string[] });

  protected readonly deck = linkedSignal(() => this.decks()[0]?.id ?? '');
  protected readonly opponent = signal<'bot' | 'human'>('bot');
  protected readonly bot = linkedSignal(() => this.bots()[0] ?? '');
  protected readonly botDeck = linkedSignal(() => (this.decks()[1] ?? this.decks()[0])?.id ?? '');

  protected readonly invitation = signal<{ gameId: string; link: string } | null>(null);
  protected readonly error = signal<string | null>(null);

  protected create(): void {
    const opponent: Opponent =
      this.opponent() === 'bot' ? { type: 'bot', bot: this.bot(), deck: this.botDeck() } : { type: 'human' };
    this.error.set(null);
    this.api.createGame(this.deck(), opponent).subscribe({
      next: (access) => {
        this.seats.saveToken(access.gameId, access.playerToken);
        if (access.joinCode === undefined) {
          this.router.navigate(['/games', access.gameId]);
          return;
        }
        this.seats.saveJoinCode(access.gameId, access.joinCode);
        this.invitation.set({ gameId: access.gameId, link: inviteLink(access.gameId, access.joinCode) });
      },
      error: (failure: HttpErrorResponse) => this.error.set(failure.error?.detail ?? failure.message),
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }
}
