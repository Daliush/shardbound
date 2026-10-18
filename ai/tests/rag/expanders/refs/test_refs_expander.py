import pytest

from rag.chunks import Chunk
from rag.expanders.refs.refs_expander import RefsExpander


def card(chunk_id: str, name: str, refs: tuple[str, ...] = ()) -> Chunk:
    return Chunk(chunk_id, "card", name, f"{name}: a card.", refs, "c", "h")


def rule(chunk_id: str, refs: tuple[str, ...] = ()) -> Chunk:
    return Chunk(chunk_id, "rule", None, f"Rule {chunk_id}.", refs, "r", "h")


CHUNKS = [
    card("ember.ash-warden", "Ash Warden", ("11.1.1", "8.5")),
    card("neutral.tempest", "Tempest", ("8.2",)),
    card("ember.cinderling", "Cinderling", ("9.3",)),
    rule("11.1.1"), rule("8.5", ("8.14",)), rule("8.2"), rule("8.14"), rule("9.3"), rule("9.11", ("9.8",)),
    rule("9.8"), rule("7.6"),
]


def test_puts_the_named_cards_first_then_the_chunks_it_is_handed_then_their_refs():
    expander = RefsExpander("refs", CHUNKS)

    expanded = expander.expand_chunks("I have Ash Warden on my board. What happens if I cast Tempest?", ["9.11", "7.6"])

    assert expanded == ["ember.ash-warden", "neutral.tempest", "9.11", "7.6", "11.1.1", "8.5", "8.2", "9.8"]


def test_follows_the_refs_one_level_deep():
    expanded = RefsExpander("refs", CHUNKS).expand_chunks("Ash Warden", [])

    assert expanded == ["ember.ash-warden", "11.1.1", "8.5"]
    assert "8.14" not in expanded, "8.5's own ref"


def test_finds_a_card_whatever_its_case():
    assert RefsExpander("refs", CHUNKS).expand_chunks("what does ASH WARDEN do?", [])[0] == "ember.ash-warden"


def test_finds_a_card_before_a_possessive():
    expanded = RefsExpander("refs", CHUNKS).expand_chunks("What does Ash Warden's Cinder Bite do?", [])

    assert expanded[0] == "ember.ash-warden"


def test_finds_no_card_inside_another_word():
    expander = RefsExpander("refs", [*CHUNKS, card("ember.cinder", "Cinder")])

    assert expander.expand_chunks("My Cinderling died.", []) == ["ember.cinderling", "9.3"]


def test_finds_the_longer_of_two_card_names_that_start_alike():
    expander = RefsExpander("refs", [*CHUNKS, card("ember.cinder", "Cinder"), card("ember.cinder-pact", "Cinder Pact")])

    assert expander.expand_chunks("Can I play Cinder Pact?", []) == ["ember.cinder-pact"]


def test_finds_no_card_through_a_letter_that_only_case_folding_reads_as_its_name():
    assert RefsExpander("refs", CHUNKS).expand_chunks("What does Tempeſt do?", []) == []


def test_finds_no_vocabulary_term_by_its_name():
    destroy = Chunk("vocabulary.destroy", "term", "Destroy", "**Destroy**: board to graveyard.", (), "t", "h")

    assert RefsExpander("refs", [*CHUNKS, destroy]).expand_chunks("Can I destroy an anchored unit?", []) == []


def test_keeps_each_chunk_once_where_it_first_comes():
    expanded = RefsExpander("refs", CHUNKS).expand_chunks("Ash Warden and Tempest", ["neutral.tempest", "8.2", "8.5"])

    assert expanded == ["ember.ash-warden", "neutral.tempest", "8.2", "8.5", "11.1.1", "8.14"]


def test_describes_itself():
    assert RefsExpander("refs", CHUNKS).description == (
        "expanded with the cards the question names and the refs of all of them, one level deep")


def test_refuses_chunks_with_no_card_to_find():
    with pytest.raises(LookupError, match="python -m rag index"):
        RefsExpander("refs", [rule("8.2")])
