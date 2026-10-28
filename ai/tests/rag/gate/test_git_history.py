import subprocess
from pathlib import Path

import pytest

from rag.gate.git_history import GitHistoryLocal


def git(root: Path, *arguments: str) -> str:
    return subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=True).stdout.strip()


def commit(root: Path, files: dict[str, str], message: str) -> str:
    for path, text in files.items():
        (root / path).parent.mkdir(parents=True, exist_ok=True)
        (root / path).write_text(text, encoding="utf-8")
    git(root, "add", "-A")
    git(root, "commit", "-q", "-m", message)
    return git(root, "rev-parse", "--short", "HEAD")


@pytest.fixture
def repository(tmp_path: Path) -> Path:
    """A main branch with one file, and a pull request branch that changes it, adds an evaluation, then changes
    retrieval again."""
    git(tmp_path, "init", "-q", "-b", "main")
    git(tmp_path, "config", "user.email", "test@example.com")
    git(tmp_path, "config", "user.name", "Test")
    commit(tmp_path, {"ai/rag/baseline.py": "one"}, "main")
    git(tmp_path, "switch", "-q", "-c", "pull-request")
    return tmp_path


def test_lists_what_the_pull_request_changes_and_adds_since_it_left_main(repository: Path):
    commit(repository, {"ai/rag/baseline.py": "two", "evals/reports/v2/dev/a.json": "{}"}, "change and evaluate")
    history = GitHistoryLocal(repository)

    assert sorted(history.list_changed_files("main")) == ["ai/rag/baseline.py", "evals/reports/v2/dev/a.json"]
    assert history.list_added_files("main", "evals/reports") == ["evals/reports/v2/dev/a.json"]


def test_lists_the_files_under_some_paths_that_changed_since_a_commit(repository: Path):
    evaluated = commit(repository, {"ai/rag/baseline.py": "two"}, "change")
    commit(repository, {"ai/rag/registry.py": "new", "ai/README.md": "docs"}, "change again")
    history = GitHistoryLocal(repository)

    assert history.list_changed_files_since(evaluated, ("ai/rag/",)) == ["ai/rag/registry.py"]
    assert history.has_commit(evaluated)
    assert not history.has_commit("0123456")
