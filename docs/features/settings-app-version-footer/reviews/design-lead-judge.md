---
date: 2026-07-26
ticket: SCH-2
feature: settings-app-version-footer
phase: design
lead: Kent
verdict: ready-for-human-approval
---

# Lead Judge Synthesis — Settings App Version Footer

## Verdict

Ready for human approval. The design pack has no open blocker/high findings.

## Architect Debate

Run ledger:

| Role | Session | Primary artifacts |
|---|---|---|
| High-level architect | `dfd362a4-f528-407a-9aa3-b426b9645b07` | `design-drafts/high-level.md`, `01-architecture.md`, `02-behavior.md`, `03-decisions.md` |
| Component architect | `799c9576-f8c0-4ab7-90db-b633ccc7439d` | `design-drafts/component.md`, `04-testing.md`, `06-api-contract.md` |

Named role launch was unavailable in this Kent session, so the lead launched two headless Kent subagents with explicit `architect-high-level` and `architect-component` role instructions and their role-file paths. The subagents worked in separate draft/final write scopes and converged through relay round 1.

Resolved debate points:

- Keep app build metadata ownership in `apps/android-next`.
- Thread primitive `appVersionName` and required `appVersionCode` through the existing UI render path.
- Model the real private shell hop: `AppShellScreen` → `AppShellContent` → `LocalTabContent` → `DesignSettingsScreen`.
- Render the settings footer in `DesignSettingsScreen`, pinned to the scaffold-padded settings root, not raw screen bounds and not as a final scroll item.
- Keep the settings footer display-only and separate from drawer `DrawerFooter` About/developer-mode behavior.
- Create mandatory `06-api-contract.md` as internal Compose UI signature SSoT while declaring external/backend/domain API N/A.
- Do not add domain/data/DI/storage/events/backend work or new Gradle dependencies.

Web prior-art was skipped because SCH-2 introduces no new external SDK, platform API, library behavior, server behavior, or external integration. This matches `1-research.md` and both architect drafts.

## Cross-Model Reviews

| Lens | Report | Final verdict | Notes |
|---|---|---|---|
| Realist | `reviews/design-realist.md` | PASS | Initial blockers on Gradle graph/theming and missing `AppShellContent` were fixed and rechecked. |
| Skeptic | `reviews/design-skeptic.md` | PASS | Initial medium/low findings on ADR precision were fixed and rechecked; final report has no findings. |
| Architect | `reviews/design-architect.md` | PASS | Initial testing-strategy findings were fixed and rechecked; final report has no findings. |
| Module direction audit | `reviews/design-module-direction.md` | PASS | No SCH-2 module-direction blocker/high findings. |

## Quality Gate Evidence

- Mandatory docs present: `01-architecture.md`, `02-behavior.md`, `03-decisions.md`, `04-testing.md`, `06-api-contract.md`.
- Conditional docs not needed: `07-events.md` and `08-storage-model.md` are N/A by spec, research, and ADRs.
- Mermaid diagrams present in `01-architecture.md` and `02-behavior.md`.
- Defensive hooks passed:
  - `.claude/hooks/check-c4-vs-gradle.sh docs/features/settings-app-version-footer/01-architecture.md`
  - `.claude/hooks/check-api-contract-types.sh docs/features/settings-app-version-footer/06-api-contract.md`
- No hopeful markers found in final design docs: `TODO`, `TBD`, `UNRESOLVED`, `REQUIRES VERIFY`, or question marks.
- No canonical Kotlin signatures outside `06-api-contract.md`.
- ADR-to-contract type audit is clean; `03-decisions.md` contains no tracked new type names requiring extra canonical entries.
- Multi-path state machine gate is N/A: this feature has a single existing settings entry path and no domain state machine.

## Lead Decision

Approve the design for implementation after human confirmation. The next phase should implement the internal UI signature changes, update all call sites and preview/test surfaces, add deterministic footer placement/display-only coverage, then run the validation commands from `04-testing.md`.
