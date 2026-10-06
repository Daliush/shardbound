# Deck format (v0.1)

Every deck is a JSON file validated by [`deck.schema.json`](deck.schema.json). Decks are used by the engine's bots, the coach's simulations and the evaluation tournaments.

## Layout

- One file per deck: `content/decks/<id>.json`, where `id` equals the file name (`ember-starter.json` has id `ember-starter`).
- A deck lists card ids with a number of copies. Each card appears in a single entry.

```json
{
  "id": "ember-starter",
  "name": "Ember Starter",
  "description": "Optional one-line summary of the deck's plan.",
  "faction": "ember",
  "cards": [
    { "card": "ember.cinderling", "count": 2 },
    { "card": "neutral.shardling", "count": 2 }
  ]
}
```

## Deck-building rules

From the rulebook ([section 2](../../docs/rules/02-deck-building.md)):

- exactly 30 cards (2.1);
- cards of the deck's faction plus neutral cards only (2.2);
- at most 2 copies of a card (2.3);
- no tokens (2.4).

## Decks

| Deck | Faction | Plan |
|---|---|---|
| [`ember-starter`](ember-starter.json) | Ember | Front-loaded aggression: cheap attackers and burn early, sacrifice and Echo in the mid game, Cinderfall against swarms. |
| [`root-starter`](root-starter.json) | Root | Slow and sturdy: a wall of Sprouts protects the player while healing and Anchor units grind the opponent down. |

## Tests

Run from the `content/` folder with `uv run pytest`; CI runs them on every change to `content/`.

- `tests/test_deck_schema.py`: every deck matches the schema, and malformed decks are rejected.
- `tests/test_deck_rules.py`: every deck follows the deck-building rules above, its id matches its file name, and the rule checker itself catches each kind of illegal deck.
- `tests/fixtures/deck-rules.json`: decks with the exact problems a checker must report. The Python tests and the Java engine (`DeckValidatorTest`) both check them, so the two deck checkers cannot disagree.
