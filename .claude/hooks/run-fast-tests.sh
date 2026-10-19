#!/usr/bin/env bash
# Stop hook, when an agent ends its turn: runs the fast test suites that the files it changed call for, the same paths
# as CI, and sends the agent back to work while one fails:
#   content/                                  the content tests, a few seconds;
#   ai/, evals/, content/ or docs/rules/      the AI tests, about ten seconds.
# The engine and the client take minutes: AGENTS.md says when to run them. Each batch of changed files is tested once,
# so a turn that changes nothing more ends, even if a suite still fails.
source "$(dirname "${BASH_SOURCE[0]}")/hook-input.sh"

list="${TMPDIR:-/tmp}/shardbound-hooks/$(read_field session_id | tr -cd '[:alnum:]-').changed"
[[ -s "$list" ]] || exit 0
changed="$(sort -u "$list")"
rm -f "$list"
if ! command -v uv >/dev/null; then
    echo "uv is not installed: the content and AI tests did not run." >&2
    exit 0
fi

failures=""
while IFS= read -r root; do
    paths="$(awk -F '\t' -v root="$root" '$1 == root { print $2 }' <<<"$changed")"
    suites=()
    if grep -q '^content/' <<<"$paths"; then
        suites+=(content)
    fi
    if grep -Eq '^(ai|evals|content|docs/rules)/' <<<"$paths"; then
        suites+=(ai)
    fi
    for suite in "${suites[@]}"; do
        if ! output="$(cd "$root/$suite" && uv run pytest -q 2>&1)"; then
            failures+="$suite/: uv run pytest failed"$'\n'"$(tail -n 40 <<<"$output")"$'\n\n'
        fi
    done
done < <(cut -f 1 <<<"$changed" | sort -u)

if [[ -n "$failures" ]]; then
    printf 'The tests of the files you changed fail. Fix them, or say why they fail, before you stop.\n\n%s' \
           "$failures" >&2
    exit 2
fi
