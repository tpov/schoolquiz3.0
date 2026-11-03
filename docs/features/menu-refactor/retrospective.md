# Pipeline Retrospective: menu-refactor

## Date
2026-04-22

## Summary

8 фаз реализации прошли 40 per-phase reviewer turns все PASS, но **cross-model Codex adversarial review (за один pass)** нашёл 6 integration-level bugs (2 blockers, 1 high, 2 medium, 1 low), которые same-model reviewers пропустили. Основной класс ошибок — **Integration Gap**: компоненты корректны изолированно, но ломаются при совместной работе между фазами (auth re-subscribe, Channel consumer split, DI orphans, mapper chain field loss). Текущий pipeline не имеет deterministic gate, который ловит integration issues до cross-phase Codex review — это единственная точка cross-model verification и она запускается в самом конце, после всех 8 фаз реализации и ~40 same-model reviewer turns.

## Bugs Analyzed

### Bug #1: `observeStats()` не re-subscribes на auth state changes (blocker)

- **Symptom**: После logout / account switch shell отображает данные предыдущего пользователя (nickname + qualification-gated menu visibility).
- **Root cause**: `UserStatsRepositoryImpl.kt:21` захватывает `currentUid()` один раз и навсегда остаётся на `userStatsDao.observeByUid(uid)`. Старый Firebase-based auth-change re-subscribe контракт (`FirebaseUserStatsDataSource.kt:25-38`) не был перенесён в новый Room-first design. `DefaultRootComponent.kt:167-185` ожидает реконнект на auth change, но он не происходит.
- **Injection point**: **Design** — `06-api-contract.md` и `08-storage-model.md` определили `observeStats(): Flow<UserStats?>` без явного auth state coupling. Legacy Firebase контракт re-subscribe не был inventoried как cross-phase invariant.
- **Propagation**: Plan точно отразил design signature (CORRECT). Implement faithfully followed plan (CORRECT). Per-phase reviewers каждый видел только свою фазу — Phase 04 (Repository impl) видел `observeStats()`, Phase 07 (Component consumer) видел consumer; никто не видел полный auth-change flow между фазами.
- **Detection gap**: Интеграционный тест для cold auth switch должен был существовать, но был не в scope ни одной фазы (Phase 04 testing covered warm cache; Phase 08 integration tests не покрыли auth transitions).
- **Failure pattern**: **Missing Side-Effect Inventory** (auth state change не перечислен как trigger для user-specific Flow) + **Integration Gap** (контракт теряется при переходе между фазами).

### Bug #2: `setLocalDeveloperLevel()` fails silently на fresh install (blocker)

- **Symptom**: На fresh install 10-tap activation не персистит `developer=100`. Shell остаётся на `UserStats.guest()`.
- **Root cause**: `UserStatsRepositoryImpl.kt:31` делает только `UPDATE` (`UserStatsDao.kt:20-21`) — если row отсутствует, silently пишет ничего. `AppApplication.kt:50-63` планирует только periodic sync без eager one-shot bootstrap для первого запуска. `InitializeAppShellUseCase.kt:20-22` читает только Room (empty at cold start).
- **Injection point**: **Design** — `08-storage-model.md` описал upsert-on-sync contract для server sync path, но не рассмотрел "fresh install + пользователь активирует dev mode до первого sync" сценарий. `06-api-contract.md` описал `setLocalDeveloperLevel(Int)` как UPDATE-level operation без явного upsert semantics.
- **Propagation**: Plan следовал design (CORRECT). Phase 04 impl followed UPDATE contract. Phase 06 impl (SyncWorker) only periodic — fresh install bootstrap не был в scope.
- **Detection gap**: Integration тест для cold-start activation sequence не существовал. Phase 04 tests (US-01..08) покрыли warm cache; Phase 08 integration tests (DM-phase7-01..04) — already-initialized dev mode.
- **Failure pattern**: **Missing Side-Effect Inventory** (fresh install scenario не inventoried) + **Assumption Not Verified** (row exists pre-activation).

### Bug #3: `rootComponent.events` Channel split между MainActivity + AppShellScreen (high)

- **Symptom**: `DevModeActivated` / `SyncStarted` events могут быть consumed no-op `TODO Phase 07` branches в `MainActivity.kt:33-35` вместо snackbar handler в `AppShellScreen.kt:123-145`. `SystemBack` ownership split nondeterministically.
- **Root cause**: `DefaultRootComponent.kt:98-103` экспонирует `events` как single-consumer `Channel.receiveAsFlow()`. Phase 03 добавил `MainActivity` events collector с `TODO Phase 07` branches (compile-fix для exhaustive when). Phase 07 добавил `AppShellScreen` consumer, но не удалил MainActivity collector. Channel с 2 consumers = race condition.
- **Injection point**: **Plan** — Phase 03 plan включил TODO stub в MainActivity для compile safety (sealed when); Phase 07 plan не включил cleanup этого stub.
- **Propagation**: Implement faithful. Per-phase review: Phase 03 reviewer одобрил TODO как legitimate compile stub; Phase 07 reviewer видел AppShellScreen collector как правильный, не re-checked MainActivity.
- **Detection gap**: Plan не имел "Cross-Phase Dependencies" секции которая бы отмечала: "Phase 03 TODO stub must be removed in Phase 07 before completion". Plan-reviewer не верифицирует Channel single-consumer contract против consumer count.
- **Failure pattern**: **Integration Gap** (cross-phase consumer split) + **Plan Faithfulness** (plan carried TODO stub которые should have been cleaned).

### Bug #4: `CreateQuestUseCase` orphaned в production Koin module (medium)

- **Symptom**: `catalogDomainModule` зарегистрирован в production (`AppApplication.kt:45-47`), но `CreateQuestUseCase` требует `QuestRepository` binding, которого нет. Latent `NoBeanDefFoundException` при первой попытке resolve.
- **Root cause**: `QuestRepository.kt:6-10` явно помечен TEMPORARY. Phase 05 зарегистрировал full catalogDomainModule в prod Koin graph не проверив что все его dependencies имеют productions bindings.
- **Injection point**: **Plan** — Phase 05 не выполнил "DI Graph Completeness" check (для каждого `get()` / Koin dependency — есть ли `single`/`factory` binding?).
- **Propagation**: Implement faithful. Per-phase reviewers trusted каждый что type существует; никто не верифицировал full Koin graph resolution.
- **Detection gap**: Нет grep-scan или Koin verify check в plan-reviewer / architect-reviewer. Koin verify API (`Verify()` из `koin-test`) не используется в pipeline.
- **Failure pattern**: **Integration Gap** (orphaned abstraction).

### Bug #5: `pictureUrl` dropped в mapper chain между Phase 05 и Phase 07 (medium)

- **Symptom**: Cached URL из `CatalogEntity.pictureUrl` (resolved в Phase 05) не доходит до `CatalogDisplayItem` — UI mapper hardcodes `pictureUrl = null`. Grid/spinner не может показывать pre-resolved images.
- **Root cause**: Phase 05 добавил pictureUrl в Entity layer (`CatalogRepositoryImpl.kt:27-37`). Phase 07 добавил `CatalogMapper.kt:7-11` который strips поле при Entity→Domain mapping, и UI mapper hardcodes null.
- **Injection point**: **Design** — `HLA-07` в `03-decisions.md` сказал "URL pre-resolution in data layer → CatalogEntity.pictureUrl cache" но не описал полный chain Entity → Domain → UI. Дизайн focused на data layer, UI chain был в Phase 07 scope без референтности обратно.
- **Propagation**: Phase 05 plan корректно описал Entity field. Phase 07 plan описал UI mapper в vacuum без reference к Phase 05 additions. Per-phase reviewers каждый видел свою часть.
- **Detection gap**: Mapper tests не делают round-trip с field-level проверкой. Tests used `.equals()` на объекте, что не ловит поле-за-полем.
- **Failure pattern**: **Integration Gap** (mapper chain break) + **Test Validates Wrong Spec** (round-trip не verify field completeness).

### Bug #6: Orphaned `Syncable` binding в AppShellDataModule (low)

- **Symptom**: `AppShellDataModule.kt:19` регистрирует `single<Syncable>` для user-stats, но `SyncModule.kt:14-18` игнорирует его и hand-builds `List<Syncable>` from repository interfaces. Catalog не register `Syncable` вообще. Dangling binding.
- **Root cause**: Inconsistent DI pattern между Phase 04 (single<Syncable>) и Phase 06 (hand-build list).
- **Injection point**: **Design** — `HLA-04` описал `Syncable` interface + `SyncWorker(syncables: List<Syncable>)` но не specified как register (`getAll<Syncable>()` DSL vs explicit list).
- **Propagation**: Plan carried ambiguity. Per-phase reviewers видели valid DSL paterns.
- **Detection gap**: Нет detection for orphaned/inconsistent DI bindings cross-phase.
- **Failure pattern**: **Integration Gap** (DI pattern drift).

### Bug #7: Plan paths несуществуют — `AppShellTransitions.kt` wrong directory (observation from feedback.txt)

- **Symptom**: `plan/phase-02/overview.md` указал `android/feature/app-shell/presentation/.../ui/AppShellTransitions.kt:31`, реальный путь `shared/feature/app-shell/domain/logic/AppShellTransitions.kt`. Dev обнаружил и поменял реальный.
- **Root cause**: Planner не верифицировал что paths соответствуют реальной файловой системе перед записью в phase overview.md.
- **Injection point**: **Plan** — planner writing paths "из головы" или из устаревших design docs.
- **Detection gap**: plan-reviewer не имеет grep/ls verification шага; нет hook для path existence check.
- **Failure pattern**: **Plan Faithfulness** (план не reflects reality) + **Incomplete Research** (paths не verified during plan).

### Bug #8: `PersistenceModule` missing в Phase 01 plan (observation from feedback.txt)

- **Symptom**: Phase 05 неожиданно потребовался `CatalogDao` binding, который требует `PersistenceModule` (Koin DI для AppDatabase + DAO). Backend-dev-p05 создал в `persistence/androidMain/di/`. Completeness-reviewer flagged как "planning gap Phase 01".
- **Root cause**: Planner не выполнил DI graph completeness trace через все фазы. Phase 01 spec'нул AppDatabase + DAO classes, но не spec'нул Koin module для них.
- **Injection point**: **Plan (Phase 01)**.
- **Detection gap**: plan-reviewer проверяет Signature Card (function signatures), но не Koin binding graph.
- **Failure pattern**: **Integration Gap** (DI graph incomplete) + **Plan Faithfulness**.

### Bug #9: security-reviewer pre-build review attempts (observation from implementation.md)

- **Symptom**: security-reviewer дважды (Phase 01, Phase 06) запустился до build gate. Самостоятельно отказался когда не нашёл build artifacts.
- **Root cause**: Reviewer prompt не требовал проверки "Build Status: PASSED" перед началом review. Reviewer начинал работу от `TaskUpdate(blocked → unblocked)` без cross-check.
- **Failure pattern**: **Review Blind Spot** (reviewer не имел детерминированного gate для подтверждения build success).

## Stage Performance

| Stage | Grade | Notes |
|-------|-------|-------|
| Research | B | Хорошо: полная module map (14 modules), 3 Walking Skeletons verified, 12 infra gaps identified. Пропущено: Fresh install scenario inventory (Bug #2), full Koin binding graph trace (Bugs #4, #6, #8), legacy Firebase auth re-subscribe contract как cross-phase invariant (Bug #1). |
| Design | C | Хорошо: 11 ADRs с Alternatives Considered, 77 test scenarios, 3 adversarial review rounds, 23 findings closed. Пропущено: Scenario Inventory (cold start / auth change / concurrent) per каждого UseCase/Flow (Bugs #1, #2); full data flow traceability Entity → Domain → UI (Bug #5); Channel/SharedFlow multicast vs single-consumer contract (Bug #3). |
| Plan | C | Хорошо: 8-phase bottom-up strategy, 27 markdown files, 5 plan review rounds, Signature Cards, ADR Coverage table, Options Considered для complex phases. Пропущено: Cross-Phase Dependencies секция (Bugs #3, #5), DI Graph Completeness check (Bugs #4, #6, #8), file path existence verification (Bug #7), TODO stub cleanup tracking (Bug #3). |
| Implement | A- | Хорошо: Все 8 фаз completed, все per-phase reviewers PASS после autonomous fix loops, scope creep обоснован и принят. Pragmatic compile-fix overlaps работали без coordination disasters. Единственный ding — несколько integration issues implement-ил faithful к плохому плану. |
| Review | C | Хорошо: 5 reviewers × 8 phases = 40 turns все PASS. Adversarial 4-lens Codex cross-phase review поймал все 6 missed bugs за один pass. Плохо: Per-phase same-model review пропустил ALL 6 integration bugs — структурная проблема (same-model blind spot + per-phase scope). security-reviewer 2× пытался review до build gate (нет deterministic check). |

## Pipeline Fixes Required

### Fix #1: Scenario Inventory mandate в design phase

- **Target file**: `.claude/commands/feature-design.md`
- **What to add**: Новая обязательная секция в design docs для каждого UseCase/Flow/Repository interface — **Scenario Inventory** с минимальным набором scenarios для инспекции: cold start (fresh install, empty cache), warm start (cache present), auth state changes (login / logout / account switch), network unavailable, concurrent modifications. Design reviewer проверяет что каждый UseCase/Flow в signature card имеет explicit handling для each scenario (или explicit N/A rationale).
- **Why**: Предотвращает **Missing Side-Effect Inventory** class. Прямое предотвращение Bug #1 (auth change scenario не inventoried) и Bug #2 (fresh install scenario не inventoried).
- **Instrument**: Design doc requirement (не hook — требует семантики доменной области). Добавить как dedicated subsection в `02-behavior.md` или `06-api-contract.md` template.
- **Prevents recurrence of**: Bug #1, Bug #2

### Fix #2: Cross-Phase Dependencies секция в phase overview template

- **Target file**: `.claude/commands/feature-plan.md` + `.claude/agents/planner.md`
- **What to add**: В phase `overview.md` template добавить обязательную секцию **Cross-Phase Dependencies**:
  ```
  ### Cross-Phase Dependencies
  
  **Consumed from previous phases** (types/Flows/Channels/DI bindings):
  - <Type> from phase-NN: consumer contract <single-consumer / multicast / state / event>
  - ...
  
  **Provided for next phases** (same format):
  - ...
  
  **Temporary stubs** (TODO markers created for compile safety):
  - <file>:<line> — TODO "Phase NN cleanup": <what needs to be removed/replaced>
  
  **Required cleanup in this phase** (from earlier temp stubs):
  - phase-NN TODO @ <file>:<line> — action: <remove / replace with real impl>
  ```
  plan-reviewer обязан cross-verify эту секцию: все "Consumed" — в "Provided" или external contracts; все Temporary stubs — имеют соответствующий Required cleanup в downstream фазе.
- **Why**: Предотвращает **Integration Gap** между фазами. Даёт planner-у механизм tracking cross-phase contracts и обязывает plan-reviewer verify.
- **Instrument**: Plan template + plan-reviewer grep-check.
- **Prevents recurrence of**: Bug #3 (TODO stub cleanup), Bug #4 (DI orphan), Bug #5 (mapper chain break), Bug #6 (DI pattern drift).

### Fix #3: Plan path existence hook

- **Target file**: `.claude/hooks/check-plan-paths.sh` (new) + `.claude/settings.json` registration
- **What to add**: Новый PostToolUse hook для Edit/Write против `docs/features/*/plan/phase-*/*.md`. Парсит markdown sections "Modified Files" и "New Files". Для каждого path:
  - Modified Files: `ls <path>` должен существовать. Если не — exit 2, blocker message.
  - New Files: parent dir должен существовать (`ls $(dirname <path>)`). Если не — exit 2, blocker message.
  - Paths relative к repo root (абсолютные — warning).
- **Why**: Предотвращает **Plan Faithfulness / Incomplete Research** class. Deterministic, fires at plan edit time.
- **Instrument**: Hook — "Hooks guarantee execution; prompts do not" (из research). 100% enforcement, не hope-based.
- **Prevents recurrence of**: Bug #7.

### Fix #4: DI Graph Completeness check для planner + plan-reviewer

- **Target file**: `.claude/agents/planner.md` + `.claude/commands/feature-plan.md`
- **What to add**: В mandatory planner steps — **DI Graph Completeness**. Для phase overview.md добавить subsection под Cross-Phase Dependencies:
  ```
  ### DI Bindings
  
  **Provided in this phase** (single/factory/viewModel):
  - <Type> via <Module>: dependencies = [<Type2>, <Type3>]
  
  **Required from earlier phases**:
  - <Type2> (expected in Phase-NN)
  
  **Koin Verify status**:
  - [ ] All `get()` / constructor deps have binding in this phase or earlier phases
  - [ ] Unit test runs `koinApplication { modules(<all modules>) }.checkModules()` if applicable
  ```
  plan-reviewer verifies closure: all Required from earlier имеет соответствующий Provided в предшествующих фазах.
- **Why**: Предотвращает **Integration Gap** для DI graph. Использует Koin's own verification API (research показал это standard practice).
- **Instrument**: Plan template + plan-reviewer check. Semi-automated (планер пишет, reviewer verifies closure).
- **Prevents recurrence of**: Bug #4, Bug #6, Bug #8.

### Fix #5: Mapper Chain Round-Trip field-level test requirement

- **Target file**: `.claude/rules/testing.md`
- **What to add**: В секцию Rules — новое правило: "Mapper round-trip tests (Entity ↔ Domain, Domain ↔ DTO) MUST verify each field explicitly, не только `equals(original)`. Используй field-by-field assertions или property-based testing with field introspection. `equals()` on data class с дефолтными значениями скрывает field-drop bugs."
  + grep check для test-reviewer: искать `assertEquals(original, mapped)` без field-level assertions как warning.
- **Why**: Предотвращает **Test Validates Wrong Spec** class. Прямое предотвращение Bug #5 (pictureUrl dropped но `equals()` mapping вернёт copy без обязательного поля если оба с defaults).
- **Instrument**: Rule в `.claude/rules/testing.md` + grep для architecture review.
- **Prevents recurrence of**: Bug #5.

### Fix #6: Reviewer build-gate confirmation требование

- **Target file**: `.claude/rules/agent-communication.md` (секция "Reviewers — подожди build gate через TaskList")
- **What to add**: Расширить секцию:
  ```
  Reviewer assignment prompt MUST include "Build Status: <PASSED|FAILED> (commit <sha or phase-ref>)". Reviewer проверяет:
  1. Строка присутствует в prompt
  2. Значение PASSED
  3. commit/phase reference валиден
  
  Если любое не выполнено → SendMessage lead-у ERROR: "Build status not confirmed in assignment" и НЕ начинает review. Не делает "самопроверку" через bash.
  ```
  А в `.claude/commands/feature-implement.md` (шаг assignment reviewer): добавить обязательное поле `Build Status: PASSED (commit ...)` в assignment template.
- **Why**: Deterministic enforcement что reviewer не начнёт pre-build. TaskCreate blockedBy уже блокирует claim, но prompt verification добавляет second layer.
- **Instrument**: Rule + assignment template requirement.
- **Prevents recurrence of**: Bug #9.

### Fix #7: Auth-Scoped Flow invariant

- **Target file**: `docs/invariants.md` (new entry #8)
- **What to add**:
  ```markdown
  ## 8. Auth-scoped Flow re-subscribe

  - **Invariant**: User-specific Flow (отдающий данные текущего пользователя — profile, stats, orders, messages) MUST be derived через `flatMapLatest` из auth state Flow / `currentUidFlow()`. Прямое `userDao.observeByUid(currentUid())` без auth trigger = violation (stale data после logout/account-switch).
  - **Constraint**: Repository implementation публичного user-specific Flow должен принимать `currentUidFlow: () -> Flow<String?>` в конструктор и применять `.flatMapLatest { uid -> uid?.let { dao.observeByUid(it) } ?: flowOf(null) }`.
  - **Owner**: `architect-reviewer` (grep check), `backend-dev` (implementation).
  - **Rule source**: `.claude/rules/auth-scoped-flow.md` (create)
  - **Added**: 2026-04-22, pipeline-retrospective menu-refactor (Bug #1).
  ```
  Также добавить `.claude/rules/auth-scoped-flow.md` с grep-check pattern: find repositories returning user-specific Flow where `currentUid()` called once outside flatMap.
- **Why**: Предотвращает рекурренс Bug #1 в других фичах (applicable для любого user-scoped data flow).
- **Instrument**: Invariant + rule + grep pattern в architect-reviewer.
- **Prevents recurrence of**: Bug #1.

### Fix #8: Channel single-consumer / SharedFlow multicast rule

- **Target file**: `.claude/rules/kotlin-conventions.md` (добавить секцию) + `.claude/agents/concurrency-reviewer.md`
- **What to add**: В kotlin-conventions.md новая секция:
  ```markdown
  ## Event streams: Channel vs SharedFlow vs StateFlow
  
  - **`Channel.receiveAsFlow()`** — **single-consumer** contract. Consumer "claims" events; multiple consumers → non-deterministic event split (race). Документируй в код-комментарии если hot.
  - **`MutableSharedFlow(replay=N, extraBufferCapacity=M)`** — multicast. Все collectors видят все emissions. Для events, которые несколько слоёв UI должны observe.
  - **`MutableStateFlow(initial)`** — state snapshot + distinct updates. Для state, не events.
  
  Таблица выбора:
  | Use case | Type |
  |----------|------|
  | One-shot event, один consumer | Channel |
  | Event, несколько observers | SharedFlow(replay=0) или (replay=1) |
  | Current state snapshot | StateFlow |
  | State history | SharedFlow(replay>=N) |
  ```
  В concurrency-reviewer checklist: grep-check `Channel.receiveAsFlow\|consumeAsFlow` + count consumers (`.collect\|collectLatest` references) — если >1, blocker.
- **Why**: Предотвращает **Integration Gap** когда event type contract неявный. Прямо адресует Bug #3.
- **Instrument**: Rule + reviewer grep.
- **Prevents recurrence of**: Bug #3.

### Fix #9: Fresh Install Bootstrap scenario в spec phase

- **Target file**: `.claude/commands/feature-spec.md` (primary journey checklist) + 0-spec template
- **What to add**: В Primary User Journeys секции добавить обязательный journey: **Fresh Install Journey** — "Что пользователь видит при первом запуске (empty cache, no persisted state)? Какой bootstrap sync / initial data load запускается? Когда периодический sync впервые стартует?". spec-reviewer проверяет наличие Fresh Install Journey во всех features где есть persistent storage / sync.
- **Why**: Предотвращает **Missing Side-Effect Inventory** для cold start scenario. Прямо адресует Bug #2.
- **Instrument**: Spec requirement + review check.
- **Prevents recurrence of**: Bug #2.

## Lessons Learned

- **Same-model per-phase review — structurally blind to integration-level bugs.** 40 phase reviewer turns (same Claude model) пропустили все 6 integration bugs, которые adversarial 4-lens Codex CLI поймал за один pass. Research подтверждает: "a model reviewing its own output inherits all its own generation biases" — 64.5% blind spot rate. Cross-phase Codex review — единственная deterministic точка catch-integration в текущем pipeline. Рекомендация: integration-level gate в plan phase (Cross-Phase Dependencies секция) чтобы переложить часть catch-integration нагрузки раньше.
- **Compile-coupled fazes требуют cross-phase dependency tracking, не strict role isolation.** Feedback пользователя подтвердил что 3 типа scope creep — legitimate для Kotlin multi-module. Но этого недостаточно: нужен explicit tracking что каждая фаза consumed/provides в terms of types, flows, channels, DI bindings. Это позволяет late-фазе catch early-фазы incomplete work (как PersistenceModule gap Phase 05).
- **Text rules без grep-check / hook ignored.** Bug #7 (plan paths) был идентифицирован в implementation lessons learned как recommendation для planner, но без mechanism — planner продолжал бы делать ту же ошибку. Research confirms: "If you try to solve these mechanics with prompts alone, you are basically hoping the model behaves. Hooks exist so you do not need hope."
- **Single-consumer vs multicast stream types — neglected distinction.** Channel.receiveAsFlow() контракт single-consumer; большинство developers/reviewers видят Flow и предполагают multicast. Это semantic gap который легко закодифицировать в rule + grep.
- **Fresh install — distinct scenario от warm start.** Cold start scenario систематически отсутствует в Primary Journey analysis. Не соответствует process death / config change (они имеют persisted state). Должен быть explicit journey.
- **Mapper round-trip tests с `equals()` скрывают field drops.** Data class с default values: mapped object может иметь field=null, `equals(original)` вернёт true если original тоже null. Field-level assertions обязательны для catch.

## Sources (research)

- [Self-Correction Bench: Revealing and Addressing the Self-Correction Blind Spot in LLMs](https://arxiv.org/html/2507.02778v1) — 64.5% blind spot rate для same-model self-correction
- [AI code review fails to catch AI-generated vulnerabilities](https://www.augmentedswe.com/p/ai-code-review-security) — same-model reviewer = "one guard wearing two hats"
- [Claude Code Advanced Best Practices 2026](https://smartscope.blog/en/generative-ai/claude/claude-code-best-practices-advanced-2026/) — hooks deterministic vs prompt-based
- [Runtime Enforcement Layer For Agents — Hooks](https://www.resilientcyber.io/p/a-look-at-an-emerging-runtime-enforcement) — hooks recursive across subagents
- [Android Developers — StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) — flatMapLatest для auth-scoped Flow
- [Kotlin flatMapLatest API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flat-map-latest.html) — cancel previous on new emission
- [Koin Verify API](https://insert-koin.io/docs/reference/koin-test/verify/) — compile-time/test-time DI graph verification
