# Sourced by the hooks of .claude/settings.json and .claude/agents/: reads the hook's JSON input once, from stdin.
# It reads string fields with sed, since Git Bash on Windows has no jq.

hook_input="$(cat)"

# The value of a string field of the input, unescaped: read_field session_id.
read_field() {
    local backslash=$'\001'
    sed -nE 's/.*"'"$1"'"[[:space:]]*:[[:space:]]*"(([^"\\]|\\.)*)".*/\1/p' <<<"$hook_input" | head -n 1 \
        | sed -e "s/\\\\\\\\/$backslash/g" -e 's/\\"/"/g' -e 's/\\n/\n/g' -e 's/\\t/\t/g' -e "s/$backslash/\\\\/g"
}
