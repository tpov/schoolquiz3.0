### Finding #1 — Sync Topology Was Not Actually Evaluated
- Severity: HIGH
- ADR affected: HLA-04
- Weakness: The ADR compares interface placement and dependency direction, but skips the real topology choice: one aggregate worker vs per-entity workers vs direct repo injection for the current 2-entity scope.
- Evidence: [03-decisions.md:88](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:88) — "`worker должен синхронизировать несколько сущностей (UserStats, Catalog, потенциально другие)`"; [03-decisions.md:106](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:106) — rejected options cover only direct platform dependencies and moving the worker into `shared/core/sync`.
- Missing alternative: `UserStatsSyncWorker` + `CatalogSyncWorker`, or a single `SyncWorker` with direct repo injection for the current MVP scope.
- Action: Add an explicit comparison of worker topologies and explain why one aggregate `SyncWorker` is required for `SyncNow` semantics and dev-mode reset, not just why `platform` should avoid feature imports.

### Finding #2 — Accepted ADR Still Leaves `CatalogDisplayItem` Ownership Undecided
- Severity: HIGH
- ADR affected: L3-03
- Weakness: This ADR is supposed to settle the `pictureUrl` delivery boundary, but the owning module is still written as an unresolved either/or.
- Evidence: [03-decisions.md:265](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:265) — "`CatalogDisplayItem` как presentation model в `android:core:designsystem` (или в presentation layer)"
- Missing alternative: Explicit evaluation of `android:core:designsystem` ownership vs feature-presentation ownership.
- Action: Pick one canonical home for `CatalogDisplayItem`, define who owns the `CatalogEntity -> CatalogDisplayItem` mapper, and cross-reference HLA-07 so the boundary is fully closed.

### Finding #3 — Central `AppDatabase` Rationale Leans on Future-Proofing, Not Current Project Cost
- Severity: MEDIUM
- ADR affected: HLA-03
- Weakness: The rejection of per-feature databases is justified with hypothetical future joins and legacy precedent, while the main downside of a shared DB, cross-feature migration coordination, is not documented.
- Evidence: [03-decisions.md:74](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:74) — "`Миграции ... централизованы в одном месте.`"; [03-decisions.md:78](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:78) — "`Отклонено: не масштабируется; в long run потребуется cross-database queries; нарушает 'central AppDatabase' паттерн из legacy.`"
- Missing alternative: Per-feature databases with shared scheduling/composition-root wiring and an explicit MVP constraint of “no cross-database joins”.
- Action: Add the current-project reasons single DB wins now, and record the downside/owner model for migrations so the ADR documents the tradeoff rather than only the upside.

### Finding #4 — HLA-07 Is Marked Accepted While Still Containing a “Pending” Boundary
- Severity: MEDIUM
- ADR affected: HLA-07
- Weakness: The ADR resolves where URLs are produced, but not how they legally reach UI; as written, the accepted decision still contains an unresolved handoff.
- Evidence: [03-decisions.md:168](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:168) — "`UI получает `pictureUrl` через... pending: см. ниже.`"; [03-decisions.md:170](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:170) — "`Это decision для architect-component: как доставить `pictureUrl` в UI`"
- Missing alternative: N/A
- Action: Update HLA-07 to point explicitly to the follow-on ADR that closes this gap and state that this is the formal resolution of the spec’s open “repository vs loader-level URL resolution” question.

### Finding #5 — `_tapProgress` Decision Promotes a Hypothetical Failure Mode into a Requirement
- Severity: MEDIUM
- ADR affected: L3-02
- Weakness: The alternatives section treats recomposition/drawer close/navigation survival as if the spec requires it, but the ADR does not show that this gesture must outlive those UI lifecycle events.
- Evidence: [03-decisions.md:247](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:247) — "`State survives drawer close/reopen`"; [03-decisions.md:252](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:252) — "`tap progress сбрасывается при recompose/drawer close/navigate; нарушает spec '10 последовательных тапов'`"
- Missing alternative: Keep `TapProgress` in drawer-local UI state and let the component own only activation/result side effects.
- Action: Either tie component ownership to a concrete requirement from spec/research, or state plainly that this is a testability/consistency preference rather than a spec-driven necessity.

### Finding #6 — No-Overlay ADR Records Simplification but Not the Escape Hatch
- Severity: LOW
- ADR affected: HLA-02
- Weakness: The ADR captures “why simpler now,” but not “how reversible later”; if User Decision #2 is revisited, the decision record does not say what boundary was intentionally kept stable.
- Evidence: [03-decisions.md:44](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:44) — "`Удалить ... LocalDeveloperOverride...`"; [03-decisions.md:56](/home/Programming/Android/schoolquiz4.0/docs/features/menu-refactor/03-decisions.md:56) — "`Отклонено user: усложняет модель без значимой пользы для MVP. 'Симплее' — прямая запись.`"
- Missing alternative: N/A
- Action: Add one sentence naming the reversible seam, for example `setLocalDeveloperLevel(...)` / activation callback, so a future overlay can be reintroduced without changing the gesture contract or footer API.

- Total findings: 6 (blocker: 0, high: 2, medium: 3, low: 1)
- Verdict: CONTESTED
- Overall commentary on rigor of "Alternatives Considered" sections: VERIFIED: HLA-01, HLA-05, L3-01, and L3-04 have solid alternatives sections tied to clean-architecture rules or concrete runtime failure modes. The weaker ADRs tend to either future-proof abstractly without project-specific cost accounting, or mark a decision accepted while still leaving the last ownership/boundary question unresolved.