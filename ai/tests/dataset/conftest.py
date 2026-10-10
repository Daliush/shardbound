import shutil
from pathlib import Path

import pytest

from dataset import content, rulebook
from dataset.paths import repo_root

ROOT = repo_root()
FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture(scope="session")
def root() -> Path:
    return ROOT


@pytest.fixture(scope="session")
def rulebook_text() -> str:
    return rulebook.text(ROOT / "docs" / "rules")


@pytest.fixture(scope="session")
def sources() -> content.Content:
    return content.load(ROOT)


@pytest.fixture(scope="session")
def rules() -> list[rulebook.Rule]:
    return rulebook.load(ROOT / "docs" / "rules")


@pytest.fixture
def version(tmp_path: Path) -> Path:
    """A dataset folder holding a few lines of each engine file, as `engine/dataset` writes them."""
    folder = tmp_path / "v-test"
    shutil.copytree(FIXTURES / "v-test", folder)
    return folder
