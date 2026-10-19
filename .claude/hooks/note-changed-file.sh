#!/usr/bin/env bash
# PostToolUse hook, after an agent edits or writes a file: notes the file, as its repository's root and its path from
# that root, so that run-fast-tests.sh knows which test suites the turn calls for. Each session has its own list in
# the temp folder. Files changed through a shell command are not seen.
source "$(dirname "${BASH_SOURCE[0]}")/hook-input.sh"

file="$(read_field file_path | tr '\\' '/')"
[[ -n "$file" ]] || exit 0
folder="$(dirname "$file")"
root="$(git -C "$folder" rev-parse --show-toplevel 2>/dev/null)" || exit 0
from_root="$(git -C "$folder" rev-parse --show-prefix)$(basename "$file")"

list_folder="${TMPDIR:-/tmp}/shardbound-hooks"
mkdir -p "$list_folder"
printf '%s\t%s\n' "$root" "$from_root" >>"$list_folder/$(read_field session_id | tr -cd '[:alnum:]-').changed"
