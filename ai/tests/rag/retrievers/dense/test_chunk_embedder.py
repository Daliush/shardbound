from dataclasses import replace

from rag.retrievers.dense.chunk_embedder import embed_changed_chunks


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
