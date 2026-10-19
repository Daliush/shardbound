#!/usr/bin/env bash
# Checks commit messages against the convention of AGENTS.md: a subject "type(scope): summary" of 72 characters at
# most, with no final period, then a blank line and a body whose lines hold 72 characters at most (a line with a link
# may be longer). Merges, reverts and fixups pass as they are.
#
# Usage, from anywhere in the repository:
#   scripts/check-commit-message.sh <file>          a message in a file, as git's commit-msg hook gives it (.githooks/)
#   scripts/check-commit-message.sh <base>..<head>  every commit of a range but merges, as CI checks a pull request
set -euo pipefail

TYPES="feat|fix|test|docs|ci|refactor|chore"
MAX_LENGTH=72

# The problems of the message read on stdin, one per line; nothing when it follows the convention.
find_problems() {
    local message subject number=0 line
    # Git's comment lines go, and with --verbose everything from its scissors line on.
    message="$(sed '/^# -* >8 -*$/,$d' | grep -v '^#' || true)"
    subject="$(head -n 1 <<<"$message")"
    if [[ "$subject" =~ ^(Merge|Revert|fixup!|squash!|amend!)[[:space:]] ]]; then
        return
    fi
    if ! [[ "$subject" =~ ^($TYPES)(\([a-z0-9-]+\))?:\ [^[:space:]] ]]; then
        echo "the subject is not \"type(scope): summary\", with a type among ${TYPES//|/, }"
    fi
    if (( ${#subject} > MAX_LENGTH )); then
        echo "the subject has ${#subject} characters, $MAX_LENGTH at most"
    fi
    if [[ "$subject" == *. ]]; then
        echo "the subject ends with a period"
    fi
    while IFS= read -r line; do
        number=$((number + 1))
        if (( number == 2 )) && [[ -n "$line" ]]; then
            echo "no blank line between the subject and the body"
        elif (( number > 2 && ${#line} > MAX_LENGTH )) && [[ "$line" != *http* ]]; then
            echo "line $number has ${#line} characters, $MAX_LENGTH at most"
        fi
    done <<<"$message"
}

if (( $# != 1 )); then
    echo "Usage: scripts/check-commit-message.sh <message file> | <base>..<head>" >&2
    exit 2
fi
failed=0
if [[ "$1" == *..* ]]; then
    for commit in $(git rev-list --no-merges --reverse "$1"); do
        problems="$(git log -1 --format=%B "$commit" | find_problems)"
        if [[ -n "$problems" ]]; then
            echo "$(git log -1 --format='%h %s' "$commit")" >&2
            sed 's/^/  - /' <<<"$problems" >&2
            failed=1
        fi
    done
else
    problems="$(find_problems <"$1")"
    if [[ -n "$problems" ]]; then
        echo "The commit message breaks the convention of AGENTS.md:" >&2
        sed 's/^/  - /' <<<"$problems" >&2
        failed=1
    fi
fi
exit "$failed"
