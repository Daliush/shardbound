import pytest

from rag.chunks import Chunk
from rag.retrievers.expansion.expansion_retriever import ExpansionRetriever
from tests.rag.retrievers.fixed_ranking import FixedRanking


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


def test_puts_the_named_cards_first_then_the_top_of_the_retriever_then_their_refs():
    expansion = ExpansionRetriever(FixedRanking(["9.11", "7.6"]), CHUNKS)

    ranked = expansion.rank_chunks("I have Ash Warden on my board. What happens if I cast Tempest?")

    assert ranked == ["ember.ash-warden", "neutral.tempest", "9.11", "7.6", "11.1.1", "8.5", "8.2", "9.8"]


def test_takes_only_the_first_top_k_chunks_of_the_retriever():
    expansion = ExpansionRetriever(FixedRanking(["7.6", "8.14", "9.3"]), CHUNKS, top_k=2)

    assert expansion.rank_chunks("What is no retaliation?") == ["7.6", "8.14"]


def test_follows_the_refs_one_level_deep():
    expansion = ExpansionRetriever(FixedRanking([]), CHUNKS)

    ranked = expansion.rank_chunks("Ash Warden")

    assert ranked == ["ember.ash-warden", "11.1.1", "8.5"]
    assert "8.14" not in ranked, "8.5's own ref"


def test_finds_a_card_whatever_its_case():
    expansion = ExpansionRetriever(FixedRanking([]), CHUNKS)

    assert expansion.rank_chunks("what does ASH WARDEN do?")[0] == "ember.ash-warden"


def test_finds_a_card_before_a_possessive():
    expansion = ExpansionRetriever(FixedRanking([]), CHUNKS)

    assert expansion.rank_chunks("What does Ash Warden's Cinder Bite do?")[0] == "ember.ash-warden"


def test_finds_no_card_inside_another_word():
    expansion = ExpansionRetriever(FixedRanking([]), [*CHUNKS, card("ember.cinder", "Cinder")])

    assert expansion.rank_chunks("My Cinderling died.") == ["ember.cinderling", "9.3"]


def test_finds_the_longer_of_two_card_names_that_start_alike():
    chunks = [*CHUNKS, card("ember.cinder", "Cinder"), card("ember.cinder-pact", "Cinder Pact")]
    expansion = ExpansionRetriever(FixedRanking([]), chunks)

    assert expansion.rank_chunks("Can I play Cinder Pact?") == ["ember.cinder-pact"]


def test_finds_no_card_through_a_letter_that_only_case_folding_reads_as_its_name():
    expansion = ExpansionRetriever(FixedRanking([]), CHUNKS)

    assert expansion.rank_chunks("What does Tempeſt do?") == []


def test_finds_no_vocabulary_term_by_its_name():
    destroy = Chunk("vocabulary.destroy", "term", "Destroy", "**Destroy**: board to graveyard.", (), "t", "h")
    expansion = ExpansionRetriever(FixedRanking([]), [*CHUNKS, destroy])

    assert expansion.rank_chunks("Can I destroy an anchored unit?") == []


def test_keeps_each_chunk_once_where_it_first_comes():
    expansion = ExpansionRetriever(FixedRanking(["neutral.tempest", "8.2", "8.5"]), CHUNKS)

    ranked = expansion.rank_chunks("Ash Warden and Tempest")

    assert ranked == ["ember.ash-warden", "neutral.tempest", "8.2", "8.5", "11.1.1", "8.14"]


def test_refuses_chunks_with_no_card_to_find():
    with pytest.raises(LookupError, match="python -m rag index"):
        ExpansionRetriever(FixedRanking([]), [rule("8.2")])
