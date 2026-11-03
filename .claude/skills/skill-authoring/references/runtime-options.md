# Runtime Options

Load this file only when you need frontmatter or runtime behavior beyond `name` and `description`.

## Frontmatter Defaults

Default to the smallest useful frontmatter:
- `name` — required, max 64 chars, lowercase a-z + hyphens, no `--`, no leading/trailing `-`, must match directory name
- `description` — required, max 1024 chars, model-facing trigger text

Only add other fields when they materially improve behavior.

## Portable Fields (agentskills.io spec)

These fields work across all compatible agents (Claude Code, Gemini CLI, Cursor, Codex, etc.):

### `license`

Use when the skill may be shared or published.

Good use:
- `license: Apache-2.0`
- `license: Proprietary. LICENSE.txt has complete terms`

### `compatibility`

Use when the skill requires specific environment, tools, or platform. Max 500 chars.

Good use:
- `compatibility: Requires Android SDK 35+, Jetpack Compose`
- `compatibility: Requires Python 3.14+ and uv`

Skip for skills with no special requirements.

### `metadata`

Arbitrary key-value map for author, version, keywords, etc.

Good use:
```yaml
metadata:
  author: team-name
  version: "1.0"
  keywords: [android, compose, edge-to-edge]
```

Useful for discovery and catalog tools.

## Claude Code Extensions

The following fields are specific to Claude Code and will be ignored by other agents. Mark them clearly in the skill if portability matters.

## Common Options

### `argument-hint`

Use when the skill is likely to be invoked manually and the expected argument shape is not obvious.

Good use:
- `argument-hint: [service-name]`
- `argument-hint: [path to file or feature description]`

### `disable-model-invocation: true`

Use when the skill should run only on explicit user intent, especially if it can:
- deploy
- mutate shared state
- create tickets or posts
- run risky commands

Avoid for harmless advisory skills that should auto-trigger.

### `allowed-tools`

Use to narrow capability when the skill benefits from strict tool boundaries.

Good use:
- review-only skills limited to read/search tools
- browser-only research skills

Avoid long, speculative lists when the default tool access is fine.

### `context: fork` and `agent`

Use when the skill should execute in a subagent instead of the current session.

Good use:
- fresh-eyes review
- isolated side tasks
- long-running background analysis

Avoid unless the separation is intentional and useful.

### `hooks`

Use for session-scoped automation that should activate only while the skill is active.

Good use:
- safety rails for dangerous commands
- temporary write restrictions
- lightweight telemetry

Avoid hooks that would be annoying if they triggered on every session.

### `model` and `effort`

Override only when the skill truly needs a different reasoning budget or model behavior.
Default to session settings when possible.

## Safety Heuristics

- If the skill has meaningful side effects, prefer explicit checkpoints.
- If the skill stores data, keep it outside the upgrade-prone skill folder.
- If the skill can surprise the user, the design is wrong.
