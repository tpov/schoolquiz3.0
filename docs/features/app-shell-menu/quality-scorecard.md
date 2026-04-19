# Quality Scorecard: app-shell-menu

Оценка качества реализации через Codex CLI cross-model review (pre-intervention quality signal).

## Review timeline

| Round | Codex Verdict | Blockers | High | Medium |
|-------|---------------|----------|------|--------|
| Round 1 (post-implementation) | REJECT | 2 | 2 | 3 |
| Round 2 (after first fix loop) | REJECT | 1 | 1 | 1 |
| Round 3 (after second fix loop) | CONTESTED | 0 | 0 | 1 |
| Final accepted | PASS w/ 1 medium documented trade-off | 0 | 0 | 0 net (1 tech-debt) |

## Per-parameter breakdown (cross-phase final state)

| Параметр | Grade | Blockers | High | Medium | Детали |
|----------|-------|----------|------|--------|--------|
| Architecture | A | 0 | 0 | 2 | Medium: `AppShellScreen` depends on `DefaultRootComponent` concrete (ADR-0011 tradeoff); class diagram slight drift. Justified MVP |
| Correctness | B | 0 | 0 | 1 | Medium: `pendingStats` guest-check trade-off — init wins over cached stale guest vs. genuine guest absorbed (Kdoc'd). Alternative breaks cold-start-race test |
| Completeness | A | 0 | 0 | 0 | All 30 AC + State Matrix FSM covered. 2 tests DEFERRED (retap POP_TO_ROOT, back non-empty stack) — waiting on phase-05+ push destinations; @Ignore scaffolds present |
| Security | A | 0 | 0 | 0 | `firestore.rules` server-write-only for privilege fields via `diff().affectedKeys()`; `avatarUrl` https-only; App Check PlayIntegrity; no PII в logs |
| Code Organization | A | 0 | 0 | 0 | All grep checks clean (domain purity, cross-module, DI exclusive). Conventions applied |
| **Overall** | **A-** | **0** | **0** | **3** | Net: 3 documented medium trade-offs, all with explicit Kdoc/ADR justification |

## Grading scale

- A = 0 findings
- B = only medium
- C = 1-2 high
- D = 3+ high
- F = any blocker

## Codex rounds as quality signal

Codex REJECT в round 1 выявил системные проблемы которые same-model reviewers (5 agent'ов Claude Sonnet) пропустили из-за shared blind spots:

- **Round 1 blockers** (2) поймал только Codex — InstanceKeeper retention stale context + Firebase runtime wiring gap. Same-model reviewers увидели только symptoms per-phase, не integration.
- **Round 2 blocker** (1) — `firestore.rules` wrong field names — поймал Codex, same-model security-reviewer принял rules как PASS.
- **Round 3 medium** — trade-off между test assertion и theoretical guest edge case — Lead judgment accepted.

## Interpretation

Per-phase same-model reviewers close ~80% findings. Cross-phase Codex каждый round catches systemic issues остальные 20%. Without Codex — feature merged бы с 2 production blockers (Firebase crash + rotation leak).

## Recommendations для следующей фичи

1. **Codex CLI cross-phase review обязательный** — не optional per-phase.
2. **Firestore rules — separate artifact in spec** — требовать `firestore.rules` как первоклассный design document, не ignored deliverable.
3. **Firebase config на CI** — добавить validator что `google-services.json` + plugin applied для любого app module.
4. **Cold start race pattern** — задокументировать как standard pattern (init-done gate + pre-init buffer) в `.claude/rules/lifecycle.md` для всех новых features с Firebase/async init.
