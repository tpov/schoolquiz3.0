---
date: 2026-07-26
ticket: SCH-2
feature: settings-app-version-footer
phase: design
---

# Design Checklist

- [x] Confirm grounding gate: `2-grounding.md` exists and includes Entry Points, Code Owners, Backend/Contract Check.
- [x] Read design node instructions and adversarial-review skill.
- [x] Spawn high-level and component architects in parallel and capture outputs/session ids.
- [x] Relay architect positions/objections until convergence or judge decision.
- [x] Verify required design docs are on disk: `01-architecture.md`, `02-behavior.md`, `03-decisions.md`, `04-testing.md`, `06-api-contract.md`.
- [x] Run defensive hooks if present: `check-c4-vs-gradle.sh`, `check-api-contract-types.sh`.
- [x] Run cross-model Realist review for `01-architecture.md` + `02-behavior.md`.
- [x] Run cross-model Skeptic review for `03-decisions.md`.
- [x] Run cross-model Architect review for `04-testing.md` + conditional docs.
- [x] Run pre-approval quality gates: docs completeness, architecture alignment, cross-model reviews, no hopeful markers, module-direction audit, ADR/API consistency, and conditional multi-path check.
- [x] Synthesize lead-judge verdict.
- [x] Obtain human approval and update docs/README after approval.
