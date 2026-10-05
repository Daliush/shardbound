"""Deck-building rules (rulebook section 2): every real deck is legal, and the checker catches each kind of illegal deck.

The illegal decks are built from synthetic cards, so these tests do not depend on the real card set.
"""

import pytest

from contentlib import DECK_FILES, DECKS_DIR, deck_problems


def synthetic_card(card_id, **extra):
    return {"id": card_id, "faction": card_id.split(".")[0], **extra}


# The legal deck uses 14 Ember cards + 1 neutral card at 2 copies each. The other cards exist to break one rule at a time.
DECK_CARD_IDS = [f"ember.card-{i:02}" for i in range(14)] + ["neutral.utility"]
CARDS = {card_id: synthetic_card(card_id) for card_id in DECK_CARD_IDS}
CARDS["neutral.spare"] = synthetic_card("neutral.spare")
CARDS["root.outsider"] = synthetic_card("root.outsider")
CARDS["neutral.token"] = synthetic_card("neutral.token", token=True)

LEGAL = {
    "id": "legal",
    "name": "Legal",
    "faction": "ember",
    "cards": [{"card": card_id, "count": 2} for card_id in DECK_CARD_IDS],
}


def variant(change):
    deck = {**LEGAL, "cards": [dict(entry) for entry in LEGAL["cards"]]}
    change(deck)
    return deck


def swap_first(deck, card_id):
    deck["cards"][0]["card"] = card_id


def third_copy_in_one_entry(deck):
    # 3 copies of the first card, 1 of the second: still 30 cards in total.
    deck["cards"][0]["count"] = 3
    deck["cards"][1]["count"] = 1


def third_copy_in_a_second_entry(deck):
    # The second card listed twice (2 + 1 copies), the first card down to 1: still 30 cards in total.
    deck["cards"][0]["count"] = 1
    deck["cards"].append({"card": deck["cards"][1]["card"], "count": 1})


# Each illegal deck breaks exactly one rule; the checker must report it with that rule's ID.
ILLEGAL = {
    "29 cards": (variant(lambda d: d["cards"][0].update(count=1)), "2.1"),
    "31 cards": (variant(lambda d: d["cards"].append({"card": "neutral.spare", "count": 1})), "2.1"),
    "card from another faction": (variant(lambda d: swap_first(d, "root.outsider")), "2.2"),
    "three copies in one entry": (variant(third_copy_in_one_entry), "2.3"),
    "three copies across two entries": (variant(third_copy_in_a_second_entry), "2.3"),
    "token in the deck": (variant(lambda d: swap_first(d, "neutral.token")), "2.4"),
}


def test_deck_files_exist():
    # Guards against a moved folder silently turning every per-deck test into zero tests.
    assert DECK_FILES, f"no deck files found under {DECKS_DIR}"


def test_deck_id_matches_file_name(deck_path, deck):
    assert deck["id"] == deck_path.stem


def test_real_deck_is_legal(deck, all_cards):
    assert deck_problems(deck, all_cards) == []


def test_checker_accepts_a_legal_deck():
    assert deck_problems(LEGAL, CARDS) == []


@pytest.mark.parametrize("name", ILLEGAL)
def test_checker_reports_illegal_deck(name):
    deck, rule = ILLEGAL[name]
    problems = deck_problems(deck, CARDS)
    assert problems and all(f"({rule})" in problem for problem in problems), problems


def test_checker_reports_unknown_card():
    deck = variant(lambda d: swap_first(d, "ember.does-not-exist"))
    assert any("does not exist" in problem for problem in deck_problems(deck, CARDS))
