"""Scores a retriever on a dataset split, from ai/: `uv run python -m rag evaluate random`."""

import argparse
from collections.abc import Callable

from dataset.paths import repo_root
from rag.chunks import Chunk, read_chunks
from rag.evaluation.questions import read_retrieval_questions
from rag.evaluation.report import evaluate_retriever, format_report
from rag.retrievers import Retriever
from rag.retrievers.random_retriever import RandomRetriever

RETRIEVERS: dict[str, Callable[[list[Chunk]], Retriever]] = {
    "random": lambda chunks: RandomRetriever([chunk.id for chunk in chunks]),
}


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m rag")
    commands = parser.add_subparsers(dest="command", required=True)
    evaluate = commands.add_parser("evaluate", help="score a retriever on a dataset split")
    evaluate.add_argument("retriever", choices=sorted(RETRIEVERS))
    evaluate.add_argument("--version", default="v1")
    evaluate.add_argument("--split", default="dev", choices=["dev", "test"],
                          help="dev to tune; test only for the published numbers")
    arguments = parser.parse_args(argv)

    root = repo_root()
    retriever = RETRIEVERS[arguments.retriever](read_chunks(root))
    questions = read_retrieval_questions(root, arguments.version, arguments.split)
    print(f"{arguments.retriever} on {arguments.version} {arguments.split}, {len(questions)} answerable questions\n")
    print(format_report(evaluate_retriever(retriever, questions)))


if __name__ == "__main__":
    main()
