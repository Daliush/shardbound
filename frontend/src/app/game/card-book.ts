import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { ApiClient } from '../api/api-client';
import { Card, TextLine } from '../api/protocol';

/** Card names and data, loaded once: views only carry card ids (examples doc, decision 2). */
@Injectable({ providedIn: 'root' })
export class CardBook {
  readonly error = signal<string | null>(null);
  readonly cards = toSignal(inject(ApiClient).cards().pipe(catchError(() => {
    this.error.set('Cards could not be loaded. Check the connection and reload.');
    return of([] as Card[]);
  })), { initialValue: [] as Card[] });
  private readonly byId = computed(() => new Map(this.cards().map((card) => [card.id, card])));
  readonly loaded = computed(() => this.cards().length > 0);

  card(id: string): Card | undefined {
    return this.byId().get(id);
  }

  name(id: string): string {
    return this.card(id)?.name ?? id;
  }

  /** The card's rules text, line by line, each line with its kind. */
  text(id: string): TextLine[] {
    return this.card(id)?.text ?? [];
  }
}
