import pytest

from rag.comparison.comparison_service import compare_evaluations, name_run


def build_run(retriever: str, rerank: str | None, recall_at_10: float, first_chunks: list[str]) -> dict:
    return {"layers": {"retriever": retriever, "expand": None, "rerank": rerank, "answer": None},
            "scores": {"overall": {"mean_recall_at": {"10": recall_at_10, "20": 0.9}}},
            "questions": [{"question_id": "rule/2.2", "top_chunk_ids": first_chunks},
                          {"question_id": "rule/4.1", "top_chunk_ids": ["4.1", "4.2"]}]}


def build_evaluation(runs: list[dict], version: str = "v2") -> dict:
    return {"dataset": {"version": version, "split": "dev"}, "runs": runs}


def test_compares_each_run_with_the_recorded_run_of_the_same_layers():
    recorded = build_evaluation([build_run("bm25", None, 0.5, ["2.2"]), build_run("bm25", "minilm-l6", 0.6, ["2.2"])])
    new = build_evaluation([build_run("bm25", "minilm-l6", 0.7, ["2.2"]), build_run("bm25", None, 0.5, ["2.2"])])

    comparison = compare_evaluations(recorded, new)

    assert [run.name for run in comparison.runs] == ["bm25 + minilm-l6", "bm25"]
    assert comparison.runs[0].recall_at_10_before == 0.6
    assert comparison.runs[0].recall_at_10_after == 0.7
    assert comparison.new_run_names == []


def test_lists_the_runs_the_recorded_evaluation_does_not_have_without_comparing_them():
    recorded = build_evaluation([build_run("bm25", None, 0.5, ["2.2"])])
    new = build_evaluation([build_run("bm25", None, 0.5, ["2.2"]), build_run("dense", None, 0.6, ["2.2"])])

    comparison = compare_evaluations(recorded, new)

    assert [run.name for run in comparison.runs] == ["bm25"]
    assert comparison.new_run_names == ["dense"]


def test_names_the_questions_whose_first_chunks_changed():
    recorded = build_evaluation([build_run("bm25", None, 0.5, ["2.2", "2.1"])])
    new = build_evaluation([build_run("bm25", None, 0.5, ["2.1", "2.2"])])

    assert compare_evaluations(recorded, new).runs[0].reranked_question_ids == ["rule/2.2"]


def test_finds_only_the_runs_that_lost_more_recall_than_allowed():
    recorded = build_evaluation([build_run("bm25", None, 0.50, ["2.2"]), build_run("dense", None, 0.60, ["2.2"])])
    new = build_evaluation([build_run("bm25", None, 0.49, ["2.2"]), build_run("dense", None, 0.55, ["2.2"])])

    drops = compare_evaluations(recorded, new).find_drops(max_lost_points=2)

    assert [run.name for run in drops] == ["dense"]
    assert drops[0].count_lost_points() == pytest.approx(5.0)


def test_refuses_to_compare_evaluations_of_different_questions():
    with pytest.raises(ValueError, match="different questions"):
        compare_evaluations(build_evaluation([], "v1"), build_evaluation([], "v2"))


def test_names_a_run_by_its_techniques_in_a_questions_order():
    assert name_run({"retriever": "rrf-bm25-dense", "expand": "refs", "rerank": "minilm-l6", "answer": None}) \
           == "rrf-bm25-dense + refs + minilm-l6"
