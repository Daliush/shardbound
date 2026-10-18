from tests.rag.retrievers.fixed_ranking import FixedRanking


def test_starts_the_pipeline_from_the_question_alone():
    assert FixedRanking(["8.2", "7.6"]).process("Can my unit attack twice?", []) == ["8.2", "7.6"]
