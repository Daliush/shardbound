"""The dataset's questions, as the evaluation reads them."""

from dataclasses import dataclass
from pathlib import Path

from dataset import jsonl
from dataset.paths import dataset_dir


@dataclass(frozen=True)
class Question:
    id: str
    category: str
    text: str
    answerable: bool
    relevant_ids: frozenset[str]
    """
    Its `citations`, what a correct answer cites: the rules of a scenario, a deck or a simple-rule question, the card
    of a factual question. Not `cards`, which lists every card of a scenario, deck fillers and whole decklists
    included. Empty for an unanswerable question.
    """


def read_questions(root: Path, version: str, split: str) -> list[Question]:
    """Every question of `dataset-<split>.jsonl`, unanswerable ones included."""
    questions = []
    for row in jsonl.read(dataset_dir(root, version) / f"dataset-{split}.jsonl"):
        questions.append(Question(id=row["id"], category=row["category"], text=row["question"],
                                  answerable=row["answerable"], relevant_ids=frozenset(row["citations"])))
    return questions
