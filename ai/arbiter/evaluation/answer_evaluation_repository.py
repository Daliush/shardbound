"""
Where the answers' evaluations are kept: in the repository's `evals/answers/`, beside the retrieval's `evals/reports/`,
which the RAG's gate and the lab read. A storage only writes the document `evaluation_json.py` gives it, under the
name it gives.
"""

from abc import ABC, abstractmethod
from pathlib import Path

from arbiter.evaluation.evaluation_json import convert_evaluation_to_json, name_evaluation
from arbiter.evaluation.runs import AnswerEvaluation

ANSWERS_DIR = Path("evals") / "answers"


class AnswerEvaluationRepository(ABC):

    @abstractmethod
    def save_evaluation(self, evaluation: AnswerEvaluation) -> str:
        """Saves the evaluation as a new document. Returns where it is."""


class AnswerEvaluationRepositoryFile(AnswerEvaluationRepository):
    """`evals/answers/<version>/<split>/<timestamp>.json` in the repository, committed with the code."""

    def __init__(self, root: Path) -> None:
        self._folder = root / ANSWERS_DIR

    def save_evaluation(self, evaluation: AnswerEvaluation) -> str:
        file = self._folder / name_evaluation(evaluation)
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(convert_evaluation_to_json(evaluation), encoding="utf-8", newline="\n")
        return str(file)
