import argparse
import json
from pathlib import Path

import pytest

from rag.__main__ import choose_techniques, main


@pytest.mark.parametrize("arguments", [["evaluate"], ["evaluate", "--description", "   "]])
def test_refuses_to_evaluate_without_saying_why(arguments: list[str]):
    with pytest.raises(SystemExit) as stopped:
        main(arguments)

    assert stopped.value.code == 2


def test_refuses_a_reranker_it_does_not_know():
    with pytest.raises(SystemExit) as stopped:
        main(["evaluate", "--rerankers", "jev", "--description", "Why"])

    assert stopped.value.code == 2


def test_refuses_to_name_techniques_beside_the_baseline():
    with pytest.raises(SystemExit) as stopped:
        main(["evaluate", "--baseline", "bm25", "--description", "Why"])

    assert stopped.value.code == 2


def test_evaluates_only_the_baseline_s_techniques_with_baseline():
    arguments = argparse.Namespace(baseline=True, retrievers=[], expanders=None, rerankers=None, contexts=None)

    assert choose_techniques(arguments) == (["rrf-bm25-dense"], ["refs"], ["minilm-l6"],
                                            ["top10-plus-named-cards-refs"])


def test_evaluates_every_context_by_default_and_none_with_the_flag_alone():
    by_default = argparse.Namespace(baseline=False, retrievers=["bm25"], expanders=[], rerankers=[], contexts=None)
    flag_alone = argparse.Namespace(baseline=False, retrievers=["bm25"], expanders=[], rerankers=[], contexts=[])

    assert choose_techniques(by_default)[3] == ["top10-plus-named-cards", "top10-plus-named-cards-refs"]
    assert choose_techniques(flag_alone)[3] == []


def test_refuses_to_name_a_context_beside_the_baseline():
    with pytest.raises(SystemExit) as stopped:
        main(["evaluate", "--baseline", "--contexts", "top10-plus-named-cards", "--description", "Why"])

    assert stopped.value.code == 2


def write_evaluation(folder: Path, name: str, recall_at_10: float) -> Path:
    run = {"layers": {"retriever": "bm25", "expand": None, "rerank": None, "answer": None},
           "scores": {"overall": {"mean_recall_at": {"10": recall_at_10, "20": 0.9}, "mean_candidate_count": 20.0}},
           "questions": []}
    file = folder / name
    file.write_text(json.dumps({"dataset": {"version": "v2", "split": "dev"}, "runs": [run]}), encoding="utf-8")
    return file


def test_compares_two_evaluations_and_passes_when_no_run_lost_more_than_allowed(tmp_path: Path, capsys):
    main(["compare", str(write_evaluation(tmp_path, "recorded.json", 0.60)),
          str(write_evaluation(tmp_path, "new.json", 0.59)), "--max-drop", "2"])

    assert "| bm25 | Recall@10 | 60.0% -> 59.0% | 20.0 -> 20.0 |" in capsys.readouterr().out


def test_stops_with_status_1_when_a_run_lost_more_recall_than_allowed(tmp_path: Path):
    with pytest.raises(SystemExit) as stopped:
        main(["compare", str(write_evaluation(tmp_path, "recorded.json", 0.60)),
              str(write_evaluation(tmp_path, "new.json", 0.55)), "--max-drop", "2"])

    assert stopped.value.code == 1
