"""The answers' files the repository holds were recorded at a clean commit, like the reports the RAG's gate checks."""

import json
import subprocess
from pathlib import Path

from arbiter.evaluation.answer_evaluation_repository import ANSWERS_DIR
from dataset.paths import repo_root


def list_tracked_answers(root: Path) -> list[Path]:
    """Every answers' file git tracks, a staged one included: an untracked file is still a draft."""
    listed = subprocess.run(["git", "ls-files", "--", ANSWERS_DIR.as_posix()], cwd=root, capture_output=True,
                            text=True, check=True).stdout
    files = []
    for line in listed.splitlines():
        if line.endswith(".json"):
            files.append(root / line)
    return files


def test_every_tracked_answers_file_was_recorded_at_a_clean_commit():
    root = repo_root()
    dirty = []
    for file in list_tracked_answers(root):
        if json.loads(file.read_text(encoding="utf-8"))["code"].endswith("-dirty"):
            dirty.append(file.relative_to(root).as_posix())

    assert dirty == [], "record it again at a clean commit: the cache makes it free"
