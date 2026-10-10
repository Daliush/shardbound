#!/usr/bin/env bash
# Builds a version of the Arbiter's evaluation dataset from end to end, into evals/datasets/<version>/:
#   1. engine/dataset (Java, no model) writes every question with its answer key;
#   2. ai/dataset (Python, Luna 6) rewords the new questions, splits them by group, and writes the two final
#      datasets, dataset-dev.jsonl and dataset-test.jsonl, with manifest.json.
#
# Usage, from anywhere in the repository (on Windows, from Git Bash or with "bash scripts/build-dataset.sh"):
#   scripts/build-dataset.sh <version> [--base <previous version>] [--overwrite] [--fake] [--workers <n>]
#
#   --base       keep the previous version's wordings and split: only new questions go to the model
#   --overwrite  build a version that is already built (it is frozen otherwise)
#   --fake       a dry run with a fake model, no key needed; meant for the "preview" version
#
# The model needs LUNA_BASE_URL, LUNA_MODEL and LUNA_API_KEY; LUNA_REASONING_EFFORT and LUNA_TEMPERATURE are optional.
set -euo pipefail

usage() {
    sed -n '7,12p' "$0" | sed 's/^# \{0,1\}//'
}

if [[ $# -lt 1 || "$1" == -* ]]; then
    usage
    exit 2
fi
version="$1"
shift

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
folder="$root/evals/datasets/$version"

# A built version is frozen: stop before the engine rewrites its files.
if [[ " $* " != *" --overwrite "* ]] && [[ -e "$folder/dataset-dev.jsonl" || -e "$folder/dataset-test.jsonl" ]]; then
    echo "evals/datasets/$version is built: a dataset version is frozen. Pass --overwrite to build it again." >&2
    exit 1
fi

echo "1/2 engine/dataset: questions and answer keys"
(cd "$root/engine" && ./mvnw -q -pl dataset -am compile exec:java -Dexec.args="$version")

echo "2/2 ai/dataset: wording and split"
(cd "$root/ai" && uv run python -m dataset build "$version" "$@")
