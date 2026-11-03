# Pipeline Retrospective: lesson-runner

## Date
2026-05-01

## Summary

САМАЯ СЛОЖНАЯ фича из 3-х в этом batch retrospective. **6 Codex review rounds** (вместо стандартных 1-2), **5 BLOCKERs + 14+ HIGH/MEDIUM**, **4 ADR-0003 amendments**, **phase-08 ADDED** для post-implementation Codex fix loop (3 rounds внутри phase-08), **manual smoke на Pixel** требуется. Если у одной фичи потребовалось 6 rounds adversarial review — это **fundamental signal systemic pipeline failure**: spec / research / grounding / design / plan не resolved consistency gates до implementation.

Главные классы ошибок:
1. **Assumption Not Verified** (R1-B4 canonical API не из source, R1-M2 @Serializable не applied, R2-H1/H2/H3 lifecycle patterns assumed) — 5+ instances.
2. **Modeling Error** (R1-B1 TopParticipant bidirectional coupling, R1-H2 UserAnswerDraft vs UserAnswer, R1-H5 component state representation) — 4+ instances.
3. **Integration Gap** (R1-B2 C4 vs Gradle drift, R2-H2 Back not intercepted, R2-M2 Coil not integrated) — 3+ instances.
4. **Lifecycle Mismatch** (R2-H1 rotation drafts lost, R2-H3 FLAG_SECURE timing) — 2+ instances.

**Phase-08 — это патч**: показатель того, что phases 01-07 + 5 same-model reviewers были systemically insufficient для catch design-level issues. Codex был запущен post-implementation, а должен был запускаться post-design.

## Bugs Analyzed

### Bug #1: TopParticipant bidirectional coupling (BLOCKER R1-B1)

- **Symptom**: Docs claim `TopParticipant` lives в `shared/core/leaderboard`, но модуля нет; тип в `lesson-runner/domain` создаёт bidirectional coupling если quiz-feature тоже использует.
- **Root cause**: Grounding flagged как Problem #2 (BLOCKER), Design выдал ADR-LR-05 (move to shared/core), но Implementation phase не applied. Grounding finding не enforced как design phase blocker.
- **Injection point**: **Design** — ADR-LR-05 принят, но не enforced на phase boundary.
- **Propagation**: Phase-01 implementation accepted ADR but не moved type. Per-phase architect missed grounding-blocker drift.
- **Detection gap**: Codex Round 1 поймал. Grounding gate должен был block phase-01 start до ADR resolved.
- **Failure pattern**: **Modeling Error** + grounding finding не enforced.

### Bug #2: C4 module graph не sync с Gradle (BLOCKER R1-B2)

- **Symptom**: `01-architecture.md:101-103` показывает `quizzes-screen → lesson-runner` import, реальный Gradle `build.gradle.kts:11-17` — пусто.
- **Root cause**: C4 diagram написан вручную, не extracted from Gradle. Manual SSoT.
- **Injection point**: **Design** — C4 diagram drift.
- **Propagation**: Plan trusted C4 diagram. Implementation focused на code, не verified diagram.
- **Detection gap**: Codex Round 1 поймал. Design phase не имел "C4 vs Gradle" round-trip check.
- **Failure pattern**: **Integration Gap** + manual SSoT (CLAUDE.md "Deterministic enforcement > hope" not applied).

### Bug #3: LessonPlaceholder + LessonRunner оба в коде (BLOCKER R1-B3)

- **Symptom**: ADR-LR-07 объявляет LessonPlaceholder removal атомарным с LessonRunner appearance. Реальный код: `QuizzesConfig.kt:34-39` имеет оба варианта.
- **Root cause**: ADR не enforced при cross-feature integration phase. Documentation describes target state, code is intermediate.
- **Injection point**: **Plan / Implement** — phase-06 (cross-feature integration) trusted ADR-LR-07 без verification.
- **Detection gap**: Codex Round 1 поймал.
- **Failure pattern**: **Plan Faithfulness** (documentation ahead of code).

### Bug #4: 06-api-contract canonical signatures не из source (BLOCKER R1-B4)

- **Symptom**: `06-api-contract.md:447-450` использует param name `lessonAttemptRepository`, real `CompleteAttemptUseCase.kt:22-26` имеет `attemptRepository`. Snippet в docs не компилируется.
- **Root cause**: `06-api-contract.md` написан вручную. Никакой generation / extraction из source.
- **Injection point**: **Design** — manual SSoT с no validation.
- **Detection gap**: Codex Round 1 поймал. Spec/design phase doesn't enforce "canonical signatures must compile against source".
- **Failure pattern**: **Assumption Not Verified** (snippets assumed correct, never compiled).

### Bug #5: Lesson result model schema gap (HIGH R1-H1)

- **Symptom**: Docs reference `Lesson.averageRating/ratingCount/top3` поля, но `LessonEntity.kt:23-32` (DB) не содержит этих columns. DB schema не updated.
- **Root cause**: Design added rating concept, но `08-storage-model.md` не updated. Schema migration не included.
- **Injection point**: **Design** — assertion vs schema drift.
- **Detection gap**: Codex Round 1 поймал.
- **Failure pattern**: **Incomplete Research** (schema assumptions not verified против DB classes).

### Bug #6: Behavior sequence calls wrong API (HIGH R1-H2)

- **Symptom**: `02-behavior.md:365-369` passes `UserAnswerDraft`, real `RunnerLogic.submitAnswer(state, answer: UserAnswer, nowMs)` принимает `UserAnswer`.
- **Root cause**: Design сделал Draft vs Submitted distinction в behavior spec, но domain layer (Walking Skeleton from spec) не содержит `UserAnswerDraft`. Behavior описывает несуществующий type.
- **Detection gap**: Codex Round 1 поймал.
- **Failure pattern**: **Modeling Error** (behavior spec не cross-checked against domain types).

### Bug #7: DI provider binding strategy не locked (HIGH R1-H4)

- **Symptom**: `01-architecture.md:713-715` говорит "register domain module", `06-api-contract.md:501-503` говорит "register adapter". Inconsistency.
- **Root cause**: ADR-LR-09 не finalized. Two design docs say different things.
- **Detection gap**: Codex Round 1 поймал.
- **Failure pattern**: **Delegated Decision Error** (decision deferred from design to phase-01).

### Bug #8: Component state representation inconsistency (HIGH R1-H5)

- **Symptom**: `01-architecture.md:386-402` says `uiState: Value`, `06-api-contract.md:271-283` says `StateFlow`. Public API undefined.
- **Detection gap**: Codex Round 1 поймал.
- **Failure pattern**: **Modeling Error** (Value vs StateFlow design choice не locked).

### Bug #9: Rotation drafts lost (HIGH R2-H1)

- **Symptom**: MultipleChoice/Ordering drafts в Compose `remember` not persisted across rotation. AC-35 broken (state should survive rotation).
- **Root cause**: Decompose `instanceKeeper` pattern not used. Phase-05 Frontend-dev не researched correct lifecycle pattern.
- **Detection gap**: Codex Round 2 поймал. Per-phase code-reviewer не tested rotation.
- **Failure pattern**: **Lifecycle Mismatch** (`remember` insufficient для complex draft state).

### Bug #10: System Back bypasses abort dialog (HIGH R2-H2)

- **Symptom**: Android Back directly ChildStack.pop, не triggering onCrossButtonTap → AC-3/34 broken (should show abort dialog).
- **Root cause**: Phase-06 (quizzes-screen integration) не wired BackCallback override. Decompose Back default = pop.
- **Detection gap**: Codex Round 2 поймал.
- **Failure pattern**: **Integration Gap** + **Lifecycle Mismatch** (BackCallback не intercepted).

### Bug #11: FLAG_SECURE Loading window — too late (HIGH R2-H3)

- **Symptom**: HARD mode requires unconditional FLAG_SECURE early. Code conditionally applies based on state, screenshots possible during Loading state.
- **Root cause**: Design не explicit о FLAG_SECURE timing (must be set before any HARD-mode screen visible).
- **Detection gap**: Codex Round 2 поймал.
- **Failure pattern**: **Lifecycle Mismatch** (FLAG_SECURE timing).

## Round-by-Round Analysis

### Round 1 (Post-implementation, 2026-04-28) — REJECT
- 4 BLOCKERS + 5 HIGH + 1 MEDIUM = 10 issues.
- Pattern: design docs describe target state, code describes actual.
- Verdict: "fundamentally flawed".
- **Should have run after design phase, не after phase-07 implement.**

### Round 2 (After phase-08 round 1 fixes) — PARTIAL
- 0 BLOCKERS + 3 HIGH + 3 MEDIUM = 6 issues.
- Pattern: lifecycle patterns (rotation, Back, FLAG_SECURE) assumed правильно implemented.
- Verdict: "fix loop applied, не fully synced".
- **Should have been caught by E2E / instrumented tests.**

### Round 3 (After phase-08 round 3 fixes) — PASS
- 0/0/0 issues. Verification round.
- Indicates phase-08 (3 internal rounds) finally converged.
- **Cost**: ~2 weeks delay, ~30 fixes applied post-implementation.

### Phase-08 Existence = Pipeline Failure Signal
- Phase-08 added как post-implementation patch.
- Means phases 01-07 + 5 same-model reviewers were insufficient.
- Codex needed для cross-artifact validation (docs vs code vs rules).
- **Pipeline question**: should phase-08 быть стандартной частью plan (pre-budgeted), или signal что adversarial review нужно raньше (post-design, post-grounding)?

## ADR-0003 Amendments Analysis

`03-decisions.md:585-596` — 4 amendments to ADR-0003 (cross-cutting architecture decision):

| Amendment | Content | Should Have Been Caught |
|-----------|---------|-------------------------|
| A | EASY does NOT interrupt — continues to end of pool | Spec FR (Domain Test Scenarios) |
| B | No feedback/answer reveal after submit | Spec UI behavior section |
| C | `timeLimitSec` ignored runtime — formula-based | Spec timer requirement |
| D | Module reference: `quiz/domain` → `lesson-runner/domain` | ADR-0003 itself (referenced wrong module) |

**Pattern**: ADR-0003 was cross-cutting (existed before lesson-runner spec), но не reviewed for "lesson-runner applicability" during spec phase. Result: 4 amendments discovered during implementation. **Pipeline question**: should existing ADRs be "re-validated" against new feature spec в spec phase?

## Stage Performance

| Stage | Grade | Notes |
|-------|-------|-------|
| Spec | B | ✅ 65 AC explicit, 1234 lines, Domain Contract complete. ❌ ADR-0003 amendments implicit (4 discovered post-impl), Top-3 server aggregation underspecified. |
| Research | B | ✅ 5 blockers identified, 10 open questions, Walking Skeleton validated. ❌ Cross-feature ADRs marked "MISSING", не enforced. Existing patterns assumed без verification (instanceKeeper, FLAG_SECURE). |
| Grounding | A | ✅ 10 problem cards explicit, BLOCKER triage clear, Independent Verification Protocol applied. ❌ Server-side gaps out-of-scope but not risk-mitigated. |
| Design | C | ✅ 15 ADRs, API contract, behavior DFDs. ❌ C4 vs Gradle drift (Bug #2), 06-api-contract not extracted from source (Bug #4), provider binding deferred (Bug #7), state representation inconsistent (Bug #8). 4 ADR-0003 amendments discovered post-impl. |
| Plan | B | ✅ 7 phases, 5 reviewers, autonomous fix loop, 227+ tests green. ❌ Cross-phase Codex review запущен post-impl, не post-design. Plan invariants (pattern dependencies) не locked early. |
| Implement | B | ✅ Все 7 phases PASSED 5/5 reviewers, build gates strict. ❌ 3 compile blockers in phase-04 (resolved), phase-08 added как patch. |
| Phase-08 (Codex fix loop) | A | ✅ 3 round convergence, all findings closed. ❌ **Existence indicates pipeline failure** — should not exist as remediation. |

## Pipeline Fixes Required

### Fix #1: Codex review post-design, не post-implementation

- **Target file**: `.claude/commands/feature-design.md` (phase 3) + `.claude/commands/feature-implement.md` (phase 3 = cross-phase Codex)
- **What to add**: Codex CLI cross-model review должен запускаться **3 раза**:
  1. **После design phase** (после `01-architecture.md` + `02-behavior.md` + `03-decisions.md` + `06-api-contract.md` написаны) — Realist + Skeptic + Architect + Minimalist lens на DESIGN docs. Цель: find design contradictions BEFORE implementation. Если blockers — fix design, NOT implementation.
  2. **После plan phase** — current Plan-as-ТЗ lens. Sequencing review.
  3. **После implementation** — current cross-phase review. Codes vs design + integration check.
  
  Phase-08-style "post-implementation Codex" — это band-aid. Lesson-runner потребовал 6 rounds потому что Codex был только post-impl; if Codex был post-design, BLOCKERs B1-B4 были бы caught до implementation.
- **Why**: Прямое предотвращение паттерна "6 rounds because design issues caught too late". Adversarial review BEFORE implementation = cheaper rework.
- **Instrument**: Step ordering. Lead requirement.
- **Prevents recurrence of**: Bugs #1, #2, #4, #6, #7, #8 (все design-level findings caught only post-impl).

### Fix #2: 06-api-contract canonical signatures должны компилироваться против source

- **Target file**: `.claude/commands/feature-design.md` (Document Responsibility Matrix) + new hook
- **What to add**: При завершении design phase — script (или hook) который extracts code snippets из `06-api-contract.md` (между ```kotlin``` блоками) и проверяет что они компилируются against actual source files. If snippet references `class FooUseCase(repo: BarRepository)` — verify `BarRepository` exists, has matching methods. Если no — design phase blocker.
  
  Альтернатива: 06-api-contract — это generated artifact из source code (через Kotlin reflection / KSP). Manual canonicalization не allowed.
- **Why**: Прямое предотвращение Bug #4. Manual SSoT драит. Если source = SSoT, drift impossible.
- **Instrument**: Script/hook + design phase blocker.
- **Prevents recurrence of**: Bug #4.

### Fix #3: C4 architecture diagram должен соответствовать Gradle build files

- **Target file**: `.claude/commands/feature-design.md` (01-architecture.md template) + new check
- **What to add**: При design phase finalization — script проверяет:
  ```
  Для каждого arrow (A → B) в C4 diagram (`01-architecture.md`):
    - Module A's `build.gradle.kts` MUST have `implementation(project(":...:B:..."))` либо explicit ADR exception.
    
  Reverse:
    - Каждый Gradle dep в build.gradle.kts MUST appear в C4 diagram.
  
  Mismatch → blocker.
  ```
- **Why**: Прямое предотвращение Bug #2. Eliminate manual diagram drift.
- **Instrument**: Script (можно add как PostToolUse hook на edit `01-architecture.md`).
- **Prevents recurrence of**: Bug #2.

### Fix #4: Existing cross-cutting ADR re-validation в spec phase

- **Target file**: `.claude/commands/feature-spec.md` (Phase 1 / Phase 3.5)
- **What to add**: После Domain Contract Lock — обязательный step "Cross-cutting ADR review". Каждый ADR в `docs/decisions/` (либо общий) который mentions architecture invariants должен быть re-validated against new feature: "Does this feature affect ADR-XXX? Are amendments needed?" Если yes — amendments captured в spec, не post-implementation.
- **Why**: Прямое предотвращение Bug ADR-0003 amendments (4 amendments discovered during implementation). Cross-cutting ADRs обязаны быть проверены при new feature spec.
- **Instrument**: Spec checklist + AskUserQuestion для каждого affected ADR.
- **Prevents recurrence of**: 4 ADR-0003 amendments retroactively применены.

### Fix #5: E2E (instrumented integration) test stage между phase-N+1 и cross-phase Codex

- **Target file**: `.claude/commands/feature-implement.md` (new step) + `.claude/rules/testing.md`
- **What to add**: После последней feature phase, перед cross-phase Codex — обязательный step "E2E Integration Tests":
  - Если фича имеет UI flow — instrumented test для Catalog → ... → Result happy path.
  - Test runs против real Decompose Component graph + Compose UI + Room DB (not pure unit).
  - Lifecycle scenarios (rotation, system Back, low-memory recreation) included.
  - `./gradlew connectedAndroidTest` PASS = pre-condition для Codex review.
- **Why**: Прямое предотвращение Bugs #9, #10, #11 (rotation drafts lost, Back bypass, FLAG_SECURE timing). These are runtime/lifecycle bugs, не visible в JVM unit tests или JVM Compose preview tests.
- **Instrument**: Pipeline step requirement + connected device check.
- **Prevents recurrence of**: Bug #9, #10, #11 + class of lifecycle issues.

### Fix #6: Grounding BLOCKER finding = phase-N start gate

- **Target file**: `.claude/commands/feature-research.md` (Grounding section) + `.claude/commands/feature-design.md`
- **What to add**: Grounding BLOCKER findings (e.g., "TopParticipant module direction violation, BLOCKER #2") явно blocker для NEXT phase до resolution. Lead не start design phase до все BLOCKERs resolved (либо в spec amendment либо в ADR ответ). Currently grounding flags blocker, design proceeds, blocker carries to implementation. Fix: blocker = phase gate.
- **Why**: Прямое предотвращение Bug #1 (TopParticipant blocker carried from grounding to phase-04). Grounding не имел enforcement что blockers resolved до design.
- **Instrument**: Lead enforcement + checklist в feature-design.md "before starting design — verify all grounding BLOCKERs resolved".
- **Prevents recurrence of**: Bug #1.

### Fix #7: Lifecycle pattern catalog для new features

- **Target file**: `.claude/skills/lifecycle-patterns/SKILL.md` (new) или `.claude/rules/lifecycle.md`
- **What to add**: Pattern library для common Android/Decompose lifecycle scenarios:
  - Rotation: `instanceKeeper` для Component state, `rememberSaveable` для Compose, `parcelize` для draft state
  - System Back: `BackCallback` registration, priority levels, override defaults
  - FLAG_SECURE: установка в Activity onCreate(), не conditionally в Compose
  - Process death: SavedStateHandle / Decompose stateKeeper
  
  Research phase обязан validate "does this feature use any of these patterns? what's expected behavior?". Если yes но pattern не documented в spec → research blocker.
- **Why**: Прямое предотвращение Bug #9, #10, #11 (rotation drafts lost, Back bypass, FLAG_SECURE timing). Эти patterns не researched в lesson-runner spec, assumed implementer знает.
- **Instrument**: Skill + research checklist.
- **Prevents recurrence of**: Bug #9, #10, #11 + general lifecycle issues.

## Lessons Learned

- **6 Codex rounds = systemic pipeline failure, не single-feature complexity.** Если adversarial review найден чтобы быть запущен 6 times, это означает каждое previous round оставило open issues. Решение: запускать Codex earlier (post-design, не post-impl), с lower budget per round.

- **Phase-08 как post-impl Codex fix loop = patch, не feature.** Phase-08 added because phases 01-07 + 5 reviewers были insufficient. Either phase-08 is **standard pre-budgeted phase** (acknowledged need), либо Codex moves earlier (preferred — cheaper).

- **4 ADR amendments discovered post-implementation = ADR review missing в spec phase.** Cross-cutting ADRs (e.g., ADR-0003) должны быть proactively re-validated при new feature spec. "Does this feature affect existing ADR?" — explicit checklist.

- **Manual SSoT (06-api-contract, C4 diagram) drifts.** Manual = drift inevitable. Either:
  - Generate from source (preferred) — KSP / Kotlin reflection.
  - Validate against source mechanically — script / hook.
  Manually maintained "canonical" = no canonicalization.

- **Lifecycle patterns не documented = implementer guesses wrong.** Decompose `instanceKeeper`, BackCallback priority, FLAG_SECURE timing — все project-level patterns. Если фича их использует, но spec/design не reference, implementer chooses default (`remember`, no override, conditional FLAG_SECURE) — wrong.

- **E2E tests gap = runtime/lifecycle bugs invisible to JVM tests.** Bugs #9-11 only visible at runtime. Pipeline currently rely на manual smoke. Manual smoke deferred = bugs missed.

- **Grounding BLOCKER без enforcement = blocker carries forward.** Grounding flagged TopParticipant violation, ADR proposed solution, implementation didn't apply. Blocker should be phase-N start gate.

## Applied Status (2026-05-01)

| Fix | Status | Target file |
|-----|--------|-------------|
| #1 Codex review post-design включает code references | **Applied** | `.claude/commands/feature-design.md` Шаг 3 — "Codex prompt MUST include code references" subsection |
| #2 06-api-contract canonical signatures compile-check | **Applied** (class-level existence; param-name drift TBD) | `.claude/hooks/check-api-contract-types.sh` (registered в settings.json) |
| #3 C4 architecture diagram vs Gradle build files | **Applied** (warn-level, не blocker) | `.claude/hooks/check-c4-vs-gradle.sh` (registered в settings.json) |
| #4 Existing cross-cutting ADR re-validation в spec phase | **Applied** | `.claude/commands/feature-spec.md` Шаг 3.6.7 (новый) |
| #5 E2E instrumented test stage перед Codex | **Applied** | `.claude/commands/feature-implement.md` Шаг 2.5.3 (новая) |
| #6 Grounding BLOCKER как phase gate | **Applied** | `.claude/commands/feature-research.md` (BLOCKER findings section в Grounding rules) |
| #7 Lifecycle patterns catalog | **Documented** в `.claude/rules/lifecycle.md` (existing) — recurring lesson logged в lessons-learned.md, full skill catalog deferred unless pattern recurs | `docs/features/lessons-learned.md` |

## Sources (research)

- [Why Do Multi-Agent LLM Systems Fail? (arXiv 2503.13657)](https://arxiv.org/html/2503.13657v3) — MAST taxonomy: FM-2.2 "proceeding with wrong assumptions" 6.80% prevalence
- [Towards Verified Code Reasoning by LLMs (arXiv 2509.26546)](https://arxiv.org/html/2509.26546v1) — agent makes incorrect assumptions about code semantics, library behavior
- [Why Multi-Agent LLM Systems Fail (Redis Blog 2026)](https://redis.io/blog/why-multi-agent-llm-systems-fail/) — error compounding, conformity bias, monoculture trap
- [Adversarial Code Review (ASDLC.io)](https://asdlc.io/patterns/adversarial-code-review/) — Critic Constitution, false positives over false negatives
- [Refute-or-Promote (arXiv 2604.19049)](https://arxiv.org/html/2604.19049) — Cross-Model Critic methodology, validation gates
- [Spec Driven Development (InfoQ 2026)](https://www.infoq.com/articles/spec-driven-development/) — executable specifications, validation/drift-detection layer
- [Architecture Drift Detection (Erode.dev, ScienceDirect)](https://erode.dev/) — view-based drift analysis, automated detection
- [Claude Code Hooks Deterministic Control (Dotzlaw)](https://www.dotzlaw.com/insights/claude-hooks/) — 100% compliance vs 70-90% prompts
- [Decompose instanceKeeper (Decompose Docs)](https://arkivanov.github.io/Decompose/component/instance-retaining/) — state retention across config changes
