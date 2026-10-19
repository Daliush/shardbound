import json
from pathlib import Path

import pytest

from rag.__main__ import main


@pytest.mark.parametrize("arguments", [["evaluate"], ["evaluate", "--description", "   "]])
def test_refuses_to_evaluate_without_saying_why(arguments: list[str]):
    with pytest.raises(SystemExit) as stopped:
        main(arguments)

    assert stopped.value.code == 2


def test_refuses_a_reranker_it_does_not_know():
    with pytest.raises(SystemExit) as stopped:
        main(["evaluate", "--rerankers", "jev", "--description", "Why"])

    assert stopped.value.code == 2


def write_evaluation(folder: Path, name: str, recall_at_10: float) -> Path:
    run = {"layers": {"retriever": "bm25", "expand": None, "rerank": None, "answer": None},
           "scores": {"overall": {"mean_recall_at": {"10": recall_at_10, "20": 0.9}}}, "questions": []}
    file = folder / name
    file.write_text(json.dumps({"dataset": {"version": "v2", "split": "dev"}, "runs": [run]}), encoding="utf-8")
    return file


def test_compares_two_evaluations_and_passes_when_no_run_lost_more_than_allowed(tmp_path: Path, capsys):
    main(["compare", str(write_evaluation(tmp_path, "recorded.json", 0.60)),
          str(write_evaluation(tmp_path, "new.json", 0.59)), "--max-drop", "2"])

    assert "| bm25 | 60.0% -> 59.0% |" in capsys.readouterr().out


def test_stops_with_status_1_when_a_run_lost_more_recall_than_allowed(tmp_path: Path):
    with pytest.raises(SystemExit) as stopped:
        main(["compare", str(write_evaluation(tmp_path, "recorded.json", 0.60)),
              str(write_evaluation(tmp_path, "new.json", 0.55)), "--max-drop", "2"])

    assert stopped.value.code == 1
