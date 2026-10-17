import pytest

from rag.retrievers.dense.chunk_embedder import embed_changed_chunks
from rag.retrievers.dense.dense_retriever import DenseRetriever


def test_ranks_every_chunk_the_closest_to_the_question_first(rule_chunks, fake_model, embedding_repository):
    embed_changed_chunks(rule_chunks, fake_model, embedding_repository)

    ranked = DenseRetriever(fake_model, embedding_repository).rank_chunks("Can my anchor unit be destroyed?")

    assert ranked[0] == "11.3.2"
    assert sorted(ranked) == sorted(chunk.id for chunk in rule_chunks)


def test_describes_itself_by_its_model(rule_chunks, fake_model, embedding_repository):
    embed_changed_chunks(rule_chunks, fake_model, embedding_repository)

    assert DenseRetriever(fake_model, embedding_repository).description == (
        "dense retrieval (fake embeddings, ranked by cosine)")


def test_refuses_a_model_whose_vectors_are_not_built_yet(fake_model, embedding_repository):
    with pytest.raises(LookupError, match="python -m rag index"):
        DenseRetriever(fake_model, embedding_repository)
