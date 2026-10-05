# Reality lens — private category and attempt-limit closure, 2026-10-01

**Verdict: PASS.** Scope: only the owner answer «1 да 2 приватные давай», AD-29 and the O4 closure delta against `/tmp/schoolquiz-private-closure-20261001/main/ARCHITECTURE-SPINE.md`. No runtime or broad code audit.

- [AD-29](../ARCHITECTURE-SPINE.md) now names the category entry «Приватные квизы» and retains the individual flat HARD quiz. Its technical prerequisite explicitly preserves technical IDs; this is accepted product copy, not a schema or route migration.
- Changing the author limit preserves accumulated started-attempt count. An already authorized active attempt may finish under its original deadlines/AD-31. A new start atomically requires both current access and startedCount below the current limit, then increments the count. Raising the limit permits only the remaining difference; lowering it neither resets history nor terminates the active attempt. Existing receipt retry remains the same operation. These rules match the owner answer without introducing a new technology or asserting runtime readiness.
- Moving O4 out of product questions is supported: the remaining row names the settings/start, counter and receipt adapter work. Future renewal, re-enable or rotation are not made mandatory questions. Competition admission and championship rights remain outside this individual-quiz delta.
- EXPERIENCE category/F11/O4, DESIGN category copy and existing limit control, and implementation-handoff mirror the decisions. Current copy takes precedence over historical HTML without editing snapshots, technical IDs or unrelated quests. UX memlog records the two decisions separately.

No blocking finding. The named private server adapters still require implementation; closed product decisions do not claim implemented behavior.
