#!/usr/bin/env bash
# Evaluates the Arbiter's retrievers from end to end, from the rulebook and the cards to the saved evaluation:
#   1. scripts/build-index.sh writes the chunks, starts the database and embeds the chunks that changed;
#   2. ai/rag (Python) scores the retrievers on a dataset split and saves the evaluation as a new file,
#      evals/reports/<version>/<split>/<timestamp>.json, which the game server serves (GET /api/evaluations/…).
#
# Usage, from anywhere in the repository (on Windows, from Git Bash or with "bash scripts/evaluate.sh"):
#   scripts/evaluate.sh [retriever…] [--version <version>] [--split dev|test]
#
# The arguments go to "python -m rag evaluate": every retriever, on the dev split of the latest dataset, unless they
# say otherwise; --split test only for the published numbers. The dataset itself is built by scripts/build-dataset.sh.
# Needs JDK 21, uv and a running Docker. An evaluation records the commit it scored at, marked dirty when the code
# has uncommitted changes: commit first to keep its result.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! docker version >/dev/null 2>&1; then
    echo "Docker is not running: start it first, the index lives in its database." >&2
    exit 1
fi
if [[ -n "$(git -C "$root" status --porcelain --untracked-files=no -- . ':(exclude)evals/reports')" ]]; then
    echo "Uncommitted changes: the evaluation will record its commit as dirty." >&2
fi

echo "1/2 scripts/build-index.sh: chunks, database and vectors"
bash "$root/scripts/build-index.sh"

echo "2/2 ai/rag: the evaluation"
(cd "$root/ai" && uv run python -m rag evaluate "$@")
