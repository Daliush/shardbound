import pytest

from rag.chunks import Chunk
from rag.named_cards import NamedCardFinder


def card(chunk_id: str, name: str) -> Chunk:
    return Chunk(chunk_id, "card", name, f"{name}: a card.", (), "c", "h")


def rule(chunk_id: str) -> Chunk:
    return Chunk(chunk_id, "rule", None, f"Rule {chunk_id}.", (), "r", "h")


CHUNKS = [card("ember.ash-warden", "Ash Warden"), card("neutral.tempest", "Tempest"),
          card("ember.cinderling", "Cinderling"), rule("8.2")]


def test_finds_the_named_cards_in_the_order_the_question_names_them():
    finder = NamedCardFinder(CHUNKS)

    assert finder.find_named_cards("Can Tempest destroy my Ash Warden?") == ["neutral.tempest", "ember.ash-warden"]


def test_finds_a_card_whatever_its_case():
    assert NamedCardFinder(CHUNKS).find_named_cards("what does ASH WARDEN do?") == ["ember.ash-warden"]


def test_finds_a_card_before_a_possessive():
    assert NamedCardFinder(CHUNKS).find_named_cards("What does Ash Warden's Cinder Bite do?") == ["ember.ash-warden"]


def test_finds_no_card_inside_another_word():
    finder = NamedCardFinder([*CHUNKS, card("ember.cinder", "Cinder")])

    assert finder.find_named_cards("My Cinderling died.") == ["ember.cinderling"]


def test_finds_the_longer_of_two_card_names_that_start_alike():
    finder = NamedCardFinder([*CHUNKS, card("ember.cinder", "Cinder"), card("ember.cinder-pact", "Cinder Pact")])

    assert finder.find_named_cards("Can I play Cinder Pact?") == ["ember.cinder-pact"]


def test_finds_no_card_through_a_letter_that_only_case_folding_reads_as_its_name():
    assert NamedCardFinder(CHUNKS).find_named_cards("What does Tempeſt do?") == []


def test_finds_no_vocabulary_term_by_its_name():
    destroy = Chunk("vocabulary.destroy", "term", "Destroy", "**Destroy**: board to graveyard.", (), "t", "h")

    assert NamedCardFinder([*CHUNKS, destroy]).find_named_cards("Can I destroy an anchored unit?") == []


def test_refuses_chunks_with_no_card_to_find():
    with pytest.raises(LookupError, match="python -m rag index"):
        NamedCardFinder([rule("8.2")])
