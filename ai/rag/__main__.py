"""The RAG's command line, from ai/. It only reads the arguments, calls a service and prints:

uv run python -m rag evaluate                      # every retriever on the dev questions (evaluation)
uv run python -m rag evaluate random bm25
"""

import argparse

from dataset.paths import repo_root
from rag.evaluation.evaluation_service import evaluate_all
from rag.evaluation.report_table import format_report_table
from rag.evaluation.run_file import find_runs_file
from rag.retrievers.registry import list_retriever_names


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m rag")
    commands = parser.add_subparsers(dest="command", required=True)
    evaluate = commands.add_parser("evaluate", help="score retrievers on a dataset split, all of them by default")
    evaluate.add_argument("retrievers", nargs="*", choices=list_retriever_names(), metavar="retriever",
                          help=", ".join(list_retriever_names()))
    evaluate.add_argument("--version", default="v1")
    evaluate.add_argument("--split", default="dev", choices=["dev", "test"],
                          help="dev to tune; test only for the published numbers")
    arguments = parser.parse_args(argv)

    root = repo_root()
    if arguments.command == "evaluate":
        runs = evaluate_all(arguments.retrievers or list_retriever_names(), root, arguments.version, arguments.split)
        for run in runs:
            print(f"retriever {run.layers.retriever}, answer {run.layers.answer} on {arguments.version} "
                  f"{arguments.split}, {run.report.overall.question_count} answerable questions\n")
            print(format_report_table(run.report) + "\n")
        print(f"Runs saved in {find_runs_file(root, arguments.version, arguments.split).relative_to(root)}")


if __name__ == "__main__":
    main()
