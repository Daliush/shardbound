import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Ambience } from '../core/ambience';
import { catchError, of, finalize } from 'rxjs';
import { ApiClient } from '../api/api-client';
import { Deck, Opponent } from '../api/protocol';
import { SeatStore, inviteLink } from '../api/seat-store';
import { DeckPicker } from './deck-picker';

@Component({
  selector: 'app-play-page',
  imports: [RouterLink, DeckPicker],
  templateUrl: './play-page.html',
  styleUrl: './play-page.css',
  host: { '[attr.data-faction]': 'faction()' },
})
export class PlayPage {
  private readonly api = inject(ApiClient);
  private readonly seats = inject(SeatStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly ambience = inject(Ambience);
  protected readonly error = signal<string | null>(null);
  protected readonly decks = toSignal(this.api.decks().pipe(catchError(() => { this.error.set('The decks could not be loaded. Try refreshing the page.'); return of([]); })), { initialValue: [] as Deck[] });
  protected readonly bots = toSignal(this.api.bots().pipe(catchError(() => { this.error.set('The opponents could not be loaded. Try refreshing the page.'); return of([]); })), { initialValue: [] as string[] });
  protected readonly deck = linkedSignal(() => {
    const requested = this.query().get('deck');
    const faction = this.query().get('faction');
    return (this.decks().find(deck => deck.id === requested) ?? this.decks().find(deck => deck.faction === faction) ?? this.decks()[0])?.id ?? '';
  });
  protected readonly faction = computed(() => this.decks().find(deck => deck.id === this.deck())?.faction ?? 'neutral');
  protected readonly opponent = signal<'bot' | 'human'>('bot');
  protected readonly bot = linkedSignal(() => this.bots()[0] ?? '');
  protected readonly botDeck = linkedSignal(() => (this.decks()[1] ?? this.decks()[0])?.id ?? '');
  protected readonly invitation = signal<{ gameId: string; link: string } | null>(null);
  protected readonly qr = signal('');
  protected readonly copied = signal(false);
  protected readonly creating = signal(false);
  constructor() {
    effect(() => this.ambience.faction.set(this.faction()));
    inject(DestroyRef).onDestroy(() => this.ambience.faction.set('neutral'));
  }
  protected create(): void {
    if (this.creating() || !this.deck()) return;
    const opponent: Opponent = this.opponent() === 'bot' ? { type: 'bot', bot: this.bot(), deck: this.botDeck() } : { type: 'human' };
    this.error.set(null); this.creating.set(true);
    this.api.createGame(this.deck(), opponent).pipe(finalize(() => this.creating.set(false))).subscribe({
      next: access => {
        this.seats.saveToken(access.gameId, access.playerToken);
        if (access.joinCode === undefined) { void this.router.navigate(['/games', access.gameId]); return; }
        this.seats.saveJoinCode(access.gameId, access.joinCode);
        const link = inviteLink(access.gameId, access.joinCode);
        this.invitation.set({ gameId: access.gameId, link });
        void import('qrcode').then(qr => qr.toDataURL(link, { width: 180, margin: 2 })).then(url => this.qr.set(url)).catch(() => this.qr.set(''));
      },
      error: (failure: HttpErrorResponse) => this.error.set(failure.error?.detail ?? failure.message),
    });
  }
  protected async copy(): Promise<void> {
    try { await navigator.clipboard.writeText(this.invitation()!.link); this.copied.set(true); }
    catch { this.error.set('Copy is unavailable. Select and copy the invite link above.'); }
  }
  protected value(event: Event): string { return (event.target as HTMLSelectElement).value; }
}

