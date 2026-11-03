# Pipeline Retrospective: quizzes-screen

## Date
2026-05-01

## Summary

7 фаз реализации прошли все per-phase reviewers PASS (~30 reviewer turns), но **cross-phase Codex review** поймал **1 BLOCKER + 6 HIGH + 7 MEDIUM** integration findings, плюс design-phase Codex Skeptic pass-1 ранее выявил **3 BLOCKERs** (SSoT contradictions, module direction violation, BackCallback assumption unverified). Главный класс ошибок — **Modeling Error** (two-entry-path geometry для HomeQuests/MyQuests stacks не unified) и **Review Blind Spot** (architect-reviewer пропустил module direction violation в HierarchyItemCard ADR). Дополнительно — **process timing issue**: smoke test fix запустился ПОСЛЕ cross-phase Codex, а должен был ДО (build/integration bugs ловятся тестами быстрее, чем review-ом).

## Bugs Analyzed

### Bug #1: KoinModuleWiringTest stale constructor (BLOCKER F-01)

- **Symptom**: `KoinModuleWiringTest.kt:205,249` instantiates `DefaultRootComponent` без `homeQuestsFactory`/`myQuestsFactory`/`quizzesFactory` params (added in phase-07). Build fails в smoke test.
- **Root cause**: Phase-07 frontend-dev claimed PASS после running только `:android:feature:quizzes-screen:presentation:test`, не full `./gradlew test`. Stale test не caught локально.
- **Injection point**: **Implement** (phase-07) — frontend-dev "all tests PASS" claim не reflects full suite.
- **Propagation**: Per-phase code-reviewer trusted dev claim. Build gate `ciCheck` was supposed to catch — но если phase-07 dev не запустил full ciCheck до broadcast reviewers, blocker carry-over.
- **Detection gap**: Smoke test (post-Codex) caught. Phase-07 dev's `ciCheck` execution не verified by lead.
- **Failure pattern**: **Lead Role Violation** (lead не verified что phase-07 build status PASSED от full suite, не subset).

### Bug #2: AppShellScreen overlay без opaque background / touch absorber (HIGH F-02)

- **Symptom**: `AppShellScreen.kt:219` overlays QuizzesScreen on top, но без opaque background — touches могут проходить through, underlying app-shell UI hit-testable.
- **Root cause**: Phase-05 (QuestListScreen) reviewed component в isolation, no full-screen overlay composition test. Phase-07 (AppShellScreen wiring) added overlay но не included background/touch wrapper.
- **Injection point**: **Design** — `02-behavior.md` не описал overlay composition semantics (full-screen Box + opaque + touch-absorbing).
- **Detection gap**: Cross-phase Codex Skeptic поймал. Phase-05 / Phase-07 reviewers focused on individual components.
- **Failure pattern**: **Integration Gap** (overlay composition pattern across phases).

### Bug #3: BreadcrumbBar popToLevel off-by-one для MyQuests entry path (HIGH F-03/F-06)

- **Symptom**: Two entry paths производят разные стек-shapes: HomeQuests `[Idle→QuestList→SectionList→...]`, MyQuests `[Idle→SectionList→...]`. `popToLevel(uiLevel + 1)` calculation подразумевает HomeQuests-shaped stack — wrong для MyQuests.
- **Root cause**: Design не unified two-entry-path state machine. Phase-05 plan описал breadcrumb для одного path, второй entry treated implicitly.
- **Injection point**: **Design** — `02-behavior.md` не имеет explicit state machine для both paths.
- **Detection gap**: Cross-phase Codex Skeptic + Architect поймали (HIGH #1 fix in cross-phase). Phase-05 architect-reviewer reviewed BreadcrumbBar в isolation, не cross-entry-path.
- **Failure pattern**: **Modeling Error** (state machine не cover both navigation paths) + **Plan Faithfulness** (plan не enforced two-path verification).

### Bug #4: KoinModuleWiringTest не загружает все required modules (HIGH F-04)

- **Symptom**: Test loads только `appShellPresentationModule`, но `DefaultRootComponent` immediately resolves `MyQuestsComponent`/`HomeQuestsComponent`/`QuizzesComponent` factories — требуют `questPresentationModule`/`quizzesPresentationModule`. Test passes (early), real graph untested.
- **Root cause**: Phase-03 plan не enumerated all Koin modules для smoke test. Backend-dev зарегистрировал module, но test scope не updated к full graph.
- **Injection point**: **Plan** (phase-03) — DI module enumeration incomplete.
- **Detection gap**: Cross-phase Codex Skeptic поймал. Phase-03 architect-reviewer не имел "test loads all production modules" check.
- **Failure pattern**: **Test Validates Wrong Spec** (test passes но verifies subset graph).

### Bug #5: HierarchyItemCard parameter type violates module direction (BLOCKER F-08)

- **Symptom**: `03-decisions.md:315,336` ADR-QS-09 предлагает `HierarchyItemCard` (lives in `core/designsystem`) принимать `HierarchyItemUi` (lives в `quizzes-screen/presentation`). Core importing feature = invariant violation.
- **Root cause**: Design phase architect не enforced "core не importnется feature" rule в ADR review.
- **Injection point**: **Design** — ADR создал contradiction.
- **Detection gap**: Codex Skeptic pass-1 поймал. Design-phase architect-reviewer pass до Codex не applied module-direction check.
- **Failure pattern**: **Review Blind Spot** (architect missed cross-module invariant в ADR).

### Bug #6: ADR-QS-05 SSoT contradiction — extend vs wrapper (BLOCKER F-09)

- **Symptom**: ADR говорит "extend `QuestDisplayItem.catalogId`", но `06-api-contract.md:307` вводит `QuestDisplayItemWithCatalog` wrapper. Two SSoT для one type.
- **Root cause**: Design phase не cross-checked ADR vs api-contract. Both written by architects, no round-trip validation.
- **Injection point**: **Design** — multiple authors, no consistency gate.
- **Detection gap**: Codex Skeptic pass-1 поймал. Design-phase architect-reviewer не имел "ADR ↔ api-contract" round-trip check.
- **Failure pattern**: **Review Blind Spot** (consistency between design docs не verified).

### Bug #7: ADR-QS-12 BackCallback priority — REQUIRES verify left unresolved (BLOCKER F-10)

- **Symptom**: ADR marked priority handling as "REQUIRES verify Essenty 2.x API", но consequences assume it works. Implementation hardcoded `priority=100` (no constant).
- **Root cause**: Design phase accepted ADR с unresolved REQUIRES, treated как "implementer will resolve". Не blocked design PASS.
- **Injection point**: **Design** — REQUIRES VERIFY accepted as "hopeful gate".
- **Detection gap**: Codex Skeptic pass-1 поймал. Design phase не имел rule "REQUIRES VERIFY blocks PASS until resolved".
- **Failure pattern**: **Assumption Not Verified** (critical API behavior assumed).

### Bug #8: Catalog name race в DFD-2 (MEDIUM F-05)

- **Symptom**: MyQuests entry: `homeQuestsComponent.state.value.catalogs` may be empty on first tap → freezes "Без каталога" в breadcrumb.
- **Root cause**: DFD-2 `02-behavior.md:230-231` показывает catalog lookup, но source state не specified. Implementation использует separate collector (homeQuestsComponent) — race condition.
- **Injection point**: **Design** — behavior spec ambiguous о catalog source.
- **Detection gap**: Cross-phase Codex Skeptic поймал. Per-phase reviewers не traced cross-component state flow.
- **Failure pattern**: **Assumption Not Verified** (state.catalogs source не explicit).

## Stage Performance

| Stage | Grade | Notes |
|-------|-------|-------|
| Research | B | ✅ Codebase mapping, cross-feature dependency graph, Walking Skeleton verified, 3 FakeQuestRepository duplicates flagged. ❌ Q4 catalog race condition noted but consequences не fully modeled. |
| Design | C | ✅ ADR matrix, behavior DFD, test strategy. ❌ 3 BLOCKERs + 3 HIGH + 7 MEDIUM (F-03, F-05, F-07, F-08, F-09, F-10) — SSoT contradictions, module direction violation, REQUIRES VERIFY accepted. Architect reviewer pass-1 missed module direction violation. |
| Plan | A- | ✅ 7 phases sequenced, plan-review pass-3 PASS via Codex, Signature Cards. ❌ Phase-07 "all tests PASS" gate не explicit (full ciCheck vs subset), DI module enumeration в phase-03 incomplete, two-entry-path breadcrumb verification не requested. |
| Implement | B+ | ✅ 7 phases, autonomous fix loop worked, ~30 reviewer turns PASS. ❌ Phase-07 frontend-dev claimed PASS без full ciCheck (Bug #1), per-phase reviewers missed cross-stack issues (Bug #2, #3). |
| Review | A- | ✅ Cross-phase Codex (4 lenses) caught all 4 cross-phase findings. Design Codex pass-1 caught 3 BLOCKERs + 9 другие issues — fix loop пробил design ad spec finalization. ❌ Per-phase same-model reviewers систематически missed cross-stack and module-direction issues. **Smoke test ordering**: ran AFTER cross-phase Codex — should be BEFORE. |

## Pipeline Fixes Required

### Fix #1: Smoke test BEFORE cross-phase Codex review

- **Target file**: `.claude/commands/feature-implement.md` (sequence Шаг 3 vs 4)
- **What to add**: Изменить sequence: build/smoke test (full `./gradlew test --no-configuration-cache` + `./gradlew assemble --no-configuration-cache` + `./gradlew connectedAndroidTest` if applicable) запускается **ДО** cross-phase Codex, не после. Codex review приходит на green build, не на debug-state. Если smoke test fail — fix loop с devs первый, Codex после green.
- **Why**: Прямое предотвращение Bug #1, #4 (KoinModuleWiringTest stale + missing modules — caught by build, not review). Codex more useful when given working code, не build-broken state. Currently smoke test = post-Codex == backwards.
- **Instrument**: Step ordering в feature-implement.md. Lead requirement.
- **Prevents recurrence of**: Bug #1, #4.

### Fix #2: REQUIRES VERIFY blocks design PASS gate

- **Target file**: `.claude/commands/feature-design.md` (Quality Gates section)
- **What to add**: Добавить gate: "REQUIRES VERIFY" markers в ADRs (или anywhere в design docs) BLOCK design phase PASS до resolution. Resolution = либо verified API behavior с link to source/docs, либо explicit user decision "accept assumption". No "implementer will resolve at impl phase" — это design debt-shifting.
- **Why**: Прямое предотвращение Bug #7. Hopeful gate = no gate.
- **Instrument**: Design phase requirement + design-reviewer check via grep `REQUIRES VERIFY` в design docs. If non-empty в final design → blocker.
- **Prevents recurrence of**: Bug #7.

### Fix #3: Module direction grep check для architect-reviewer в design phase

- **Target file**: `.claude/agents/architect-reviewer.md`
- **What to add**: При design ADR review — обязательный grep audit: для каждой proposed component class / interface signature in ADRs:
  ```bash
  # Если class живёт в core, его signature не должна reference feature types
  rg "core/designsystem.*\b<TypeName>\b.*<FeatureType>" docs/features/<slug>/03-decisions.md
  
  # Если class живёт в feature, его signature не должна reference другую feature
  rg "feature/<slug-A>.*\b<TypeName>\b.*<TypeFromFeatureB>" docs/features/<slug>/
  ```
- **Why**: Прямое предотвращение Bug #5 (HierarchyItemCard living в core but typed с feature). Module-direction violation в ADR — это design-level issue, не impl-level.
- **Instrument**: architect-reviewer prompt + grep checklist.
- **Prevents recurrence of**: Bug #5.

### Fix #4: Two-entry-path / multi-stack-shape state machine — обязательный design artifact

- **Target file**: `.claude/commands/feature-design.md` (02-behavior.md template)
- **What to add**: Если фича имеет multiple entry points, которые приводят к разным ChildStack shapes (e.g., HomeQuests vs MyQuests, Login-via-email vs Login-via-google), `02-behavior.md` ОБЯЗАН включить **Multi-Path State Machine**:
  ```
  ## Stack Shapes по entry point
  
  ### Path A: <name>
  Initial stack: [Step1, Step2, Step3]
  
  ### Path B: <name>  
  Initial stack: [Step2, Step3]  # diverges
  
  ## Operations across paths
  | Operation | Path A behavior | Path B behavior |
  |-----------|----------------|-----------------|
  | Back at Step2 | pop to Step1 | pop closes feature |
  | Breadcrumb tap level 0 | pop to Step1 | pop closes feature |
  ```
- **Why**: Прямое предотвращение Bug #3 (popToLevel off-by-one). Two-path geometry слишком easy миссить если только один path documented.
- **Instrument**: Design template + design-reviewer check.
- **Prevents recurrence of**: Bug #3.

### Fix #5: ADR ↔ api-contract round-trip consistency check

- **Target file**: `.claude/commands/feature-design.md` (Document Responsibility Matrix)
- **What to add**: После того как `03-decisions.md` и `06-api-contract.md` написаны — обязательный round-trip check: для каждого ADR который mentions a public type / function signature, проверить что `06-api-contract.md` describes the SAME signature. Inconsistency → blocker.
  ```bash
  # Pseudo:
  for adr_type in $(grep "TypeName" 03-decisions.md):
    if grep -q "TypeName" 06-api-contract.md:
      compare signatures (extend vs wrapper vs other) → exact match required
    else:
      blocker: ADR mentions type, contract не canonicalizes it
  ```
- **Why**: Прямое предотвращение Bug #6 (ADR-QS-05 extend vs wrapper SSoT contradiction).
- **Instrument**: Design phase requirement + scripted check (можно add как hook на edit/finish design phase).
- **Prevents recurrence of**: Bug #6.

### Fix #6: Phase-07 (or final phase) "full ciCheck PASS" verification gate

- **Target file**: `.claude/commands/feature-implement.md` (Build Gate section) + `.claude/agents/code-reviewer.md`
- **What to add**: Final phase build gate ОБЯЗАН:
  1. Run `./gradlew ciCheck --no-configuration-cache` (full suite, not subset)
  2. Output exit code zero
  3. Lead verifies actual gradle output (last lines) before approving phase DONE
  4. Lead asks coder для git commit hash + build log link in RESULT message
- **Why**: Прямое предотвращение Bug #1. Currently coder claims "all tests PASS", lead trusts. Need verification.
- **Instrument**: Build Gate enhancement + Lead verification step.
- **Prevents recurrence of**: Bug #1.

### Fix #7: Cross-feature state source attribution в DFD

- **Target file**: `.claude/commands/feature-design.md` (DFD requirements)
- **What to add**: Для каждого arrow в DFD, который reads state — explicit "source component" annotation. Avoid implicit assumptions like "state.catalogs always available". If state читается from another component, document race conditions: "if source state empty at first read → fallback / loading / defer".
- **Why**: Прямое предотвращение Bug #8 (catalog name race). DFD assumption "state.catalogs ready" — race condition not modeled.
- **Instrument**: Design DFD requirement + design-reviewer check.
- **Prevents recurrence of**: Bug #8.

## Lessons Learned

- **Smoke test BEFORE Codex review = build/integration bugs caught by tools (cheap), not by Codex (expensive).** Phase-07 KoinModuleWiringTest stale constructor — это компиляция, ловится `./gradlew test` за секунды. Codex spent its budget на build-broken state, would have found design issues faster если smoke test был зелёный first.

- **REQUIRES VERIFY = no gate.** ADR-QS-12 marked BackCallback priority как "REQUIRES verify" но не blocked design PASS. Implementation hardcoded `priority=100` без verifying constant existence in Essenty 2.1.0. "Hopeful gate" = no gate.

- **Multi-entry-path features нужны Multi-Path State Machine.** HomeQuests vs MyQuests had different stack shapes; popToLevel off-by-one для one path. Design doc focused on ONE path, second treated "implicitly same" — generic class of issue.

- **Architect-reviewer должен иметь module-direction grep check в ADRs.** Bug #5 (HierarchyItemCard living в core but typed с feature) — это design-level issue (ADR contained the violation), not impl-level. Per-phase architect-reviewer focuses on code; design-phase architect should grep ADR signatures против module rules.

- **ADR ↔ api-contract round-trip = design consistency gate.** Two SSoT для один тип (ADR-QS-05 extend vs api-contract wrapper) — silent design inconsistency. Should have explicit consistency check.

## Applied Status (2026-05-01)

| Fix | Status | Target file |
|-----|--------|-------------|
| #1 Smoke test BEFORE cross-phase Codex | **Applied** | `.claude/commands/feature-implement.md` Шаг 2.5 (новая) |
| #2 REQUIRES VERIFY blocks design PASS | **Applied** | `.claude/commands/feature-design.md` Gate 5 |
| #3 Module direction grep check для architect-reviewer | **Applied** | `.claude/agents/architect-reviewer.md` Section 7 + `.claude/commands/feature-design.md` Gate 6 |
| #4 Multi-Path State Machine artifact | **Applied** | `.claude/commands/feature-design.md` Gate 8 |
| #5 ADR ↔ api-contract round-trip | **Applied** | `.claude/commands/feature-design.md` Gate 7 |
| #6 Phase final "full ciCheck PASS" verification | **Applied** | `.claude/commands/feature-implement.md` Шаг 2.5.1 |
| #7 Cross-feature state source attribution в DFD | **Documented** в feature-design.md DFD requirements (lead enforcement; нет automated check) | `.claude/commands/feature-design.md` (existing DFD section) |

## Sources (research)

- [When One Model Reviews Its Own Work (DEV Community 2026)](https://dev.to/john_wade_dev/when-one-model-reviews-its-own-work-the-case-for-adversarial-cross-model-review-37k1) — same-model blind spot, structural heterogeneity for review
- [Adversarial Code Review (ASDLC.io)](https://asdlc.io/patterns/adversarial-code-review/) — quality gates: deterministic / probabilistic / acceptance
- [Refute-or-Promote: Adversarial Stage-Gated Multi-Agent Review (arXiv 2604.19049)](https://arxiv.org/html/2604.19049) — cross-model critic, validation gates
- [Spec Driven Development (InfoQ)](https://www.infoq.com/articles/spec-driven-development/) — design as executable, contract verification
- [Claude Code Hooks: Deterministic Control Layer (Dotzlaw)](https://www.dotzlaw.com/insights/claude-hooks/) — hooks 100% compliance vs prompts 70-90%
