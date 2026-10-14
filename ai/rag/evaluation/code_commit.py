"""The commit an evaluation scored at."""

import subprocess
from pathlib import Path

from rag.repository.evaluation_repository import REPORTS_DIR


def read_code_commit(root: Path) -> str:
    """
    The commit the code was evaluated at, "-dirty" when a tracked file had uncommitted changes. The reports are left
    out: they say nothing about the code that scores.
    """
    commit = run_git(root, "rev-parse", "--short", "HEAD")
    changes = run_git(root, "status", "--porcelain", "--untracked-files=no", "--", ".",
                      f":(exclude){REPORTS_DIR.as_posix()}")
    return f"{commit}-dirty" if changes else commit


def run_git(root: Path, *arguments: str) -> str:
    return subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=True).stdout.strip()
