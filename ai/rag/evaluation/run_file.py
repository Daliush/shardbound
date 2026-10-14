"""
The runs of a dataset split, in `evals/reports/<version>/<split>.json`, for the benchmark explorer (design doc §8.2):

{"dataset": {"version": "v1", "split": "dev"},
 "runs": [{"layers": {"retriever": "bm25", "answer": null}, "code": "60192f9",
           "scores": {"overall": {…}, "by_category": {…}},
           "questions": [{"question_id": …, "top_chunk_ids": […], …}]}]}
"""

import json
import subprocess
from dataclasses import asdict
from pathlib import Path

from rag.evaluation.runs import Run

REPORTS_DIR = Path("evals") / "reports"


def find_runs_file(root: Path, version: str, split: str) -> Path:
    return root / REPORTS_DIR / version / f"{split}.json"


def read_code_commit(root: Path) -> str:
    """
    The commit the code was evaluated at, "-dirty" when a tracked file had uncommitted changes. The reports are left
    out: a runs file not committed yet says nothing about the code that scores.
    """
    commit = run_git(root, "rev-parse", "--short", "HEAD")
    changes = run_git(root, "status", "--porcelain", "--untracked-files=no", "--", ".",
                      f":(exclude){REPORTS_DIR.as_posix()}")
    return f"{commit}-dirty" if changes else commit


def run_git(root: Path, *arguments: str) -> str:
    return subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=True).stdout.strip()


def save_runs(file: Path, version: str, split: str, runs: list[Run], code_commit: str) -> None:
    """A run evaluated again replaces the one with the same layers, in place; the other runs of the file stay."""
    saved_runs = json.loads(file.read_text(encoding="utf-8"))["runs"] if file.is_file() else []
    runs_by_layers = {}
    for saved_run in saved_runs:
        runs_by_layers[layers_key(saved_run["layers"])] = saved_run
    for run in runs:
        new_run = {"layers": asdict(run.layers), "code": code_commit,
                   "scores": {"overall": asdict(run.report.overall),
                              "by_category": {category: asdict(scores)
                                              for category, scores in run.report.by_category.items()}},
                   "questions": [asdict(result) for result in run.report.questions]}
        runs_by_layers[layers_key(new_run["layers"])] = new_run
    file.parent.mkdir(parents=True, exist_ok=True)
    content = {"dataset": {"version": version, "split": split}, "runs": list(runs_by_layers.values())}
    file.write_text(json.dumps(content, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def layers_key(layers: dict) -> tuple:
    return tuple(sorted(layers.items()))
