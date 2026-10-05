"""Helpers shared by the content tests: where the card and deck files are, how to read them, and the deck rules."""

import json
from collections import Counter
from pathlib import Path

CONTENT_DIR = Path(__file__).resolve().parents[1]
CARDS_DIR = CONTENT_DIR / "cards"
DECKS_DIR = CONTENT_DIR / "decks"
CARD_FILES = sorted(CARDS_DIR.glob("*/*.json"))
DECK_FILES = sorted(p for p in DECKS_DIR.glob("*.json") if not p.name.endswith(".schema.json"))

DECK_SIZE = 30  # rule 2.1
MAX_COPIES = 2  # rule 2.3


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def all_effects(card):
    """Every effect of a card, wherever it sits: spell effects, Fracture steps, attacks, abilities."""
    yield from card.get("effects", [])
    for step in card.get("fracture", []):
        yield from step["effects"]
    for attack in card.get("attacks", []):
        yield from attack["effects"]
    for ability in card.get("abilities", []):
        yield from ability["effects"]


def deck_problems(deck, cards):
    """Every way `deck` breaks the deck-building rules (rulebook section 2). Empty if the deck is legal.

    `cards` maps card ids to card data.
    """
    problems = []

    total = sum(entry["count"] for entry in deck["cards"])
    if total != DECK_SIZE:
        problems.append(f"has {total} cards, needs exactly {DECK_SIZE} (2.1)")

    listed = Counter(entry["card"] for entry in deck["cards"])
    for card_id, times in listed.items():
        if times > 1:
            problems.append(f"lists {card_id} {times} times; use one entry with a count (2.3)")

    copies = Counter()
    for entry in deck["cards"]:
        copies[entry["card"]] += entry["count"]
    for card_id, count in copies.items():
        card = cards.get(card_id)
        if card is None:
            problems.append(f"contains {card_id}, which does not exist")
            continue
        if count > MAX_COPIES:
            problems.append(f"has {count} copies of {card_id}, max {MAX_COPIES} (2.3)")
        if card.get("token"):
            problems.append(f"contains the token {card_id}; tokens are never in a deck (2.4)")
        if card["faction"] not in (deck["faction"], "neutral"):
            problems.append(f"contains {card_id}, a {card['faction']} card, in a deck of faction {deck['faction']} (2.2)")

    return problems
