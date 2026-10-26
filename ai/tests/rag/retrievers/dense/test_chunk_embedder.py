from dataclasses import replace

import numpy as np

from rag.chunks import Chunk
from rag.retrievers.dense.chunk_embedder import embed_changed_chunks
from rag.retrievers.dense.chunk_embedding import ChunkEmbedding
from rag.retrievers.dense.embedding_models import EmbeddingModel


class KeyedModel(EmbeddingModel):
    """A model whose vectors the index keeps under a key of its own, as a model run in another format would."""

    name = "fake"

    def __init__(self, key: str) -> None:
        self.key = key

    @property
    def index_key(self) -> str:
        return self.key

    def embed_chunk(self, chunk: Chunk) -> ChunkEmbedding:
        return ChunkEmbedding(chunk, np.ones(3))

    def embed_question(self, question: str) -> np.ndarray:
        return np.ones(3)


def test_embeds_every_chunk_the_first_time_and_none_when_nothing_changed(rule_chunks, fake_model,
                                                                          embedding_repository):
    assert embed_changed_chunks(rule_chunks, fake_model, embedding_repository) == 3
    assert embed_changed_chunks(rule_chunks, fake_model, embedding_repository) == 0
    assert len(fake_model.embedded_texts) == 3


def test_embeds_again_only_the_chunk_whose_text_changed(rule_chunks, fake_model, embedding_repository):
    embed_changed_chunks(rule_chunks, fake_model, embedding_repository)
    healed = replace(rule_chunks[1], text="[8. Effects] Heal X: restores X defense to a unit or a player.", hash="h2b")

    assert embed_changed_chunks([rule_chunks[0], healed, rule_chunks[2]], fake_model, embedding_repository) == 1
    assert fake_model.embedded_texts[-1] == healed.text
    assert embedding_repository.find_embedded_hashes("fake")["8.4"] == "h2b"


def test_embeds_every_chunk_again_for_the_same_model_run_another_way(rule_chunks, embedding_repository):
    embed_changed_chunks(rule_chunks, KeyedModel("fake/bfloat16"), embedding_repository)

    assert embed_changed_chunks(rule_chunks, KeyedModel("fake/float32"), embedding_repository) == 3
    assert set(embedding_repository.find_embedded_hashes("fake/float32")) == {"11.3.2", "8.4", "2.1"}
