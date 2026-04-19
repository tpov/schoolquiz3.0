---
name: <skill-name>
# name rules (agentskills.io spec): lowercase a-z + hyphens, no --, no leading/trailing -, max 64 chars, must match directory name
description: <what the skill does>. Use when <the user phrasing and contexts that should trigger it>.
# max 1024 chars, model-facing trigger text
#
# Portable fields (agentskills.io spec — work across all compatible agents):
# license: Apache-2.0
# compatibility: Requires Android SDK 35+, Jetpack Compose
# metadata:
#   author: team-name
#   version: "1.0"
#   keywords: [android, compose]
#
# Claude Code extensions (ignored by other agents):
# argument-hint: [expected input]
# disable-model-invocation: true
# allowed-tools: Read, Grep, Glob
# context: fork
# agent: <agent-type>
# hooks:
#   PreToolUse:
#     - matcher: <tool matcher>
#       hooks:
#         - type: command
#           command: <command>
---

# <Skill Title>

One short paragraph on the role of the skill and the kind of jobs it should handle.

## Workflow

1. State the primary pattern and why it fits.
2. Load only the references or assets needed for this task.
3. Gather missing inputs if the task cannot be completed safely without them.
4. Produce the output or perform the workflow.
5. Validate the result before finalizing.

## Gotchas

- <undertrigger risk>
- <common failure mode>
- <dangerous assumption to avoid>

## Deliverable

- <expected output shape>
- <what to mention about assumptions, validation, or next steps>
