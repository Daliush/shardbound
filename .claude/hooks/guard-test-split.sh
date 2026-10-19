#!/usr/bin/env bash
# PreToolUse hook, before each shell command: refuses a command that runs on the test split. The test split only gives
# the published numbers and is never used to choose anything (AGENTS.md), so an agent does not run it. The maintainer
# does, in a session started with SHARDBOUND_ALLOW_TEST_SPLIT=1: a command cannot set this variable for the hook,
# which reads the session's own environment. A guardrail against a mistake, not against an agent that tries to get by.
source "$(dirname "${BASH_SOURCE[0]}")/hook-input.sh"

if [[ "${SHARDBOUND_ALLOW_TEST_SPLIT:-}" == 1 ]]; then
    exit 0
fi
if grep -Eq -- '--split(=|[[:space:]]+)test([^[:alnum:]_-]|$)' <<<"$(read_field command)"; then
    echo "The test split only gives the published numbers, and nothing is chosen on it (AGENTS.md): run on dev." \
         "The maintainer runs the test split." >&2
    exit 2
fi
