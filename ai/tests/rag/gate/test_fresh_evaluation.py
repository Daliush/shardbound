import json
from pathlib import Path

from rag.gate.fresh_evaluation import check_fresh_evaluation
from rag.gate.git_history import GitHistory

BASELINE_LAYERS = {"retriever": "rrf-bm25-dense", "expand": "refs", "rerank": "minilm-l6", "answer": None}
EVALUATION = "evals/reports/v2/dev/2026-10-26T10-00-00Z.json"


class GitHistoryInMemory(GitHistory):
    """A pull request's history written by hand: what it changes and adds, and what changed since each commit."""

    def __init__(self, changed: list[str], added: list[str] = (), commits: dict[str, list[str]] | None = None) -> None:
        self.changed = changed
        self.added = list(added)
        self.changed_since = commits or {}

    def list_changed_files(self, base: str) -> list[str]:
        return self.changed

    def list_added_files(self, base: str, folder: str) -> list[str]:
        return [file for file in self.added if file.startswith(folder)]

    def has_commit(self, commit: str) -> bool:
        return commit in self.changed_since

    def list_changed_files_since(self, commit: str, paths: tuple[str, ...]) -> list[str]:
        return [file for file in self.changed_since[commit] if file.startswith(paths)]


def write_evaluation(root: Path, path: str, code: str, layers: list[dict]) -> None:
    file = root / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps({"code": code, "runs": [{"layers": run} for run in layers]}), encoding="utf-8")


def test_needs_no_evaluation_when_no_file_retrieval_depends_on_changed(tmp_path: Path):
    history = GitHistoryInMemory(changed=["frontend/src/app/lab/presentation.ts", "ai/README.md"])

    verdict = check_fresh_evaluation(history, tmp_path, "origin/main")

    assert verdict.passed


def test_fails_when_a_change_to_retrieval_comes_without_an_evaluation(tmp_path: Path):
    history = GitHistoryInMemory(changed=["ai/rag/baseline.py", "frontend/src/app/lab/presentation.ts"])

    verdict = check_fresh_evaluation(history, tmp_path, "origin/main")

    assert not verdict.passed
    assert "ai/rag/baseline.py" in verdict.message and "presentation.ts" not in verdict.message


def test_passes_a_clean_evaluation_of_the_files_the_pull_request_has(tmp_path: Path):
    write_evaluation(tmp_path, EVALUATION, "1a2b3c4", [BASELINE_LAYERS, {**BASELINE_LAYERS, "rerank": None}])
    history = GitHistoryInMemory(changed=["ai/rag/baseline.py", EVALUATION], added=[EVALUATION],
                                 commits={"1a2b3c4": [EVALUATION, "ai/README.md"]})

    assert check_fresh_evaluation(history, tmp_path, "origin/main").passed


def test_reads_the_newest_evaluation_the_pull_request_adds(tmp_path: Path):
    older = "evals/reports/v2/dev/2026-10-25T10-00-00Z.json"
    write_evaluation(tmp_path, older, "0000000-dirty", [BASELINE_LAYERS])
    write_evaluation(tmp_path, EVALUATION, "1a2b3c4", [BASELINE_LAYERS])
    history = GitHistoryInMemory(changed=["content/cards/ember/ash-warden.json"], added=[EVALUATION, older],
                                 commits={"1a2b3c4": []})

    assert check_fresh_evaluation(history, tmp_path, "origin/main").passed


def test_fails_an_evaluation_recorded_with_uncommitted_changes(tmp_path: Path):
    write_evaluation(tmp_path, EVALUATION, "1a2b3c4-dirty", [BASELINE_LAYERS])
    history = GitHistoryInMemory(changed=["ai/rag/baseline.py"], added=[EVALUATION], commits={"1a2b3c4": []})

    verdict = check_fresh_evaluation(history, tmp_path, "origin/main")

    assert not verdict.passed and "uncommitted" in verdict.message


def test_fails_an_evaluation_recorded_before_a_later_change_to_retrieval(tmp_path: Path):
    write_evaluation(tmp_path, EVALUATION, "1a2b3c4", [BASELINE_LAYERS])
    history = GitHistoryInMemory(changed=["ai/rag/baseline.py"], added=[EVALUATION],
                                 commits={"1a2b3c4": ["ai/rag/rerankers/registry.py", "ai/README.md"]})

    verdict = check_fresh_evaluation(history, tmp_path, "origin/main")

    assert not verdict.passed
    assert "before ai/rag/rerankers/registry.py changed" in verdict.message


def test_fails_an_evaluation_recorded_at_a_commit_the_branch_does_not_have(tmp_path: Path):
    write_evaluation(tmp_path, EVALUATION, "9f9f9f9", [BASELINE_LAYERS])
    history = GitHistoryInMemory(changed=["ai/rag/baseline.py"], added=[EVALUATION], commits={})

    verdict = check_fresh_evaluation(history, tmp_path, "origin/main")

    assert not verdict.passed and "does not have" in verdict.message


def test_fails_an_evaluation_without_a_run_of_the_baseline(tmp_path: Path):
    write_evaluation(tmp_path, EVALUATION, "1a2b3c4", [{**BASELINE_LAYERS, "rerank": "qwen3-reranker-0.6b"}])
    history = GitHistoryInMemory(changed=["ai/rag/baseline.py"], added=[EVALUATION], commits={"1a2b3c4": []})

    verdict = check_fresh_evaluation(history, tmp_path, "origin/main")

    assert not verdict.passed
    assert "rrf-bm25-dense + refs + minilm-l6" in verdict.message
