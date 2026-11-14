"""Two saved evaluations of one dataset split, run by run: how each run's decision metric and number of chunks moved,
and which questions it now ranks differently. The documents are read as `evaluation_json.py` writes them."""

import json
from dataclasses import dataclass
from pathlib import Path

LAYER_ORDER = ("retriever", "expand", "rerank", "context", "answer")

CONTEXT_RECALL = "mean_context_recall"
"""The decision metric, in a group's scores."""
RECALL_AT_10 = "mean_recall_at"
"""Where an evaluation recorded before context recall keeps Recall@10, under "10": what context recall is for a
ranking."""


@dataclass(frozen=True)
class RunComparison:
    """One run found in both evaluations, by its layers."""

    name: str
    """Its techniques in a question's order, skipped steps left out: "rrf-bm25-dense + refs + minilm-l6"."""
    metric_name: str
    """"Context recall", or "Recall@10" when one of the two evaluations was recorded before context recall: for a
    ranking, the same value."""
    before: float
    after: float
    candidates_before: float
    candidates_after: float
    """How many chunks the run returns, on average: after a context, how many the prompt holds."""
    reranked_question_ids: list[str]
    """The questions whose kept chunks changed, in the new evaluation's order."""

    def count_lost_points(self) -> float:
        """How many points of the decision metric the run lost, negative when it gained."""
        return (self.before - self.after) * 100


@dataclass(frozen=True)
class Comparison:
    runs: list[RunComparison]
    new_run_names: list[str]
    """The runs of the new evaluation that the recorded one does not have: nothing to compare them with."""

    def find_drops(self, max_lost_points: float) -> list[RunComparison]:
        """The runs whose decision metric lost more than `max_lost_points` points."""
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
    """The two runs on context recall when both have it, else on Recall@10, which an evaluation recorded before
    context recall keeps and which context recall is for a ranking."""
    scores_before = before["scores"]["overall"]
    scores_after = after["scores"]["overall"]
    if CONTEXT_RECALL in scores_before and CONTEXT_RECALL in scores_after:
        metric_name, value_before, value_after = ("Context recall", scores_before[CONTEXT_RECALL],
                                                  scores_after[CONTEXT_RECALL])
    else:
        metric_name, value_before, value_after = ("Recall@10", scores_before[RECALL_AT_10]["10"],
                                                  scores_after[RECALL_AT_10]["10"])
    return RunComparison(name=name, metric_name=metric_name, before=value_before, after=value_after,
                         candidates_before=scores_before["mean_candidate_count"],
                         candidates_after=scores_after["mean_candidate_count"],
                         reranked_question_ids=find_reranked_questions(before["questions"], after["questions"]))


def find_reranked_questions(before: list[dict], after: list[dict]) -> list[str]:
    """The questions of `after` whose kept chunks differ from `before`'s, a question new to `after` included: a
    ranking's first 20, or a whole context."""
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
