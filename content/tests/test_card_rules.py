"""Card rules the JSON Schema cannot express (listed in content/cards/README.md)."""

from cardlib import CARD_FILES, CARDS_DIR, all_effects


def test_card_files_exist():
    # Guards against a moved folder silently turning every per-card test into zero tests.
    assert CARD_FILES, f"no card files found under {CARDS_DIR}"


def test_id_matches_file_path(card_path, card):
    assert card["id"] == f"{card_path.parent.name}.{card_path.stem}"


def test_faction_matches_folder(card_path, card):
    assert card["faction"] == card_path.parent.name


def test_card_names_are_unique(all_cards):
    names = [card["name"] for card in all_cards.values()]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    assert not duplicates, f"duplicate card names: {duplicates}"


def test_link_only_on_neutral_spells(card):
    # Rule 8.11
    if any(effect["effect"] == "link" for effect in all_effects(card)):
        assert card["faction"] == "neutral" and card["type"] == "spell"


def test_recall_only_on_ember_cards(card):
    # Rule 8.13
    if any(effect["effect"] == "recall" for effect in all_effects(card)):
        assert card["faction"] == "ember"


def test_summon_refers_to_an_existing_token(card, all_cards):
    for effect in all_effects(card):
        if effect["effect"] == "summon":
            token = all_cards.get(effect["token"])
            assert token is not None, f"{effect['token']} does not exist"
            assert token.get("token") is True, f"{effect['token']} is not a token"


def test_self_target_not_used_on_spells(card):
    if card["type"] == "spell":
        assert all(effect.get("target") != "self" for effect in all_effects(card))


def test_at_most_two_fracture_5_cards(all_cards):
    fracture_5 = sorted(card_id for card_id, card in all_cards.items() if len(card.get("fracture", [])) == 5)
    assert len(fracture_5) <= 2, f"too many Fracture 5 cards: {fracture_5}"
