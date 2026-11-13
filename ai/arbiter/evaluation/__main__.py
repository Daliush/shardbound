"""The answers' evaluation's command line, from ai/. It only reads the arguments, calls the evaluation and prints:

uv run python -m arbiter.evaluation --description "Step 9: the first checks of the answers"
uv run python -m arbiter.evaluation claude-haiku-5.5 --description "…" --version v2 --split dev
uv run python -m arbiter.evaluation --contexts top10-plus-named-cards --description "…"   # one context only

The Arbiter answers from each context of the baseline's ranking (rag/baseline.py, rag/contexts/registry.py), one run
per context and generator, so the index must be built and the database running, and calls Claude with the key in
CLAUDE_KEY; every completion is cached, so a rerun costs nothing. It has its own command, apart from
`python -m arbiter`, since nothing of the Arbiter imports its evaluation.
"""

import argparse

from arbiter.evaluation.answer_evaluation_repository import AnswerEvaluationRepositoryFile
from arbiter.evaluation.evaluation_json import name_evaluation
from arbiter.evaluation.evaluation_service import evaluate_answers
from arbiter.evaluation.report_table import format_report_table
from arbiter.generation.registry import BASELINE_GENERATOR, list_generator_names
from dataset.paths import LATEST_VERSION, repo_root
from rag.contexts.registry import list_context_names


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m arbiter.evaluation",
                                     description="answer every question of a split and score the answers")
    parser.add_argument("generators", nargs="*", choices=list_generator_names(), metavar="generator",
                        help=f"the generators to evaluate, the baseline's ({BASELINE_GENERATOR}) by default: "
                             f"{', '.join(list_generator_names())}")
    parser.add_argument("--contexts", nargs="+", choices=list_context_names(), metavar="context",
                        default=list_context_names(),
                        help=f"the contexts each generator answers from, every one by default: "
                             f"{', '.join(list_context_names())}")
    parser.add_argument("--description", required=True, type=refuse_blank,
                        help="why this evaluation is run, kept in its file")
    parser.add_argument("--version", default=LATEST_VERSION)
    parser.add_argument("--split", default="dev", choices=["dev", "test"],
                        help="test only for the published numbers")
    arguments = parser.parse_args(argv)

    evaluation = evaluate_answers(arguments.generators or [BASELINE_GENERATOR], arguments.contexts, repo_root(),
                                  arguments.version, arguments.split, arguments.description,
                                  AnswerEvaluationRepositoryFile(repo_root()))
    for run in evaluation.runs:
        print(f"context {run.layers.context}, answer {run.layers.answer} on {arguments.version} {arguments.split}, "
              f"{run.report.overall.question_count} questions\n{run.description}\n")
        print(format_report_table(run.report) + "\n")
    print(f"Saved as answers/{name_evaluation(evaluation)}")


def refuse_blank(text: str) -> str:
    """The text as given, unless it says nothing: an evaluation's file keeps its description for good."""
    if not text.strip():
        raise argparse.ArgumentTypeError("say why this evaluation is run")
    return text


if __name__ == "__main__":
    main()
