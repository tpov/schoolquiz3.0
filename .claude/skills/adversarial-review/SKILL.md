---
name: adversarial-review
description: Cross-model adversarial review for design and implementation changes. Use when running review for a feature plan, design, implementation, or architecture decision, especially when the workflow calls for Skeptic, Breaker, Architect, Minimalist, or Realist lenses via Codex CLI or Claude CLI.
disable-model-invocation: true
---

# Adversarial Review

This skill defines the canonical adversarial review protocol.
Reviewers are critics, not implementers: they hunt for real bugs, boundary failures, and modeling mistakes.

Load `references/cli-protocol.md` when you need the exact CLI commands, lens details, reviewer prompt template, verdict logic, or lead-judgment rules.

## Core Rules

- Run reviewers through external CLI only (`codex exec` preferred, `claude -p` fallback).
- Never use shared-context subagents for this review.
- Use cross-model review whenever possible to reduce blind spots.
- Design review must include the Realist lens.
- Reviewers produce findings only; they do not edit code.

## Gotchas

- Same-model review often shares the same modeling mistakes as the author.
- A review without exact `file:line` or `design-doc:section` evidence is not actionable.
- If the CLI is unavailable, stop and report that blocker instead of silently downgrading the review.
