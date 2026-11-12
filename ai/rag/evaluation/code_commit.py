"""The commit an evaluation scored at."""

import subprocess
from pathlib import Path

from rag.repository.evaluation_repository import REPORTS_DIR

EVALUATION_OUTPUTS = (REPORTS_DIR, Path("evals") / "answers")
"""What the evaluations write, the retrieval's reports and the answers' files: they say nothing about the code that
scores."""


def read_code_commit(root: Path) -> str:
    """
    The commit the code was evaluated at, "-dirty" when a tracked file had uncommitted changes. The evaluations'
    outputs are left out, so that a file of one waiting to be committed does not make the next one dirty.
    """
    commit = run_git(root, "rev-parse", "--short", "HEAD")
    excluded = []
    for output in EVALUATION_OUTPUTS:
        excluded.append(f":(exclude){output.as_posix()}")
    changes = run_git(root, "status", "--porcelain", "--untracked-files=no", "--", ".", *excluded)
    return f"{commit}-dirty" if changes else commit


def run_git(root: Path, *arguments: str) -> str:
    return subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=True).stdout.strip()
