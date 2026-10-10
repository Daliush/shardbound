"""Where things are in the repository."""

from pathlib import Path


def repo_root(start: Path | None = None) -> Path:
    """The repository holding this code: the first folder above `start` with `content/cards/` and `docs/rules/`."""
    here = (start or Path(__file__)).resolve()
    for folder in (here, *here.parents):
        if (folder / "content" / "cards").is_dir() and (folder / "docs" / "rules").is_dir():
            return folder
    raise FileNotFoundError(f"No Shardbound repository above {here}")


def dataset_dir(root: Path, version: str) -> Path:
    return root / "evals" / "datasets" / version
