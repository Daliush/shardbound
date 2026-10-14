import json
from pathlib import Path

from rag.evaluation.run_file import save_runs
from rag.evaluation.runs import Layers, Run
from rag.evaluation.scores import QuestionResult, Report, Scores


def build_run(retriever: str, recall: float) -> Run:
    scores = Scores(1, {10: recall, 20: recall}, recall)
    result = QuestionResult("rule/2.2", "simple-rule", ["2.2", "2.1"], {10: recall, 20: recall}, recall)
    return Run(Layers(retriever=retriever), Report(overall=scores, by_category={"simple-rule": scores},
                                                   questions=[result]))


def test_saves_each_run_by_its_layers_with_an_empty_answer_layer(tmp_path: Path):
    file = tmp_path / "dev.json"

    save_runs(file, "v1", "dev", [build_run("bm25", 1.0)], "60192f9")

    saved = json.loads(file.read_text(encoding="utf-8"))
    assert saved["dataset"] == {"version": "v1", "split": "dev"}
    [run] = saved["runs"]
    assert run["layers"] == {"retriever": "bm25", "answer": None}
    assert run["code"] == "60192f9"
    assert run["scores"]["overall"]["mean_recall_at"] == {"10": 1.0, "20": 1.0}
    assert run["questions"][0]["top_chunk_ids"] == ["2.2", "2.1"]


def test_replaces_a_run_evaluated_again_in_place_and_keeps_the_others(tmp_path: Path):
    file = tmp_path / "dev.json"
    save_runs(file, "v1", "dev", [build_run("random", 0.0), build_run("bm25", 0.5)], "aaaaaaa")

    save_runs(file, "v1", "dev", [build_run("random", 0.1)], "bbbbbbb")

    runs = json.loads(file.read_text(encoding="utf-8"))["runs"]
    assert [(run["layers"]["retriever"], run["code"]) for run in runs] == [("random", "bbbbbbb"), ("bm25", "aaaaaaa")]
