@AGENTS.md

## Claude Code

The instructions above are shared with every coding agent. On top of them, Claude Code loads a skill of `.claude/skills/` by itself when a task matches its description, and `.claude/settings.json` runs these hooks, from `.claude/hooks/`:

- Before each shell command, `guard-test-split.sh` refuses a command that runs on the test split. The maintainer runs it, in a session started with `SHARDBOUND_ALLOW_TEST_SPLIT=1`.
- After each file an agent edits, `note-changed-file.sh` notes it. When the turn ends, `run-fast-tests.sh` runs the content and AI tests these files call for, and sends the agent back to work while one fails.

Three review agents in `.claude/agents/` read a change and report its problems, without changing anything: a hook of theirs, `allow-read-only-commands.sh`, refuses any shell command that does not read. Call them before a commit, and the pull request review calls them too:

- `rules-reviewer`: the rule IDs, proposed rules, the engine's trace and the tests named by rule ID, the cards' closed lists.
- `eval-integrity-auditor`: nothing chosen on the test split, answer keys from the engine, frozen dataset versions, the split by interaction.
- `docs-sync-checker`: every doc the change calls for, and every path the instructions cite.
