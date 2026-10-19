@AGENTS.md

## Claude Code

The instructions above are shared with every coding agent. On top of them, Claude Code loads a skill of `.claude/skills/` by itself when a task matches its description, and `.claude/settings.json` runs these hooks, from `.claude/hooks/`:

- Before each shell command, `guard-test-split.sh` refuses a command that runs on the test split. The maintainer runs it, in a session started with `SHARDBOUND_ALLOW_TEST_SPLIT=1`.
