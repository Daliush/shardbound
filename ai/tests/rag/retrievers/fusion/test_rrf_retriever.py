from rag.retrievers.fusion.rrf_retriever import RrfRetriever
from tests.rag.retrievers.fixed_ranking import FixedRanking

QUESTION = "Can my anchored unit be destroyed?"


def test_ranks_first_a_chunk_both_retrievers_rank_well_over_one_that_only_one_ranks_first():
    lexical = FixedRanking(["11.3.2", "8.2", "11.3.1"])
    semantic = FixedRanking(["11.3.3", "11.3.2", "8.2"])

    ranked = RrfRetriever([lexical, semantic]).rank_chunks(QUESTION)

    # 11.3.2: 1/61 + 1/62; 8.2: 1/62 + 1/63; 11.3.3: 1/61; 11.3.1: 1/63
    assert ranked == ["11.3.2", "8.2", "11.3.3", "11.3.1"]


def test_scores_a_chunk_one_retriever_leaves_out_from_the_others_only():
    lexical = FixedRanking(["11.3.2"])
    semantic = FixedRanking(["11.3.3", "11.3.2", "8.2"])

    ranked = RrfRetriever([lexical, semantic]).rank_chunks(QUESTION)

    assert ranked == ["11.3.2", "11.3.3", "8.2"]


def test_a_smaller_k_lets_a_first_rank_beat_a_chunk_both_rank_fourth():
    lexical = FixedRanking(["11.3.3", "7.5", "8.1", "11.3.2"])
    semantic = FixedRanking(["9.3", "6.6", "8.4", "11.3.2"])

    # k = 60: 11.3.2 scores 2/64, 11.3.3 1/61. k = 1: 11.3.2 scores 2/5, 11.3.3 1/2.
    assert RrfRetriever([lexical, semantic]).rank_chunks(QUESTION).index("11.3.2") == 0
    assert RrfRetriever([lexical, semantic], k=1).rank_chunks(QUESTION)[:2] == ["11.3.3", "9.3"]


def test_breaks_a_tie_by_the_order_of_the_retrievers():
    ranked = RrfRetriever([FixedRanking(["8.2"]), FixedRanking(["11.3.2"])]).rank_chunks(QUESTION)

    assert ranked == ["8.2", "11.3.2"]
