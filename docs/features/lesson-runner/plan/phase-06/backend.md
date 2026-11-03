---
phase: phase-06
role: backend-dev
---

# Phase 06 — Backend Tasks

## Pattern Invariants

- Атомарная замена: удалить `LessonPlaceholder` и добавить `LessonRunner` в ОДНОМ commit (compile errors guide migration) — per precedent `DefaultRootComponent.kt:113-114` (atomic child replacement)
- `isHardChecked` — `MutableStateFlow<Set<String>>` в component, НЕ persistable state — per `DefaultQuizzesComponent.kt:117` (`hardCheckedIds` precedent)
- `combine(flow1, flow2)` — использовать Kotlin Flows `combine` operator для reactive bestStars/hardUnlocked; per `DefaultQuizzesComponent.kt:117` exhaustive when precedent
- `exhaustive when` — НЕ добавлять `else` в `createChild` / `QuizzesScreen`; per `DefaultQuizzesComponent.kt:117` (exhaustive when enforced by compiler)
- `LessonRunnerComponentFactory` — fun interface; lambda binding в `AppApplication.kt` (composition root, Phase-07), НЕ в `quizzesPresentationModule`
- **ADR-LR-17 (Compose exception)**: `QuizzesScreen.kt` импортирует `LessonRunnerScreen` `@Composable` из `lesson-runner/presentation` напрямую — ONE-WAY dependency разрешена; precedent: `AppShellScreen.kt:53-56`. Reverse direction — blocker.
- **ADR-QS-15 SUPERSEDED** (2026-04-27): initial navigation design заменён ADR-LR-16 + ADR-LR-17 — см. `docs/features/quizzes-screen/03-decisions.md:499`. Действующие решения — ADR-LR-16 и ADR-LR-17.
- `QuizzesChild.LessonRunner(component: LessonRunnerRootComponent)` — использует typed interface из `lesson-runner/presentation` (ADR-LR-16); НЕ из `core/navigation`
- `QuizzesComponent.popCurrentChild()` ОБЯЗАН быть добавлен в интерфейс `QuizzesComponent` — делегирует `navigation.pop()` (per `06-api-contract.md:342`); вызывается `QuizzesScreen` как `onNavigateBack` callback (A2 hybrid)

---

## Modify `QuizzesConfig` — atomic LessonPlaceholder replacement

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../config/QuizzesConfig.kt`
- **Тип:** sealed class (existing — modify)
- **Сигнатура:** remove `LessonPlaceholder`, add `@Serializable data class LessonRunner(val lessonId: String, val mode: Difficulty, val titles: List<String>) : QuizzesConfig()`
- **Вход:** N/A — sealed class modification
- **Поведение / Выход:**
  - УДАЛИТЬ `@Serializable data class LessonPlaceholder(...)` из sealed class
  - ДОБАВИТЬ `@Serializable data class LessonRunner(val lessonId: String, val mode: Difficulty, val titles: List<String>) : QuizzesConfig()`
  - `mode: Difficulty` — требует Phase-01 `@Serializable` on Difficulty
  - `titles: List<String>` — breadcrumb path including lesson title last
  - `lessonId: String` — raw string (LessonId.value); per `06-api-contract.md:17`: "XxxId не @Serializable"
- **Edge cases:**
  - После удаления `LessonPlaceholder` — компилятор покажет все когда-ветви для исправления (compile-guided)
  - `QuizzesConfigSerializationTest` нужно обновить: убрать `LessonPlaceholder` round-trip, добавить `LessonRunner` round-trip
  - Backward compat с saved state: нет (атомарная замена безопасна per ADR-LR-07 — нет production saved state)
- **Depends on:** `Difficulty` (Phase-01 @Serializable)
- **Canonical reference:** `06-api-contract.md:17`
- **Rationale:** ADR-LR-07 full atomic replacement

---

## Modify `QuizzesChild` — add/remove variants

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt`
- **Тип:** sealed interface (existing — modify)
- **Сигнатура:** remove `LessonPlaceholder`, add `data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild`
- **Вход:** N/A
- **Поведение / Выход:**
  - `data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild`
  - `LessonRunnerRootComponent` — interface из `android/feature/lesson-runner/presentation/` (ADR-LR-16); НЕ из `core/navigation`
  - УДАЛИТЬ `LessonPlaceholder` variant
- **Edge cases:**
  - `LessonRunnerRootComponent` импорт из `com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent` — это одностороннее dep `quizzes-screen → lesson-runner` per ADR-LR-17
  - `quizzes-screen/presentation/build.gradle.kts` добавляет `implementation(project(":android:feature:lesson-runner:presentation"))` — backend-dev owned file
- **Depends on:** `LessonRunnerRootComponent` (lesson-runner/presentation, Phase-04, `06-api-contract.md:284`)
- **Canonical reference:** `06-api-contract.md:46`
- **Rationale:** ADR-LR-07; `QuizzesChild` mirrors `QuizzesConfig` variants; typed interface per ADR-LR-16

---

## Modify `DefaultQuizzesComponent` — createChild update + new deps + popCurrentChild

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt`
- **Тип:** class (existing — modify)
- **Сигнатура:** add `lessonAttemptRepository: LessonAttemptRepository`, `authRepository: AuthRepository`, `lessonRunnerFactory: LessonRunnerComponentFactory` constructor params; add `popCurrentChild()`; update `createChild`
- **Вход:** 3 new constructor deps
- **Поведение / Выход:**
  - Constructor: добавить `private val lessonAttemptRepository: LessonAttemptRepository`, `private val authRepository: AuthRepository`, `private val lessonRunnerFactory: LessonRunnerComponentFactory`
  - `createChild(config: QuizzesConfig, componentContext: ComponentContext)` per existing pattern at `DefaultQuizzesComponent.kt:113`:
    - УДАЛИТЬ ветку `is QuizzesConfig.LessonPlaceholder`
    - ДОБАВИТЬ `is QuizzesConfig.LessonRunner -> QuizzesChild.LessonRunner(lessonRunnerFactory.create(componentContext, LessonId(config.lessonId), config.mode))`
    - Передавать `lessonAttemptRepository` и `authRepository` в `DefaultLessonListComponent` factory call
  - `lessonRunnerFactory: LessonRunnerComponentFactory` — импортируется из `lesson-runner/presentation` (ADR-LR-16)
  - `override fun popCurrentChild() { navigation.pop() }` — вызывается `QuizzesScreen` как `onNavigateBack` callback когда `LessonRunnerScreen` получает `RunnerEvent.NavigateBack` (A2 hybrid per `06-api-contract.md:342`)
- **Edge cases:**
  - `LessonId(config.lessonId)` — конвертация `String → LessonId` domain value object при вызове factory
  - Exhaustive `when` — компилятор enforce при удалении `LessonPlaceholder`
  - `lessonRunnerFactory` инжектируется через Koin `get()` в `quizzesPresentationModule`
  - `popCurrentChild()` добавляется в `QuizzesComponent` interface (existing interface file — update atomically)
- **Depends on:** `LessonRunnerComponentFactory` (lesson-runner/presentation, `06-api-contract.md:354`), `LessonAttemptRepository` (lesson-runner/domain), `AuthRepository` (app-shell/domain)
- **Canonical reference:** `06-api-contract.md:240`, `06-api-contract.md:342`
- **Rationale:** `DefaultQuizzesComponent` — coordination root для quizzes ChildStack; factory injection per ADR-LR-16; `popCurrentChild()` — navigation contract A2 hybrid entry point

---

## Create `LessonItemUi`

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../uistate/LessonItemUi.kt`
- **Тип:** data class
- **Сигнатура:** `data class LessonItemUi(val id: String, val title: String, val orderLabel: String? = null, val subtitleCount: String? = null, val bestStarsRawTenths: Int = 0, val hardUnlocked: Boolean = false, val isHardChecked: Boolean = false)`
- **Вход:** lesson data + computed state
- **Поведение / Выход:**
  - `bestStarsRawTenths: Int = 0` — 0..30; `StarRating(rating = raw/10f)` в UI
  - `hardUnlocked: Boolean = false` — ∃ EASY attempt with `allShownAnswersAre9 == true`
  - `isHardChecked: Boolean = false` — ephemeral; NOT persisted; from `MutableStateFlow<Set<String>>` in component
  - Defaults ensure backward-compat с existing test code
- **Edge cases:**
  - `isHardChecked` — НЕ хранится в Room; UI state only
  - `orderLabel`, `subtitleCount` nullable — per `HierarchyItemUi.kt:6` pattern
- **Depends on:** N/A
- **Canonical reference:** `06-api-contract.md:474`
- **Rationale:** Lesson-specific card model; Checkbox + Stars not in generic HierarchyItemUi (ADR-LR-11)

---

## Modify `DefaultLessonListComponent` — add 2 deps + combine flow

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultLessonListComponent.kt`
- **Тип:** class (existing — modify)
- **Сигнатура:** add `private val lessonAttemptRepository: LessonAttemptRepository`, `private val authRepository: AuthRepository`
- **Вход:** 2 new deps
- **Поведение / Выход:**
  - `private val hardCheckedSet: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())`
  - In `init`: `coroutineScope.launch { combine(lessonRepository.observeByTheme(themeId), authRepository.observeUid().flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else attemptRepository.observeAllByUser(uid) }, hardCheckedSet) { lessons, attempts, checkedSet -> mapToUi(lessons, attempts, checkedSet) }.collect { _lessonItems.value = it } }`
  - `mapToUi(lessons, attempts, checkedSet)`:
    - Per lesson: `bestStarsRawTenths = computeBestStars(attempts.filter{it.lessonId == lesson.id}).rawTenths`
    - `hardUnlocked = computeHardUnlocked(attempts.filter{it.lessonId == lesson.id})`
    - `isHardChecked = lesson.id.value in checkedSet`
  - `onLessonClick(item: LessonItemUi)`:
    - `val mode = if (item.hardUnlocked && item.isHardChecked) Difficulty.HARD else Difficulty.EASY`
    - `navigation.pushNew(QuizzesConfig.LessonRunner(lessonId=item.id, mode=mode, titles=config.titles + item.title))`
  - `onHardCheckToggled(lessonId: String)`:
    - Только если item.hardUnlocked → toggle in `hardCheckedSet`
  - `lifecycle.doOnDestroy { hardCheckedSet.value = emptySet() }` — stateful field reset
  - `computeBestStars` и `computeHardUnlocked` — из `RunnerLogic` (domain, pure functions) или inline calculation using spec formulas
- **Edge cases:**
  - `authRepository.observeUid()` — если null (user not authenticated) → пустой список attempts → hardUnlocked=false, bestStars=0
  - `RunnerLogic.computeBestStars` и `computeHardUnlocked` — взять из Walking Skeleton (существуют); не переписывать logic
  - `coroutineScope` — из Decompose ComponentContext или Essenty lifecycle-aware scope
- **Depends on:** `LessonAttemptRepository` (lesson-runner/domain, ADR-QS-16), `AuthRepository` (app-shell/domain), `RunnerLogic` (lesson-runner/domain, ADR-QS-16)
- **Canonical reference:** `06-api-contract.md:240`, `02-behavior.md DFD 1`
- **Rationale:** Расширение per Grounding Problem 7 (Option B: LessonItemCard в quizzes-screen)

---

## Modify `DefaultQuizzesComponent` Koin: `quizzesPresentationModule`

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt`
- **Тип:** Koin module (existing — modify)
- **Сигнатура:** update `factory<QuizzesComponent>` binding
- **Вход:** 3 new `get()` calls
- **Поведение / Выход:**
  - Add `lessonAttemptRepository = get()` to `DefaultQuizzesComponent` constructor
  - Add `authRepository = get()`
  - Add `lessonRunnerFactory = get<LessonRunnerComponentFactory>()` — `LessonRunnerComponentFactory` разрешается из Koin; binding живёт в `AppApplication.kt` (Phase-07 composition root per `06-api-contract.md:374`)
  - НЕ добавлять `single<LessonRunnerComponentFactory>` в `quizzesPresentationModule` — этот binding в `AppApplication.kt` (composition root) per canonical wiring
- **Edge cases:**
  - `LessonRunnerComponentFactory` import в `quizzesPresentationModule` — из `lesson-runner/presentation` (ADR-LR-16); допустимо в DI Koin module (composition root boundary)
  - `get<LessonRunnerComponentFactory>()` — Koin resolves singleton factory binding из composition root
- **Depends on:** `LessonRunnerComponentFactory` (lesson-runner/presentation, `06-api-contract.md:323`), `LessonAttemptRepository`, `AuthRepository`
- **Canonical reference:** `06-api-contract.md:258`
- **Rationale:** `quizzesPresentationModule` передаёт factory в `DefaultQuizzesComponent`; factory binding — в composition root (Phase-07), не здесь

---

## Modify `QuizzesScreen` — exhaustive when update

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt`
- **Тип:** Composable fun (existing — modify)
- **Сигнатура:** update exhaustive `when(child)` block
- **Вход:** N/A
- **Поведение / Выход:**
  - УДАЛИТЬ ветку `is QuizzesChild.LessonPlaceholder -> LessonPlaceholderScreen(...)`
  - ДОБАВИТЬ ветку `is QuizzesChild.LessonRunner -> LessonRunnerScreen(component = child.component, onNavigateBack = { component.popCurrentChild() }, onSegmentClick = component::popToLevel)` — canonical per `06-api-contract.md:333`
  - `import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen` — допустимо per ADR-LR-17 (Compose-composition exception; one-way `quizzes-screen → lesson-runner`); precedent: `AppShellScreen.kt:53-56`
  - `import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent` — already present via QuizzesChild.LessonRunner
  - `onNavigateBack = { component.popCurrentChild() }` — A2 hybrid navigation contract; `popCurrentChild()` → `navigation.pop()` in `DefaultQuizzesComponent`
- **Edge cases:**
  - `LessonRunnerScreen` import — ALLOWED per ADR-LR-17 (ChildStack Compose rendering exception): only `@Composable` screen function imported, not component class implementation, not use cases, not repositories
  - Reverse direction (`lesson-runner/presentation` → `quizzes-screen/presentation`) — BLOCKER всегда; verify: `rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"` — must be empty
  - Одностороннее направление: `QuizzesScreen` получает `LessonRunnerRootComponent` typed interface + рендерит `LessonRunnerScreen` — НЕ наоборот
  - `quizzes-screen/presentation/build.gradle.kts` — добавить `implementation(project(":android:feature:lesson-runner:presentation"))` (backend-dev scaffold file)
- **Depends on:** `QuizzesChild.LessonRunner`, `LessonRunnerScreen` (lesson-runner/presentation, Phase-05), `LessonRunnerRootComponent` (lesson-runner/presentation, `06-api-contract.md:284`)
- **Canonical reference:** ADR-LR-17; `06-api-contract.md:46`
- **Rationale:** Direct Compose import per ADR-LR-17 ChildStack rendering exception; Open Question 3 RESOLVED; precedent: `AppShellScreen.kt:53-56`

---

## Modify `quizzes-screen/presentation/build.gradle.kts` — add lesson-runner dep

- **Файл:** `android/feature/quizzes-screen/presentation/build.gradle.kts`
- **Тип:** build script (existing — modify, backend-dev owned)
- **Сигнатура:** add `implementation(project(":android:feature:lesson-runner:presentation"))` in `dependencies { }`
- **Вход:** N/A — 1-line addition
- **Поведение / Выход:**
  - Добавить зависимость на `lesson-runner/presentation` module
  - Разрешает import `LessonRunnerRootComponent`, `LessonRunnerComponentFactory`, `LessonRunnerScreen` из `lesson-runner/presentation` в `quizzes-screen/presentation`
  - Направление строго одностороннее: `quizzes-screen → lesson-runner` (НЕ наоборот)
- **Edge cases:**
  - Gradle dependency — добавить в `dependencies { }` блок; порядок не важен
  - `implementation` (не `api`) — зависимость не транзитивная
- **Depends on:** `android/feature/lesson-runner/presentation/build.gradle.kts` (Phase-04)
- **Canonical reference:** ADR-LR-17; ADR-LR-16
- **Rationale:** Gradle dep required для cross-module import per ADR-LR-17; scaffold file — backend-dev owned

---

## Delete `LessonPlaceholder*` files + Update tests referencing it

Per ADR-LR-07 `Consequences` (paths verified through grep against actual code at `android/feature/quizzes-screen/presentation/src`):

**DELETE production files (4):**
- DELETE `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt` (interface)
- DELETE `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt`
- DELETE `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt`
- DELETE `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt`

**DELETE test files (3):**
- DELETE `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt`
- DELETE `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt`
- DELETE `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt`

**UPDATE tests referencing `LessonPlaceholder` (test-dev в Phase-06 tests.md scope):**
- UPDATE `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt`: replace `LessonPlaceholder` round-trip case с `LessonRunner` case
- UPDATE `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt`: replace assertion `pushNew(QuizzesConfig.LessonPlaceholder(...))` на `pushNew(QuizzesConfig.LessonRunner(lessonId, mode, titles))` и обновить test data для bestStars/hardUnlocked

These deletions/updates happen in atomic commit together with `QuizzesConfig` / `QuizzesChild` changes — `:android:feature:quizzes-screen:presentation:test` Gradle target должен оставаться зелёным после atomic commit. Pre-commit verification: `./gradlew :android:feature:quizzes-screen:presentation:test` зелёный (no `LessonPlaceholder` symbol unresolved errors).
