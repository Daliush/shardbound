import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Card, Deck, Opponent, SeatAccess } from './protocol';

/** The REST resources of the game server (spec §12). Cards are loaded once and cached. */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private cards$?: Observable<Card[]>;

  cards(): Observable<Card[]> {
    this.cards$ ??= this.http.get<Card[]>('/api/cards').pipe(shareReplay(1));
    return this.cards$;
  }

  decks(): Observable<Deck[]> {
    return this.http.get<Deck[]>('/api/decks');
  }

  bots(): Observable<string[]> {
    return this.http.get<string[]>('/api/bots');
  }

  createGame(deck: string, opponent: Opponent): Observable<SeatAccess> {
    return this.http.post<SeatAccess>('/api/games', { deck, opponent });
  }

  joinGame(gameId: string, joinCode: string, deck: string): Observable<SeatAccess> {
    return this.http.post<SeatAccess>(`/api/games/${gameId}/join`, { joinCode, deck });
  }
}
