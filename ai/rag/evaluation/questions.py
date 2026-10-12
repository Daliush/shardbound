"""The dataset's questions as retrieval sees them: a text and the chunks a correct answer needs."""

from dataclasses import dataclass
from pathlib import Path

from dataset import jsonl
from dataset.paths import dataset_dir


@dataclass(frozen=True)
class RetrievalQuestion:
    id: str
    category: str
    text: str
    relevant_ids: frozenset[str]


def read_retrieval_questions(root: Path, version: str, split: str) -> list[RetrievalQuestion]:
    """
    The answerable questions of `dataset-<split>.jsonl`. Their relevant chunks are their `citations`, what a correct
    answer cites: the rules of a scenario, a deck or a simple-rule question, the card of a factual question. Not
    `cards`, which lists every card of a scenario, deck fillers and whole decklists included. An unanswerable
    question has no relevant chunk, so it is left out.
    """
    rows = jsonl.read(dataset_dir(root, version) / f"dataset-{split}.jsonl")
    return [RetrievalQuestion(row["id"], row["category"], row["question"], frozenset(row["citations"]))
            for row in rows if row["answerable"]]
