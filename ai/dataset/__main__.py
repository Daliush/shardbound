"""`uv run python -m dataset build <version>`: see ai/README.md."""

import argparse
from pathlib import Path

from dataset.build import build
from dataset.llm import CachedClient, FakeClient, OpenAiCompatibleClient
from dataset.paths import dataset_dir, repo_root

BASE_URL = "LUNA_BASE_URL"
MODEL = "LUNA_MODEL"
API_KEY = "LUNA_API_KEY"


def main() -> None:
    parser = argparse.ArgumentParser(prog="python -m dataset")
    commands = parser.add_subparsers(dest="command", required=True)
    build_command = commands.add_parser("build", help="build evals/datasets/<version>/ from the engine's files")
    build_command.add_argument("version")
    build_command.add_argument("--fake", action="store_true", help="a dry run with the fake model, without a key")
    build_command.add_argument("--overwrite", action="store_true", help="build a version that already exists")
    build_command.add_argument("--workers", type=int, default=8, help="model calls in parallel")
    args = parser.parse_args()

    root = repo_root()
    if args.fake:
        client = FakeClient()
    else:
        client = CachedClient(OpenAiCompatibleClient.from_environment(BASE_URL, MODEL, API_KEY),
                              Path(__file__).resolve().parents[1] / ".cache" / "llm")
    manifest = build(root, dataset_dir(root, args.version), client, workers=args.workers, overwrite=args.overwrite)
    counts = manifest["counts"]
    print(f"{counts['total']} questions written to evals/datasets/{args.version}/ "
          f"({counts['by_split'].get('dev', 0)} dev, {counts['by_split'].get('test', 0)} test); "
          f"{manifest['wording']['template_kept']} kept their template, "
          f"{manifest['wording']['rule_questions_dropped']} rule questions dropped.")


if __name__ == "__main__":
    main()
