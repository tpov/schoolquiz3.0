# Lessons Learned — Pipeline Retrospectives Archive

Этот файл — архив обобщённых уроков из ретроспектив фич. Pipeline leads читают `docs/invariants.md` (cross-feature инварианты) и свежие `docs/features/<slug>/retrospective.md` для текущей фичи. Этот файл — для людей, которые хотят понять эволюцию методологии.

Формат одной записи:

```
### <date> — <feature-slug>: <one-line lesson>
- **Pattern**: <имя failure pattern>
- **Lesson**: <что future research/design должны проверять>
- **Example**: <конкретный пример из этой фичи>
```

---

### 2026-04-19 — app-shell-menu: Plan = ТЗ, не implementation

- **Pattern**: Plan Faithfulness + Model Default Behavior + Missing Deterministic Enforcement
- **Lesson**: planner LLM по умолчанию копирует готовый код из design docs (`06-api-contract.md`) в plan-файлы, даже когда text rule запрещает. Решение — 3-слойная защита: (1) Signature Card format в `planner.md`, (2) hook `check-plan-no-code.sh` на файловой системе, (3) Plan Review Lens для Codex CLI
- **Example**: `plan/phase-01/backend.md` = 783 строки, 22 fenced `\`\`\`kotlin` блока (72% содержания), прошло 3 раунда Codex CLI plan review без замечаний. Text rule `feature-plan.md:188` игнорировался 7 фаз подряд

### 2026-04-19 — app-shell-menu: DRY для design docs — canonical single source

- **Pattern**: Document Duplication
- **Lesson**: каждый публичный domain type имеет ровно ОДИН canonical source — в `06-api-contract.md`. Другие docs (01-architecture, 02-behavior, plan/*.md) ссылаются, не дублируют полные сигнатуры. Drift неизбежен, когда одно изменение требует правок в 4 файлах
- **Example**: Navigator описан 5 раз — в 0-spec.md, 01-architecture.md, 06-api-contract.md, plan/phase-01/backend.md, domain/Navigator.kt. Добавлена Document Responsibility Matrix в `feature-design.md` с grep-check командой

### 2026-04-19 — app-shell-menu: Alternatives — first-class artifact для complex фаз

- **Pattern**: Methodology Gap
- **Lesson**: ADR best practice (Nygard 2011, "The most valuable part of ADR is the rejected alternatives") применяется к phase-level planning. Для complex фаз (3+ модулей / FSM / новый pattern) plan содержит Options Considered таблицу с recommended + rejected + trade-offs. Без альтернатив — переписать фазу в случае проблемы в коде дорого
- **Example**: все 7 фаз `app-shell-menu` содержали ровно один вариант реализации. User feedback: "несколько вариантов, а потом с каждым ревью он убирал или приоритизировал пункты — что-бы можно было легко переписывать если проблема реально проявится"

### 2026-04-19 — app-shell-menu: Shared-model review blind spot

- **Pattern**: Review Blind Spot
- **Lesson**: Codex CLI эффективен для correctness bugs (поймал Firebase wiring gap, InstanceKeeper leak, firestore.rules wrong fields — 3 critical issues). НО: Codex делит те же pipeline-assumptions с автором plan → не ловит methodology drift (plan = код, document duplication). Нужен **explicit review lens** с checklist, не general-purpose review
- **Example**: 3 round'а Codex plan review пропустили 22 `\`\`\`kotlin` блока в plan-файлах. После retrospective создан `.claude/skills/adversarial-review/references/plan-review-lens.md` с явным чеклистом

### 2026-04-19 — app-shell-menu: Text rules → Deterministic enforcement

- **Pattern**: Missing Deterministic Enforcement
- **Lesson**: правила в markdown (CLAUDE.md, feature-*.md) LLM рационализирует в обход. Для правил, которые МОГУТ быть автоматизированы — используй hooks (`.claude/hooks/*.sh`) + grep + exit 2. Это 100% enforcement vs 0% для text rules в реальности
- **Example**: `feature-plan.md:188` "Только phase files, никакого кода" legal 6+ месяцев, но planner его игнорировал. После retrospective добавлен hook `check-plan-no-code.sh` (PostToolUse, exit 2 с объяснением)

### 2026-05-01 — home-and-my-quests / quizzes-screen / lesson-runner: Per-phase same-model review structurally blind to design/integration

- **Pattern**: Review Blind Spot + Modeling Error
- **Lesson**: Per-phase reviewers (architect/code/security/completeness/concurrency) — все same Claude model. ~30-40 review turns per feature все PASS, но cross-phase Codex (4 lenses, different model) ловит ВСЕ blockers за один pass. Web research: same-model self-review = 64.5% blind spot rate. Mitigation на уровне pipeline: ADR-vs-code grep audit обязательная часть phase-level architect-reviewer (recurrent ловится deterministically), Codex stays cross-phase final gate.
- **Example**: home-and-my-quests B1-B5 (Firestore rules, subtree-atomic cursor, cursor strategy, cv > local filter, archived cascade) — все BLOCKERs прошли через 5 phases × 5 reviewers = 25 turns, поймал cross-phase Codex Skeptic+Architect+Realist 4-lens. lesson-runner потребовал 6 Codex rounds потому что pipeline catch'ил design issues только post-impl.

### 2026-05-01 — lesson-runner: Manual SSoT (06-api-contract, C4 diagram) drift inevitable

- **Pattern**: Modeling Error + Assumption Not Verified
- **Lesson**: Manually maintained "canonical" docs (06-api-contract.md signatures, 01-architecture.md C4 diagrams, ADR vs implementation) drift через 1-2 фазы. Industry practice (Spec-Driven Development, InfoQ 2026): либо generate canonical из source (KSP, reflection), либо deterministically validate против source (script / hook). Manual canonicalization без automated verification = no canonicalization.
- **Example**: lesson-runner Bug #2 — C4 diagram показывает `quizzes-screen → lesson-runner` import, реальный `build.gradle.kts:11-17` пустой. Bug #4 — `06-api-contract.md:447-450` snippets used `lessonAttemptRepository`, real `CompleteAttemptUseCase.kt:22-26` имеет `attemptRepository`. После retrospective: hooks `check-c4-vs-gradle.sh` + `check-api-contract-types.sh` deterministically verify drift.

### 2026-05-01 — quizzes-screen: Smoke test ordering wrong (after Codex, not before)

- **Pattern**: Lead Role Violation + Test Validates Wrong Spec
- **Lesson**: `./gradlew test` ловит build/integration bugs за секунды, Codex review занимает минуты + tokens. Если smoke test запускается ПОСЛЕ Codex review (currently feature-implement.md sequence) — Codex spent budget на build-broken state, finds симптомы вместо design issues. Correct sequence: phases → smoke test (gate) → Codex (input: green build).
- **Example**: quizzes-screen Bug #1 — `KoinModuleWiringTest.kt:205` instantiated `DefaultRootComponent` без 3 added factory params (phase-07). Phase-07 frontend-dev ran `:android:feature:quizzes-screen:presentation:test` (subset), claimed PASS. Smoke test (post-Codex) caught — но Codex tokens already spent. После retrospective: smoke test moved BEFORE Codex в feature-implement.md.

### 2026-05-01 — lesson-runner: Codex post-design без code references = drift miss

- **Pattern**: Review Blind Spot
- **Lesson**: Codex review запускался post-design phase (правильно), но получал design docs БЕЗ ссылок на existing code (Walking Skeleton, build.gradle.kts). Result: ловил contradictions внутри docs, не drift docs vs source. Lesson-runner: 6 Codex rounds (spec, design, plan, post-impl rounds) — все same-model gap не resolved. Mitigation: Codex prompt в design phase ОБЯЗАН включать code references (Walking Skeleton domain dir, Gradle build files для модулей упомянутых в C4).
- **Example**: lesson-runner Bug #4 — canonical API в `06-api-contract.md` написан вручную, не extracted from source. Codex post-design не имел access к Walking Skeleton domain → не поймал drift. Поймал только cross-phase Codex post-impl (Round 1 BLOCKER).

### 2026-05-01 — home-and-my-quests: Deferred HIGH findings без user approval = silent debt

- **Pattern**: Lead Role Violation + Delegated Decision Error
- **Lesson**: 5 из 8 HIGH findings home-and-my-quests были помечены DEFERRED в `implementation.md` без AskUserQuestion approval. Это нарушение CLAUDE.md "Escalate, не импровизируй" — silent debt accept. HIGH/BLOCKER decisions about deferring должны быть user-approved через explicit AskUserQuestion.
- **Example**: H4 (DefaultRootComponent → SyncWorker import — layer violation), H6 (getKoin() в Composable). Оба marked "phase-05-debt" / "deferred" без user awareness. После retrospective: `feature-implement.md` Шаг 4.5 — деferred HIGH findings обязаны через AskUserQuestion approval до handoff.

### 2026-05-01 — lesson-runner: Lifecycle patterns (instanceKeeper, BackCallback, FLAG_SECURE) не в research scope

- **Pattern**: Lifecycle Mismatch + Incomplete Research
- **Lesson**: Decompose `instanceKeeper` для rotation state retention, BackCallback override priorities, FLAG_SECURE timing — все project-level lifecycle patterns. Если spec не reference, implementer выбирает default (`remember` для drafts, no Back override, conditional FLAG_SECURE) — wrong. JVM/preview tests их не ловят, нужны instrumented connectedAndroidTest. Research phase обязан validate "does this feature use these patterns? what's expected behavior?" Если yes — задокументировать в spec.
- **Example**: lesson-runner Bugs #9-11 — rotation drafts lost (AC-35), system Back bypass (AC-3/34), FLAG_SECURE Loading window (AC-28). Все caught Codex Round 2 после implementation. Manual smoke required. После retrospective: feature-implement.md Шаг 2.5.3 — обязательный E2E instrumented test stage перед Codex.
