import { ChangeDetectorRef, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiClient } from '../api/api-client';
import { Deck } from '../api/protocol';
import { CardComponent } from '../cards/card';
import { Inspect } from '../cards/inspect';
import { Ambience } from '../core/ambience';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { CardBook } from '../game/card-book';
import { CollectionQuery, FACTION_FILTERS, GROUPINGS, SORTINGS, TYPES, arrange, readQuery, writeQuery } from './collection';

/**
 * Every card of the game, found by name or effect, filtered by faction, type and deck, and grouped by faction, deck,
 * cost or type. The choice lives in the URL, so a view can be shared. A card opens in the 3D showcase.
 */
@Component({
  selector: 'app-collection-page',
  imports: [CardComponent, Icon],
  templateUrl: './collection-page.html',
  styleUrls: ['./collection-page.css', './collection-shelves.css'],
  host: { '[attr.data-faction]': 'query().faction || "neutral"' },
})
export class CollectionPage {
  protected readonly book = inject(CardBook);
  protected readonly inspect = inject(Inspect);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly settings = inject(Settings);
  private readonly ambience = inject(Ambience);
  private readonly changes = inject(ChangeDetectorRef);
  protected readonly factions = FACTION_FILTERS;
  protected readonly types = TYPES;
  protected readonly groupings = GROUPINGS;
  protected readonly sortings = SORTINGS;
  protected readonly decks = toSignal(inject(ApiClient).decks().pipe(catchError(() => of([] as Deck[]))), { initialValue: [] as Deck[] });
  protected readonly query = signal<CollectionQuery>(readQuery(this.route.snapshot.queryParams));
  protected readonly shelves = computed(() => arrange(this.book.cards(), this.decks(), this.query()));
  protected readonly shown = computed(() => new Set(this.shelves().flatMap(shelf => shelf.cards.map(entry => entry.card.id))).size);
  protected readonly filtered = computed(() => ['search', 'faction', 'type', 'deck'].some(key => this.query()[key as keyof CollectionQuery] !== ''));
  /** A card shows once in every grouping but by deck, so only then can it slide from its old place to its new one. */
  protected readonly slides = computed(() => this.query().group !== 'deck');

  constructor() {
    effect(() => {
      const queryParams = writeQuery(this.query());
      untracked(() => this.router.navigate([], { relativeTo: this.route, queryParams, replaceUrl: true }));
    });
    effect(() => this.ambience.faction.set(this.query().faction || 'neutral'));
    inject(DestroyRef).onDestroy(() => this.ambience.faction.set('neutral'));
  }

  /** Changes the view; the cards slide to their new places, unless the reader asked for less motion or is typing. */
  protected update(changes: Partial<CollectionQuery>, slide = true): void {
    const apply = () => { this.query.update(query => ({ ...query, ...changes })); this.changes.detectChanges(); };
    const transitions = document as Document & { startViewTransition?: (update: () => void) => unknown };
    if (!slide || this.settings.reducedMotion() || !transitions.startViewTransition) apply();
    else transitions.startViewTransition(apply);
  }
  protected toggle(key: 'faction' | 'type', value: string): void { this.update({ [key]: this.query()[key] === value ? '' : value }); }
  protected clear(): void { this.update({ search: '', faction: '', type: '', deck: '' }); }
  protected value(event: Event): string { return (event.target as HTMLInputElement | HTMLSelectElement).value; }
  protected transitionName(cardId: string): string | null { return this.slides() ? 'card-' + cardId.replace(/\W/g, '-') : null; }
  protected copies(count: number): string { return count === 1 ? '1 copy' : count + ' copies'; }
}
