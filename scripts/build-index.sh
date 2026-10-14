#!/usr/bin/env bash
# Builds the Arbiter's RAG index from end to end:
#   1. engine/corpus (Java) writes the chunks into ai/.corpus/chunks.jsonl;
#   2. Docker starts the database, Postgres with pgvector (the db service of compose.yaml);
#   3. ai/rag (Python) saves the chunks, then embeds the ones that changed, for every local embedding model.
#
# Usage, from anywhere in the repository (on Windows, from Git Bash or with "bash scripts/build-index.sh"):
#   scripts/build-index.sh
#
# Needs JDK 21, uv and a running Docker. The first run downloads each embedding model once (Qwen3-Embedding-0.6B:
# about 1.2 GB, kept in the Hugging Face cache). A run where nothing changed embeds nothing.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "1/3 engine/corpus: the chunks"
(cd "$root/engine" && ./mvnw -q -pl corpus -am compile exec:java)

echo "2/3 the database"
(cd "$root" && docker compose up -d --wait db)

echo "3/3 ai/rag: the vectors"
(cd "$root/ai" && uv run python -m rag index)
