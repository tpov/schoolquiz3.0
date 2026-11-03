---
phase: 03
role: frontend-dev
---

# Phase-03 Frontend Tasks: Quizzes Module Skeleton

### Pattern Invariants

- `quizzes-screen/presentation` НИКОГДА не импортирует `android/feature/quest/presentation` или `android/feature/app-shell/presentation` (Invariant 3 — `.claude/rules/clean-architecture.md:62-66`).
- `QuizzesConfig` fields: только `String`, `List<String>`, примитивы. Никаких `@JvmInline value class` — они не `@Serializable` в codebase (`1-research.md:42-45`).
- `BackCallback(priority = 100)` — числовой литерал. DEFERRED (verified): `BackCallback.PRIORITY_OVERLAY` отсутствует в Essenty 2.1.0 (`gradle/libs.versions.toml:36` — `essenty = "2.1.0"`). Существующий паттерн в проекте: `DefaultRootComponent.kt:140` — `BackCallback(isEnabled = true)` без явного priority. Quizzes backCallback использует `priority = 100` (выше default 0). Unblock criteria: обновление Essenty до ≥ 2.4.0 где появляется `PRIORITY_OVERLAY`.
- `childStack(handleBackButton = false)` — manual back management (ADR-QS-12, `03-decisions.md`).
- `backCallback.isEnabled` обновляется только через `childStack.subscribe { ... }` — нет прямого мутирования в nav methods.
- `lifecycle.doOnDestroy { componentJob.cancel() }` — обязательный паттерн. Reference: `DefaultHomeQuestsComponent.kt:33-48`.

---

## Create QuizzesConfig

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../config/QuizzesConfig.kt`
- **Тип:** `@Serializable sealed class`
- **Сигнатура:** `@Serializable sealed class QuizzesConfig`
- **Вход:** N/A (configuration types)
- **Поведение / Выход:**
  - 6 вариантов: `Idle` (data object), `QuestList`, `SectionList`, `ThemeList`, `LessonList`, `LessonPlaceholder`
  - Все поля — только `String` и `List<String>` (не value classes)
  - `titles: List<String>` — frozen breadcrumb path snapshot на момент push
  - `LessonPlaceholder` дополнительно несёт `lessonTitle: String` — для placeholder text
- **Edge cases:**
  - `Idle` — `data object` без полей; `@Serializable data object`
  - Все variants — `@Serializable` annotation
- **Depends on:** `kotlinx.serialization`
- **Canonical reference:** `06-api-contract.md:329`
- **Rationale:** `@Serializable` обязателен для `childStack(serializer = QuizzesConfig.serializer())` — process death restoration (ADR-QS-02). Только primitive fields — `@JvmInline value class` не `@Serializable` в codebase.

---

## Create QuizzesNavigator

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../navigation/QuizzesNavigator.kt`
- **Тип:** interface
- **Сигнатура:** `interface QuizzesNavigator`
- **Вход:** N/A
- **Поведение / Выход:**
  - `fun openQuestList(catalogId: CatalogId, catalogName: String)` — HomeQuests entry
  - `fun openSectionList(questId: QuestId, titles: List<String>)` — MyQuests entry
  - `fun dismissQuizzes()` — collapse to Idle anchor
- **Edge cases:**
  - `CatalogId`, `QuestId` — из `shared/core/catalog/domain` и `shared/feature/quest/domain` (не из quest/presentation)
- **Depends on:** `CatalogId`, `QuestId`
- **Canonical reference:** `06-api-contract.md:11`
- **Rationale:** Interface живёт ТОЛЬКО в quizzes-screen/presentation. quest/presentation не импортирует его (ADR-QS-01). DefaultQuizzesComponent реализует.

---

## Create QuizzesComponent interface

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuizzesComponent.kt`
- **Тип:** interface
- **Сигнатура:** `interface QuizzesComponent`
- **Вход:** N/A
- **Поведение / Выход:**
  - `val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>>`
  - `fun openQuestList(catalogId: CatalogId, catalogName: String)`
  - `fun openSectionList(questId: QuestId, titles: List<String>)`
  - `fun popToLevel(uiLevel: Int)` — breadcrumb tap handler
  - `fun dismissQuizzes()`
- **Edge cases:**
  - `popToLevel(uiLevel)` → `navigation.popTo(uiLevel + 1)` offset (+1 для Idle anchor)
- **Depends on:** `QuizzesConfig`, `QuizzesChild`, Decompose `Value`, `ChildStack`
- **Canonical reference:** `06-api-contract.md:392`
- **Rationale:** Public interface — AppShellScreen и lambda closures из DefaultRootComponent работают с этим interface, не с DefaultQuizzesComponent напрямую.

---

## Create QuizzesChild sealed interface

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuizzesChild.kt`
- **Тип:** sealed interface
- **Сигнатура:** `sealed interface QuizzesChild`
- **Вход:** N/A
- **Поведение / Выход:**
  - `data object Idle : QuizzesChild` — anchor, AppShellScreen не рендерит UI
  - `data class QuestList(val component: QuestListComponent) : QuizzesChild`
  - `data class SectionList(val component: SectionListComponent) : QuizzesChild`
  - `data class ThemeList(val component: ThemeListComponent) : QuizzesChild`
  - `data class LessonList(val component: LessonListComponent) : QuizzesChild`
  - `data class LessonPlaceholder(val component: LessonPlaceholderComponent) : QuizzesChild`
- **Edge cases:**
  - В Phase-03 child Component interfaces ещё не существуют — использовать заглушки или создать пустые interfaces в той же фазе
- **Depends on:** child component interfaces (заглушки в Phase-03, полные в Phase-04)
- **Canonical reference:** `06-api-contract.md:500`
- **Rationale:** Sealed interface — exhaustive when в QuizzesScreen (Phase-05). Idle variant — overlay hidden condition.

---

## Create DefaultQuizzesComponent

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/DefaultQuizzesComponent.kt`
- **Тип:** class
- **Сигнатура:** `class DefaultQuizzesComponent(componentContext: ComponentContext, questRepository: QuestRepository, sectionRepository: SectionRepository, themeRepository: ThemeRepository, lessonRepository: LessonRepository) : ComponentContext by componentContext, QuizzesComponent`
- **Вход:**
  - `componentContext: ComponentContext` — Decompose lifecycle
  - 4 repository interfaces (injected by Koin через childFactory в Phase-04; stored as fields для передачи в child factory)
- **Поведение / Выход:**
  - Canonical full impl: `06-api-contract.md:392` — реализует весь DefaultQuizzesComponent constructor, childStack, backCallback, all nav methods
  - `createChild(config, ctx)` — в Phase-03: заглушка для non-Idle configs (например `QuizzesChild.Idle` для всех или `TODO()`) — Phase-04 заменяет полным `when` блоком
  - Lifecycle: `private val componentJob = SupervisorJob()` + `lifecycle.doOnDestroy { componentJob.cancel() }` — паттерн из `DefaultHomeQuestsComponent.kt:33-48`
  - Serialization fallback: `try/catch(SerializationException)` вокруг stateKeeper.consume → reset к `listOf(QuizzesConfig.Idle)`
- **Edge cases:**
  - `PRIORITY_OVERLAY` absent in Essenty 2.x → `priority = 100` (REQUIRES verify)
  - `pushNew` на уже существующий config → Decompose no-op (safe)
  - `dismissQuizzes()` при `stack=[Idle]` → `popToFirst()` no-op
- **Depends on:** `QuizzesConfig`, `QuizzesChild`, `QuizzesNavigator`, Decompose `ChildStack`, `StackNavigation`, Essenty `BackCallback`, `QuestRepository`, `SectionRepository`, `ThemeRepository`, `LessonRepository`
- **Canonical reference:** `06-api-contract.md:392`
- **Rationale:** Self-contained navigation unit с изолированным ChildStack (ADR-QS-03). First serialized ChildStack в проекте (ADR-QS-02). Manual BackCallback с explicit priority (ADR-QS-12).

---

## Create QuizzesPresentationModule (Koin)

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../di/QuizzesPresentationModule.kt`
- **Тип:** Koin module (val declaration)
- **Сигнатура:** `val quizzesPresentationModule = module { ... }`
- **Вход:** N/A
- **Поведение / Выход:**
  - `factory<QuizzesComponent> { (ctx: ComponentContext) -> DefaultQuizzesComponent(componentContext=ctx, questRepository=get(), sectionRepository=get(), themeRepository=get(), lessonRepository=get()) }`
  - `get()` для repositories — Koin resolves из shared data module registrations
- **Edge cases:**
  - `QuizzesComponent` factory принимает `ComponentContext` через `parametersOf(...)` — verify паттерн с `QuestPresentationModule.kt:25-41`
- **Depends on:** `DefaultQuizzesComponent`, `QuizzesComponent`, repository interfaces
- **Canonical reference:** `06-api-contract.md:742`
- **Rationale:** Consistent с `QuestPresentationModule` паттерном (ADR-CMP-51). Child components создаются через childFactory, не через отдельные Koin registrations.

---

## Create stub child component interfaces (Phase-03 placeholder)

- **Файл:** `android/feature/quizzes-screen/presentation/src/main/kotlin/.../component/QuestListComponent.kt` и аналоги
- **Тип:** interface (4 stub interfaces: QuestListComponent, SectionListComponent, ThemeListComponent, LessonListComponent, LessonPlaceholderComponent)
- **Сигнатура:** `interface QuestListComponent` (пустые stubs для компиляции `QuizzesChild`)
- **Вход:** N/A (Phase-03 placeholder)
- **Поведение / Выход:**
  - Пустые interfaces или minimal stubs с `val state` и click handlers (определяет frontend-dev по canonical definition в Phase-04)
  - Нужны только чтобы `QuizzesChild.QuestList(val component: QuestListComponent)` компилировался
- **Edge cases:**
  - Phase-04 заменяет stub implementations полными DefaultXxx
- **Depends on:** N/A
- **Canonical reference:** `06-api-contract.md:529`
- **Rationale:** Stub interfaces позволяют `QuizzesChild` sealed interface компилироваться без Phase-04 зависимости.
