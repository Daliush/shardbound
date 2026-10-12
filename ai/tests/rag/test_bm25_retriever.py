import pytest

from rag.chunks import Chunk
from rag.retrievers.bm25_retriever import Bm25Retriever, Bm25Settings

CHUNKS = [
    Chunk("8.2", "rule", "[8. Effects] **Destroy**: sends a unit or relic to the graveyard.", (), "r", "h"),
    Chunk("8.4", "rule", "[8. Effects] **Heal X**: restores X defense to a unit.", (), "r", "h"),
    Chunk("11.3.4", "rule", "[11.3 Anchor] A unit at 0 defense while protected is **doomed**.", (), "r", "h"),
    Chunk("7.6", "rule", "[7. Combat] **No retaliation**: the target deals nothing back.", (), "r", "h"),
]


def test_ranks_first_the_chunk_sharing_the_rarest_word_with_the_question():
    ranked = Bm25Retriever(CHUNKS).rank_chunks("Is my unit doomed?")

    assert ranked[0] == "11.3.4"


def test_leaves_out_the_chunks_sharing_no_word_with_the_question():
    assert Bm25Retriever(CHUNKS).rank_chunks("What does Heal do?") == ["8.4"]


def test_stemming_matches_destroyed_with_destroy():
    assert Bm25Retriever(CHUNKS).rank_chunks("Destroyed?") == ["8.2"]
    assert Bm25Retriever(CHUNKS, Bm25Settings(stemming=False)).rank_chunks("Destroyed?") == []


def test_keeps_the_stop_words_unless_told_to_remove_them():
    question = "Does a unit deal no retaliation?"

    assert Bm25Retriever(CHUNKS).rank_chunks(question)[0] == "7.6"
    assert "7.6" in Bm25Retriever(CHUNKS, Bm25Settings(stop_words_removed=True)).rank_chunks(question)


@pytest.mark.parametrize("formula", ["lucene", "robertson", "atire", "bm25l", "bm25+"])
def test_every_formula_ranks_the_matching_chunk_first(formula: str):
    assert Bm25Retriever(CHUNKS, Bm25Settings(formula=formula)).rank_chunks("Is my unit doomed?")[0] == "11.3.4"
