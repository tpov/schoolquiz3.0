# Adversarial Review

## When to use

During review phase of design or implementation. Turns review into adversarial analysis: reviewers actively try to break the result.

## Key principle

Reviewers attack from different lenses. Final deliverable: synthesized verdict with lead judgment. Reviewers do NOT make changes — they find problems.

**Hard constraint:** reviewers MUST run via external CLI (`codex exec` or `claude -p`). Do not use subagents or Agent tool — they share your context and blind spots.

## Pre-flight check (MANDATORY)

```bash
if command -v codex &>/dev/null; then
  REVIEWER_CLI="codex"
elif command -v claude &>/dev/null; then
  REVIEWER_CLI="claude"
else
  echo "ERROR: Neither codex nor claude CLI found."
  exit 1
fi
```

If NO CLI found — STOP and tell the user. Do NOT fallback to Agent tool.

**Output ВСЕГДА в файл.** Никогда не выводить review в stdout. Используй `-o` для Codex и `>` redirect для Claude CLI. Output большой — без файла приходится перезапускать.

## CLI execution

**Primary: Codex CLI** (cross-model, catches Claude blind spots):
```bash
codex exec "$REVIEWER_PROMPT" --sandbox read-only --ephemeral -o "$REVIEW_DIR/skeptic.md"
```

**Fallback: Claude CLI** (same model, fresh session):
```bash
claude -p "$REVIEWER_PROMPT" --model sonnet --permission-mode plan \
  --no-session-persistence --allowedTools "Read,Grep,Glob" > "$REVIEW_DIR/skeptic.md"
```

## Reviewer lenses

| Change size | Lines | Files | Lenses |
|-------------|-------|-------|--------|
| Small | <50 | 1-2 | Skeptic only |
| Medium | 50-200 | 3-5 | Skeptic + Architect |
| Large | 200+ | 5+ | Skeptic + Architect + Minimalist |
| Design review | any | any | All 4 including Realist |

### Skeptic — "Why does this exist? What breaks if it fails?"
### Architect — "Does this violate architectural boundaries?"
### Minimalist — "Can this be done with less code?"
### Realist — "Does the design model match what the code actually does?"

## Verdict logic

| Verdict | Condition | Action |
|---------|-----------|--------|
| PASS | No high/blocker findings | Continue |
| CONTESTED | High findings, reviewers disagree | Lead judgment |
| REJECT | Blocker or consensus high | Fix loop |
