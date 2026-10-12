"""Scores retrievers on a dataset split, from ai/: `uv run python -m rag evaluate random bm25`."""

import argparse
from collections.abc import Callable

from dataset.paths import repo_root
from rag.chunks import Chunk, read_chunks
from rag.evaluation.questions import read_retrieval_questions
from rag.evaluation.report import evaluate_retriever, format_report
from rag.retrievers import Retriever
from rag.retrievers.bm25_retriever import Bm25Retriever, Bm25Settings
from rag.retrievers.random_retriever import RandomRetriever

BM25_VARIANTS = {
    "bm25": Bm25Settings(),
    "bm25-no-stemming": Bm25Settings(stemming=False),
    "bm25-stop-words-removed": Bm25Settings(stop_words_removed=True),
    "bm25-robertson": Bm25Settings(formula="robertson"),
    "bm25-atire": Bm25Settings(formula="atire"),
    "bm25l": Bm25Settings(formula="bm25l"),
    "bm25+": Bm25Settings(formula="bm25+"),
}

RETRIEVERS: dict[str, Callable[[list[Chunk]], Retriever]] = {
    "random": lambda chunks: RandomRetriever([chunk.id for chunk in chunks]),
    **{name: (lambda chunks, settings=settings: Bm25Retriever(chunks, settings))
       for name, settings in BM25_VARIANTS.items()},
}


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m rag")
    commands = parser.add_subparsers(dest="command", required=True)
    evaluate = commands.add_parser("evaluate", help="score retrievers on a dataset split")
    evaluate.add_argument("retrievers", nargs="+", choices=sorted(RETRIEVERS), metavar="retriever",
                          help=", ".join(sorted(RETRIEVERS)))
    evaluate.add_argument("--version", default="v1")
    evaluate.add_argument("--split", default="dev", choices=["dev", "test"],
                          help="dev to tune; test only for the published numbers")
    arguments = parser.parse_args(argv)

    root = repo_root()
    chunks = read_chunks(root)
    questions = read_retrieval_questions(root, arguments.version, arguments.split)
    for name in arguments.retrievers:
        print(f"{name} on {arguments.version} {arguments.split}, {len(questions)} answerable questions\n")
        print(format_report(evaluate_retriever(RETRIEVERS[name](chunks), questions)) + "\n")


if __name__ == "__main__":
    main()
