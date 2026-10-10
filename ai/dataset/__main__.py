"""`uv run python -m dataset build <version>`: see ai/README.md."""

import argparse
import os
from pathlib import Path

from dataset.build import build
from dataset.llm import CachedClient, FakeClient, ModelApiError, OpenAiCompatibleClient
from dataset.paths import dataset_dir, repo_root

BASE_URL = "LUNA_BASE_URL"
MODEL = "LUNA_MODEL"
API_KEY = "LUNA_API_KEY"
TEMPERATURE = "LUNA_TEMPERATURE"
REASONING_EFFORT = "LUNA_REASONING_EFFORT"
# Luna 6 (gpt-6-luna) only takes temperature 1, its own; LUNA_TEMPERATURE overrides it for another model.
DEFAULT_TEMPERATURE = 1.0


def main() -> None:
    parser = argparse.ArgumentParser(prog="python -m dataset")
    commands = parser.add_subparsers(dest="command", required=True)
    build_command = commands.add_parser("build", help="word and split evals/datasets/<version>/")
    build_command.add_argument("version")
    build_command.add_argument("--base", help="the previous version, whose wordings and split are kept")
    build_command.add_argument("--fake", action="store_true", help="a dry run with the fake model, without a key")
    build_command.add_argument("--overwrite", action="store_true", help="build a version that already exists")
    build_command.add_argument("--workers", type=int, default=8, help="model calls in parallel")
    args = parser.parse_args()

    root = repo_root()
    if args.fake:
        client = FakeClient()
    else:
        model = OpenAiCompatibleClient.from_environment(BASE_URL, MODEL, API_KEY, TEMPERATURE, REASONING_EFFORT,
                                                        DEFAULT_TEMPERATURE)
        client = CachedClient(model, Path(__file__).resolve().parents[1] / ".cache" / "llm")
        sent = ", ".join(f"{name}={value}" for name, value in model.options.items())
        print(f"Calling {model.model} at {os.environ[BASE_URL]} with {sent}.", flush=True)
    try:
        base = dataset_dir(root, args.base) if args.base else None
        manifest = build(root, dataset_dir(root, args.version), client, base=base, workers=args.workers,
                         overwrite=args.overwrite)
    except ModelApiError as error:
        raise SystemExit(f"{error}\nThe responses received so far are cached; fix the setting and run again.")
    counts = manifest["counts"]
    wording = manifest["wording"]
    print(f"{counts['total']} questions written to evals/datasets/{args.version}/dataset-{{dev,test}}.jsonl "
          f"({counts['by_split'].get('dev', 0)} dev, {counts['by_split'].get('test', 0)} test): "
          f"{wording['reused']} wordings kept from the base, {wording['reworded']} reworded, "
          f"{wording['template_kept']} kept their template.")


if __name__ == "__main__":
    main()
