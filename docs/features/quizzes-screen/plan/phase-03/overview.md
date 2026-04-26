---
phase: 03
name: Quizzes Module Skeleton — Gradle module + QuizzesConfig + DefaultQuizzesComponent (Idle anchor) + Koin DI
layer: presentation (new module)
complex: true
status: ready
---

# Phase-03: Quizzes Module Skeleton

## Goal

Создать новый Gradle module `android/feature/quizzes-screen/presentation/` с полной структурой пакетов. Реализовать `QuizzesConfig` (`@Serializable sealed class`, 6 вариантов), `QuizzesNavigator` interface, `QuizzesComponent` interface, `DefaultQuizzesComponent` (со стеком и Idle anchor, без child components — заглушки в childFactory), `QuizzesPresentationModule` (Koin). Зарегистрировать модуль в `AppApplication.kt`. Написать тесты backCallback lifecycle, dismissQuizzes, ChildStack initial state, QuizzesConfig serialization round-trip.

## Scope

- Новый Gradle module (backend-dev: build.gradle.kts, settings.gradle.kts)
- Kotlin source files (frontend-dev: configs, interfaces, DefaultQuizzesComponent)
- Koin module (frontend-dev: di/)
- AppApplication.kt update (backend-dev: scaffold)
- Tests (test-dev: JVM unit)

## Role Inputs

- `backend.md` — backend-dev (Gradle scaffold)
- `frontend.md` — frontend-dev (Kotlin code)
- `tests.md` — test-dev

## Layer

**presentation** (новый Android module `android/feature/quizzes-screen/presentation/`)

## Review Tags

`presentation`, `decompose`, `serialization`, `koin-di`, `back-handling`, `concurrency-review`

Concurrency-review добавлен: `childStack.subscribe { backCallback.isEnabled = ... }` — async subscription на lifecycle callbacks, shared mutable state `backCallback.isEnabled`.

## Complex Tag: YES

Критерии complex:
1. Затрагивает 4+ модулей: новый `quizzes-screen/presentation`, `apps/android-next` (AppApplication), `settings.gradle.kts`, `libs.versions.toml`/`build.gradle.kts`
2. Реализует новый архитектурный паттерн: **first serialized ChildStack** + manual BackCallback с `PRIORITY_OVERLAY`
3. REQUIRES tag: verify `BackCallback.PRIORITY_OVERLAY` в Essenty 2.x

### Options Considered

| Критерий | Option A (recommended): manual BackCallback | Option B: childStack(handleBackButton=true) | Option C: GlobalScope BackCallback |
|----------|----------------------------------------------|---------------------------------------------|-------------------------------------|
| Back priority control | Explicit `PRIORITY_OVERLAY` — guaranteed higher than root | Default priority — LIFO order, fragile on refactor | — |
| Correctness | Correct per ADR-QS-12 | Risky — same priority as DefaultRootComponent.backHandler | Leak |
| Test cost | 1d (QZ-U-01..05) | 0.5d | N/A |
| Coupling с Decompose | Low — standard BackCallback API | Medium — dependent on childStack behavior | High |
| Essenty REQUIRES | Yes — verify PRIORITY_OVERLAY constant | No | No |

**Recommended: Option A** (ADR-QS-12)

**Rationale:** Option A гарантирует корректный back priority. Option B LIFO fragile — при рефакторинге DefaultRootComponent порядок регистрации может измениться. ADR-QS-12 явно выбирает A.

**Rejected Option B:** LIFO порядок хрупкий. При `handleBackButton=true` quizzes использует дефолтный priority = тот же что у DefaultRootComponent.backHandler → не гарантировано что quizzes callback вызывается первым.

**Rejected Option C:** GlobalScope — memory leak, antipattern.

---

## State Matrix Coverage

Matrix rows (из `02-behavior.md`):
- **Matrix 3 (Overlay visibility)**: строки 1-9 — `DefaultQuizzesComponent` реализует state machine (`Idle` → overlay hidden, non-Idle → overlay shown)
- **ChildStack state machine** из `02-behavior.md` High-Level State Machine: transitions Idle↔QuestList↔SectionList↔... — backCallback.isEnabled управляется через `childStack.subscribe`

В этой фазе childFactory создаёт только `QuizzesChild.Idle` (заглушки для других конфигов). Matrix rows 2-8 (non-Idle transitions) полностью реализуются в Phase-04.

## Domain Contract Coverage

Feature Domain Contract = N/A. Эта фаза реализует navigation skeleton — `QuizzesConfig` (serialization contract) и `DefaultQuizzesComponent` (ChildStack + backCallback).

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|------------|--------------|-----------------|--------------|------------|
| **Problem 1**: Внутренний ChildStack в `QuizzesComponent` | frontend-dev (quizzes-screen/presentation) | `DefaultQuizzesComponent.init` — `childStack(serializer=QuizzesConfig.serializer(), handleBackButton=false)` + backCallback setup | `handleBackButton = false`; manual `BackCallback(PRIORITY_OVERLAY)` (ADR-QS-12); заглушки в childFactory до Phase-04 | Новый module + DefaultQuizzesComponent с StackNavigation + subscribe | `DefaultQuizzesComponentTest` QZ-U-01..09 |
| **Problem 6**: Process death + StateKeeper для QuizzesConfig | frontend-dev + backend-dev (kotlinx-serialization plugin) | `childStack(serializer = QuizzesConfig.serializer())` — first stack в проекте с `serializer != null` | `@Serializable` на всех 6 вариантах; `PRIORITY_OVERLAY` constant REQUIRES verify | `@Serializable sealed class QuizzesConfig` + try/catch SerializationException fallback | `QuizzesConfigSerializationTest` SER-01..11 |

## New Files

| File | Owner | Canonical ref |
|------|-------|---------------|
| `android/feature/quizzes-screen/presentation/build.gradle.kts` | backend-dev | `01-architecture.md` (module deps) |
| `android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml` | backend-dev | standard library module manifest |
| `android/feature/quizzes-screen/presentation/src/main/kotlin/.../config/QuizzesConfig.kt` | frontend-dev | `06-api-contract.md:329` |
| `android/feature/quizzes-screen/presentation/src/main/kotlin/.../navigation/QuizzesNavigator.kt` | frontend-dev | `06-api-contract.md:11` |
| `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuizzesComponent.kt` | frontend-dev | `06-api-contract.md:392` |
| `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuizzesComponent.kt` | frontend-dev | `06-api-contract.md:392` |
| `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuizzesChild.kt` | frontend-dev | `06-api-contract.md:500` |
| `android/feature/quizzes-screen/presentation/src/main/kotlin/.../di/QuizzesPresentationModule.kt` | frontend-dev | `06-api-contract.md:742` |
| `android/feature/quizzes-screen/presentation/src/test/kotlin/.../DefaultQuizzesComponentTest.kt` | test-dev | `04-testing.md §2` |
| `android/feature/quizzes-screen/presentation/src/test/kotlin/.../QuizzesConfigSerializationTest.kt` | test-dev | `04-testing.md §8` |
| `android/feature/quizzes-screen/presentation/src/test/kotlin/.../QuizzesStateKeeperRestoreTest.kt` | test-dev | `04-testing.md §12` |

## Modified Files

| File | Owner | Change |
|------|-------|--------|
| `settings.gradle.kts` | backend-dev | `+include(":android:feature:quizzes-screen:presentation")` |
| `apps/android-next/src/main/.../AppApplication.kt` | backend-dev | `+quizzesPresentationModule` в startKoin modules list |

## Deleted Files

none

## Dependencies

Depends on: Phase-01 (FakeQuestRepository — для тестов), Phase-02 (QuestDisplayItem.catalogId, QuestCard API). Phase-04+ depends on this phase.

## Acceptance Criteria

1. `./gradlew :android:feature:quizzes-screen:presentation:assemble --no-configuration-cache` — зелёный (новый module компилируется).
2. `DefaultQuizzesComponent` инициализируется с `childStack(serializer = QuizzesConfig.serializer(), handleBackButton = false, initialStack = { listOf(QuizzesConfig.Idle) })`.
3. `backCallback.isEnabled = false` при `stack = [Idle]`; `true` когда `backStack.isNotEmpty()`.
4. `QuizzesConfigSerializationTest` зелёный — round-trip каждого из 6 вариантов (SER-01..11).
5. `DefaultQuizzesComponentTest` зелёный — QZ-U-01..09.
6. `QuizzesPresentationModule` зарегистрирован в `AppApplication.kt`.
7. `./gradlew allTests --no-configuration-cache` — зелёный.
8. `./gradlew assemble --no-configuration-cache` — зелёный.
9. Новые stateful fields в `DefaultQuizzesComponent` (`backCallback`, `childStack`) сбрасываются при component destroy (lifecycle-aware, `doOnDestroy { componentJob.cancel() }`).

## Tests Required

TDD — параллельно:

**DefaultQuizzesComponentTest (JVM unit)**:
- `when stack=[Idle] then backCallback disabled`: given fresh component, then backCallback.isEnabled == false
- `when pushNew(QuestList) then backCallback enabled`: given pushNew via openQuestList, then backCallback.isEnabled == true
- `when pop to Idle then backCallback disabled`: given stack=[Idle,QuestList], when pop, then backCallback.isEnabled == false
- `backCallback priority equals PRIORITY_OVERLAY`: given registered callback, then priority == BackCallback.PRIORITY_OVERLAY (or numeric 100)
- `dismissQuizzes collapses to [Idle]`: given stack=[Idle,QuestList,SectionList], when dismissQuizzes(), then items.size == 1
- `dismissQuizzes when stack=[Idle] is noop`: given stack=[Idle], when dismissQuizzes(), then items.size == 1, no exception
- `initial stack contains exactly Idle`: given fresh component, then items.size == 1, active is QuizzesChild.Idle
- `Idle childFactory returns QuizzesChild.Idle`: given childFactory(QuizzesConfig.Idle), then QuizzesChild.Idle

**QuizzesConfigSerializationTest (JVM unit)**:
- SER-01..06: round-trip каждого варианта через `Json.encodeToString/decodeFromString`
- SER-07: stack `[Idle, QuestList, SectionList]` serializes to identical stack
- SER-08: titles with кириллица survive round-trip
- SER-09: empty titles list survives round-trip
- SER-10: missing required field → SerializationException → fallback to `[Idle]`
- SER-11: unknown discriminator → fallback to `[Idle]`

**QuizzesStateKeeperRestoreTest (JVM unit)**:
- PD-01..05: StateKeeperDispatcher save/restore stack

## Pattern Invariants

1. `QuizzesComponent` (new module) ОБЯЗАН НЕ импортировать из `android/feature/quest/presentation` или `android/feature/app-shell/presentation` — Invariant 3 (`.claude/rules/clean-architecture.md:62-66`). Grep: `grep -rE "^import.*android\.feature\.(quest|app_shell)\.presentation" android/feature/quizzes-screen/`.
2. `childStack(handleBackButton = false)` — Decompose не управляет back; manual BackCallback (ADR-QS-12, `03-decisions.md`). Existing pattern: `DefaultRootComponent.kt:139-143`.
3. `backCallback.isEnabled` обновляется ТОЛЬКО через `childStack.subscribe { ... }` — не вручную в методах nav.
4. `QuizzesConfig` поля — только `String`, `List<String>`, примитивы. Никаких `@JvmInline value class` (`CatalogId` etc.) — они не `@Serializable` в текущей codebase (`1-research.md:42-45`).
5. `BackCallback(priority = 100)` — DEFERRED: `BackCallback.PRIORITY_OVERLAY` отсутствует в Essenty 2.1.0 (`gradle/libs.versions.toml:36`). Verified via grep: no `PRIORITY_OVERLAY` in codebase. Implementer использует `priority = 100` (выше default 0). Unblock criteria: Essenty ≥ 2.4.0.
6. `lifecycle.doOnDestroy { componentJob.cancel() }` — обязательный паттерн для всех Default*Component (per `DefaultHomeQuestsComponent.kt:33-48`).

## Validation

| # | Command | Expected |
|---|---------|----------|
| 1 | `./gradlew :android:feature:quizzes-screen:presentation:assemble --no-configuration-cache` | passes — новый module компилируется |
| 2 | `./gradlew :android:feature:quizzes-screen:presentation:test --no-configuration-cache` | passes — DefaultQuizzesComponentTest + QuizzesConfigSerializationTest green |
| 3 | `./gradlew allTests --no-configuration-cache` | passes |
| 4 | `./gradlew assemble --no-configuration-cache` | passes |
| 5 | `grep -rE "^import.*android\.feature\.(quest\|app_shell)\.presentation" android/feature/quizzes-screen/` | empty (Invariant 3) |

## Handoff Notes

- childFactory для non-Idle configs — заглушки (throw или placeholder child). Phase-04 реализует реальные children.
- `BackCallback.PRIORITY_OVERLAY` — DEFERRED. Константа отсутствует в Essenty 2.1.0 (verified: `gradle/libs.versions.toml:36`, grep по всему проекту — 0 matches). Hardcoded `priority = 100` (существующий паттерн: `DefaultRootComponent.kt:140`). Implementer использует Pattern Invariant: `BackCallback(priority = 100)`. Unblock: Essenty ≥ 2.4.0.
- После Phase-03 skeleton готов — Phase-04 (drill-down children) ДОЛЖЕН завершиться до Phase-05 (screens): screens используют Decompose `Value<UiState>` от 5 child components; компиляция Phase-05 зависит от Phase-04 interfaces.
