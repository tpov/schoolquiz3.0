# Design Patterns

Use this file to choose the content shape of a skill before you write it.

## Skill Or Rule?

Before choosing a skill pattern, decide whether the content should be a rule instead:

- Use `.claude/rules/` for always-on project standards, coding conventions, architecture boundaries, and path-scoped guidance.
- Use a skill when the instructions are optional, task-shaped, or reusable as a workflow the model should load only when relevant.
- If the content answers "how should we work in this repo most of the time?", it is usually a rule.
- If the content answers "what should happen for this specific kind of task?", it is usually a skill.

## 0. Domain Knowledge

Use when the skill's value is in concrete examples, code patterns, and checklists — not in process or orchestration.

Typical layout:
- `SKILL.md` contains steps and routing to references
- `references/` contains RIGHT/WRONG code examples, grouped by topic

Best for:
- framework migration guides (edge-to-edge, Compose migration)
- API usage patterns with code samples
- platform-specific conventions with concrete examples

Watch out for:
- stuffing all examples into one SKILL.md instead of splitting by topic into `references/`
- adding process ceremony (patterns, gotchas, inversion) when examples speak for themselves

This type does NOT require:
- explicit pattern selection
- `## Gotchas` section (RIGHT/WRONG examples serve the same purpose)
- inversion phase or approval checkpoints

## 1. Tool Wrapper

Use when the skill should make the agent reliable with a library, framework, CLI, SDK, internal API, or team convention.

Typical layout:
- `SKILL.md` explains when to load the wrapper and how to apply it
- `references/` contains conventions, gotchas, code snippets, API notes

Best for:
- internal libraries
- framework conventions
- design systems
- SDK usage and footguns

Watch out for:
- giant tutorials inside `SKILL.md`
- repeating obvious library knowledge instead of org-specific rules

## 2. Generator

Use when the output must be structurally consistent across runs.

Typical layout:
- `assets/` contains the template
- `references/` contains style rules or field definitions
- `SKILL.md` orchestrates loading, variable collection, and population

Best for:
- reports
- commit messages
- architecture stubs
- standard documents

Watch out for:
- embedding the whole template inline when it should live in `assets/`
- generating before collecting missing variables

## 3. Reviewer

Use when the skill should evaluate something against explicit criteria and report findings by severity.

Typical layout:
- `references/review-checklist.md` or another rubric file
- `SKILL.md` defines review flow and output structure

Best for:
- PR review
- security audits
- checklist-based QA
- policy validation

Watch out for:
- style nitpicks without rationale
- findings without severity or evidence

## 4. Inversion

Use when the agent should interview the user before acting.

Typical layout:
- `SKILL.md` defines phases and gating questions
- optional `assets/` template for the final synthesis

Best for:
- planning
- requirement capture
- risky or ambiguous workflows
- tasks with hidden constraints

Watch out for:
- asking questions whose answers already exist in the conversation
- long questionnaires when only 1-2 missing facts matter

## 5. Pipeline

Use when skipped steps cause failure and the skill must enforce sequencing.

Typical layout:
- `SKILL.md` defines ordered stages and checkpoints
- `references/` and `assets/` are loaded only at the step where they matter

Best for:
- multi-stage documentation
- deployment workflows
- migration playbooks
- complex transformations

Watch out for:
- no checkpoint between risky phases
- too many brittle steps that prevent adaptation

## Pattern Composition

Patterns are composable, but every extra pattern must earn its keep.

Good combinations:
- `Inversion + Generator`: gather missing variables, then fill a template
- `Pipeline + Reviewer`: produce output, then audit it before finalizing
- `Tool Wrapper + Reviewer`: evaluate code against a framework-specific checklist
- `Inversion + Pipeline`: gather requirements first, then execute a phased workflow

Avoid:
- combining every pattern "just in case"
- hiding the primary pattern from the reader

## Anthropic-Style Content Rules

These rules usually matter more than the exact folder layout:

- Don't state the obvious. Add only knowledge that changes model behavior.
- Build a `Gotchas` section from real failure modes.
- Use the filesystem as progressive disclosure:
  - `references/` for detailed rules and docs
  - `assets/` for templates and output material
  - `scripts/` for deterministic or repetitive logic
- Avoid railroading. Give guardrails, not needless ceremony.
- Think through setup. If a skill needs config, teach it how to detect missing config and ask for it.
- Treat `description` as model-facing trigger text, not a human summary.
- If the skill needs memory, prefer a stable location such as `${CLAUDE_PLUGIN_DATA}` rather than mutable files inside the skill folder.

## Quick Decision Guide

Choose the primary pattern by the most important failure mode:
- Concrete code examples are the main value -> Domain Knowledge
- Missing domain knowledge -> Tool Wrapper
- Inconsistent output shape -> Generator
- Unstructured critique -> Reviewer
- Acting before enough context -> Inversion
- Skipped steps or unsafe ordering -> Pipeline
