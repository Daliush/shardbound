from rag.retrievers.random.random_retriever import RandomRetriever

CHUNK_IDS = [f"8.{n}" for n in range(1, 23)]


def test_ranks_every_chunk_in_the_same_order_for_a_question_at_every_run():
    ranked = RandomRetriever(CHUNK_IDS).rank_chunks("What does Destroy do?")

    assert sorted(ranked) == sorted(CHUNK_IDS)
    assert ranked == RandomRetriever(CHUNK_IDS).rank_chunks("What does Destroy do?")


def test_ranks_another_question_in_another_order():
    retriever = RandomRetriever(CHUNK_IDS)

    assert retriever.rank_chunks("What does Destroy do?") != retriever.rank_chunks("What does Heal do?")


def test_describes_itself_by_its_seed():
    assert RandomRetriever(CHUNK_IDS, seed=7).description == "every chunk in a random order (seed 7)"
