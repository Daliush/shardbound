import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ApiClient } from '../api/api-client';
import { Card } from '../api/protocol';

/** Card names and data, loaded once: views only carry card ids (examples doc, decision 2). */
@Injectable({ providedIn: 'root' })
export class CardBook {
  private readonly cards = toSignal(inject(ApiClient).cards(), { initialValue: [] as Card[] });
  private readonly byId = computed(() => new Map(this.cards().map((card) => [card.id, card])));

  card(id: string): Card | undefined {
    return this.byId().get(id);
  }

  name(id: string): string {
    return this.card(id)?.name ?? id;
  }
}
