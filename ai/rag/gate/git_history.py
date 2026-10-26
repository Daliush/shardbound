"""What the gate asks git: the files a pull request changes or adds, and what changed since an evaluation's commit."""

import subprocess
from abc import ABC, abstractmethod
from pathlib import Path

from rag.evaluation.code_commit import run_git


class GitHistory(ABC):
    """A pull request's history, its files named by their path from the repository's root."""

    @abstractmethod
    def list_changed_files(self, base: str) -> list[str]:
        """The files the pull request changes, added ones included, since it left `base`."""

    @abstractmethod
    def list_added_files(self, base: str, folder: str) -> list[str]:
        """The files the pull request adds under `folder` since it left `base`."""

    @abstractmethod
    def has_commit(self, commit: str) -> bool:
        """Whether `commit` is a commit of this history."""

    @abstractmethod
    def list_changed_files_since(self, commit: str, paths: tuple[str, ...]) -> list[str]:
        """The files under `paths` that differ between `commit` and the commit checked out."""


class GitHistoryLocal(GitHistory):
    """The repository's own git. CI checks out a pull request merged into its base, so a branch behind it shows the
    base's newer files too."""

    def __init__(self, root: Path) -> None:
        self._root = root

    def list_changed_files(self, base: str) -> list[str]:
        return run_git(self._root, "diff", "--name-only", f"{base}...HEAD").splitlines()

    def list_added_files(self, base: str, folder: str) -> list[str]:
        return run_git(self._root, "diff", "--name-only", "--diff-filter=A", f"{base}...HEAD", "--",
                       folder).splitlines()

    def has_commit(self, commit: str) -> bool:
        found = subprocess.run(["git", "cat-file", "-e", f"{commit}^{{commit}}"], cwd=self._root, capture_output=True)
        return found.returncode == 0

    def list_changed_files_since(self, commit: str, paths: tuple[str, ...]) -> list[str]:
        return run_git(self._root, "diff", "--name-only", commit, "HEAD", "--", *paths).splitlines()
