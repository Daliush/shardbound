"""The answer keys of the dataset's questions, as the answer metrics read them."""

from dataclasses import dataclass
from pathlib import Path

from dataset import jsonl
from dataset.paths import dataset_dir
from rag.named_cards import NamedCardFinder


@dataclass(frozen=True)
class AnswerKey:
    """A question and what the engine or the card data says a correct answer to it holds (design doc §6.1)."""

    question_id: str
    category: str
    question: str
    answerable: bool
    citations: list[str]
    """What a correct answer cites: a scenario's essential rules then its context rules, the rule of a simple-rule or
    deck question, the card of a factual question. Empty for an unanswerable question."""
    rules: list[str]
    """The rules the key events carry, in the order the events happen: the steps of a scenario."""
    named_cards: list[str]
    """The cards the question names, found as the RAG finds them. Not the dataset's `cards`, which lists every card of
    a scenario, the opponent's hidden ones and the deck fillers included."""
    trace: list[str]
    """Every event of a scenario, incidental ones included, with the rules it carries: "UnitDestroyed[8.2]". Empty when
    no game is played."""


def read_answer_keys(root: Path, version: str, split: str, card_finder: NamedCardFinder) -> list[AnswerKey]:
    """Every question of `dataset-<split>.jsonl`, unanswerable ones included, with the cards it names."""
    answer_keys = []
    for row in jsonl.read(dataset_dir(root, version) / f"dataset-{split}.jsonl"):
        answer_keys.append(AnswerKey(question_id=row["id"], category=row["category"], question=row["question"],
                                     answerable=row["answerable"], citations=row["citations"], rules=row["rules"],
                                     named_cards=card_finder.find_named_cards(row["question"]), trace=row["trace"]))
    return answer_keys
