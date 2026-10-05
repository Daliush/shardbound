"""Helpers shared by the content tests: where the card files are, and how to read them."""

import json
from pathlib import Path

CONTENT_DIR = Path(__file__).resolve().parents[1]
CARDS_DIR = CONTENT_DIR / "cards"
CARD_FILES = sorted(CARDS_DIR.glob("*/*.json"))


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
