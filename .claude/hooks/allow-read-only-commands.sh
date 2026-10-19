#!/usr/bin/env bash
# PreToolUse hook of the review agents (.claude/agents/): lets a shell command through only when each of its parts
# reads, since an agent that reviews a change never changes the repository. A part reads when it starts with a command
# of READ_ONLY, does not redirect into a file and runs no command substitution. A guardrail, not a sandbox.
source "$(dirname "${BASH_SOURCE[0]}")/hook-input.sh"

READ_ONLY='^(git (diff|log|show|status|blame|ls-files|rev-parse|merge-base|grep)|gh pr (diff|view)|ls|cat|head|tail|wc|grep|rg|sort|uniq|cut|find|python3? scripts/check-doc-paths\.py|cd)([[:space:]]|$)'

command="$(read_field command)"
refuse() {
    echo "A review agent only reads: $1. Allowed: git diff, log, show, status, blame, ls-files, grep; gh pr diff and" \
         "view; ls, cat, head, tail, wc, grep, rg, sort, uniq, cut, find; scripts/check-doc-paths.py." >&2
    exit 2
}

if grep -Eq '\$\(|`' <<<"$command"; then
    refuse "no command substitution"
fi
if sed -E 's/[0-9]?>[[:space:]]*\/dev\/null//g; s/[0-9]?>&[0-9]//g' <<<"$command" | grep -q '>'; then
    refuse "no redirection into a file"
fi
if grep -Eq -- '(^|[[:space:]])-(delete|exec|execdir|fprint)' <<<"$command"; then
    refuse "find only lists"
fi
while IFS= read -r part; do
    part="$(sed -E 's/^[[:space:]]+//' <<<"$part")"
    if [[ -n "$part" ]] && ! grep -Eq "$READ_ONLY" <<<"$part"; then
        refuse "\"${part%% *}\" is not a reading command"
    fi
done < <(sed -E 's/(&&|\|\||[;|])/\n/g' <<<"$command")
