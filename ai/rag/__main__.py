"""The RAG's command line, from ai/. It only reads the arguments, calls a service and prints:

uv run python -m rag index                                    # chunks and vectors into the database (indexing)
uv run python -m rag evaluate --description "Why it is run"   # each retriever, expanded or not, reranked or not
uv run python -m rag evaluate bm25 dense-qwen3-0.6b --rerankers minilm-l6 --description "Why it is run"
uv run python -m rag evaluate bm25 --expanders --rerankers --description "Why it is run"   # a flag alone: none of them
"""

import argparse

from dataset.paths import repo_root
from rag.evaluation.evaluation_json import name_evaluation
from rag.evaluation.evaluation_service import evaluate_all
from rag.evaluation.report_table import format_report_table
from rag.expanders.registry import list_expander_names
from rag.indexing.indexing_service import build_index
from rag.repository.evaluation_repository import EvaluationRepositoryFile
from rag.rerankers.registry import list_reranker_names
from rag.retrievers.registry import list_retriever_names


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m rag")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("index", help="write the chunks and their vectors into the database, for every model")
    evaluate = commands.add_parser("evaluate", allow_abbrev=False,
                                   help="score retrievers on a dataset split, all of them by default")
    evaluate.add_argument("retrievers", nargs="*", choices=list_retriever_names(), metavar="retriever",
                          help=", ".join(list_retriever_names()))
    evaluate.add_argument("--expanders", nargs="*", choices=list_expander_names(), metavar="expander",
                          help="each retriever runs again with each of them, every one by default; --expanders alone "
                               "for none: " + ", ".join(list_expander_names()))
    evaluate.add_argument("--rerankers", nargs="*", choices=list_reranker_names(), metavar="reranker",
                          help="each retriever runs again with each of them, every one by default; --rerankers alone "
                               "for none: " + ", ".join(list_reranker_names()))
    evaluate.add_argument("--description", required=True, type=refuse_blank,
                          help="why this evaluation is run, kept in its file for the benchmark explorer")
    evaluate.add_argument("--version", default="v2")
    evaluate.add_argument("--split", default="dev", choices=["dev", "test"],
                          help="dev to tune; test only for the published numbers")
    arguments = parser.parse_args(argv)

    root = repo_root()
    if arguments.command == "index":
        for model_name, embedded in build_index(root).items():
            print(f"{model_name}: {embedded} chunks embedded, the others unchanged")
    else:
        expanders = list_expander_names() if arguments.expanders is None else arguments.expanders
        rerankers = list_reranker_names() if arguments.rerankers is None else arguments.rerankers
        evaluation = evaluate_all(arguments.retrievers or list_retriever_names(), expanders, rerankers, root,
                                  arguments.version, arguments.split, arguments.description,
                                  EvaluationRepositoryFile(root))
        for run in evaluation.runs:
            print(f"retriever {run.layers.retriever}, expand {run.layers.expand}, rerank {run.layers.rerank}, answer "
                  f"{run.layers.answer} on {arguments.version} {arguments.split}, "
                  f"{run.report.overall.question_count} answerable questions\n{run.description}\n")
            print(format_report_table(run.report) + "\n")
        print(f"Saved as {name_evaluation(evaluation)}")


def refuse_blank(text: str) -> str:
    """The text as given, unless it says nothing: an evaluation's file keeps its description for good."""
    if not text.strip():
        raise argparse.ArgumentTypeError("say why this evaluation is run")
    return text


if __name__ == "__main__":
    main()
