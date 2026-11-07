"""The Arbiter's command line, from ai/. It only reads the arguments, calls the Arbiter and prints:

uv run python -m arbiter ask "What happens if I cast Tempest while my Root Sentinel is anchored?"
uv run python -m arbiter ask "…" --generator claude-haiku-5.5

It answers with the baseline's pipeline (rag/baseline.py), so the index must be built, and calls Claude with the key
in CLAUDE_KEY.
"""

import argparse

from arbiter.arbiter import build_arbiter
from arbiter.generation.registry import BASELINE_GENERATOR, list_generator_names
from arbiter.reply_text import format_reply
from dataset.paths import repo_root
from rag.baseline import BASELINE


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m arbiter")
    commands = parser.add_subparsers(dest="command", required=True)
    ask = commands.add_parser("ask", help="answer one question with the baseline's pipeline")
    ask.add_argument("question")
    ask.add_argument("--generator", default=BASELINE_GENERATOR, choices=list_generator_names(),
                     help=", ".join(list_generator_names()))
    arguments = parser.parse_args(argv)

    arbiter = build_arbiter(BASELINE, arguments.generator, repo_root())
    print(format_reply(arbiter.answer_question(arguments.question)))


if __name__ == "__main__":
    main()
