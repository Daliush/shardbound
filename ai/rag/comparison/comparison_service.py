"""Two saved evaluations of one dataset split, run by run: how each run's recall moved, and which questions it now
ranks differently. The documents are read as `evaluation_json.py` writes them."""

import json
from dataclasses import dataclass
from pathlib import Path

LAYER_ORDER = ("retriever", "expand", "rerank", "answer")


@dataclass(frozen=True)
class RunComparison:
    """One run found in both evaluations, by its layers."""

    name: str
    """Its techniques in a question's order, skipped steps left out: "rrf-bm25-dense + refs + minilm-l6"."""
    recall_at_10_before: float
    recall_at_10_after: float
    recall_at_20_before: float
    recall_at_20_after: float
    reranked_question_ids: list[str]
    """The questions whose first chunks changed, in the new evaluation's order."""

    def count_lost_points(self) -> float:
        """How many points of Recall@10 the run lost, negative when it gained."""
        return (self.recall_at_10_before - self.recall_at_10_after) * 100


@dataclass(frozen=True)
class Comparison:
    runs: list[RunComparison]
    new_run_names: list[str]
    """The runs of the new evaluation that the recorded one does not have: nothing to compare them with."""

    def find_drops(self, max_lost_points: float) -> list[RunComparison]:
        """The runs whose Recall@10 lost more than `max_lost_points` points."""
        drops = []
        for run in self.runs:
            if run.count_lost_points() > max_lost_points:
                drops.append(run)
        return drops


def compare_evaluation_files(recorded: Path, new: Path) -> Comparison:
    return compare_evaluations(json.loads(recorded.read_text(encoding="utf-8")),
                               json.loads(new.read_text(encoding="utf-8")))


def compare_evaluations(recorded: dict, new: dict) -> Comparison:
    """
    Every run of `new`, against the run of `recorded` that has the same layers. A run that `new` lacks is not
    compared: an evaluation may score only some of the pipelines.
    """
    if recorded["dataset"] != new["dataset"]:
        raise ValueError(f"The two evaluations score different questions: {recorded['dataset']} and {new['dataset']}")
    recorded_runs = {}
    for run in recorded["runs"]:
        recorded_runs[name_run(run["layers"])] = run
    runs = []
    new_run_names = []
    for run in new["runs"]:
        name = name_run(run["layers"])
        if name in recorded_runs:
            runs.append(compare_runs(name, recorded_runs[name], run))
        else:
            new_run_names.append(name)
    return Comparison(runs=runs, new_run_names=new_run_names)


def compare_runs(name: str, before: dict, after: dict) -> RunComparison:
    recall_before = before["scores"]["overall"]["mean_recall_at"]
    recall_after = after["scores"]["overall"]["mean_recall_at"]
    return RunComparison(name=name,
                         recall_at_10_before=recall_before["10"], recall_at_10_after=recall_after["10"],
                         recall_at_20_before=recall_before["20"], recall_at_20_after=recall_after["20"],
                         reranked_question_ids=find_reranked_questions(before["questions"], after["questions"]))


def find_reranked_questions(before: list[dict], after: list[dict]) -> list[str]:
    """The questions of `after` whose first chunks differ from `before`'s, a question new to `after` included."""
    first_chunks_before = {}
    for question in before:
        first_chunks_before[question["question_id"]] = question["top_chunk_ids"]
    reranked = []
    for question in after:
        if first_chunks_before.get(question["question_id"]) != question["top_chunk_ids"]:
            reranked.append(question["question_id"])
    return reranked


def name_run(layers: dict) -> str:
    names = []
    for layer in LAYER_ORDER:
        if layers.get(layer):
            names.append(layers[layer])
    return " + ".join(names)
