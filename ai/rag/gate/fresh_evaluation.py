"""
A pull request that changes what retrieval depends on must commit an evaluation of its own code, so that main always
records one of its own: the next pull request is compared with it.
"""

import json
from dataclasses import asdict, dataclass
from pathlib import Path

from rag.baseline import BASELINE
from rag.gate.git_history import GitHistory
from rag.gate.retrieval_inputs import RETRIEVAL_INPUTS, find_retrieval_changes
from rag.repository.evaluation_repository import REPORTS_DIR

FILES_NAMED = 5
"""How many files a message names before it only counts the others."""


@dataclass(frozen=True)
class Verdict:
    passed: bool
    message: str


def check_fresh_evaluation(history: GitHistory, root: Path, base: str) -> Verdict:
    """
    Passes when the pull request changes no file retrieval depends on, or when the newest evaluation it adds was
    recorded at a clean commit whose files retrieval depends on are those the pull request has, and holds a run of the
    baseline. `base` is the branch the pull request goes into, such as "origin/main".
    """
    changes = find_retrieval_changes(history.list_changed_files(base))
    if not changes:
        return Verdict(True, "No file retrieval depends on changed: no evaluation is needed.")
    added = history.list_added_files(base, REPORTS_DIR.as_posix())
    if not added:
        return Verdict(False, f"This pull request changes {name_files(changes)}: record an evaluation of it "
                              f"(scripts/evaluate.sh) and commit it.")
    newest = max(added, key=lambda path: Path(path).name)
    evaluation = json.loads((root / newest).read_text(encoding="utf-8"))
    code = evaluation["code"]
    if code.endswith("-dirty"):
        return Verdict(False, f"{newest} was recorded with uncommitted changes: record it again at a clean commit.")
    if not history.has_commit(code):
        return Verdict(False, f"{newest} was recorded at {code}, a commit this branch does not have: record it again.")
    late = history.list_changed_files_since(code, RETRIEVAL_INPUTS)
    if late:
        return Verdict(False, f"{newest} was recorded at {code}, before {name_files(late)} changed: record it again.")
    if not has_baseline_run(evaluation):
        return Verdict(False, f"{newest} has no run of the baseline ({describe_baseline()}): the next pull request "
                              f"could not be compared with it. Record it with the baseline's techniques.")
    return Verdict(True, f"{newest} was recorded at {code}, with every file retrieval depends on as this pull request "
                         f"has it.")


def has_baseline_run(evaluation: dict) -> bool:
    for run in evaluation["runs"]:
        if run["layers"] == asdict(BASELINE):
            return True
    return False


def describe_baseline() -> str:
    """"rrf-bm25-dense + refs + minilm-l6": the baseline's techniques, in a question's order."""
    names = []
    for name in asdict(BASELINE).values():
        if name is not None:
            names.append(name)
    return " + ".join(names)


def name_files(files: list[str]) -> str:
    """"ai/rag/a.py, ai/rag/b.py and 3 more": the first files, then how many others."""
    named = ", ".join(files[:FILES_NAMED])
    others = len(files) - FILES_NAMED
    return f"{named} and {others} more" if others > 0 else named
