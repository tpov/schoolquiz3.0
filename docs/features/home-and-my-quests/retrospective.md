# Pipeline Retrospective: home-and-my-quests

## Date
2026-05-01

## Summary

5 фаз реализации прошли все per-phase reviewers PASS, но **cross-phase Codex review** (4 lenses: Skeptic + Realist + Architect + Minimalist) поймал **5 BLOCKERs + 8 HIGH + 9 MEDIUM** findings, которые same-model per-phase review пропустил. Главный класс ошибок — **Modeling Error** (B3, B4, H3, H7) и **Delegated Decision Error** (M1-M9): ADR-assertions фиксировали правила (cv > local filter, Clock.System.now(), cache-busting URL, bootstrap limit), но per-phase reviewers не cross-checked ADR vs реализацию. Дополнительно — **Review Blind Spot** для design-level constraints (Firestore rules, array-contains-any edge case, logout side-effect).

5 из 8 HIGH findings были помечены DEFERRED после реализации — это означает, что pipeline принял implementation с known violations без эскалации пользователю.

## Bugs Analyzed

### Bug #1: Cursor advance ПЕРЕД завершением downstream cascade (BLOCKER B2)

- **Symptom**: `CascadingSyncOrchestrator.kt:119` advances cursor BEFORE subtree success — при failure потомков курсор уже продвинут, следующий retry пропустит детей.
- **Root cause**: ADR-CMP-49 requires "subtree-atomic advance" (`02-behavior.md:447-448`), but code advances cursor unconditionally. TODO маркер явно указывает проблему.
- **Injection point**: **Plan** — Phase-03 plan описал subtree-atomic semantics, но не enforced.
- **Propagation**: Implement carried TODO. Per-phase reviewer (Phase-03) не сравнил ADR с code. Code-reviewer accept TODO как "future work".
- **Detection gap**: Cross-phase Codex review (Skeptic + Architect lens) поймал. Per-phase architect-reviewer не имел checklist "ADR vs code line-by-line".
- **Failure pattern**: **Commit-Before-Action** + **Missing Side-Effect Inventory** (failure consequences не inventoried).

### Bug #2: SSoT split — spec говорит max(dto.lastModifiedAt), код Clock.System.now() (BLOCKER B3)

- **Symptom**: 3 source-of-truth конфликта: `0-spec.md:86` (FR#14) → "max(dto.lastModifiedAt)", `CascadingSyncOrchestrator.kt:67` → `Clock.System.now()`, `03-decisions.md:687` (Amendment) → blesses Clock.now().
- **Root cause**: FR#14 не settled до design phase. ADR-CMP-49 amendment появился в phase-03 (after design approval) для bless code reality. Spec contradicted.
- **Injection point**: **Research** — FR#14 cursor strategy не resolved до design. Design proceeded с ambiguity.
- **Propagation**: Design carried unresolved spec. Implement chose Clock.now() для safety. Phase-03 amendment retroactively blessed code.
- **Detection gap**: Grounding Gate (`2-grounding.md` Independent Verification Protocol) должен был flag spec contradiction до design. Не flagged.
- **Failure pattern**: **Assumption Not Verified** (FR#14 не cross-checked against code expectations).

### Bug #3: cv > local filter отсутствует — каскад срабатывает на stale parents (BLOCKER B4)

- **Symptom**: `SectionRepositoryImpl.kt:32`, `ThemeRepositoryImpl.kt:31`, `LessonRepositoryImpl.kt:31`, `QuestRepositoryImpl.kt:55` возвращают ВСЕ обработанные IDs без фильтра по `cv > local`. Каскад срабатывает на stale parents.
- **Root cause**: ADR-CMP-49:906 явно требует `cv > local` filter. Phase-02 implementation не реализовал. Per-phase architect-reviewer не verified ADR vs code.
- **Injection point**: **Plan** + **Implement** — plan не enforced ADR fidelity, implement trustfully followed pattern из Catalog без version guard.
- **Propagation**: 4 repository implementations skipped guard consistently (mechanical копипаста M6).
- **Detection gap**: Cross-phase Codex (Skeptic + Architect) поймал. Per-phase reviewer не имел grep check для ADR keywords vs code.
- **Failure pattern**: **Modeling Error** (design не отражена в коде) + **Plan Faithfulness**.

### Bug #4: Firestore security rules missing для 5 новых collections (BLOCKER B1)

- **Symptom**: AC#35-40 require client-side rules tests, но `firestore.rules` не содержит блоков для новых collections (quests/sections/themes/lessons/questions). Клиент не может читать.
- **Root cause**: Plan README:83 marked AC#35-40 as "out-of-scope (server-enforced)", но это incorrect — client tests требуют local rules для emulator.
- **Injection point**: **Design** + **Plan** — `06-api-contract.md` спроектировал contract без concrete rules. Plan accept "server-enforced" justification.
- **Detection gap**: Cross-phase Skeptic + Realist поймал. Per-phase completeness-reviewer не verified AC#35-40 client artifact.
- **Failure pattern**: **Review Blind Spot** + **Incomplete Research** (Firestore rules как client-deployable artifact не researched).

### Bug #5: Archived каталог не триггерит каскадную очистку детей (BLOCKER B5)

- **Symptom**: Archived parent → дети остаются видны UI. Spec invariant B (downward cascade) не реализован client-side.
- **Root cause**: `0-spec.md` orphan cleanup section depends on Server Invariant B. Design не enforced explicit confirmation что backend owner это implementiruje.
- **Injection point**: **Design** — depends on server side без contract verification.
- **Detection gap**: Cross-phase Skeptic поймал. Research phase не documented "if server X не делает Y, что произойдёт client-side?".
- **Failure pattern**: **Integration Gap** (server boundary contract).

### Bug #6: Sign-out не эмитит guest UserStats — stale shell (HIGH H2)

- **Symptom**: `UserStatsRepositoryImpl.kt:25-27` возвращает emptyFlow при uid=null. AppShell сохраняет stale privileges (admin меню остаётся видимым после logout).
- **Root cause**: Logout как side-effect не inventoried в spec. Repository implementation chose emptyFlow для null uid (defensive), не emit guest defaults.
- **Injection point**: **Spec** — Primary User Journeys не включил logout/account-switch.
- **Detection gap**: Cross-phase Skeptic поймал. Per-phase code-reviewer не traced full auth state flow.
- **Failure pattern**: **Missing Side-Effect Inventory** (logout). **Это тот же класс что menu-refactor Bug #1** — auth-scoped Flow без re-subscribe.

### Bug #7: DefaultRootComponent импортирует concrete SyncWorker из platform/android-services (HIGH H4)

- **Symptom**: `DefaultRootComponent.kt:18,231` имеет import platform/android-services SyncWorker — нарушение one-way module chain (presentation → platform = backwards).
- **Root cause**: ADR-HMQ-06 требует one-way chain, но реализация прокинула SyncWorker напрямую для simplicity.
- **Injection point**: **Implement** — frontend-dev impl без проверки import direction.
- **Detection gap**: Cross-phase Architect поймал. Per-phase architect-reviewer не имел grep `^import .*platform\.` в presentation.
- **Failure pattern**: **Lead Role Violation** — accepted без enforcement.

### Bug #8: Koin §13 SSoT mismatch — (ctx) vs (ctx, nav) (HIGH H5)

- **Symptom**: `06-api-contract.md:801` SSoT говорит factory(ctx), реальный `QuestPresentationModule.kt:25` требует factory(ctx, nav).
- **Root cause**: Canonical signature в 06-api-contract.md не cross-checked с actual Decompose component constructor signatures.
- **Injection point**: **Design** — 06-api-contract.md написан вручную, не extracted из source.
- **Detection gap**: Cross-phase Architect + Realist поймал. Plan-reviewer не имел round-trip verification "06-api-contract signature == implementation constructor".
- **Failure pattern**: **Plan Faithfulness** (план не отражает реальность кода).

### Bug #9: getKoin() в Composables (HIGH H6, marked phase-05-debt)

- **Symptom**: `AppShellScreen.kt:332,349` — `getKoin().koinInject()` напрямую в Composable, нарушение `di-patterns.md` правила.
- **Root cause**: Phase-05 frontend-dev marked as TODO(phase-05-debt) для compile safety. После cross-phase это accepted as known debt не fixed.
- **Injection point**: **Implement** — frontend-dev знал о violation но не escalated к lead.
- **Detection gap**: Cross-phase Architect + Realist поймал. Phase-05 architect-reviewer accepted TODO вместо blocking.
- **Failure pattern**: **Lead Role Violation** (frontend-dev сам решил accept violation как debt).

### Bug #10: 8 deferred items без clear owner (M1-M9 cluster)

- **Symptom**: M1 (AC traceability gaps), M2 (KoinModuleWiringTest scope), M3 (cache-busting URL), M4 (bootstrap limit), M6 (mechanical repository copy-paste), M7 (CascadingSyncOrchestrator dead dep), M8 (Badge API future-proof scope creep), M9 (unused gradle deps).
- **Root cause**: Each ADR/spec assertion accepted на trust в design phase. Implementation phase reviewers не имели consistent ADR-vs-code grep audit.
- **Injection point**: Distributed across **Design** (ADR not testable) + **Implement** (compliance not verified).
- **Detection gap**: Cross-phase Minimalist поймал М5-М9. Per-phase reviewers focused на code quality, не ADR fidelity.
- **Failure pattern**: **Delegated Decision Error** (decisions cite spec/ADR, but verification deferred without explicit gate).

## Stage Performance

| Stage | Grade | Notes |
|-------|-------|-------|
| Research | C | ✅ Cross-feature dependency map, 4 user decisions closed, Walking Skeleton generated. ❌ FR#14 cursor strategy не settled (Bug #2), Server Invariant B confirmation отсутствует (Bug #5), Firestore rules как client artifact не researched (Bug #4). |
| Design | C+ | ✅ 7 design docs, 12 ADRs, 3 Codex pre-implement reviews. ❌ ADR-CMP-49 cursor strategy amendment в phase-03 (после approval), §13 SSoT mismatch (Bug #8), getKoin() Composable не forbidden (Bug #9). Skeptic ADR audit (9 ADRs flagged REJECT) не routed в design fix loop. |
| Plan | B- | ✅ 5 phases ясно sequenced, 4 plan-review blockers resolved, AC coverage map. ❌ ADR vs code fidelity не verified в plan-reviewer (Bug #1, #3), AC#35-40 marked out-of-scope incorrectly (Bug #4), §13 Koin signature не verified (Bug #8). |
| Implement | B | ✅ 5 phases all PASSED 5/5 reviewers, smoke test green, 5 BLOCKERs fixed в cross-phase. ❌ 5 deferred HIGH findings (H1, H3, H4, H5, H6) accepted без escalation, mechanical copy-paste без abstraction (Bug #10/M6), AC traceback incomplete (Bug #10/M1). |
| Review | B- | ✅ 5 same-model reviewers per phase, cross-phase Codex 4-lens caught all blockers, fix verification loop. ❌ Per-phase same-model reviewers пропустили все 5 BLOCKERs + 8 HIGH (структурный same-model blind spot). Cross-phase Codex — единственная точка catch-integration. Phase-level architect не имел ADR-vs-code grep checklist. |

## Pipeline Fixes Required

### Fix #1: ADR-vs-code fidelity grep audit для phase-level architect-reviewer

- **Target file**: `.claude/agents/architect-reviewer.md` + `.claude/rules/clean-architecture.md`
- **What to add**: При phase-level review — для каждого ADR из `03-decisions.md`, который содержит constraint про code (например "all repositories must filter cv > local", "subtree-atomic cursor advance", "use Clock.System.now()"), architect-reviewer обязан выполнить grep / line-by-line search в changed code: матчится ли реализация с ADR. Если ADR cites pattern (e.g., `cv > local`), искать pattern в каждой repository implementation. Если 1+ repository missing pattern → blocker.
- **Why**: Прямое предотвращение Bug #1, #3, #10 (M3, M4). Текущий per-phase reviewer trustfully accept "design says X, code говорит X look-alike", без line-level verification.
- **Instrument**: Agent role refinement + grep checklist (semi-automated — architect-reviewer pulls ADR keywords, runs grep, reports matches). Не hook, поскольку semantic, но deterministic в смысле "должен быть запущен каждый phase review".
- **Prevents recurrence of**: Bug #1, #3, #10.

### Fix #2: Spec contradictions Gate в Grounding phase

- **Target file**: `.claude/commands/feature-research.md` (Grounding section)
- **What to add**: В Independent Verification Protocol добавить explicit step: "Для каждого FR/AC в spec — найти все references в design/code expectations. Если spec говорит one thing и existing/expected code говорит another → STOP, [SPEC CONTRADICTION] в `2-grounding.md`. Lead эскалирует пользователю до design phase. **Не start design без resolved contradictions.**"
- **Why**: Предотвращает Bug #2 (FR#14 cursor strategy contradicted в phase-03). ADR amendments в phase-03 — pipeline failure: spec должен был быть resolved до design.
- **Instrument**: Research phase requirement (text rule + Lead enforcement). Lead не starts design до Grounding Gate clean.
- **Prevents recurrence of**: Bug #2 (and similar spec/code contradictions).

### Fix #3: Server-side contract assumption gate в Design phase

- **Target file**: `.claude/commands/feature-design.md` (новая section)
- **What to add**: Если design depends на server invariant (например "server enforces cascade delete", "server returns sorted results"), design ОБЯЗАН включить **Server Contract Verification** subsection: цитата spec invariant + ссылка на server code/docs/contract который это enforces. Если no concrete server reference → [SERVER CONTRACT UNVERIFIED] в Open Questions, Lead эскалирует пользователю.
- **Why**: Предотвращает Bug #5 (Archived cascade depends on Server Invariant B без confirmation), Bug #4 (Firestore rules client side).
- **Instrument**: Design doc requirement + design-reviewer check.
- **Prevents recurrence of**: Bug #4, #5.

### Fix #4: Logout / auth-state-change scenario — обязательный Primary Journey

- **Target file**: `.claude/commands/feature-spec.md` (Primary User Journeys section)
- **What to add**: В Primary User Journey checklist добавить: для каждой фичи которая использует user-specific data (profile, stats, sync, auth-gated UI) — обязательный journey "Logout / Account Switch": что происходит с persisted state, какие Flows должны emit guest defaults, какой stale UI risk.
- **Why**: Прямое предотвращение Bug #6 (logout side-effect missing). Это тот же pattern что menu-refactor Bug #1 (auth re-subscribe). Дважды повторился — нужен enforcement.
- **Instrument**: Spec requirement + spec-reviewer check.
- **Prevents recurrence of**: Bug #6, и любой recurrence auth-scoped Flow без re-subscribe.

### Fix #5: §13 Koin SSoT cross-check — round-trip verification

- **Target file**: `.claude/commands/feature-design.md` (Document Responsibility Matrix section) + `.claude/agents/architect-reviewer.md`
- **What to add**: В Design phase — обязательная "round-trip" verification: each canonical signature из `06-api-contract.md` (Koin factories, repository signatures) должна быть verified против actual stub/skeleton code в Walking Skeleton (если есть) или против expected component constructor. Если sig mismatch — [SSoT VIOLATION], block phase.
- **Why**: Предотвращает Bug #8. Устраняет manual drift между ADR/contract docs и реальным code.
- **Instrument**: Design + Plan reviewer requirement. Lead-level enforcement через grep `factory<TypeName>` in 06-api-contract.md vs `class TypeName(...)` in source.
- **Prevents recurrence of**: Bug #8.

### Fix #6: Deferred findings explicit ownership

- **Target file**: `.claude/commands/feature-implement.md` (Cross-Phase Review section)
- **What to add**: В implementation.md "Remaining Issues" / "Known Debt" section — каждый deferred item ОБЯЗАН иметь:
  ```
  - **<ID>**: <symptom>
    - Owner: <who fixes — current feature post-MVP / next feature / followup ticket #N>
    - Gate: <when fixed — before next feature implementation / next sprint / etc>
    - Rationale: <why deferred — accepted scope vs accepted debt>
    - Severity confirmed: <by user — yes/no>
  ```
  Если severity HIGH/BLOCKER deferred — Lead эскалирует пользователю с явной AskUserQuestion accept/reject.
- **Why**: Предотвращает silent acceptance HIGH violations. 5 из 8 HIGH findings home-and-my-quests были помечены DEFERRED без user approval — это нарушение CLAUDE.md "Escalate, не импровизируй" принципа.
- **Instrument**: Lead requirement + AskUserQuestion для high severity. Text rule + escalation check.
- **Prevents recurrence of**: Bug #7, #9 silent accept; ensures user awareness.

### Fix #7: Mechanical repository abstraction (от M6)

- **Target file**: `.claude/rules/clean-architecture.md` (новая section "Repository abstraction threshold") OR phase plan template
- **What to add**: Если 3+ repository implementations имеют идентичную structure (same fetch → cache → emit pattern, parametrized только по type), planner ОБЯЗАН либо abstraction (template/generic), либо explicit ADR-justification "не abstracting because ...". Не делать "mechanical copy-paste" без conscious decision.
- **Why**: Предотвращает Bug #10/M6 (4 mechanical repository stacks = 120+ lines дублирования). Reflects DRY at architectural level.
- **Instrument**: Plan template requirement + plan-reviewer check via `find ... | wc -l` для repository pattern.
- **Prevents recurrence of**: Bug #10/M6.

## Lessons Learned

- **Same-model per-phase review structurally blind to ADR-vs-code drift.** 5 phase reviewers × 5 phases = 25 reviewer turns все PASS. Cross-phase Codex (4 lenses) поймал 5 BLOCKERs + 8 HIGH за один pass. Это тот же systemic problem что menu-refactor (40 reviewer turns missed 6 integration bugs). Решение: сделать ADR-vs-code grep audit обязательной частью каждого per-phase architect-reviewer.

- **ADR amendments в phase-N+ означают spec/grounding gap в phase 0.** ADR-CMP-49 amendment в phase-03 для bless `Clock.System.now()` — поздняя коррекция поверх неразрешённого FR#14 contradiction. Если spec и expected code не aligned до design → design phase перепишет spec post-hoc. Pipeline fix: Grounding Gate должен flag spec contradictions ДО design start.

- **Deferred HIGH findings без user approval = silent debt accept.** 5 из 8 HIGH findings DEFERRED в `implementation.md` без AskUserQuestion. Это нарушение "Escalate, не импровизируй". Decisions about deferring HIGH severity должны быть user-approved.

- **Server-side contract assumptions без verification = client-only fix shape impossible.** Bug #5 (Archived cascade) depends on Server Invariant B, но design не verified. Result: client passes review, runtime fails. Design phase обязан Server Contract Verification.

- **Logout/account-switch — Primary User Journey, не edge case.** Это recurring pattern (menu-refactor Bug #1 + home-and-my-quests Bug #6) — auth-scoped Flow без re-subscribe. Обязательный journey в spec.

- **Mechanical copy-paste = architectural smell.** 4 mechanical repository stacks с 95% identical code = M6 finding. DRY-at-architecture-level должен быть planning gate, не post-implementation observation.

## Applied Status (2026-05-01)

| Fix | Status | Target file |
|-----|--------|-------------|
| #1 ADR-vs-code grep audit для architect-reviewer | **Applied** | `.claude/agents/architect-reviewer.md` Section 6 |
| #2 Spec contradictions Gate в Grounding | **Applied** | `.claude/commands/feature-research.md` (BLOCKER findings = phase gate) |
| #3 Server-side contract assumption | **Partially Applied** (covered by feature-design.md REQUIRES VERIFY gate, server-specific section deferred) | `.claude/commands/feature-design.md` Gate 5 |
| #4 Logout/auth Primary Journey enforcement | **Already exists** (`.claude/commands/feature-spec.md:203` Шаг 3.6.5) — recurrence indicates needs stronger enforcement (review check) | `.claude/rules/auth-scoped-flow.md` (NEW) + `docs/invariants.md#8` |
| #5 §13 Koin SSoT round-trip | **Applied** | `.claude/commands/feature-design.md` Gate 7 + hook `check-api-contract-types.sh` |
| #6 Deferred findings explicit ownership | **Applied** | `.claude/commands/feature-implement.md` Шаг 4.5 |
| #7 Mechanical repository abstraction | **Deferred** — пока не codified как rule (требует case-by-case decision) | TBD if recurs |

## Sources (research)

- [When One Model Reviews Its Own Work: The Case for Adversarial Cross-Model Review (DEV Community 2026)](https://dev.to/john_wade_dev/when-one-model-reviews-its-own-work-the-case-for-adversarial-cross-model-review-37k1) — same-model blind spot, structural heterogeneity
- [Adversarial Code Review (ASDLC.io)](https://asdlc.io/patterns/adversarial-code-review/) — Critic Constitution, gate architecture
- [Why Do Multi-Agent LLM Systems Fail? (arXiv 2503.13657)](https://arxiv.org/html/2503.13657v3) — MAST taxonomy, FM-2.2 (proceeding with wrong assumptions, 6.80%)
- [Claude Code Hooks: The Deterministic Control Layer for AI Agents (Dotzlaw)](https://www.dotzlaw.com/insights/claude-hooks/) — hooks 100% vs prompts 70-90% compliance
- [Spec Driven Development: When Architecture Becomes Executable (InfoQ)](https://www.infoq.com/articles/spec-driven-development/) — SSoT and architectural drift
- [Automated Contract Testing (Medium/InstaTunnel 2026)](https://medium.com/@instatunnel/automated-contract-testing-how-to-detect-api-drift-before-it-reaches-production-6c2a77baa2a3) — design-first to prevent drift
