---
name: glm
description: Use the local GLM CLI wrapper when the user explicitly wants to run an external GLM/Z.AI model from Claude Code, send a question to a cheaper sidecar model, or ask how to invoke that local tool on a prompt or file.
argument-hint: "[prompt or task for GLM]"
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Bash(.claude/skills/glm/scripts/glm_query.py *)
---

# GLM Tool Wrapper

Use the skill-local CLI wrapper at `.claude/skills/glm/scripts/glm_query.py`.
This skill is manual-only: run it when the user explicitly asks to use GLM or asks how to run the GLM sidecar tool.

## Before Running

Check that the wrapper can resolve one of these API key names:
- `ZAI_API_KEY`
- `GLM_API_KEY`

The wrapper first checks the current shell environment, then Claude settings files such as `.claude/settings.secrets.json`, `.claude/settings.local.json`, `~/.claude/settings.secrets.json`, and `~/.claude/settings.local.json`.
If neither key is available there, stop and tell the user exactly what is missing.
Do not edit shell profiles or secret files unless the user explicitly asks.

## Default Command

For a normal one-shot prompt, use:

```bash
.claude/skills/glm/scripts/glm_query.py --prompt "$ARGUMENTS"
```

## Common Variants

Use the research profile defined in `PROJECT-CONTEXT.md`:

```bash
# Replace PROFILE with `glm_research_profile` from PROJECT-CONTEXT.md
.claude/skills/glm/scripts/glm_query.py --profile PROFILE --json --prompt "$ARGUMENTS"
```

Use the debug profile defined in `PROJECT-CONTEXT.md`:

```bash
# Replace PROFILE with `glm_debug_profile` from PROJECT-CONTEXT.md
.claude/skills/glm/scripts/glm_query.py --profile PROFILE --json --prompt "$ARGUMENTS"
```

Both profiles use `glm-5` and force thinking on by default. You can still override with `--max-tokens`, `--temperature`, `--model`, or `--thinking off`.

Use a specific model:

```bash
.claude/skills/glm/scripts/glm_query.py --model glm-5 --prompt "$ARGUMENTS"
```

Print normalized JSON instead of plain text:

```bash
.claude/skills/glm/scripts/glm_query.py --json --prompt "$ARGUMENTS"
```

Read the prompt from a file the user explicitly referenced:

```bash
.claude/skills/glm/scripts/glm_query.py --prompt-file path/to/prompt.txt
```

Inspect the prepared request without calling the network:

```bash
.claude/skills/glm/scripts/glm_query.py --dry-run --prompt "$ARGUMENTS"
```

Show CLI usage:

```bash
.claude/skills/glm/scripts/glm_query.py --help
```

## File Handling Rules

- Read only the files needed for the current task.
- Prefer sending compact excerpts or a focused summary instead of dumping large files wholesale.
- Never send secrets, `.env` contents, tokens, private keys, or unrelated internal data.
- If the user asks to send a file, verify the path first and explain briefly what will be sent.

## Response Rules

- If the user asked to "just run GLM", return the GLM output plainly.
- If the user asked a broader question, include a short summary first and then the GLM output if useful.
- If the command fails, report the exact command and the important error message.
