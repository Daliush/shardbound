"""
Where evaluations are kept: in the repository's `evals/reports/` today, in a bucket later. A storage only writes the
document `evaluation_json.py` gives it, under the name it gives, so a bucket is one more implementation.
"""

from abc import ABC, abstractmethod
from pathlib import Path

from rag.evaluation.evaluation_json import convert_evaluation_to_json, name_evaluation
from rag.evaluation.runs import Evaluation

REPORTS_DIR = Path("evals") / "reports"


class EvaluationRepository(ABC):

    @abstractmethod
    def save_evaluation(self, evaluation: Evaluation) -> str:
        """Saves the evaluation as a new document. Returns where it is."""


class EvaluationRepositoryFile(EvaluationRepository):
    """`evals/reports/<version>/<split>/<timestamp>.json` in the repository, committed with the code."""

    def __init__(self, root: Path) -> None:
        self._folder = root / REPORTS_DIR

    def save_evaluation(self, evaluation: Evaluation) -> str:
        file = self._folder / name_evaluation(evaluation)
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(convert_evaluation_to_json(evaluation), encoding="utf-8", newline="\n")
        return str(file)
