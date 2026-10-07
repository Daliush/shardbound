---
name: shardbound-content-sync
description: The game content is checked twice — by the Python content tests (content/tests/) and by the Java engine (engine/core content loader and DeckValidator) — so some changes must land on both sides at once. Use this whenever you change a deck-building rule (rulebook section 2: deck size, copies per card, factions, tokens), the deck file format or deck.schema.json, the card file format or card.schema.json, the closed lists of effects, targets, triggers or keywords, the text templates, or the messages of a content check — even a single number such as the deck size. It lists every file to update on each side and in the docs, so neither suite drifts from the other.
---

# Keeping the two content checks in sync

## Why there are two

- **The Python content tests** (`content/tests/`, `uv run pytest` in `content/`) check the files of `content/` in their own CI. They need no JVM, and they will also serve the Python dataset tooling later.
- **The Java engine** (`engine/core`, package `content`) loads the same files when it starts and refuses illegal decks when a game starts. Later, decks will come from players through the deck builder, so the engine cannot trust the files or call Python.

Both must agree exactly, messages included: the API returns the engine's messages to players, and they cite rule IDs. A change made on one side only leaves the two disagreeing about what a legal deck or a valid card is.

## What to change, for each kind of change

### A deck-building rule (rulebook section 2)

| Side | Files |
|---|---|
| Rulebook | `docs/rules/02-deck-building.md`, the summary in `docs/design.md` §2.1, and `12-open-points.md`. The maintainer decides (see `shardbound-rules`). |
| Shared cases | `content/tests/fixtures/deck-rules.json`: decks with the exact problems both checkers must report. Change it first: both suites check it, so a side left behind fails its CI. |
| Python | `content/tests/contentlib.py` (`deck_problems`, `DECK_SIZE`, `MAX_COPIES`) and `content/tests/test_deck_rules.py` |
| Java | `engine/core/.../content/DeckValidator.java` (same constants, same message strings) and `DeckValidatorTest.java` |
| Docs | the "Deck-building rules" list in `content/decks/README.md`, and the deck constraints in the `shardbound-card-authoring` skill |
| Decks | the starter decks in `content/decks/` must stay legal under the new rule; both suites check them |

### The deck file format

| Side | Files |
|---|---|
| Python | `content/decks/deck.schema.json`, `content/tests/test_deck_schema.py` |
| Java | `engine/core/.../content/json/DeckParser.java`, the `Deck` / `DeckEntry` records, `ContentLoaderTest` |
| Docs | `content/decks/README.md`; spec §4 if the model changes |

### The card file format, or a closed list (effects, targets, triggers, keywords)

| Side | Files |
|---|---|
| Rulebook | sections 8, 9, 10 or 11 for a closed-list change (a game design decision, see `shardbound-rules`) |
| Python | `content/cards/card.schema.json`, `content/cards/text-templates.json`, `test_card_schema.py`, `test_card_rules.py`, `test_text_templates.py` |
| Java | the model records in `engine/core/.../content/` (`Effect`, `TargetSpec`, `Trigger`, `Keyword`…), `content/json/CardParser.java` and `EffectParser.java`, and `ContentLoader.checkReferences` for cross-references. The engine also has to implement the new element: see `shardbound-engine-dev`, recipes 1 to 3. |
| Docs | `content/cards/README.md`, and the closed lists in the `shardbound-card-authoring` skill |

### The text templates

Python checks that every effect, target, trigger and keyword has wording (`test_text_templates.py`). The engine loads the same file strictly (`ContentLoader` into `content.TextTemplates`, by dotted key such as `effects.heal.player`) and writes card text with it (`text.CardTextRenderer`). On the Java side:

- `CardTextRendererTest` has one case per template key, and fails when a key of the file has no case: a new key needs a case there, and a new effect, target, trigger or keyword needs its rendering in `CardTextRenderer`;
- its golden test reads the two examples of `content/cards/README.md` itself, so changing their wording there or in the templates changes what the test expects.

## Checks that exist only in Python, on purpose

The Python linter (`test_card_rules.py`) also checks these:

- `id` matches the file path;
- card names are unique;
- Link appears only on neutral spells, and Recall only on Ember cards;
- `self` is not used in spells;
- there are at most two Fracture 5 cards.

These are rules for writing cards. The engine does not need them to run a game, so it does not repeat them. If one starts to matter at runtime, for example cards created by players, port it to the engine and add it to this list's Java side.

The engine, for its part, checks only what it needs to run safely: strict structure (unknown fields, wrong types, missing fields), `attack_target` only inside attacks, and that cross-references exist (deck cards, summoned tokens).

## How to make the change

1. Get the rule decided and written first (`docs(rules)` commit), unless it is a format-only change.
2. Change both sides **in the same commit**, tests included, so neither CI goes red between two commits. Use the type of the main change: `feat(content)` or `feat(decks)`.
3. Run both suites:
   ```bash
   cd content && uv run pytest
   cd engine && ./mvnw verify
   ```
4. For deck rules, the shared cases do the comparison: both suites assert the same problem strings. For format changes there is no shared file yet, so compare by hand: a file the schema rejects must also be rejected by the Java parser.
