import { Card, Deck } from '../api/protocol';
import { DEFAULT_QUERY, arrange, readQuery, writeQuery } from './collection';

function card(id: string, name: string, changes: Partial<Card> = {}): Card {
  return { id, name, faction: id.split('.')[0], type: 'unit', cost: 2, token: false, keywords: [], text: [], ...changes };
}

const CARDS = [
  card('ember.ash-warden', 'Ash Warden', { cost: 3, text: [{ kind: 'attack', text: 'Cinder Bite (2 Shards): Deal 4 damage to the target.' }] }),
  card('ember.spark-dart', 'Spark Dart', { type: 'spell', cost: 1 }),
  card('tide.moonpull', 'Moonpull', { type: 'spell', cost: undefined, fracture: [{ step: 1, cost: 1 }, { step: 2, cost: 2 }], text: [{ kind: 'effect', text: 'Freeze an enemy unit.' }] }),
  card('neutral.shardling', 'Shardling', { cost: 1 }),
  card('root.sprout', 'Sprout', { cost: undefined, token: true }),
];
const DECKS: Deck[] = [
  { id: 'ember-starter', name: 'Ember Starter', faction: 'ember', cards: [{ card: 'ember.ash-warden', count: 2 }, { card: 'neutral.shardling', count: 1 }] },
  { id: 'tide-starter', name: 'Tide Starter', faction: 'tide', cards: [{ card: 'tide.moonpull', count: 2 }, { card: 'neutral.shardling', count: 2 }] },
];
const names = (shelves: ReturnType<typeof arrange>) => shelves.map(shelf => shelf.title + ': ' + shelf.cards.map(entry => entry.card.name + (entry.copies ? ' ×' + entry.copies : '')).join(', '));

describe('the collection', () => {
  it('groups by faction, cheapest first, and leaves out an empty faction', () => {
    expect(names(arrange(CARDS, DECKS, DEFAULT_QUERY))).toEqual([
      'Ember: Spark Dart, Ash Warden', 'Tide: Moonpull', 'Root: Sprout', 'Neutral: Shardling',
    ]);
  });

  it('groups by deck with each card’s copies, a card in two decks in both, and the cards no deck holds apart', () => {
    expect(names(arrange(CARDS, DECKS, { ...DEFAULT_QUERY, group: 'deck' }))).toEqual([
      'Ember Starter: Shardling ×1, Ash Warden ×2', 'Tide Starter: Moonpull ×2, Shardling ×2', 'In no starter deck: Spark Dart, Sprout',
    ]);
  });

  it('groups by printed cost, a Fracture spell at its first step and a token at no cost', () => {
    expect(names(arrange(CARDS, DECKS, { ...DEFAULT_QUERY, group: 'cost', sort: 'name' }))).toEqual([
      '1 Shard: Moonpull, Shardling, Spark Dart', '3 Shards: Ash Warden', 'No cost: Sprout',
    ]);
  });

  it('finds a card by its name or its effect, whatever the case and accents, and filters by faction, type and deck', () => {
    const titles = (query: Partial<typeof DEFAULT_QUERY>) => arrange(CARDS, DECKS, { ...DEFAULT_QUERY, group: 'none', ...query })[0]?.cards.map(entry => entry.card.name) ?? [];
    expect(titles({ search: 'FRÉEZE' })).toEqual(['Moonpull']);
    expect(titles({ search: 'cinder warden' })).toEqual(['Ash Warden']);
    expect(titles({ type: 'spell', faction: 'ember' })).toEqual(['Spark Dart']);
    expect(titles({ deck: 'tide-starter' })).toEqual(['Moonpull', 'Shardling']);
    expect(titles({ search: 'nothing like this' })).toEqual([]);
  });

  it('keeps the view in the URL without its defaults, and ignores values it does not know', () => {
    const query = { ...DEFAULT_QUERY, search: 'eel', faction: 'tide', group: 'deck' as const };
    expect(writeQuery(query)).toEqual({ q: 'eel', faction: 'tide', group: 'deck' });
    expect(readQuery(writeQuery(query))).toEqual(query);
    expect(readQuery({ faction: 'void', group: 'rarity', sort: 'power' })).toEqual(DEFAULT_QUERY);
  });
});
