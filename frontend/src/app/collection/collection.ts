import { Params } from '@angular/router';
import { Card, Deck } from '../api/protocol';
import { FACTIONS } from '../core/factions';

export const GROUPINGS = [
  { id: 'faction', name: 'Faction' }, { id: 'deck', name: 'Deck' }, { id: 'cost', name: 'Cost' }, { id: 'type', name: 'Type' }, { id: 'none', name: 'None' },
] as const;
export const SORTINGS = [{ id: 'cost', name: 'Cost' }, { id: 'name', name: 'Name' }] as const;
export const FACTION_FILTERS = [...FACTIONS.map(({ id, name }) => ({ id, name })), { id: 'neutral', name: 'Neutral' }];
export const TYPES = [{ id: 'unit', name: 'Units' }, { id: 'spell', name: 'Spells' }, { id: 'relic', name: 'Relics' }];
export type Grouping = typeof GROUPINGS[number]['id'];
export type Sorting = typeof SORTINGS[number]['id'];

/** What the collection shows: the filters, then how the cards are grouped and ordered. An empty filter keeps all. */
export interface CollectionQuery { search: string; faction: string; type: string; deck: string; group: Grouping; sort: Sorting; }
/** One group of cards under its heading; in a deck, each card carries its number of copies. */
export interface Shelf { key: string; title: string; detail: string; faction: string | null; cards: ShelfCard[]; }
export interface ShelfCard { card: Card; copies: number | null; }

export const DEFAULT_QUERY: CollectionQuery = { search: '', faction: '', type: '', deck: '', group: 'faction', sort: 'cost' };
const PARAMS: Record<keyof CollectionQuery, string> = { search: 'q', faction: 'faction', type: 'type', deck: 'deck', group: 'group', sort: 'sort' };
const NEUTRAL = { id: 'neutral', name: 'Neutral', style: 'Shards that any deck can hold' };

/** What a card costs as printed: its cost, or its first Fracture step's; a token costs nothing. */
export function printedCost(card: Card): number | null {
  return card.cost ?? card.fracture?.[0]?.cost ?? null;
}

/** The query in the URL; a value it does not know falls back to the default. */
export function readQuery(params: Params): CollectionQuery {
  const text = (key: keyof CollectionQuery) => typeof params[PARAMS[key]] === 'string' ? params[PARAMS[key]] as string : '';
  const oneOf = <T extends string>(key: keyof CollectionQuery, allowed: readonly T[]): T => {
    const value = text(key);
    return allowed.includes(value as T) ? value as T : DEFAULT_QUERY[key] as T;
  };
  return {
    search: text('search'), deck: text('deck'),
    faction: oneOf('faction', FACTION_FILTERS.map(faction => faction.id)), type: oneOf('type', TYPES.map(type => type.id)),
    group: oneOf('group', GROUPINGS.map(group => group.id)), sort: oneOf('sort', SORTINGS.map(sort => sort.id)),
  };
}

/** The query as URL parameters, leaving out the defaults. */
export function writeQuery(query: CollectionQuery): Params {
  return Object.fromEntries((Object.keys(PARAMS) as (keyof CollectionQuery)[])
    .filter(key => query[key] !== DEFAULT_QUERY[key]).map(key => [PARAMS[key], query[key]]));
}

/** The cards the query keeps, grouped and ordered as it asks; a group left empty is left out. */
export function arrange(cards: Card[], decks: Deck[], query: CollectionQuery): Shelf[] {
  const deck = decks.find(each => each.id === query.deck);
  const words = fold(query.search).split(/\s+/).filter(Boolean);
  const kept = cards.filter(card => (!query.faction || card.faction === query.faction) && (!query.type || card.type === query.type)
    && (!deck || copiesIn(deck, card) > 0) && words.every(word => searchable(card).includes(word)));
  const shelf = (key: string, title: string, detail: string, faction: string | null, members: Card[], from = deck): Shelf =>
    ({ key, title, detail, faction, cards: order(members, query.sort).map(card => ({ card, copies: from ? copiesIn(from, card) : null })) });
  return group().filter(each => each.cards.length > 0);

  function group(): Shelf[] {
    switch (query.group) {
      case 'faction':
        return [...FACTIONS, NEUTRAL].map(faction => shelf(faction.id, faction.name, faction.style, faction.id, kept.filter(card => card.faction === faction.id)));
      case 'deck':
        return [
          ...decks.filter(each => !deck || each === deck)
            .map(each => shelf(each.id, each.name, each.description ?? '', each.faction, kept.filter(card => copiesIn(each, card) > 0), each)),
          shelf('none', 'In no starter deck', 'Cards no starter deck holds yet, and tokens', null, kept.filter(card => decks.every(each => copiesIn(each, card) === 0))),
        ];
      case 'cost': {
        const costs = [...new Set(kept.map(printedCost))].sort((a, b) => (a ?? Infinity) - (b ?? Infinity));
        return costs.map(cost => shelf('cost-' + cost, cost === null ? 'No cost' : cost + (cost === 1 ? ' Shard' : ' Shards'),
          cost === null ? 'Tokens, made by other cards' : '', null, kept.filter(card => printedCost(card) === cost)));
      }
      case 'type':
        return TYPES.map(type => shelf(type.id, type.name, '', null, kept.filter(card => card.type === type.id)));
      case 'none':
        return [shelf('all', '', '', null, kept)];
    }
  }
}

function copiesIn(deck: Deck, card: Card): number {
  return deck.cards.find(entry => entry.card === card.id)?.count ?? 0;
}

function order(cards: Card[], sort: Sorting): Card[] {
  const byName = (a: Card, b: Card) => a.name.localeCompare(b.name);
  const byCost = (a: Card, b: Card) => (printedCost(a) ?? Infinity) - (printedCost(b) ?? Infinity) || byName(a, b);
  return [...cards].sort(sort === 'name' ? byName : byCost);
}

/** Lower case and without accents, so that "eel" finds Mire Eel whatever the reader types. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/** A card is found by its name, type, faction, keywords and rules text. */
function searchable(card: Card): string {
  return fold([card.name, card.type, card.faction, card.token ? 'token' : '', ...card.keywords, ...card.text.map(line => line.text)].join(' '));
}
