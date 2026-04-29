Reading prompt from stdin...
OpenAI Codex v0.125.0 (research preview)
--------
workdir: /home/Programming/Android/schoolquiz4.0
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, $TMPDIR, /home/tpov/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019dd12c-06d2-7252-b6c4-ed4a8072c885
--------
user
You are an adversarial code reviewer for the lesson-runner feature.

Apply 3 lenses in sequence:
1. Realist — does the code do what spec says? Are AC 1-65 from 0-spec.md actually covered?
2. Skeptic — what edge cases, race conditions, error paths are missed? What assumptions are wrong?
3. Architect — are layer boundaries respected? Are cross-feature ADRs (LR-01..LR-20) enforced? Is DI wiring correct?

Inputs:
- Diff: /home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch (6638 lines, lesson-runner-only changes)
- Spec: docs/features/lesson-runner/0-spec.md (1234 lines, contains Feature Domain Contract + AC 1-65)
- Implementation summary: docs/features/lesson-runner/implementation.md
- ADRs: docs/features/lesson-runner/03-decisions.md (ADR-LR-01..LR-20)

Focus on cross-phase integration issues — not per-phase code style. Specifically check:
- DI chain: questionSchemaModule → lessonRunnerDataModule → lessonRunnerDomainKoinAdapter → lessonRunnerPresentationModule, no duplicate bindings
- Walking Skeleton domain (~89 tests) NOT modified beyond rename
- ADR-LR-09 C1: lessonRunnerDomainKoinAdapter in data/androidMain
- ADR-LR-16: LessonRunnerRootComponent interface in lesson-runner/presentation, NOT core/navigation
- ADR-LR-17: quizzes-screen → lesson-runner one-way; reverse blocker
- ADR-LR-18: DifficultyConverter removed (mapper-based)
- ADR-LR-19: RunnerUiState.Result flat projection (no PII)
- ADR-LR-20: LessonRunnerComponentFactory in presentation module, not duplicated

Severity: BLOCKER / HIGH / MEDIUM / LOW. Always cite file:line. Output structured findings list.

deprecated: `[features].collab` is deprecated. Use `[features].multi_agent` instead.
Enable it with `--enable multi_agent` or `[features].multi_agent` in config.toml. See https://developers.openai.com/codex/config-basic#feature-flags for details.
hook: SessionStart
hook: SessionStart Completed
hook: UserPromptSubmit
hook: UserPromptSubmit Completed
codex
I’ll use the `android-architecture` lens for module/DI boundaries and the `kotlin-diagnostics` lens for adversarial bug hunting, then keep the output in review-findings form with concrete file lines.
exec
/bin/bash -lc "sed -n '1,220p' /home/tpov/.codex/skills/kotlin-diagnostics/SKILL.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
---
name: kotlin-diagnostics
description: Агент-поисковик багов для Kotlin / Compose Multiplatform / Android / Spring / KMP / Backend. Может запускать системные команды, инструментировать проект, анализировать логи, но не исправляет код без прямого подтверждения.
---


Ты — интеллектуальный агент-диагност, способный выявлять ошибки в проектах на Kotlin / Compose Multiplatform / Android / JVM / Spring / Backend.  
Твоя цель — максимально точно и быстро локализовать проблему, не внося изменения в код до явного разрешения пользователя.

================================================================================
# 0. EXECUTION CONFIDENCE RULES (NO PER-STEP CONFIRMATION)

Ты НЕ обязан спрашивать разрешения для промежуточных действий.  
Ты выполняешь все диагностические шаги автоматически, **без вопросов**, включая:

- запуск системных команд (`gradle`, `docker`, `adb`, `logs`)
- пересборки
- перезапуск контейнеров
- чтение/анализ логов
- временную инструментировку (добавление debug-логов, таймеров, трассировки)
- сканирование файлов
- выполнение тестов
- выполнение скриптов
- просмотр состояния системы

Эти действия выполняются **автоматически, без запросов**, если они не меняют код проекта.

## Но:

Ты обязан ОСТАНОВИТЬСЯ перед финальным внесением правок в проект:

- перед изменением файлов кода,
- перед удалением файлов,
- перед изменением конфигураций,
- перед совершением любых необратимых операций.

И спросить разрешение в форме:

> “Готов применить фикс. Скажи OK / Fix / Done / Исправь — чтобы я внёс изменения.”

================================================================================
# 1. DIAGNOSIS WORKFLOW (STRICT LOGIC)

Когда пользователь говорит “найди баг”, ты выполняешь последовательные фазы.

## Фаза 1 — Анализ проекта (statical scanning)
Проанализируй:

### Kotlin / KMP / Compose ошибки:
- неправильное использование remember
- бесконечные recompositions
- неправильная инициализация state
- корутинные ошибки (race, cancellation, deadlock)
- утечки ComponentContext в Decompose
- неправильные dispatchers
- platform types → NPE risks
- неправильные sealed branch matchings
- suspend вызовы не в корутине
- ошибки в DI (Kodein/Inject)
- shared flow без replay
- мутабельность состояния в UI
- неправильное управление жизненным циклом

### Backend / Spring:
- транзакции
- lazy-loading ошибки
- JPA mapping проблемы
- циклические Spring DI зависимости
- дублирующиеся beans
- неправильные nullable поля
- неверная конфигурация Docker сервисов

### Multiplatform:
- общая логика KMP
- несоответствия между JVM/Native/JS
- IO и concurrency ошибки

### Common:
- NPE / !! misuse
- unreachable code
- dead code
- логические ошибки
- неверные зависимости
- неправильные импорты


## Фаза 2 — Автоматический запуск системных команд
Ты МОЖЕШЬ запускать команды **без подтверждения**, если они не разрушают проект.

### Build
- `./gradlew build --stacktrace`
- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew :app:build`

### Logs
- `docker compose logs`
- `docker logs <container>`
- `adb logcat`

### Docker
- `docker compose up -d`
- `docker compose restart`
- `docker ps`

### System
- `ls`, `cat`, `ps`, `kill`
- `./scripts/*`
- `df -h`, `free -m`

Эти команды используются только для диагностики.


## Фаза 3 — Интеллектуальная инструментировка (TEMP ONLY)
Ты можешь автоматически:

- вставлять временные debug-логи
- добавлять println
- временно логировать входы/выходы use-cases
- логировать состояние Component
- добавлять таймеры
- трассировать навигацию Decompose
- логировать exceptions через try/catch
- включать debug-флаги в конфигурации

НО:

### ❗ ВСЕ изменения — временные
### ❗ Код НЕ модифицируется окончательно
### ❗ Ты НЕ правишь бизнес-логику


## Фаза 4 — Runtime анализ
После запуска:

- анализируй stacktrace
- связывай его с местами в коде
- ищи root cause:
    - NPE
    - IllegalState
    - Uninitialized
    - coroutine cancellation
    - lifecycle misuse
    - неправильные параметры
    - конфликт версий библиотек
    - нехватка токенов/конфигураций
    - ошибки в последовательности вызовов


## Фаза 5 — Финальный вывод (Bug Localization)

В конце дай строго:

### 1) Problem summary
короткое описание, что сломано.

### 2) Root cause (конкретная строка/модуль/компонент)
объяснение корневой причины.

### 3) Why it happens
цепочка событий → ошибка.

### 4) Minimal Reproduction
если возможно — путь к повторению.

### 5) Proposed fix (PATCH, DIFF)
предложи конкретный diff:

```diff
--- old.kt
+++ new.kt
@@
  <fix>
```

Но НЕ применяй его автоматически.

### 6) Instruction
Напиши:

> “Скажи OK / Done / Fix / Исправь / Примени — чтобы я применил изменения”.

================================================================================
# 2. FIX APPLY RULES (MULTILANGUAGE & FLEXIBLE)

Ты применяешь изменения **ТОЛЬКО**, если пользователь говорит фразу, значение которой = *«да, применяй фикс»*.

РАЗРЕШЁННЫЕ ТРИГГЕРЫ:

### English
- ok
- okay
- yes
- yes apply
- fix
- fix it
- apply
- apply patch
- done
- do it
- go ahead
- make it
- confirm

### Russian
- ок
- да
- давай
- хорошо
- пофикси
- исправь
- примени
- примени патч
- сделай
- фиксируй
- запускай
- готово

### Semantic approval
Любая фраза со смыслом:

exec
/bin/bash -lc "sed -n '1,220p' /home/tpov/.codex/skills/android-architecture/SKILL.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
---
name: android-architecture
description: Expert guidance on setting up and maintaining a modern Android application architecture using Clean Architecture and Hilt. Use this when asked about project structure, module setup, or dependency injection.
---

# Android Modern Architecture & Modularization

## Instructions

When designing or refactoring an Android application, adhere to the **Guide to App Architecture** and **Clean Architecture** principles.

### 1. High-Level Layers
Structure the application into three primary layers. Dependencies must strictly flow **inwards** (or downwards) to the core logic.

*   **UI Layer (Presentation)**:
    *   **Responsibility**: Displaying data and handling user interactions.
    *   **Components**: Activities, Fragments, Composables, ViewModels.
    *   **Dependencies**: Depends on the Domain Layer (or Data Layer if simple). **Never** depends on the Data Layer implementation details directly.
*   **Domain Layer (Business Logic) [Optional but Recommended]**:
    *   **Responsibility**: Encapsulating complex business rules and reuse.
    *   **Components**: Use Cases (e.g., `GetLatestNewsUseCase`), Domain Models (pure Kotlin data classes).
    *   **Pure Kotlin**: Must NOT contain any Android framework dependencies (no `android.*` imports).
    *   **Dependencies**: Depends on Repository Interfaces.
*   **Data Layer**:
    *   **Responsibility**: Managing application data (fetching, caching, saving).
    *   **Components**: Repositories (implementations), Data Sources (Retrofit APIs, Room DAOs).
    *   **Dependencies**: Depends only on external sources and libraries.

### 2. Dependency Injection with Hilt
Use **Hilt** for all dependency injection.

*   **@HiltAndroidApp**: Annotate the `Application` class.
*   **@AndroidEntryPoint**: Annotate Activities and Fragments.
*   **@HiltViewModel**: Annotate ViewModels; use standard `constructor` injection.
*   **Modules**:
    *   Use `@Module` and `@InstallIn(SingletonComponent::class)` for app-wide singletons (e.g., Network, Database).
    *   Use `@Binds` in an abstract class to bind interface implementations (cleaner than `@Provides`).

### 3. Modularization Strategy
For production apps, use a multi-module strategy to improve build times and separation of concerns.

*   **:app**: The main entry point, connects features.
*   **:core:model**: Shared domain models (Pure Kotlin).
*   **:core:data**: Repositories, Data Sources, Database, Network.
*   **:core:domain**: Use Cases and Repository Interfaces.
*   **:core:ui**: Shared Composables, Theme, Resources.
*   **:feature:[name]**: Standalone feature modules containing their own UI and ViewModels. Depends on `:core:domain` and `:core:ui`.

### 4. Checklist for implementation
- [ ] Ensure `Domain` layer has no Android dependencies.
- [ ] Repositories should default to main-safe suspend functions (use `Dispatchers.IO` internally if needed).
- [ ] ViewModels should interact with the UI layer via `StateFlow` (see `android-viewmodel` skill).

codex
I’m going to map the patch first: touched files, the spec/ADR anchors, and the current source layout. That gives us a clean checklist for cross-phase integration instead of drifting into per-phase style comments.
exec
/bin/bash -lc "rg --files docs/features/lesson-runner shared android apps | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
apps/android-next/src/main/AndroidManifest.xml
apps/android-next/proguard-rules.pro
apps/android-next/build.gradle.kts
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt
apps/android-next/src/main/res/values/themes.xml
android/core/navigation/src/main/AndroidManifest.xml
android/core/userguide/src/main/AndroidManifest.xml
android/core/navigation/consumer-rules.pro
android/core/navigation/proguard-rules.pro
docs/features/lesson-runner/plan/phase-05/frontend.md
docs/features/lesson-runner/plan/phase-05/tests.md
docs/features/lesson-runner/plan/phase-05/overview.md
docs/features/lesson-runner/plan/phase-01/tests.md
docs/features/lesson-runner/plan/phase-01/backend.md
docs/features/lesson-runner/plan/phase-01/overview.md
docs/features/lesson-runner/plan/README.md
apps/android-next/src/main/res/values-night/themes.xml
android/core/navigation/build.gradle.kts
android/core/userguide/consumer-rules.pro
android/core/userguide/proguard-rules.pro
android/core/userguide/build.gradle.kts
docs/features/lesson-runner/plan/phase-06/frontend.md
docs/features/lesson-runner/plan/phase-06/tests.md
docs/features/lesson-runner/plan/phase-06/backend.md
docs/features/lesson-runner/plan/phase-06/overview.md
docs/features/lesson-runner/plan/phase-02/tests.md
docs/features/lesson-runner/plan/phase-02/backend.md
docs/features/lesson-runner/plan/phase-02/overview.md
android/core/designsystem/build.gradle.kts
android/core/designsystem/consumer-rules.pro
android/core/designsystem/proguard-rules.pro
docs/features/lesson-runner/plan/phase-03/tests.md
docs/features/lesson-runner/plan/phase-03/backend.md
docs/features/lesson-runner/plan/phase-03/overview.md
shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/AuthUidChanged.kt
shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/RawUserStats.kt
shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/UserStatsDataSource.kt
shared/core/stats/build.gradle.kts
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
docs/features/lesson-runner/plan/phase-04/tests.md
docs/features/lesson-runner/plan/phase-04/backend.md
docs/features/lesson-runner/plan/phase-04/overview.md
shared/core/question-schema/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContentParserTest.kt
docs/features/lesson-runner/plan/phase-07/tests.md
docs/features/lesson-runner/plan/phase-07/backend.md
docs/features/lesson-runner/plan/phase-07/overview.md
docs/features/lesson-runner/README.md
docs/features/lesson-runner/0-spec.md
docs/features/lesson-runner/implementation.md
docs/features/lesson-runner/06-api-contract.md
docs/features/lesson-runner/01-architecture.md
docs/features/lesson-runner/03-decisions.md
docs/features/lesson-runner/07-events.md
docs/features/lesson-runner/05-prior-art.md
docs/features/lesson-runner/2-grounding.md
docs/features/lesson-runner/quality-scorecard.md
docs/features/lesson-runner/1-research.md
docs/features/lesson-runner/_codex-review/plan-round-4/skeptic-and-architect.md
docs/features/lesson-runner/_codex-review/realist.md
shared/core/preferences/src/androidMain/AndroidManifest.xml
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/CandidateId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/Difficulty.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/OptionId.kt
android/feature/local/settings/presentation/src/main/AndroidManifest.xml
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/BlankId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContentParser.kt
docs/features/lesson-runner/_codex-review/round-4/final.md
docs/features/lesson-runner/_codex-review/skeptic.md
docs/features/lesson-runner/_codex-review/plan-round-3/skeptic-and-architect.md
shared/core/preferences/build.gradle.kts
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch
docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md
docs/features/lesson-runner/_codex-review/architect.md
android/core/designsystem/src/test/kotlin/com/tpov/schoolquiz/android/core/designsystem/StarRatingTest.kt
android/core/designsystem/src/test/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizThemeInvariantsTest.kt
android/core/designsystem/src/test/kotlin/com/tpov/schoolquiz/android/core/designsystem/BrandComponentsInvariantsTest.kt
docs/features/lesson-runner/_codex-review/plan-round-2/skeptic-and-architect.md
docs/features/lesson-runner/_codex-review/plan-round-6/skeptic-and-architect.md
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BreadcrumbBar.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogGrid.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/StarRating.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CategoryIcon.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BrandCard.kt
docs/features/lesson-runner/_codex-review/plan-round-1/skeptic-and-architect.md
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogSpinner.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BrandCircleIconButton.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BrandPrimaryButton.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BrandSecondaryButton.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/HierarchyItemCard.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BrandProgressBar.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Shape.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Type.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt
docs/features/lesson-runner/_codex-review/round-3/final.md
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/catalog/DesignCatalogScreen.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Color.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizTheme.kt
android/feature/local/settings/presentation/build.gradle.kts
android/feature/local/settings/presentation/consumer-rules.pro
android/feature/local/settings/presentation/proguard-rules.pro
docs/features/lesson-runner/_codex-review/round-5/final.md
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/CatalogDisplayItem.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/QuestDisplayItem.kt
android/core/designsystem/src/main/AndroidManifest.xml
docs/features/lesson-runner/_codex-review/plan-round-5/skeptic-and-architect.md
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDao.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionEntity.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogEntity.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDao.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/TopParticipantListConverter.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestEntity.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionEntity.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeEntity.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionDao.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsEntity.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverter.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt
shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt
docs/features/lesson-runner/_codex-review/round-2/realist.md
docs/features/lesson-runner/_codex-review/round-2/skeptic.md
docs/features/lesson-runner/_codex-review/round-2/architect.md
docs/features/lesson-runner/02-behavior.md
android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/HierarchyItemCardTest.kt
docs/features/lesson-runner/04-testing.md
android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCardLongClickTest.kt
android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BreadcrumbBarTest.kt
shared/core/question-schema/src/androidMain/AndroidManifest.xml
docs/features/lesson-runner/08-storage-model.md
android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizThemeTest.kt
android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizComponentsTest.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt
shared/core/question-schema/build.gradle.kts
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt
android/core/designsystem/src/main/res/values/strings.xml
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt
android/feature/lesson-runner/presentation/src/main/AndroidManifest.xml
shared/core/catalog/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/fake/FakeCatalogRepository.kt
shared/core/test/build.gradle.kts
android/feature/lesson-runner/presentation/build.gradle.kts
shared/core/catalog/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/CatalogTest.kt
shared/core/catalog/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/CatalogRepositoryContractTest.kt
shared/core/test/src/androidMain/AndroidManifest.xml
shared/core/leaderboard/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/TopParticipantSerializableTest.kt
shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverterTest.kt
shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/MyQuestsComponent.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/di/QuestPresentationModule.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/HomeQuestsComponent.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/HomeQuestsScreen.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/MyQuestsScreen.kt
shared/core/leaderboard/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/TopParticipant.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogOfflineEmptyIntegrationTest.kt
shared/core/leaderboard/build.gradle.kts
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/mapper/QuestToDisplayItem.kt
android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt
shared/core/logger/src/androidMain/AndroidManifest.xml
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogFirstFetchIntegrationTest.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/fake/FakeCatalogLocalDataSource.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogWarmCacheIntegrationTest.kt
android/feature/quest/presentation/src/main/AndroidManifest.xml
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogMapperTest.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/fake/FakeCatalogDao.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/fake/FakeCatalogRemoteDataSource.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/fake/FakeSyncStateRepository.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/fake/FakeCatalogUrlResolver.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogRepositoryImplTest.kt
shared/core/catalog/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/catalog/data/CatalogArchiveIntegrationTest.kt
shared/core/catalog/domain/build.gradle.kts
shared/core/persistence/build.gradle.kts
android/feature/quest/test-fixtures/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/fake/FakeCatalogRepository.kt
android/feature/quest/test-fixtures/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/fake/TestFixtures.kt
android/feature/quest/test-fixtures/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/fake/FakeAuthRepository.kt
android/feature/quest/test-fixtures/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/fake/FakeQuestRepository.kt
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt
android/feature/quest/test-fixtures/build.gradle.kts
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/1.json
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/3.json
shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/2.json
shared/core/logger/build.gradle.kts
shared/core/catalog/data/build.gradle.kts
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/di/CatalogDomainModule.kt
shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/repository/CatalogRepository.kt
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration3to4.kt
shared/core/persistence/src/androidMain/AndroidManifest.xml
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeClock.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionContentParser.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAuthRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAbortAttemptUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeStartLessonAttemptUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeSubmitLessonRatingUseCase.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt
shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeCatalogRepository.kt
shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeThemeRepository.kt
shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeSectionRepository.kt
shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeAuthRepository.kt
shared/core/sync/src/commonTest/kotlin/com/tpov/schoolquiz/shared/core/sync/fake/FakeQuestionRepository.kt

exec
/bin/bash -lc "sed -n '1,220p' docs/features/lesson-runner/03-decisions.md" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "sed -n '1,260p' docs/features/lesson-runner/implementation.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
# Implementation — lesson-runner

Status: **implemented**
Дата: 2026-04-28
Branch: `kmp-skillify-4.0`

## Summary

Lesson-runner — gameplay loop викторины — реализован атомарной заменой `LessonPlaceholder` на полноценный flow в `quizzes-screen`. Один вопрос на экран, таймер, auto-random на timeout, результат с процентами/звёздами/топ-3, опрос «Оцените урок», запись попытки в Room. Все 65 acceptance criteria из `0-spec.md` покрыты. 7 фаз pipeline pass (phase-01 → phase-07).

## Phases Completed

| Phase | Goal | Verdict | Notable findings |
|-------|------|---------|-------------------|
| Phase-01 | Foundation & Core Types | PASS (5/5 reviewers) | mini-fix `KoinModuleWiringTest.kt:320,393` (pre-existing baseline) |
| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
| Phase-03 | Data Layer (NEW lesson-runner/data) | PASS (5/5 reviewers) | jvmAndAndroidMain intermediate source set |
| Phase-04 | Presentation Component | PASS (5/5 reviewers) | 3 compile blockers + HIGH (componentJob, double-complete) — все resolved fix loop |
| Phase-05 | Compose UI (LessonRunnerScreen + 12 composables) | PASS (5/5 reviewers) | ADR-LR-19: RunnerUiState.Result flat projection (security-driven) |
| Phase-06 | Quizzes-screen Integration (atomic replace) | PASS (5/5 reviewers) | LessonListUiState sealed (Loading/Empty/Loaded) |
| Phase-07 | Composition Root + Smoke | PASS (4/5 reviewers; concurrency N/A) | ADR-LR-20: LessonRunnerComponentFactory in presentation module |

## Review Verdicts

| Reviewer | Phases | Total findings | Blockers resolved | High resolved | Medium resolved | Low documented |
|----------|--------|----------------|-------------------|---------------|-----------------|----------------|
| architect-reviewer | 1-7 | 5 | 0 | 2 | 2 | 1 |
| code-reviewer | 1-7 | 9 | 1 | 2 | 4 | 2 |
| security-reviewer | 1-7 | 7 | 0 | 0 | 2 | 5 |
| completeness-reviewer | 1-7 | 8 | 5 | 0 | 1 | 2 |
| concurrency-reviewer | 2-6 | 5 | 0 | 1 | 1 | 3 |

Все findings закрыты автономно через reviewer↔coder loop. Lead вмешался только для 3 design escalations (ADR-LR-18/19/20).

## Changed Files

### NEW Modules (3 Gradle modules)

- `shared/core/leaderboard/` — TopParticipant @Serializable (1 file)
- `shared/feature/lesson-runner/data/` — RepositoryImpls, mappers, providers, Koin modules (10 files)
- `android/feature/lesson-runner/presentation/` — Component, states, events, mapper, Compose UI, fakes (~30 files including tests)

### NEW Files in existing modules (~12)

- `shared/core/question-schema/`: KotlinxSerializationQuestionContentParser.kt, di/QuestionSchemaModule.kt
- `shared/core/persistence/`: LessonAttemptEntity, LessonRatingSubmittedLocalEntity, LessonAttemptDao, LessonRatingLocalDao, TopParticipantListConverter, Migration3to4
- `shared/feature/lesson-runner/domain/provider/`: AttemptIdProvider, RandomSeedProvider, RatingIdProvider
- `android/feature/quizzes-screen/presentation/`: uistate/LessonItemUi.kt, screen/LessonItemCard.kt, uistate/LessonListUiState.kt

### Modified Files (~20)

Domain: Lesson +3 fields (averageRating, ratingCount, top3), AttemptId/RatingId .raw→.value, QuestionContent + Difficulty @Serializable.
Data: LessonEntity +3 cols, LessonMapper backward-compat, LessonDtoMapper, FirestoreLessonDtoMapper (HTTPS-only avatarUrl).
Persistence: AppDatabase v4, PersistenceModule (addMigrations + addTypeConverter, removed fallbackToDestructiveMigration).
Quizzes-screen: QuizzesConfig (LessonRunner add, LessonPlaceholder remove), QuizzesChild, DefaultQuizzesComponent (+3 deps), QuizzesScreen, DefaultLessonListComponent (combine flow + 2 deps), LessonListScreen, QuizzesPresentationModule, build.gradle.kts.
Composition root: AppApplication.kt (+4 modules), apps/android-next/build.gradle.kts.

### Deleted Files

Production (4): LessonPlaceholderComponent.kt, DefaultLessonPlaceholderComponent.kt, LessonPlaceholderScreen.kt, LessonPlaceholderUiState.kt.
Tests (3): DefaultLessonPlaceholderComponentTest.kt, FakeLessonPlaceholderComponent.kt, LessonPlaceholderScreenTest.kt.
Domain (1): TopParticipant.kt в lesson-runner/domain (moved to core/leaderboard).
Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).

## Test Coverage

- **Walking Skeleton domain (commonTest)**: ~89 tests via fakes (DT-01..82) — all green
- **Phase-01 (foundation)**: 19 tests — parser round-trip, rename verification, serialization, Lesson fields, providers, TopParticipant
- **Phase-02 (persistence)**: 19 tests — MT-01..07 migration (instrumented), DAO-01..05 (instrumented), Mapper-01..04 (jvm), TypeConverter tests
- **Phase-03 (data)**: 13 tests — IT-01/IT-08 proxy, providers, mappers
- **Phase-04 (presentation)**: 41 PT tests + IT-02/03 — Component lifecycle, state, events
- **Phase-05 (Compose UI)**: 27 CT tests + 3 @Ignore (CT-22..24 deferred to phase-06) — instrumented Compose tests
- **Phase-06 (quizzes integration)**: 11 tests — PT-15..17, PT-34..36, CT-22..24, serialization
- **Phase-07 (composition root)**: 8 IT-09a..h — Koin wiring resolution

**Total**: ~227+ tests (Walking Skeleton + 7 phases). All green at pipeline close.

## Build Validation

- `./gradlew ciCheck --no-configuration-cache` — GREEN (2300+ tasks)
- `./gradlew test --no-configuration-cache` — GREEN
- `./gradlew allTests --no-configuration-cache` — GREEN
- `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` — GREEN
- `./gradlew :shared:core:persistence:connectedAndroidTest` — 46/46 на Pixel 10 Pro
- `./gradlew :android:feature:lesson-runner:presentation:connectedAndroidTest` — 27/27 (3 @Ignore deferred) на TECNO KG5m
- `./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest` — included in build gate
- `./gradlew detekt ktlintCheck --no-configuration-cache` — GREEN

## Smoke

- App installed on Pixel 10 Pro (`adb install` → Success)
- Launched via `adb shell monkey -p com.tpov.schoolquiz.next -c android.intent.category.LAUNCHER 1` — no FATAL log entries
- Полная UI navigation (Catalog → Quest → Section → Theme → Lesson → tap → LessonRunnerScreen) — deferred user verification (interactive UI testing вне scope automated smoke)

## ADR Additions During Implementation

| ADR | Phase | Decision | Rationale |
|-----|-------|----------|-----------|
| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
| LR-19 | 04→05 | RunnerUiState.Result uses flat projection instead of attempt: Attempt aggregate | Security: attempt contained PII (userId, codeAnswer, attemptId) — minimal exposure principle in public StateFlow. 06-api-contract.md:408 + frontend.md Pattern Invariant superseded. |
| LR-20 | 04→07 | single<LessonRunnerComponentFactory> in lessonRunnerPresentationModule, NOT in AppApplication | Phase-04 already established single<> binding; phase-07 plan duplication would cause Koin override warning. Per 06-api-contract.md:374, factory binding stays in feature presentation module. |

Все ADRs added в `docs/features/lesson-runner/03-decisions.md` и referenced from updated phase plans.

## Remaining Issues / Known Gaps

1. **Cross-phase Codex CLI review** — не запущен. Diff vs master = 6525 files (prior KMP refactor). Focused lesson-runner diff невозможен на этой ветке. Recommended: Codex review при следующем merge to main, scoped к lesson-runner-only changes.
2. **Manual smoke полный flow** — interactive UI navigation deferred user verification. Все automated gates green.
3. **Pre-existing debt не trogался** в этой фиче:
   - `android/core/designsystem/QuestCard.kt:29` — импортирует shared.feature.quest.domain (core-imports-feature violation). Existed before phase-01.
   - `shared/core/sync/CascadingSyncOrchestrator.kt:5-12` — импортирует feature repositories. Existed before.
4. **Out of Scope per spec** (deferred features, документированы в 0-spec):
   - Cascade sync для lesson_attempts / lesson_ratings
   - Cloud Functions для агрегации Lesson.averageRating / top3
   - Avatar sync (subset of users/{uid})
   - Logout cleanup local lesson_attempts
   - Sync state прохождения между sessions
   - Лидерборд экран (выше top-3)
   - Repetition mechanism

## Quality Scorecard

См. `docs/features/lesson-runner/quality-scorecard.md` для детальной разбивки по параметрам (Architecture B, Correctness B, Completeness A, Security A, Code Organization A — Overall B+).

## Pipeline Statistics

- 7 phases × ~7-9 task-units = ~55 task instances
- 5 reviewer agents × 7 phases = 35 review passes (concurrency reviewer phases 2-6 only = 30 effective)
- 3 ADR escalations (LR-18/19/20) — all resolved within 1-2 lead cycles
- 0 architectural mismatch escalations требовавших спец работы вне plan
- 0 phases reverted / re-planned — все progressed forward через autonomous loop

 succeeded in 0ms:
---
date: 2026-04-27
authors: architect-high-level (ADR-LR-01..LR-07, ADR-LR-16), architect-component (ADR-LR-08..15)
feature: lesson-runner
---

# Architecture Decisions: Lesson Runner

<!-- HL_SECTION_START: ADR-LR-01..LR-07 (architect-high-level writes here) -->

## ADR-LR-01: lesson-runner/domain → lesson:domain (cross-feature import)

**Status**: ACCEPTED — устраняет Missing ADR (Grounding Problem 6)

### Context

Walking Skeleton `shared/feature/lesson-runner/domain/` уже содержит прямые импорты из `shared/feature/lesson/domain/`:
- `LessonId` — ключ entity для `Attempt.lessonId`, `LessonAttemptRepository`, `LessonRatingRepository`
- `LessonRepository` — `StartLessonAttemptUseCase` читает `Lesson.version` (snapshot) и через `getById` для проверки LessonNotFound guard

**Verified** `[shared/feature/lesson-runner/domain/build.gradle.kts]`: `commonMain` зависит от `:shared:feature:lesson:domain`.

Per Invariant 3 (`docs/invariants.md:27`): прямой cross-feature import должен быть задокументирован в ADR.

### Decision

**Разрешить one-way import** `lesson-runner:domain → lesson:domain` для `LessonId` и `LessonRepository`.

Направление строго one-way: `lesson:domain` не импортирует `lesson-runner:domain`. `LessonId` — корневой идентификатор entity; без него `Attempt` и `LessonAttemptRepository` теряют typed identity. `LessonRepository.getById` — единственный путь получить `Lesson.version` snapshot на старте попытки (see `0-spec.md §21`: `lessonVersion = lesson.version` at attempt start).

### Alternatives Considered

**Вариант B — Использовать raw `String` lessonId в lesson-runner:domain**:
- (-) Теряется type safety: `Attempt.lessonId: String` vs `Attempt.lessonId: LessonId` — compile-time защита исчезает
- (-) `LessonRepository` interface невозможно использовать без `LessonId` type
- (-) Нарушает DDD принцип: lesson-runner без связи с Lesson — бессмысленная фича

**Вариант C — Вынести `LessonId` в shared/core**:
- (-) `LessonId` — domain-specific value class принадлежащий `lesson:domain` bounded context; перемещение в core ради одной фичи — чрезмерная абстракция
- (-) Дополнительный Gradle module, blast radius на все feature modules

### Consequences

- Граф зависимостей: `lesson-runner:domain → lesson:domain` one-way ✓
- Обратный import `lesson:domain → lesson-runner:domain` запрещён — будет blocker при architect-reviewer grep check
- `Invariant 3` выполнен при отсутствии обратного импорта

### Risk if wrong (6 months out)

Если `lesson:domain` эволюционирует так что `LessonId` меняет тип или переезжает в core — нужно обновить `Attempt`, все use cases и все тесты Walking Skeleton. Высокая стоимость reversal, но риск маловероятен: `LessonId` — стабильный корневой идентификатор bounded context.

---

## ADR-LR-02: lesson-runner/domain → question:domain (cross-feature import)

**Status**: ACCEPTED — устраняет Missing ADR (Grounding Problem 6)

### Context

Walking Skeleton импортирует из `shared/feature/question/domain/`:
- `QuestionId` — `RunnerQuestion.sourceId: QuestionId`, codeAnswer индексация
- `QuestionRepository` — `StartLessonAttemptUseCase` получает questions через `observeByLesson(lessonId).first()`
- `Question` — filtered и parsed на старте попытки

**Verified** `[shared/feature/lesson-runner/domain/build.gradle.kts]`: `:shared:feature:question:domain` в commonMain.

### Decision

**Разрешить one-way import** `lesson-runner:domain → question:domain` для `QuestionId`, `QuestionRepository`, `Question`.

`Question.payload` — источник для `QuestionContentParser.parse()`. `Question.order`, `Question.archived` — фильтрация и сортировка eligibleQuestions (см. `0-spec.md §22` canonical pipeline). Без `QuestionRepository` невозможно построить subset для попытки.

### Alternatives Considered

**Вариант B — Получать questions через `LessonRepository` bundled со списком вопросов**:
- (-) Расширяет `LessonRepository` ответственностью за Question — нарушение SRP; Question — отдельный bounded context
- (-) Ломает существующий cascade sync архитектуру (Questions sync отдельно от Lessons)

### Consequences

- Граф: `lesson-runner:domain → question:domain` one-way ✓
- Обратный import запрещён
- Pattern совпадает с existing undocumented pattern: `question:domain → lesson:domain` (existing debt, не блокер)

### Risk if wrong (6 months out)

Если `question:domain` меняет `Question.payload` формат или schema вопроса добавляет 5-й тип — `RunnerLogic.evaluateAnswer` нужно обновить. Compile-time exhaustive `when` ловит пропущенные branches — безопасно. Средняя стоимость.

---

## ADR-LR-03: lesson-runner/domain → app-shell:domain (cross-feature import)

**Status**: ACCEPTED — устраняет Missing ADR (Grounding Problem 6)

### Context

`StartLessonAttemptUseCase` требует текущий Firebase Auth UID для `RunnerState.Ready.userId` snapshot. `AuthRepository` — `shared/feature/app-shell/domain/`. Walking Skeleton уже импортирует его.

**Verified** `[shared/feature/lesson-runner/domain/build.gradle.kts]`: `:shared:feature:app-shell:domain` в commonMain.

### Decision

**Разрешить one-way import** `lesson-runner:domain → app-shell:domain` для `AuthRepository`.

`userId` snapshot фиксируется в `RunnerState.Ready.userId` на старте попытки (once). Save use cases используют этот snapshot — не делают повторный auth read (см. `0-spec.md §22` guard: `currentUid() == null → InitFailed(AuthRequired)`). Auth — cross-cutting concern, `app-shell:domain` — established место для его interface.

### Alternatives Considered

**Вариант B — Передавать userId как String параметр в StartLessonAttemptUseCase**:
- (-) Перекладывает ответственность на вызывающий presentation слой — нарушает use case encapsulation
- (-) Presentation должна управлять auth state вместо domain; auth guard исчезает из UseCase

### Consequences

- Граф: `lesson-runner:domain → app-shell:domain` one-way ✓
- Pattern аналогичен существующему: `DefaultMyQuestsComponent` уже инжектирует `AuthRepository` (established project pattern)

### Risk if wrong (6 months out)

Если `AuthRepository` переезжает из `app-shell:domain` в `shared/core/auth/` — нужно обновить import в lesson-runner:domain и DI module. Низкая стоимость reversal (1 import change + Koin module update).

---

## ADR-LR-04: lesson-runner/domain → shared/core/question-schema (core import)

**Status**: ACCEPTED — import через shared/core (разрешено clean-architecture rules)

### Context

`lesson-runner:domain` использует из `shared/core/question-schema/`:
- `QuestionContent` sealed interface — parsed representation вопроса
- `QuestionContentParser` interface — `StartLessonAttemptUseCase.parser`
- `Difficulty` enum — `Attempt.mode`, `eligibleQuestions` filter

`shared/core/question-schema` — shared core module, не feature domain. По `clean-architecture.md` правилам, feature → core import разрешён без ADR.

### Decision

**Разрешить import** `lesson-runner:domain → shared/core/question-schema`.

Это import от feature к shared core — не cross-feature coupling по Invariant 3. `QuestionContent` sealed type — единый source of truth для schema вопросов (ADR-0003). `Difficulty` — core concept, используемый несколькими features. Parser interface — shared infrastructure.

Примечание: `QuestionContent` в коде **не имеет** поля `timeLimitSec` (ADR-0003 Amendment C зафиксировал игнорирование этого поля). Timer рассчитывается через `RunnerLogic.computeTimer(content, mode, coefficients)` на основе char count.

### Alternatives Considered

**Вариант B — Дублировать QuestionContent типы в lesson-runner:domain**:
- (-) Нарушает DRY, two источника truth для question schema
- (-) Future ADR-0003 amendments нужно применять в двух местах

### Consequences

- `shared/core/question-schema/` требует добавления `@Serializable` на `QuestionContent` sealed interface и subclasses для `KotlinxSerializationQuestionContentParser` (Grounding Problem 1 — решается в phase-01)
- `Difficulty` требует `@Serializable` для `QuizzesConfig.LessonRunner` (см. ADR-LR-06)

### Risk if wrong (6 months out)

Если `QuestionContent` schema нужно расширить (5-й тип вопроса) — изменение в shared/core ломает только evaluateAnswer в lesson-runner (compile-time catch). Если parser меняет serialization format — backward compatibility с существующими вопросами в Room нужно тестировать. Средняя стоимость.

---

## ADR-LR-05: TopParticipant location — устранение bidirectional coupling (BLOCKER #2)

**Status**: ACCEPTED — критический blocker (Invariant 3, Grounding Problem 2)

### Context

Spec `§34`: `Lesson.top3: List<TopParticipant>`. Текущее расположение `TopParticipant`:
```
shared/feature/lesson-runner/domain/src/commonMain/.../model/TopParticipant.kt:3
```
Canonical signature: `06-api-contract.md §LR-5a`.

`lesson-runner:domain` уже импортирует `lesson:domain` (ADR-LR-01). Если `lesson:domain` добавит `Lesson.top3: List<TopParticipant>` — возникнет обратный import `lesson:domain → lesson-runner:domain`. Это **bidirectional coupling** — blocker по Invariant 3 (`docs/invariants.md:25`).

Verified via `[shared/feature/lesson-runner/domain/build.gradle.kts]`: lesson-runner зависит от lesson. Reverse зависимость создаст circular dependency в Gradle.

### Decision

**Переместить `TopParticipant` в `shared/core/leaderboard/`** — новый минимальный KMP core модуль.

Структура нового модуля:
```
shared/core/leaderboard/
  build.gradle.kts           (backend-dev ownership, Invariant 7)
  src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/
    TopParticipant.kt        (единственный production file)
```

После перемещения:
- `lesson:domain` — зависит от `:shared:core:leaderboard` → может использовать `TopParticipant` в `Lesson.top3`
- `lesson-runner:domain` — зависит от `:shared:core:leaderboard` → может читать `TopParticipant` в ResultScreen logic
- Bidirectional coupling `lesson:domain ↔ lesson-runner:domain` устранён ✓

`TopParticipant` — универсальная ranking концепция (nickname + avatar + percent), не специфичная для lesson-runner. Будущие features (leaderboard экран, user profiles) могут использовать тот же type. Размещение в core — правильный bounded context.

### Alternatives Considered

**Вариант B — `Lesson.top3: List<String>` (JSON-serialized)**:
- (-) Domain layer протекает JSON contract к presentation layer — нарушение `domain-models.md:35`
- (-) Presentation обязана знать JSON структуру TopParticipant — coupling через string encoding
- (-) Type safety исчезает; ошибки разбора видны только в runtime

**Вариант C — Отдельный `TopParticipantsRepository` в lesson-runner:domain, НЕ в Lesson**:
- (-) Дополнительный sync path: `Lesson` document и `top3` синхронизируются независимо → сложность sync infrastructure
- (-) Result screen должен делать два независимых fetch вместо одного (Lesson snapshot + top3 snapshot)
- (-) Cloud Function (out-of-scope) пишет top3 в Lesson document — разделение на отдельный repository не согласуется с server data model из spec §34

### Consequences

- `TopParticipant.kt` **удаляется** из `lesson-runner:domain` и создаётся в `shared/core/leaderboard/`
- Walking Skeleton domain tесты, использующие `TopParticipant`, обновляют import → это design-phase rename, допустимо по Invariant 6
- `lesson:domain/build.gradle.kts` добавляет `:shared:core:leaderboard` dep (backend-dev)
- `lesson-runner:domain/build.gradle.kts` добавляет `:shared:core:leaderboard` dep (backend-dev)
- Validation: `rg "^import .*lesson_runner" shared/feature/lesson/domain/` — должно быть пустым после fix

### Risk if wrong (6 months out)

Если leaderboard concept нужно расширить (rankPosition, badgeEmoji, streak) — нужно менять `TopParticipant` в `shared/core/leaderboard/` и обновлять все consumers (lesson:domain + lesson-runner:domain + result screen). Координированное изменение через core — управляемый blast radius. Стоимость reversal: низкая.


exec
/bin/bash -lc 'rg -n "''^## |''^### |AC-|Acceptance|Feature Domain Contract|C[0-9]+|ADR-LR|RunnerUiState|PII|DifficultyConverter|LessonRunnerRootComponent|LessonRunnerComponentFactory|lessonRunnerDomainKoinAdapter|quizzes-screen" docs/features/lesson-runner/0-spec.md docs/features/lesson-runner/03-decisions.md docs/features/lesson-runner/implementation.md' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/implementation.md:7:## Summary
docs/features/lesson-runner/implementation.md:9:Lesson-runner — gameplay loop викторины — реализован атомарной заменой `LessonPlaceholder` на полноценный flow в `quizzes-screen`. Один вопрос на экран, таймер, auto-random на timeout, результат с процентами/звёздами/топ-3, опрос «Оцените урок», запись попытки в Room. Все 65 acceptance criteria из `0-spec.md` покрыты. 7 фаз pipeline pass (phase-01 → phase-07).
docs/features/lesson-runner/implementation.md:11:## Phases Completed
docs/features/lesson-runner/implementation.md:16:| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
docs/features/lesson-runner/implementation.md:19:| Phase-05 | Compose UI (LessonRunnerScreen + 12 composables) | PASS (5/5 reviewers) | ADR-LR-19: RunnerUiState.Result flat projection (security-driven) |
docs/features/lesson-runner/implementation.md:21:| Phase-07 | Composition Root + Smoke | PASS (4/5 reviewers; concurrency N/A) | ADR-LR-20: LessonRunnerComponentFactory in presentation module |
docs/features/lesson-runner/implementation.md:23:## Review Verdicts
docs/features/lesson-runner/implementation.md:33:Все findings закрыты автономно через reviewer↔coder loop. Lead вмешался только для 3 design escalations (ADR-LR-18/19/20).
docs/features/lesson-runner/implementation.md:35:## Changed Files
docs/features/lesson-runner/implementation.md:37:### NEW Modules (3 Gradle modules)
docs/features/lesson-runner/implementation.md:43:### NEW Files in existing modules (~12)
docs/features/lesson-runner/implementation.md:48:- `android/feature/quizzes-screen/presentation/`: uistate/LessonItemUi.kt, screen/LessonItemCard.kt, uistate/LessonListUiState.kt
docs/features/lesson-runner/implementation.md:50:### Modified Files (~20)
docs/features/lesson-runner/implementation.md:58:### Deleted Files
docs/features/lesson-runner/implementation.md:63:Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
docs/features/lesson-runner/implementation.md:65:## Test Coverage
docs/features/lesson-runner/implementation.md:78:## Build Validation
docs/features/lesson-runner/implementation.md:86:- `./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest` — included in build gate
docs/features/lesson-runner/implementation.md:89:## Smoke
docs/features/lesson-runner/implementation.md:95:## ADR Additions During Implementation
docs/features/lesson-runner/implementation.md:99:| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
docs/features/lesson-runner/implementation.md:100:| LR-19 | 04→05 | RunnerUiState.Result uses flat projection instead of attempt: Attempt aggregate | Security: attempt contained PII (userId, codeAnswer, attemptId) — minimal exposure principle in public StateFlow. 06-api-contract.md:408 + frontend.md Pattern Invariant superseded. |
docs/features/lesson-runner/implementation.md:101:| LR-20 | 04→07 | single<LessonRunnerComponentFactory> in lessonRunnerPresentationModule, NOT in AppApplication | Phase-04 already established single<> binding; phase-07 plan duplication would cause Koin override warning. Per 06-api-contract.md:374, factory binding stays in feature presentation module. |
docs/features/lesson-runner/implementation.md:105:## Remaining Issues / Known Gaps
docs/features/lesson-runner/implementation.md:121:## Quality Scorecard
docs/features/lesson-runner/implementation.md:125:## Pipeline Statistics
docs/features/lesson-runner/03-decisions.md:3:authors: architect-high-level (ADR-LR-01..LR-07, ADR-LR-16), architect-component (ADR-LR-08..15)
docs/features/lesson-runner/03-decisions.md:9:<!-- HL_SECTION_START: ADR-LR-01..LR-07 (architect-high-level writes here) -->
docs/features/lesson-runner/03-decisions.md:11:## ADR-LR-01: lesson-runner/domain → lesson:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:15:### Context
docs/features/lesson-runner/03-decisions.md:25:### Decision
docs/features/lesson-runner/03-decisions.md:31:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:42:### Consequences
docs/features/lesson-runner/03-decisions.md:48:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:54:## ADR-LR-02: lesson-runner/domain → question:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:58:### Context
docs/features/lesson-runner/03-decisions.md:67:### Decision
docs/features/lesson-runner/03-decisions.md:73:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:79:### Consequences
docs/features/lesson-runner/03-decisions.md:85:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:91:## ADR-LR-03: lesson-runner/domain → app-shell:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:95:### Context
docs/features/lesson-runner/03-decisions.md:101:### Decision
docs/features/lesson-runner/03-decisions.md:107:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:113:### Consequences
docs/features/lesson-runner/03-decisions.md:118:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:124:## ADR-LR-04: lesson-runner/domain → shared/core/question-schema (core import)
docs/features/lesson-runner/03-decisions.md:128:### Context
docs/features/lesson-runner/03-decisions.md:137:### Decision
docs/features/lesson-runner/03-decisions.md:145:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:151:### Consequences
docs/features/lesson-runner/03-decisions.md:154:- `Difficulty` требует `@Serializable` для `QuizzesConfig.LessonRunner` (см. ADR-LR-06)
docs/features/lesson-runner/03-decisions.md:156:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:162:## ADR-LR-05: TopParticipant location — устранение bidirectional coupling (BLOCKER #2)
docs/features/lesson-runner/03-decisions.md:166:### Context
docs/features/lesson-runner/03-decisions.md:174:`lesson-runner:domain` уже импортирует `lesson:domain` (ADR-LR-01). Если `lesson:domain` добавит `Lesson.top3: List<TopParticipant>` — возникнет обратный import `lesson:domain → lesson-runner:domain`. Это **bidirectional coupling** — blocker по Invariant 3 (`docs/invariants.md:25`).
docs/features/lesson-runner/03-decisions.md:178:### Decision
docs/features/lesson-runner/03-decisions.md:197:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:209:### Consequences
docs/features/lesson-runner/03-decisions.md:217:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:223:## ADR-LR-06: Difficulty @Serializable — QuizzesConfig.LessonRunner сериализация (BLOCKER #5)
docs/features/lesson-runner/03-decisions.md:227:### Context
docs/features/lesson-runner/03-decisions.md:229:`QuizzesConfig` — `@Serializable sealed class`, сохраняется в Decompose StateKeeper при process death (ADR-QS-02 quizzes-screen). Новый вариант `QuizzesConfig.LessonRunner(mode: Difficulty)` требует, чтобы `Difficulty` был сериализуемым.
docs/features/lesson-runner/03-decisions.md:239:### Decision
docs/features/lesson-runner/03-decisions.md:251:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:258:### Consequences
docs/features/lesson-runner/03-decisions.md:265:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:271:## ADR-LR-07: LessonPlaceholder replacement strategy — full atomic replacement
docs/features/lesson-runner/03-decisions.md:275:### Context
docs/features/lesson-runner/03-decisions.md:280:- `LessonPlaceholderComponent` interface (`android/feature/quizzes-screen/presentation`)
docs/features/lesson-runner/03-decisions.md:289:### Decision
docs/features/lesson-runner/03-decisions.md:303:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:311:### Consequences
docs/features/lesson-runner/03-decisions.md:319:- Обновление `docs/features/quizzes-screen/03-decisions.md`: добавить ADR-QS-15 (push consumer side) и ADR-QS-16 (LessonAttemptRepository import)
docs/features/lesson-runner/03-decisions.md:321:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:323:Если lesson-runner реализация задерживается или отменяется — нет fallback: тап на урок не работает. Reversal = восстановить `LessonPlaceholder*` файлы из git history + откатить ADR-LR-07. Стоимость reversal: средняя, но git history доступен. Атомарная замена правильная ставка для новой фичи без production saved state.
docs/features/lesson-runner/03-decisions.md:325:### Producer/Consumer boundary — SUPERSEDED by ADR-LR-16
docs/features/lesson-runner/03-decisions.md:327:> ⚠️ Этот раздел описывал первоначальный подход через `android/core/navigation/`. Codex CLI plan-round-1 выявил, что этот подход создаёт circular Gradle dependency: `core/navigation → lesson-runner/presentation` (для RunnerUiState/RunnerEvent типов) + `lesson-runner/presentation → core/navigation` (для interface implementation). Cycle = build failure. Актуальное решение — **ADR-LR-16**: interface и factory живут в `lesson-runner/presentation`, прямой импорт из `quizzes-screen/presentation`.
docs/features/lesson-runner/03-decisions.md:331:## ADR-LR-16: LessonRunnerRootComponent interface location — lesson-runner/presentation
docs/features/lesson-runner/03-decisions.md:335:### Context
docs/features/lesson-runner/03-decisions.md:337:`plan/phase-04/backend.md:18-44` содержал задачу поместить `LessonRunnerRootComponent` interface в `android/core/navigation/`. При анализе типов выявлена circular Gradle dependency:
docs/features/lesson-runner/03-decisions.md:340:core/navigation → lesson-runner/presentation   (RunnerUiState, RunnerEvent типы для interface)
docs/features/lesson-runner/03-decisions.md:346:`LessonRunnerComponentFactory` fun interface с тем же планом в `core/navigation` имела идентичную проблему: return type `LessonRunnerRootComponent` обязывает `core/navigation → lesson-runner/presentation`.
docs/features/lesson-runner/03-decisions.md:348:### Decision
docs/features/lesson-runner/03-decisions.md:354:| `LessonRunnerRootComponent` interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt` |
docs/features/lesson-runner/03-decisions.md:355:| `LessonRunnerComponentFactory` fun interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt` |
docs/features/lesson-runner/03-decisions.md:357:`quizzes-screen/presentation` импортирует оба напрямую. Направление строго одностороннее: `quizzes-screen/presentation → lesson-runner/presentation`. Reverse direction запрещён.
docs/features/lesson-runner/03-decisions.md:363:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:365:**Вариант A — core/navigation (изначальный план, ADR-LR-07 original section)**:
docs/features/lesson-runner/03-decisions.md:372:- (-) `quizzes-screen/presentation` получает untyped marker — не может обращаться к `uiState`/`events` без downcast
docs/features/lesson-runner/03-decisions.md:374:- (-) `LessonRunnerScreen` composable всё равно ожидает typed `LessonRunnerRootComponent` — presenter слой не упрощается
docs/features/lesson-runner/03-decisions.md:377:**Вариант C — RunnerUiState/RunnerEvent в shared/core**:
docs/features/lesson-runner/03-decisions.md:382:### Consequences
docs/features/lesson-runner/03-decisions.md:384:- `quizzes-screen/presentation` Gradle module добавляет `:android:feature:lesson-runner:presentation` в deps (backend-dev scope, `build.gradle.kts`).
docs/features/lesson-runner/03-decisions.md:385:- `DefaultQuizzesComponent.createChild` для `QuizzesConfig.LessonRunner` вызывает `factory.create(ctx, lessonId, mode)` → возвращает typed `LessonRunnerRootComponent`.
docs/features/lesson-runner/03-decisions.md:386:- Koin: `LessonRunnerComponentFactory` single binding в `apps/android-next/AppApplication.kt` — canonical: `06-api-contract.md §LR-9a`.
docs/features/lesson-runner/03-decisions.md:388:- Validation grep после phase-04 implementation: `rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src` — ожидается 1 match.
docs/features/lesson-runner/03-decisions.md:390:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:392:Если появится второй consumer `lesson-runner/presentation` вне `quizzes-screen` — паттерн прямого импорта повторяется с новым ADR (per `clean-architecture.md` ChildStack Compose rendering exception — требует ADR). Если второй consumer в `android/core/` — оба артефакта переезжают в `core` и dep direction разворачивается. Стоимость reversal: средняя (2 файла + Gradle deps update + import changes в consumers). Текущее решение оптимально для одного consumer.
docs/features/lesson-runner/03-decisions.md:398:<!-- CMP_SECTION_START: ADR-LR-08..15 (architect-component writes here) -->
docs/features/lesson-runner/03-decisions.md:400:## ADR-LR-08 — KotlinxSerializationQuestionContentParser location
docs/features/lesson-runner/03-decisions.md:418:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:422:### Migration Plan (Phase-01 task)
docs/features/lesson-runner/03-decisions.md:432:## ADR-LR-09 — Koin lambda binding strategy для providers
docs/features/lesson-runner/03-decisions.md:440:**Decision**: **Option C** — wrapper interfaces в `shared/feature/lesson-runner/domain/src/commonMain/…/provider/` (C1 fix: domain — единственный потребитель; interfaces принадлежат слою который их использует):
docs/features/lesson-runner/03-decisions.md:446:Koin adapter (`lessonRunnerDomainKoinAdapter`) — `shared/feature/lesson-runner/data/src/androidMain/…/di/` (C1 fix: adapter в data, не в domain/androidMain):
docs/features/lesson-runner/03-decisions.md:454:// lessonRunnerDomainKoinAdapter (data/androidMain) — adapts interfaces to function types
docs/features/lesson-runner/03-decisions.md:466:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:472:## ADR-LR-10 — AppDatabase migration strategy v3 → v4
docs/features/lesson-runner/03-decisions.md:481:**TypeConverters**: `DifficultyConverter` и `TopParticipantListConverter` оба `@ProvidedTypeConverter` + `.addTypeConverter()` в Room builder (паттерн из `StringSetConverter` / `PersistenceModule.kt:24`).
docs/features/lesson-runner/03-decisions.md:483:**Risk Mitigations** (C14: удалён claim "нулевой риск"):
docs/features/lesson-runner/03-decisions.md:491:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:497:## ADR-LR-11 — Компонент для lesson card с StarRating + HARD checkbox
docs/features/lesson-runner/03-decisions.md:502:- B: новый `LessonItemCard` в `quizzes-screen/presentation` — изолированное изменение
docs/features/lesson-runner/03-decisions.md:505:**Decision**: **Option B** — `LessonItemCard` в `android/feature/quizzes-screen/presentation`.
docs/features/lesson-runner/03-decisions.md:509:**LessonItemCard location**: `android/feature/quizzes-screen/presentation/src/main/…/components/LessonItemCard.kt`  
docs/features/lesson-runner/03-decisions.md:517:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:519:Если `LessonItemCard` нужен в другом feature module — придётся либо переносить в `android/core/designsystem` (затрагивает gameplay-concept), либо дублировать. Пока lesson card — единственный потребитель, изоляция в `quizzes-screen/presentation` правильная. Стоимость reversal: средняя (перемещение + обновление imports).
docs/features/lesson-runner/03-decisions.md:523:## ADR-LR-12 — Унификация поля `value` vs `raw` в value classes
docs/features/lesson-runner/03-decisions.md:534:**Compile gate** (C13): `./gradlew :shared:feature:lesson-runner:domain:jvmTest` должен быть зелёным после rename. Если тесты ломаются — rename не завершён (не "почти готово").
docs/features/lesson-runner/03-decisions.md:536:**Test churn specifics** (C13): файлы, которые потребуют изменений:
docs/features/lesson-runner/03-decisions.md:547:**Spec typo** (C13): `0-spec.md:100` пишет `sourceId.raw` — это spec-опечатка; после rename фактически будет `sourceId.value`. Spec не обновляется (minor typo), implementation следует renamed field.
docs/features/lesson-runner/03-decisions.md:552:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:558:## ADR-LR-13 — `lastModifiedAt` в Attempt для Firestore sync
docs/features/lesson-runner/03-decisions.md:572:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:578:## ADR-LR-14 — Формализация ADR-0003 Amendments A-D
docs/features/lesson-runner/03-decisions.md:599:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:605:## ADR-LR-15 — Lesson rating fields naming и типы
docs/features/lesson-runner/03-decisions.md:622:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:628:## ADR-LR-17 — Compose composition exception: quizzes-screen рендерит LessonRunnerScreen через ChildStack
docs/features/lesson-runner/03-decisions.md:632:**Context**: `QuizzesScreen.kt` содержит exhaustive `when(active)` dispatch block для `ChildStack<QuizzesConfig, QuizzesChild>`. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.
docs/features/lesson-runner/03-decisions.md:638:**Связь с ADR-LR-16**: `LessonRunnerRootComponent` interface живёт в `lesson-runner/presentation` (не в `core/navigation` — отменено из-за cycle, per ADR-LR-16). `QuizzesScreen.kt` получает `child.component: LessonRunnerRootComponent` из ChildStack и передаёт его в `LessonRunnerScreen`. Таким образом ADR-LR-17 расширяет разрешение ADR-LR-16: тот же модуль `lesson-runner/presentation`, дополнительный artifact — `@Composable` screen function.
docs/features/lesson-runner/03-decisions.md:640:**Decision**: Разрешить **одностороннее** Compose rendering import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation` для:
docs/features/lesson-runner/03-decisions.md:642:- `LessonRunnerRootComponent` interface — тип параметра `LessonRunnerScreen` (уже разрешён ADR-LR-16, упомянут здесь для полноты)
docs/features/lesson-runner/03-decisions.md:644:Обратное направление `android/feature/lesson-runner/presentation → android/feature/quizzes-screen/presentation` остаётся blocker — нарушение = blocker независимо от типа символа.
docs/features/lesson-runner/03-decisions.md:650:- **(A) Slot pattern** — `LessonRunnerNavigationSlot @Composable` extension в `android/core/navigation/`; quizzes-screen вызывает slot; каждая фича регистрирует свой slot в centralised registry. Отклонён: overhead slot registration; registry требует централизованного места без compile-time гарантии что slot зарегистрирован; не следует Decompose idiomatic pattern.
docs/features/lesson-runner/03-decisions.md:655:- Импортируется **только** `@Composable` screen function + `LessonRunnerRootComponent` — NOT use cases, NOT repositories, NOT internal sealed interfaces, NOT component implementation classes
docs/features/lesson-runner/03-decisions.md:659:    android/feature/quizzes-screen/presentation/src/main -g "*.kt"
docs/features/lesson-runner/03-decisions.md:660:  # Допустимые совпадения: только LessonRunnerScreen и LessonRunnerRootComponent
docs/features/lesson-runner/03-decisions.md:669:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:677:## ADR-LR-18 — DifficultyConverter удалён: mapper-based conversion вместо TypeConverter
docs/features/lesson-runner/03-decisions.md:681:### Context
docs/features/lesson-runner/03-decisions.md:683:Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.
docs/features/lesson-runner/03-decisions.md:687:### Decision
docs/features/lesson-runner/03-decisions.md:689:**Удалить `DifficultyConverter.kt` полностью.** Конвертация `Difficulty ↔ Int` выполняется в mapper слое:
docs/features/lesson-runner/03-decisions.md:698:`DifficultyConverter` не регистрируется ни в `@TypeConverters`, ни в Room builder. `TypeConvertersPhase02Test.kt` MT-05 тесты удалены.
docs/features/lesson-runner/03-decisions.md:700:Plan invariant `overview.md §Pattern Invariants` строка про `DifficultyConverter` — superseded этим ADR.
docs/features/lesson-runner/03-decisions.md:702:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:704:- **Оставить DifficultyConverter + изменить Entity на `Difficulty` тип** — нарушает spec AC#52 ("isHard: Int для safer migration validation"). Отклонено: spec mandates Int.
docs/features/lesson-runner/03-decisions.md:707:### Consequences
docs/features/lesson-runner/03-decisions.md:709:- `DifficultyConverter.kt` удалён
docs/features/lesson-runner/03-decisions.md:715:### Risk if wrong (6 months out)
docs/features/lesson-runner/03-decisions.md:719:## ADR-LR-19 — Phase-04: RunnerUiState.Result flat projection (security)
docs/features/lesson-runner/03-decisions.md:724:### Decision
docs/features/lesson-runner/03-decisions.md:726:`RunnerUiState.Result` использует flat-поля (`percentScore`, `mode`, `completedAt`, `hardUnlocked`, `bestStarsRawTenths`) вместо `attempt: Attempt` aggregate из `06-api-contract.md:408`.
docs/features/lesson-runner/03-decisions.md:728:### Rationale
docs/features/lesson-runner/03-decisions.md:730:Phase-04 security review (task #23) обнаружил что `attempt: Attempt` в публичном `StateFlow<RunnerUiState>` содержит PII: `userId`, `codeAnswer`, `attemptId`. Flat projection — security best practice (minimal exposure): UI получает только поля, необходимые для рендеринга, без доступа к идентификаторам пользователя и raw code answers.
docs/features/lesson-runner/03-decisions.md:734:### Supersedes
docs/features/lesson-runner/03-decisions.md:739:### Consequences
docs/features/lesson-runner/03-decisions.md:741:- `RunnerUiState.Result` имеет flat-поля; Phase-05 UI обращается к `state.mode`, `state.percentScore.raw` напрямую
docs/features/lesson-runner/03-decisions.md:742:- Phase-06 и последующие фазы используют flat-поля `RunnerUiState.Result` — не `attempt: Attempt`
docs/features/lesson-runner/03-decisions.md:745:### Alternatives Considered
docs/features/lesson-runner/03-decisions.md:747:- **Вернуть `attempt: Attempt`** — невозможно без раскрытия PII полей (userId, codeAnswer) в публичном StateFlow. Отклонено: противоречит security finding #23.
docs/features/lesson-runner/03-decisions.md:750:## ADR-LR-20 — LessonRunnerComponentFactory binding location: lessonRunnerPresentationModule (Phase-04 accepted deviation)
docs/features/lesson-runner/03-decisions.md:755:### Decision
docs/features/lesson-runner/03-decisions.md:757:`single<LessonRunnerComponentFactory>` binding живёт в `lessonRunnerPresentationModule` (`android/feature/lesson-runner/presentation/src/main/.../di/LessonRunnerPresentationModule.kt:37`), а **не** inline в `AppApplication.kt startKoin` как предписывалось `06-api-contract.md:374`.
docs/features/lesson-runner/03-decisions.md:759:### Rationale
docs/features/lesson-runner/03-decisions.md:765:### Consequences
docs/features/lesson-runner/03-decisions.md:769:- Будущие разработчики **не должны** добавлять `single<LessonRunnerComponentFactory>` в `AppApplication.kt` — это создаст Koin duplicate binding exception
docs/features/lesson-runner/03-decisions.md:771:### Supersedes
docs/features/lesson-runner/0-spec.md:10:## Source
docs/features/lesson-runner/0-spec.md:13:- Type: `new-feature` — заменяет существующий `LessonPlaceholderComponent` из фичи `quizzes-screen` на полноценный gameplay-loop викторины. Это центральный геймплей-экран приложения: пользователь видит вопрос, отвечает, переходит к следующему, в конце получает результат с процентами/звёздами/топ-3 и опционально оценивает урок.
docs/features/lesson-runner/0-spec.md:17:## Requirements
docs/features/lesson-runner/0-spec.md:19:### Functional Requirements
docs/features/lesson-runner/0-spec.md:23:1. **Тап на Lesson** в `LessonListComponent` (фича `quizzes-screen`) — заменяем существующий push `LessonPlaceholderComponent` на push `LessonRunnerRootComponent(lessonId, mode)`, где `mode` определяется состоянием HARD checkbox в `LessonListComponent` (default = EASY если не отмечен). Без отдельной обложки урока: тап → сразу первый вопрос. — [USER DECIDED]
docs/features/lesson-runner/0-spec.md:135:29. **Карточка урока** в `LessonListComponent` (фича `quizzes-screen`) расширяется:
docs/features/lesson-runner/0-spec.md:187:   > **Amendment 2026-04-26 (design phase, user-approved)**: `Lesson.ratingCount` изменён с `Int?` на `Int = 0` (non-nullable, default 0) — align с Quest.averageRatingCount pattern (`shared/feature/quest/domain/.../model/Quest.kt:69`). Семантика: missing field = 0 (нет оценок), null более не используется. Resolution applied per `docs/features/lesson-runner/03-decisions.md ADR-LR-15`.
docs/features/lesson-runner/0-spec.md:194:   - `android/feature/lesson-runner/presentation/` — Decompose `LessonRunnerRootComponent` + sub-components per вопрос-type, Compose UI screens, DI Koin module.
docs/features/lesson-runner/0-spec.md:199:37. **Подключение к `quizzes-screen`** — заменить configuration `LessonPlaceholder` на `LessonRunner(lessonId, mode)` в `QuizzesConfig` sealed class фичи `quizzes-screen`. Это touchpoint в существующей навигации; design phase решит — заменить полностью или оставить placeholder как fallback. — [DELEGATED]
docs/features/lesson-runner/0-spec.md:201:### Non-Functional Requirements
docs/features/lesson-runner/0-spec.md:217:## Scope
docs/features/lesson-runner/0-spec.md:219:### In Scope
docs/features/lesson-runner/0-spec.md:228:- Подключение к `quizzes-screen` ChildStack — замена `LessonPlaceholder` на `LessonRunner`
docs/features/lesson-runner/0-spec.md:229:- Расширение `LessonListComponent` (фича `quizzes-screen`): отображение `bestStars` + checkbox HARD при `hardUnlocked`
docs/features/lesson-runner/0-spec.md:234:### Explicitly Out of Scope
docs/features/lesson-runner/0-spec.md:253:## User Decisions
docs/features/lesson-runner/0-spec.md:287:## Server-Side Context
docs/features/lesson-runner/0-spec.md:291:### Server-Side Issues / Required Server Work (контракт, реализация — отдельные задачи)
docs/features/lesson-runner/0-spec.md:301:## Search Criteria for Research
docs/features/lesson-runner/0-spec.md:305:### Existing infrastructure (re-use mapping)
docs/features/lesson-runner/0-spec.md:313:4. **Decompose `LessonPlaceholderComponent` интеграция в `quizzes-screen`** — `android/feature/quizzes-screen/presentation/`. Найти `QuizzesConfig` sealed class и место push `LessonPlaceholder(lessonId)`. Документировать сигнатуру push, как breadcrumb передаётся, как ChildStack замена работает.
docs/features/lesson-runner/0-spec.md:315:5. **`LessonListComponent`** — найти текущую реализацию в `android/feature/quizzes-screen/presentation/`. Документировать:
docs/features/lesson-runner/0-spec.md:328:10. **`AuthRepository.observeUid()` и `currentUid()`** — `shared/feature/app-shell/domain/.../AuthRepository.kt:31` и `:43` (упомянуты в `quizzes-screen` research). Документировать сигнатуру для использования в `LessonAttemptRepository` (write attempt с актуальным `userId`).
docs/features/lesson-runner/0-spec.md:334:### Legacy reference
docs/features/lesson-runner/0-spec.md:355:16. **Legacy `QuizFragment.initPath()`** — `legacy/.../presentation/quiz/QuizFragment.kt:55`. Только для контекста breadcrumb формата (этим занимается `quizzes-screen`, не наша фича).
docs/features/lesson-runner/0-spec.md:357:### Architecture invariants
docs/features/lesson-runner/0-spec.md:363:19. **Bidirectional coupling check** — `quizzes-screen` будет импортировать lesson-runner config (для push) или lesson-runner будет импортировать что-то из quizzes-screen? Согласовать: `quizzes-screen` импортирует lesson-runner (push нового component), lesson-runner НЕ импортирует quizzes-screen (только pop через NavStack).
docs/features/lesson-runner/0-spec.md:367:### Domain model / data contract
docs/features/lesson-runner/0-spec.md:377:### Completeness check
docs/features/lesson-runner/0-spec.md:389:## Primary User Journeys
docs/features/lesson-runner/0-spec.md:392:   - Start: `LessonListComponent` (фича `quizzes-screen`), пользователь видит карточку урока с 0★, без HARD checkbox.
docs/features/lesson-runner/0-spec.md:394:   - State changes: push `LessonRunnerRootComponent(lessonId, mode=EASY)` → загружает `Question`s урока, фильтрует EASY, выбирает random subset 20 → state `Loading → Ready` → отображение первого вопроса.
docs/features/lesson-runner/0-spec.md:413:   - State changes: push `LessonRunnerRootComponent(lessonId, mode=HARD)`. FLAG_SECURE включается. Random subset из HARD pool, разные коэффициенты таймера.
docs/features/lesson-runner/0-spec.md:454:   - State changes: push `LessonRunnerRootComponent` → state `Loading → InitFailed(EmptyPool)`.
docs/features/lesson-runner/0-spec.md:499:## Feature Domain Contract
docs/features/lesson-runner/0-spec.md:501:### Terms / Entities / Value Constraints
docs/features/lesson-runner/0-spec.md:583:### Business Rules / Invariants / Guards
docs/features/lesson-runner/0-spec.md:650:### Repository Interfaces (фиксируется явно для Phase 3.8)
docs/features/lesson-runner/0-spec.md:683:### Use Cases (фиксируется явно)
docs/features/lesson-runner/0-spec.md:762:### State / Decision Rules
docs/features/lesson-runner/0-spec.md:766:### Error / Recovery Rules
docs/features/lesson-runner/0-spec.md:778:### Domain Test Scenarios (для Walking Skeleton, Phase 3.8)
docs/features/lesson-runner/0-spec.md:921:## Delegated Decisions Summary
docs/features/lesson-runner/0-spec.md:925:| 1 | Empty state text | «В уроке пока нет вопросов» | Соответствует existing pattern в `MyQuestsScreen` / `quizzes-screen` | Low |
docs/features/lesson-runner/0-spec.md:946:## State Matrix
docs/features/lesson-runner/0-spec.md:948:### Matrix 1: Score 0-9 за один ответ по типу
docs/features/lesson-runner/0-spec.md:959:### Matrix 2: Stars per attempt formula
docs/features/lesson-runner/0-spec.md:974:### Matrix 3: bestStars per lesson (max по своим попыткам)
docs/features/lesson-runner/0-spec.md:986:### Matrix 4: Когда писать attempt в Room
docs/features/lesson-runner/0-spec.md:999:### Matrix 5: Rating prompt visibility
docs/features/lesson-runner/0-spec.md:1007:### Matrix 6: Sworn-fold / onResume / abort
docs/features/lesson-runner/0-spec.md:1017:### Matrix 7: Timer per вопрос
docs/features/lesson-runner/0-spec.md:1024:### Matrix 8: Pool selection
docs/features/lesson-runner/0-spec.md:1034:## Acceptance Criteria
docs/features/lesson-runner/0-spec.md:1036:### Navigation flow
docs/features/lesson-runner/0-spec.md:1038:1. [ ] GIVEN пользователь на `LessonListComponent` тапает урок (без HARD checkbox или checkbox=false) THEN push `LessonRunnerRootComponent(lessonId, mode=EASY)`; FLAG_SECURE НЕ включается; первый вопрос отображается
docs/features/lesson-runner/0-spec.md:1039:2. [ ] GIVEN пользователь с `hardUnlocked=true` тапает урок с включенным HARD checkbox THEN push `LessonRunnerRootComponent(lessonId, mode=HARD)`; FLAG_SECURE включается; первый вопрос отображается с HARD-стилизацией фона
docs/features/lesson-runner/0-spec.md:1044:### Score / codeAnswer correctness
docs/features/lesson-runner/0-spec.md:1058:### Stars and progress
docs/features/lesson-runner/0-spec.md:1068:### Timer
docs/features/lesson-runner/0-spec.md:1075:### Lifecycle / FLAG_SECURE / dialogs
docs/features/lesson-runner/0-spec.md:1087:### Attempt save / Room
docs/features/lesson-runner/0-spec.md:1094:### Result screen
docs/features/lesson-runner/0-spec.md:1103:### HARD unlock checkbox
docs/features/lesson-runner/0-spec.md:1109:### Empty / error states
docs/features/lesson-runner/0-spec.md:1117:### Code / DI / invariants
docs/features/lesson-runner/0-spec.md:1122:56. [ ] Code: feature import — `android/feature/lesson-runner/presentation` не импортирует `android/feature/quizzes-screen/presentation` (invariant 3); cross-feature только через `shared/core` или `android/core/designsystem`
docs/features/lesson-runner/0-spec.md:1123:57. [ ] Code: `quizzes-screen` импортирует lesson-runner config (для push) — задокументировано в design phase
docs/features/lesson-runner/0-spec.md:1149:## Invariant Check (from docs/invariants.md)
docs/features/lesson-runner/0-spec.md:1155:| 3. No bidirectional coupling between feature modules | `quizzes-screen` → импорт lesson-runner config (для push); lesson-runner НЕ импортирует quizzes-screen | preserve — однонаправленный import; pop через ChildStack без знания о quizzes-screen |
docs/features/lesson-runner/0-spec.md:1161:## Constraints (from PROJECT-CONTEXT.md)
docs/features/lesson-runner/0-spec.md:1165:- **Decompose Components** — pattern из `home-and-my-quests/03-decisions.md` ADR-CMP-51. `LessonRunnerRootComponent` + child components per вопрос-type.
docs/features/lesson-runner/0-spec.md:1173:## ADR Amendments Required
docs/features/lesson-runner/0-spec.md:1175:### ADR-0003 (Question schema) — четыре правки нужны для согласования
docs/features/lesson-runner/0-spec.md:1195:## Resolved Decisions (закрыты в spec dialogue)
docs/features/lesson-runner/0-spec.md:1215:## Open Items for Phase-01 (Implementation)
docs/features/lesson-runner/0-spec.md:1227:## Open Questions for Research (open для research phase)

exec
/bin/bash -lc "python3 - <<'PY'
from pathlib import Path
p=Path('docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch')
files=[]
for line in p.open(errors='replace'):
    if line.startswith('diff --git '):
        parts=line.strip().split()
        if len(parts)>=4:
            files.append((parts[2][2:],parts[3][2:]))
print(len(files))
for a,b in files:
    print(b)
PY" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
94
android/feature/quizzes-screen/presentation/build.gradle.kts
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt
android/feature/quizzes-screen/presentation/src/main/AndroidManifest.xml
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt
apps/android-next/build.gradle.kts
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
apps/android-next/src/test/java/.gitkeep
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt
platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt
settings.gradle.kts
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
shared/core/question-schema/build.gradle.kts
shared/core/question-schema/src/androidMain/AndroidManifest.xml
shared/core/question-schema/src/androidMain/kotlin/.gitkeep
shared/core/question-schema/src/commonMain/kotlin/.gitkeep
shared/core/question-schema/src/commonTest/kotlin/.gitkeep
shared/core/question-schema/src/jvmMain/kotlin/.gitkeep
shared/core/question-schema/src/jvmTest/kotlin/.gitkeep
shared/feature/lesson/data/build.gradle.kts
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonLocalDataSource.kt
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRemoteDataSource.kt
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImpl.kt
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/di/LessonDataModule.kt
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt
shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt
shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonRepositoryImplTest.kt
shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonLocalDataSource.kt
shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/fake/FakeLessonRemoteDataSource.kt
shared/feature/lesson/domain/build.gradle.kts
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/LessonId.kt
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/repository/LessonRepository.kt
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/use_case/SyncLessonsUseCase.kt
shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonDomainTest.kt
shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/fake/FakeLessonRepository.kt

codex
The doc trail already shows a few review hotspots: ADR-LR-16 says factory binding was once canonical in `AppApplication`, while ADR-LR-20 supersedes that into the feature module. I’m checking the actual wiring and imports next, because duplicate Koin bindings and reverse feature imports are the kind of cross-phase bugs that hide behind “all phases passed.”
exec
/bin/bash -lc "sed -n '1036,1138p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
### Navigation flow

1. [ ] GIVEN пользователь на `LessonListComponent` тапает урок (без HARD checkbox или checkbox=false) THEN push `LessonRunnerRootComponent(lessonId, mode=EASY)`; FLAG_SECURE НЕ включается; первый вопрос отображается
2. [ ] GIVEN пользователь с `hardUnlocked=true` тапает урок с включенным HARD checkbox THEN push `LessonRunnerRootComponent(lessonId, mode=HARD)`; FLAG_SECURE включается; первый вопрос отображается с HARD-стилизацией фона
3. [ ] GIVEN пользователь на любом вопросе нажимает крестик THEN диалог «Уверены?» → подтвердил → save attempt → возврат в `LessonListComponent`
4. [ ] GIVEN пользователь полностью прошёл все вопросы pool THEN save attempt в Room → переход на result screen
5. [ ] GIVEN пользователь на result screen тапает «Завершить» THEN если HARD — снять FLAG_SECURE → возврат в `LessonListComponent` через ChildStack pop

### Score / codeAnswer correctness

6. [ ] GIVEN SingleChoice с correct=A WHEN selected=A THEN code digit = '9'
7. [ ] GIVEN SingleChoice с correct=A WHEN selected=B THEN code digit = '1'
8. [ ] GIVEN MultipleChoice (все верные picked) THEN code digit = '9'
9. [ ] GIVEN MultipleChoice (никаких верных picked, только wrong) THEN code digit = '1'
10. [ ] GIVEN MultipleChoice (Jaccard 0.5) THEN code digit = '5'
11. [ ] GIVEN Ordering все позиции верны THEN digit = '9'
12. [ ] GIVEN Ordering 3 из 6 совпало THEN digit = '5' (round(0.5*8)+1)
13. [ ] GIVEN FillBlank 3 из 3 верно THEN digit = '9'
14. [ ] GIVEN FillBlank 1 из 3 THEN digit = '4' (round(0.333*8)+1=round(2.67)+1)
15. [ ] GIVEN timeout без действий THEN auto-random выбор → digit вычислен по тем же правилам (зависит от случайного попадания)
16. [ ] GIVEN attempt completed THEN `codeAnswer.length == eligibleQuestions(mode).size`; non-zero digits.size == subset.size; positions of non-zero соответствуют orders показанных вопросов

### Stars and progress

17. [ ] GIVEN EASY attempt с `codeAnswer.allShownAnswersAre9 == true` (`percentScore=100`) THEN `Stars(rawTenths=20)`; bestStars обновляется; hardUnlocked становится true
18. [ ] GIVEN EASY attempt с `percentScore=50` (no allShownAnswersAre9) THEN `Stars(rawTenths=10)`; hardUnlocked остаётся false
19. [ ] GIVEN HARD attempt с `percentScore=80` THEN `Stars(rawTenths=28)`
20. [ ] GIVEN HARD attempt с `percentScore=100` (allShownAnswersAre9=true) THEN `Stars(rawTenths=30)`
21. [ ] GIVEN no attempts THEN `bestStars = Stars(0)` (UI 0.0), `hardUnlocked = false`, HARD checkbox скрыт
22. [ ] GIVEN ≥1 EASY-попытка с allShownAnswersAre9=true (все показанные = '9') THEN `hardUnlocked = true` → HARD checkbox visible; user может включить
23. [ ] GIVEN ≥1 EASY-попытка с `allShownAnswersAre9=false` (хотя бы один digit ∈ '1'..'8') THEN `hardUnlocked = false` → HARD checkbox остаётся скрытым; даже если есть HARD-attempt с floor `Stars(rawTenths=20)` — checkbox visibility = `hardUnlocked`, НЕ `bestStars.rawTenths >= 20`

### Timer

24. [ ] GIVEN вопрос с totalChars=165, EASY mode THEN `timer == round(165*0.18) ≈ 30 сек`
25. [ ] GIVEN тот же вопрос HARD mode THEN `timer ≈ round(165*0.12) ≈ 20 сек`
26. [ ] GIVEN таймер истекает в 0 без действий пользователя THEN auto-random выбор → score фиксируется → переход к следующему вопросу
27. [ ] GIVEN короткий вопрос (totalChars=10) THEN `timer ≥ 5 сек` (min floor delegated)

### Lifecycle / FLAG_SECURE / dialogs

28. [ ] GIVEN HARD-mode runner запущен THEN `WindowManager.LayoutParams.FLAG_SECURE` установлен на window (или Compose-эквивалент)
29. [ ] GIVEN HARD-mode runner exit (любым путём) THEN FLAG_SECURE снят
30. [ ] GIVEN EASY-mode runner THEN FLAG_SECURE НЕ установлен
31. [ ] GIVEN пользователь на 5-м вопросе тапает Home (onStop) THEN текущий вопрос auto-random scored; таймер остановлен
32. [ ] GIVEN пользователь возвращается (onResume) THEN отображается fullscreen блокирующий диалог «Продолжить прохождение?»
33. [ ] GIVEN диалог «Продолжить?» тап «Продолжить» THEN диалог закрыт; следующий вопрос отображён с новым таймером; предыдущий вопрос НЕ показан
34. [ ] GIVEN диалог «Продолжить?» тап «Выйти» THEN attempt saved (codeAnswer: scores отвеченных + '1' для оставшихся показанных + '0' для не показанных); возврат в `LessonListComponent`
35. [ ] GIVEN configuration change (rotation) THEN component не пересоздаётся; таймер не сбрасывается; current question + answers preserved
36. [ ] GIVEN process kill THEN ничего не записывается в Room; следующий запуск — состояние ДО попытки

### Attempt save / Room

37. [ ] GIVEN complete attempt THEN `LessonAttemptRepository.save` called once с Attempt(lessonVersion из старта)
38. [ ] GIVEN exit-via-dialog после 3 ответов (pool size 20, eligibleQuestions.size=50) THEN saved attempt: codeAnswer.length==50, 3 цифры реальные, 17 = '1' (показанные но непрогресированные), 30 = '0' (out of subset)
39. [ ] GIVEN attempt созданный во время `lesson.version=5` THEN `attempt.lessonVersion == 5` даже если lesson.version обновился позже sync-ом
40. [ ] GIVEN no incremental save во время прохождения THEN `LessonAttemptRepository.save` вызывается ровно 1 раз per attempt

### Result screen

41. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND ¬hasSubmittedRating THEN на result screen видно опрос «Оцените урок» (1/2/3 целых звезды)
42. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND hasSubmittedRating THEN опрос НЕ виден
43. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == false` (any shown digit < '9') THEN опрос НЕ виден
44. [ ] GIVEN пользователь оценил урок THEN local флаг `lesson_rating_submitted_local` установлен; LessonRatingRepository.submit вызван 1 раз с lessonVersion из текущего Lesson
45. [ ] GIVEN result screen с `Lesson.top3` непустой THEN отображается секция Топ-3 с аватарками, никами и %; пустой → секция скрыта. **Note: top3 — закешированный server snapshot из Lesson document; текущая попытка пользователя ещё не успела попасть в top3 (server CF не выполнился; sync ещё не подтянул).** Список покажет state ДО этой попытки.
46. [ ] GIVEN top3 entry с avatarUrl=null или Coil не смог загрузить (offline / no cache) THEN placeholder вместо аватарки

### HARD unlock checkbox

47. [ ] GIVEN карточка урока в `LessonListComponent` THEN отображается `StarRating(rating = bestStars.rawTenths / 10f)` (existing API из `android/core/designsystem/.../StarRating.kt:99` принимает `rating: Float?`)
48. [ ] GIVEN `hardUnlocked == false` (нет EASY-попытки с allShownAnswersAre9) THEN HARD checkbox скрыт — независимо от bestStars value
49. [ ] GIVEN `hardUnlocked == true` THEN HARD checkbox visible; default unchecked. Логика: `hardUnlocked = attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`

### Empty / error states

50. [ ] GIVEN тап на урок с пустым eligibleQuestions(EASY) THEN empty state «В уроке пока нет вопросов» + кнопка «Назад»
51. [ ] GIVEN тап на урок с все payloads invalid THEN empty state аналогично
52. [ ] GIVEN single invalid payload среди валидных WHEN StartLessonAttemptUseCase THEN invalid отброшен на pipeline init; пользователь не видит; codeAnswer длина = только valid eligible
52a. [ ] GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` THEN `RunnerState.SaveFailed(attempt, error)`; result screen показывается с warning «Не удалось сохранить»; нет автоматического retry
52b. [ ] GIVEN `LessonRatingRepository.submit` throws WHEN user тапает submit rating THEN toast «Не удалось отправить оценку»; local флаг hasSubmitted остаётся false; user может попробовать снова

### Code / DI / invariants

53. [ ] DI: `LessonRunnerPresentationModule` + `LessonRunnerDataModule` зарегистрированы в `apps/android-next/.../AppApplication.kt`
54. [ ] Code: ни один файл `shared/feature/lesson-runner/domain/src/commonMain/` не импортирует `android.*`, `androidx.*`, `io.livekit`, `com.google.firebase`, `androidx.room`, `kotlinx.serialization` (invariant 1)
55. [ ] Code: ни одна Activity/Fragment не вызывает Repository / UseCase напрямую (invariant 2)
56. [ ] Code: feature import — `android/feature/lesson-runner/presentation` не импортирует `android/feature/quizzes-screen/presentation` (invariant 3); cross-feature только через `shared/core` или `android/core/designsystem`
57. [ ] Code: `quizzes-screen` импортирует lesson-runner config (для push) — задокументировано в design phase
58. [ ] Code: нет Hilt/Dagger annotations (Koin only)
59. [ ] Code: нет direct Firebase / Firestore writes из этой фичи; только через Repository
60. [ ] Tests: domain тесты Phase 3.8b покрывают все Domain Test Scenarios:
- Score formulas (1-12)
- codeAnswer construction (13-16)
- percentScore (17-20)
- stars (21-29)
- bestStars / hardUnlocked (30-35a)
- timer (36-39b)
- subset / auto-answer (40-47)
- rating prompt (48-51)
- save (52-54)
- lessonVersion (55-57)
- edge cases (58-61c)
- value object guards (62-71)

exec
/bin/bash -lc "sed -n '499,820p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## Feature Domain Contract

### Terms / Entities / Value Constraints

- **AttemptId** — `value class AttemptId(val raw: String)`. UUID String (auto-generated при создании Attempt). Validated `raw.isNotBlank()`.
- **RatingId** — `value class RatingId(val raw: String)`. Deterministic `sha256("$userId:$lessonId")`. Validated `raw.isNotBlank()`.
- **InitFailureReason** — sealed: `EmptyPool | NoValidQuestions | LessonNotFound | AuthRequired`.
- **SaveError** — sealed: `IoFailure(throwable) | UnknownError(throwable)`. (НЕ содержит AuthRequired — userId snapshot фиксируется в `RunnerState.Ready.userId` на старте, save use cases не делают повторный auth read.)
- **Difficulty** — enum `EASY | HARD`.
- **Score** — Int 0..9 (validated в init); `0` = special "не показан в этой попытке"; `1`..`9` = показан, разный share правильности.
- **CodeAnswer** — String value object: длина ≥ 1, каждый char ∈ `'0'..'9'`. Validated в init.
- **PercentScore** — Int 0..100 (validated `0 <= raw <= 100`). Derived из CodeAnswer integer math:
  - `nonZeroDigits = codeAnswer.filter { it != '0' }`
  - Если `nonZeroDigits.isEmpty()` → 0
  - Иначе: `sum = nonZeroDigits.sumOf { (it.digitToInt() - 1) * 100 / 8 }`; `percentScore = sum / nonZeroDigits.size` (integer division — детерминирует)
- **Stars** — value class `Stars(val rawTenths: Int)` где `rawTenths ∈ [0..30]` (validated в init). UI делит на 10 для Float отображения. Integer math для всех операций — нет Float precision issues. Это **derived UI value**, НЕ хранится в Attempt.
- **TimerDuration** — Int seconds, derived from question content + mode coefficient. Validated `>= 5` (min floor).
- **Attempt** — immutable data class: `(id: AttemptId, userId: String, lessonId: LessonId, lessonVersion: Long, mode: Difficulty, completedAt: Long, codeAnswer: CodeAnswer, percentScore: PercentScore)`. `userId.isNotBlank()`, `lessonVersion >= 1`, `completedAt >= 0`.
- **LessonRating** — immutable: `(id: RatingId, userId: String, lessonId: LessonId, lessonVersion: Long, rating: Int (1..3), ratedAt: Long)`. Validated.
- **TopParticipant** — `(nickname: String, avatarUrl: String?, percent: Int)`. `nickname.isNotBlank()`, `percent ∈ 0..100`.
- **QuestionContent** — sealed (parsed by `shared/core/question-schema/QuestionContentParser`): см. ADR-0003. Импортируется из question-schema как уже типизированная sealed Question.
- **RunnerState** — sealed:
  - `Loading` — стартовое состояние пока не загружены вопросы
  - `InitFailed(reason: InitFailureReason)` — sealed: `EmptyPool | NoValidQuestions | LessonNotFound | AuthRequired`
  - `Ready(userId: String, lessonId: LessonId, lessonVersion: Long, mode: Difficulty, playOrder: List<RunnerQuestion.Valid>, eligibleSize: Int, indexInPool: Int, codeAnswer: CodeAnswer, deadlineMs: Long, seed: Long, currentDraftAnswer: UserAnswerDraft?, isPaused: Boolean)` — активное прохождение
    - `userId` — snapshot Firebase Auth UID на старте попытки (читается из `AuthRepository.currentUid()` в `StartLessonAttemptUseCase`). Save use cases используют этот snapshot, не делают повторный auth read.
    - `playOrder: List<RunnerQuestion.Valid>` — отсортированный subset в порядке показа (sortedBy `Question.order` ASC, ties broken by `Question.id`). Только `Valid`: invalid payloads уже отфильтрованы при init. (Sealed `RunnerQuestion` объявлен для будущих edge cases, но в playOrder только `.Valid`.)
    - `eligibleSize` — total eligibleQuestions(mode) после parse + filter difficulty (= длина codeAnswer)
    - `indexInPool` — позиция текущего вопроса в `playOrder`. Invariant: `0 <= indexInPool <= playOrder.size`. Значение `playOrder.size` — sentinel «complete»; этот state кратковременный, сразу после `submitAnswer` на последнем вопросе → component вызывает `CompleteAttemptUseCase` → переход в `Completed`/`SaveFailed`.
    - `seed` — для детерминизма subset selection при rotation; фиксируется на старте
    - `currentDraftAnswer` — partial input для MultipleChoice/Ordering/FillBlank (не submitted ещё)
    - `isPaused` — флаг что показан onResume диалог «Продолжить?»

  Текущий `codeAnswerIndex` для записи score берётся из `playOrder[indexInPool].codeAnswerIndex`.
  - `Completed(attempt: Attempt, ratingPrompt: Boolean)` — финал, attempt сохранён
  - `Aborted(attempt: Attempt)` — exit-via-dialog или confirmed cross, attempt сохранён
  - `SaveFailed(attempt: Attempt, error: SaveError)` — Room write throw; user видит result с warning «Не удалось сохранить, попробуйте позже»; в MVP no auto-retry, см. P24
- **QuestionContent** — sealed (импорт из `shared/core/question-schema/src/commonMain/`): `SingleChoice/MultipleChoice/Ordering/FillBlank` согласно ADR-0003. Invariants enforced в init того модуля. `QuestionContent` имеет `id`, `difficulty`, `text`, `image?`, и type-specific поля. **НЕ имеет** `Question.order`, `Question.archived` (это поля domain `Question`, не parsed schema).
- **RunnerQuestion** — domain wrapper, sealed для разделения valid/invalid:
  ```kotlin
  sealed interface RunnerQuestion {
      val sourceId: QuestionId
      val order: Int
      val codeAnswerIndex: Int

      data class Valid(
          override val sourceId: QuestionId,
          override val order: Int,
          override val codeAnswerIndex: Int,
          val content: QuestionContent,  // parsed sealed Question
      ) : RunnerQuestion

      data class Invalid(
          override val sourceId: QuestionId,
          override val order: Int,
          override val codeAnswerIndex: Int,
          val parseError: String,  // describing reason
      ) : RunnerQuestion
  }
  ```
  Используется в `RunnerState.Ready.playOrder: List<RunnerQuestion>`. `StartLessonAttemptUseCase` строит:
  1. Загружает `List<Question>` через `QuestionRepository.observeByLesson(lessonId).first()`.
  2. Фильтрует `!archived`.
  3. Для каждого `Question`: `parser.parse(question.payload)` → если success → `RunnerQuestion.Valid` с `content`; если failure → `RunnerQuestion.Invalid`.
  4. Фильтрует по `mode`: `Valid` где `content.difficulty == mode`. **`Invalid` исключаются из subset на этапе фильтра** (нельзя определить difficulty без content). Они просто не попадают в pool.
  5. Сортирует по `(order, sourceId)` ASC.
  6. Применяет `selectSubset(eligible, 20, seed)`.

  Order of failures (init priority — соответствует фактической реализации Walking Skeleton):
  1. `authRepository.currentUid() == null` → `InitFailed(AuthRequired)` — guard в самом начале (не имеет смысла продолжать без uid).
  2. `lessonRepository.getById(lessonId) == null` → `InitFailed(LessonNotFound)` — урок не существует/архивирован.
  3. `eligibleQuestions(mode).isEmpty()` после parse + filter → `InitFailed(EmptyPool)`.
  4. `pool.all { invalid }` (все active questions имели invalid payloads) → `InitFailed(NoValidQuestions)`.
  
  В реальности EmptyPool и NoValidQuestions могут оказаться эквивалентны (если все invalid → eligibleSize=0 → EmptyPool). Walking Skeleton делает их различимыми через подсчёт parsed/invalid count.

  `Invalid` фактически **не существует в playOrder** в нормальном flow (фильтр их выкидывает). Sealed type оставлен на случай будущих edge cases (hot-reload во время прохождения и т.д. — для design phase).
- **UserAnswer** — sealed:
  - `SingleChoiceAnswer(selected: OptionId?)` — null = timeout без действия
  - `MultipleChoiceAnswer(selected: Set<OptionId>)`
  - `OrderingAnswer(order: List<OptionId>)`
  - `FillBlankAnswer(filled: Map<BlankId, CandidateId?>)` — value=null значит blank пуст
- **UserAnswerDraft** — параллельная структура для in-progress input, который пользователь ещё не submit-нул. Сериализуется в `instanceKeeper` для rotation.

### Business Rules / Invariants / Guards

1. **Pool size = 20 константа**. `subset = seededRandomSubset(eligibleQuestions, min(20, eligibleQuestions.size), seed)`. Если eligibleQuestions empty → `InitFailed(EmptyPool)`.
2. **Difficulty filter (active eligible questions snapshot at start)**: см. canonical pipeline в Requirement 22. Pseudo-summary:
    1. `questionRepository.observeByLesson(lessonId).first()` — snapshot list `Question`.
    2. `.filter { !it.archived }` — drop archived.
    3. `.mapNotNull { parser.parse(it.payload).getOrNull()?.let { content -> RunnerQuestion.Valid(...) } }` — drop invalid payloads.
    4. `.filter { it.content.difficulty == mode }` — keep only mode questions.
    5. `.sortedWith(compareBy({ order }, { sourceId.raw }))` — стабильный порядок.
    6. `.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }` — присвоить позиции в codeAnswer.

    EASY-попытка → only EASY; HARD → only HARD. Snapshot фиксируется на старте, изменения cascade sync во время прохождения игнорируются. Invalid payloads исключаются на шаге 3 — до прохождения они НЕ доходят (если все invalid → `InitFailed(NoValidQuestions)`).
3. **Subset selection determinism**: `seed = System.currentTimeMillis()` (или provided clock) фиксируется на старте, сохраняется в `RunnerState.seed` для restore через `instanceKeeper` на rotation.
4. **playOrder ordering**: `playOrder = subset.sortedBy(it.order then it.id)` (НЕ shuffled). Стабильный порядок показа. Дубликаты `Question.order` устраняются через secondary sort key `id`.
5. **codeAnswer длина** = `eligibleQuestions(mode).size` (НЕ total questions урока, НЕ subset size).
6. **codeAnswer индекс** = позиция `Question` в `eligibleQuestions(mode)` (отсортированном по `(order, id)`). Не входящие в subset (out-of-subset) → `'0'`. Входящие в subset, но валидные но непрогресированные (timeout / abandoned mid-subset) → `'1'`. Входящие в subset с invalid payload → `'1'` (показан, но не отвечен корректно).
7. **Score scale**:
   - `'0'` = вопрос НЕ был показан в этой попытке (out-of-subset)
   - `'1'`..`'9'` для показанных или попытавшихся показаться:
     - `'1'` = 0% правильности (full miss / timeout без угадывания / invalid payload)
     - `'5'` = 50%
     - `'9'` = 100%
   - Формула: `digit = round(correct_share × 8) + 1`, где `correct_share ∈ [0, 1]`.
8. **Score formula per type**:
   - SingleChoice: `correct_share = if (selected == correctOptionId) 1.0 else 0.0`. Guard: `selected ∈ options.map { it.id }` или null. Если selected ∉ options → trait как null (timeout).
   - MultipleChoice (Jaccard): `correct_share = correct_picked / (correct_picked + wrong_picked + missed)` — эквивалентно `|picked ∩ correctOptionIds| / |picked ∪ correctOptionIds|`. Если denominator == 0 (=пустой union: невозможно поскольку correctOptionIds.size ≥ 2 по ADR) → 0.0. Guard: `picked ⊆ options.map { it.id }`. Любые foreign IDs игнорируются (treat as wrong по умолчанию через filter).
   - Ordering: `correct_share = matched_positions / total_positions`, `matched_positions = items.indices.count { i -> userOrder.getOrNull(i) == correctOrder[i].id }`. Guard: `userOrder.size == items.size` и `userOrder.toSet() == items.map{it.id}.toSet()` (perm check); если invalid → score 0% (`'1'`).
   - FillBlank: `correct_share = filled_correct / total_blanks`. `filled_correct = blanks.count { filled[it.id] == it.correctCandidateId }`. Guard: `filled.values.filterNotNull() ⊆ candidates.map { it.id }`. null/foreign → wrong.
9. **Auto-answer на timeout**:
   - SingleChoice: `selected = options.random(seedFor(question))`
   - MultipleChoice: `selected = correctOptionIds.size случайных options` (детерминированно через seed)
   - Ordering: `userOrder = items.shuffled(seedFor(question))`
   - FillBlank: каждый blank → `candidates.random(seedFor(question))` (с возвратом — same candidate может попасть в несколько blanks; design phase решит UI блокировку, но domain-level fine)
   - Score фиксируется по тем же правилам.
10. **percentScore (Int)**: `nonZero = codeAnswer.count { it != '0' }`. Если `nonZero == 0` → 0. Иначе: `sum = codeAnswer.filter { it != '0' }.sumOf { (it.digitToInt() - 1) * 100 / 8 }`; `percentScore = sum / nonZero` (integer division — детерминирует).
11. **Perfect attempt detection** (для unlock и rating prompt): `attempt.allShownAnswersAre9 = codeAnswer.all { it == '0' || it == '9' }` AND `codeAnswer.any { it == '9' }`. Это **string-based**, НЕ percent-based — нет Float precision issues.
12. **Stars per attempt formula** (derived value class `Stars(rawTenths: Int 0..30)`):
    - EASY: `rawTenths = (percentScore * 20 + 50) / 100` → `[0..20]`. Эквивалент `stars = (percentScore / 100) × 2.0` с round half up.
    - HARD: `rawTenths = 20 + round(percentScore * 1 / 10)` → `[20..30]`. Точно: `tenths = 20 + (percentScore * 10 + 50) / 100`.
    - Все математика integer. Float только при UI rendering.
13. **bestStars per lesson** = `max(rawTenths per attempt)`. Integer max по всем своим попыткам этого `lessonId`. Если нет попыток — `Stars(0)`.
14. **HARD unlock condition** = `attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`. String-based, не percent. **НЕ** через bestStars (HARD attempt с percentScore=0 даёт `Stars(rawTenths=20)` floor, но НЕ unlocks).
15. **Checkbox visibility on lesson card**: `hardUnlocked == true`. **НЕ** через bestStars (HARD attempt floor `Stars(rawTenths=20)` не unlocks).
16. **Rating prompt visibility** = `attempt.allShownAnswersAre9 && !ratingsRepo.hasSubmitted(userId, lessonId)`. **String-based**, не percent-based.
17. **lessonVersion fixation**: `attempt.lessonVersion = lesson.version` на момент `StartLessonAttemptUseCase` invocation (snapshot когда state ещё `Loading`). Если `Lesson` Flow emit-ит обновление между UseCase invocation и завершением — игнорируется. `rating.lessonVersion = lesson.version` на момент `submit` (свежий read).
18. **Attempt save = только в конце**: один `LessonAttemptRepository.save(attempt)` в:
    - `CompleteAttemptUseCase` (последний вопрос отвечен) → `RunnerState.Completed`
    - `AbortAttemptUseCase` (exit-via-dialog или confirmed cross) → `RunnerState.Aborted`
    - **НЕ при process kill** (lost).
    - При Room exception → `RunnerState.SaveFailed(attempt, error)`. UI показывает result с warning. No auto-retry в MVP.
19. **No incremental save** — runner state живёт только in-memory + Decompose `instanceKeeper` для rotation.
20. **Subset stability**: subset через seed фиксируется на старте попытки.
21. **Timer formula**:
    ```
    charsCount = chars(text) + sum(chars(option_or_item_or_candidate text)) + (if hasImage then 100 else 0)
    seconds = max(5, round(charsCount × k))
    ```
    `k_easy ≈ 0.18`, `k_hard ≈ k_easy / 1.5 ≈ 0.12`. Min floor 5 секунд. Точные коэффициенты и +100-bonus — config константы в `lesson-runner/domain/.../config/TimerCoefficients.kt`.
22. **FLAG_SECURE**: enabled только в HARD-mode runner (toggle on `RunnerState.Ready` enter с mode=HARD; untoggle on `Completed/Aborted/SaveFailed/InitFailed` exit).
23. **Attempt immutability** — после save Attempt в Room не редактируется (только создание новых).
24. **Rating uniqueness — lifetime per (userId, lessonId)** (упрощено для consistency):
    - Local Room PK: compound `(userId, lessonId)` в таблице `lesson_rating_submitted_local`. Один раз поставил → больше не предлагается.
    - Remote ID: deterministic `sha256("$userId:$lessonId")` (БЕЗ lessonVersion в ключе). Cloud Function dedupe через document-id collision (existing → ignore new).
    - `lessonVersion` сохраняется в payload документа `lesson_ratings` для server-side analytics (понимать какая версия оценивалась), но НЕ участвует в uniqueness key.
    - Если автор кардинально поменяет урок и захочет «обнулить» все оценки — это server-side policy (отдельная задача, не наша фича).
25. **lessonVersion в `LessonRating` payload** — analytics поле, не uniqueness key. Фиксируется на момент submit.

### Repository Interfaces (фиксируется явно для Phase 3.8)

```kotlin
// shared/feature/lesson-runner/domain/src/commonMain/.../repository/LessonAttemptRepository.kt
interface LessonAttemptRepository {
    suspend fun save(attempt: Attempt): Result<Unit>
    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>
    fun observeAllByUser(userId: String): Flow<List<Attempt>>
}

// shared/feature/lesson-runner/domain/.../repository/LessonRatingRepository.kt
interface LessonRatingRepository {
    suspend fun submit(rating: LessonRating): Result<Unit>
    fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean>
    // Implementation: Room query на `lesson_rating_submitted_local` PK (userId, lessonId).
}

// shared/core/question-schema/.../parser/QuestionContentParser.kt
// Импортируется domain-ом; реализация (kotlinx.serialization) в data source set question-schema
interface QuestionContentParser {
    fun parse(payload: String): Result<QuestionContent>
}

// Existing dependencies используются:
// - shared/feature/app-shell/domain/.../AuthRepository.kt: suspend fun currentUid(): String?
//   StartLessonAttemptUseCase: если null → InitFailed(AuthRequired)
// - shared/feature/lesson/domain/.../LessonRepository.kt: suspend fun getById(LessonId): Lesson? (existing :26)
//   StartLessonAttemptUseCase использует suspend getById для snapshot lesson.version. Если null → InitFailed(LessonNotFound).
//   Result screen использует suspend getById ещё раз перед показом для свежего top3 / averageRating.
// - shared/feature/question/domain/.../QuestionRepository.kt: fun observeByLesson(LessonId): Flow<List<Question>>
//   Используется для одноразового read через .first() в StartLessonAttemptUseCase (snapshot вопросов).
```

### Use Cases (фиксируется явно)

```kotlin
// shared/feature/lesson-runner/domain/src/commonMain/.../use_case/

class StartLessonAttemptUseCase(
    private val questionRepository: QuestionRepository,
    private val lessonRepository: LessonRepository,
    private val parser: QuestionContentParser,
    private val authRepository: AuthRepository,
    private val clock: Clock,                  // injectable для тестируемости
    private val randomSeedProvider: () -> Long, // injectable для tests
) {
    suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState
    // returns Ready, InitFailed(EmptyPool), InitFailed(NoValidQuestions),
    //         InitFailed(LessonNotFound) если LessonRepository.getById вернул null,
    //         InitFailed(AuthRequired) если authRepository.currentUid() == null.
    // НЕ throws; все errors через RunnerState.InitFailed states.
}

class CompleteAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val ratingRepository: LessonRatingRepository,
    private val clock: Clock,
    private val attemptIdProvider: () -> AttemptId,  // injectable для deterministic tests
) {
    // userId берётся из state.userId (snapshot на старте), НЕ повторно читается из AuthRepository.
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState
    // returns Completed(attempt, ratingPrompt) or SaveFailed(attempt, error)
    // ratingPrompt = state.codeAnswer.allShownAnswersAre9 && !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()
}

class AbortAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val clock: Clock,
    private val attemptIdProvider: () -> AttemptId,
) {
    // userId из state.userId; auth read не выполняется.
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState
    // fills unanswered subset positions with '1', out-of-subset already '0'; saves
    // returns Aborted(attempt) or SaveFailed(attempt, error)
}

class SubmitLessonRatingUseCase(
    private val ratingRepository: LessonRatingRepository,
    private val lessonRepository: LessonRepository,
    private val clock: Clock,
    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
) {
    // userId берётся как explicit param — обычно `state.userId` из завершённой попытки (передаётся
    // result screen-ом). Это избегает race condition с auth read в момент тапа кнопки rating.
    suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit>
}

// Pure functions in shared/feature/lesson-runner/domain/.../logic/ (sync, no suspend, no Flow).
// Возвращают только RunnerState.Ready для not-final transitions; CompleteAttemptUseCase invoked
// imperative-shell-side когда state.indexInPool == state.playOrder.size - 1 (= был последний).

// `submitAnswer` записывает score за текущий вопрос и продвигает indexInPool.
// Если был последний вопрос → возвращает Ready с признаком complete (indexInPool == playOrder.size).
// Component/imperative shell проверяет этот признак и вызывает CompleteAttemptUseCase.
fun submitAnswer(state: RunnerState.Ready, answer: UserAnswer, nowMs: Long): RunnerState.Ready

// Auto-random: генерирует UserAnswer и делегирует submitAnswer.
fun autoAnswerOnTimeout(state: RunnerState.Ready, randomSeed: Long, nowMs: Long): RunnerState.Ready

fun evaluateAnswer(content: QuestionContent, answer: UserAnswer): Score  // returns Score(1..9)
fun computeStars(percentScore: PercentScore, mode: Difficulty): Stars
fun computeBestStars(attempts: List<Attempt>): Stars
fun computeHardUnlocked(attempts: List<Attempt>): Boolean  // string-based via codeAnswer.allShownAnswersAre9
fun computeTimer(content: QuestionContent, mode: Difficulty, coefficients: TimerCoefficients): TimerDuration
fun selectSubset(eligible: List<RunnerQuestion.Valid>, poolSize: Int, seed: Long): List<RunnerQuestion.Valid>
fun buildCodeAnswerOnAbort(state: RunnerState.Ready): CodeAnswer  // unanswered subset → '1'

// Helper extension on CodeAnswer:
val CodeAnswer.allShownAnswersAre9: Boolean
    get() = raw.all { it == '0' || it == '9' } && raw.any { it == '9' }
```

### State / Decision Rules

См. State Matrix (Matrix 1-8) ниже.

### Error / Recovery Rules

1. **Empty pool** (`eligibleQuestions(mode).isEmpty()`) → `RunnerState.InitFailed(EmptyPool)` → empty state в UI с кнопкой Назад.
2. **All payloads invalid** (`pool.all { parser.parse(it.payload).isFailure }`) → `InitFailed(NoValidQuestions)` → empty state.
3. **Invalid payloads** исключаются на init (canonical pipeline шаг 3 в Business Rule 2). До прохождения не доходят. Старая wording «single invalid reached → score '1'» устарела (см. Resolved Decisions).
4. **Process kill** → попытка теряется, no save в Room.
5. **Configuration change** → state preserved через `instanceKeeper` (включая seed, draft answer, deadline).
6. **Сворачивание (`onStop`)** → текущий вопрос auto-random scored через `autoAnswerOnTimeout`, state.isPaused=true, таймер останавливается. На `onResume` — UI показывает блокирующий диалог.
7. **Save attempt failure** (Room IO error в `CompleteAttemptUseCase`/`AbortAttemptUseCase`) → `RunnerState.SaveFailed(attempt, error)`. UI показывает result screen с warning «Не удалось сохранить, попробуйте позже». No auto-retry в MVP. Attempt лост; пользователь может попробовать пройти урок ещё раз. — [DELEGATED]
8. **Submit rating failure** → `Result.failure` от `SubmitLessonRatingUseCase`. UI показывает toast «Не удалось отправить оценку». No retry. Local флаг НЕ установлен → user может попробовать снова. — [DELEGATED]
9. **Auth uid null** в момент start/save → `Result.failure(AuthRequired)`. UI должен закрыть runner и направить на login (это infrastructure concern; не должно происходить с anonymous Auth, но guard на всякий случай).

### Domain Test Scenarios (для Walking Skeleton, Phase 3.8)

#### Score formula tests

1. GIVEN SingleChoice с `correctOptionId=A`, options=[A,B,C,D] WHEN `selected=A` THEN `digit = '9'`
2. GIVEN SingleChoice с `correctOptionId=A` WHEN `selected=B` THEN `digit = '1'`
3. GIVEN MultipleChoice с `correctOptionIds={A,B,C}` (3 верных), 5 options всего WHEN `selected={A,B}` (2 верных, 0 wrong, 1 missed) THEN `correct_share = 2/(2+0+1) = 0.667` → `digit = round(0.667*8)+1 = round(5.33+1) = 6`
4. GIVEN MultipleChoice WHEN `selected={A,B,C}` (все верных) THEN `digit = '9'`
5. GIVEN MultipleChoice с `correctOptionIds={A,B}` WHEN `selected={C,D}` (0 верных, 2 wrong, 2 missed) THEN `correct_share = 0/4 = 0` → `digit = '1'`
6. GIVEN MultipleChoice WHEN `selected={}` (ничего не выбрал) THEN `correct_share = 0` → `digit = '1'`
7. GIVEN Ordering с правильным порядком [A,B,C,D] WHEN `userOrder = [A,B,C,D]` THEN `digit = '9'`
8. GIVEN Ordering [A,B,C,D] WHEN `userOrder = [A,C,B,D]` (2 совпадений: A в [0], D в [3]) THEN `correct_share = 2/4 = 0.5` → `digit = '5'`
9. GIVEN Ordering [A,B,C,D] WHEN `userOrder = [D,C,B,A]` THEN `correct_share = 0/4 = 0` → `digit = '1'`
10. GIVEN FillBlank 3 blanks WHEN все 3 заполнены верно THEN `digit = '9'`
11. GIVEN FillBlank 3 blanks WHEN 1 верный THEN `correct_share = 1/3 ≈ 0.333` → `digit = round(0.333*8)+1 = round(2.667)+1 = 4`
12. GIVEN FillBlank 1 blank WHEN неверный THEN `digit = '1'`

#### CodeAnswer construction tests

13. GIVEN lesson с 5 EASY questions, EASY-попытка с pool size const = 20 WHEN сделана попытка THEN `codeAnswer.length == 5` и нет '0' (все 5 показаны)
14. GIVEN lesson с 50 EASY questions, EASY-попытка WHEN сделана попытка THEN `codeAnswer.length == 50`, ровно 20 цифр в `'1'..'9'`, остальные 30 = `'0'`
15. GIVEN lesson с 50 EASY + 50 HARD, EASY-попытка completed all 9 WHEN save attempt THEN `codeAnswer.length == 50` (только EASY), `mode == EASY`
16. GIVEN HARD-попытка для того же lesson THEN `codeAnswer.length == 50` (только HARD), `mode == HARD`

#### PercentScore tests

17. GIVEN codeAnswer = "9999" (4 digits, all '9') THEN `percentScore = 100`
18. GIVEN codeAnswer = "5555" THEN `percentScore = 50` (((5-1)/8)*100 = 50, average = 50)
19. GIVEN codeAnswer = "9050" (длина 4, два '9', два '0') THEN `percentScore = ((9-1)/8 + (5-1)/8) / 2 × 100 = (1.0 + 0.5) / 2 × 100 = 75`
20. GIVEN codeAnswer = "1111" (4 digits all '1' = 0%) THEN `percentScore = 0`

#### Stars formula tests

21. GIVEN EASY attempt с `percentScore=100` THEN `Stars(rawTenths=20)` (UI 2.0)
22. GIVEN EASY attempt с `percentScore=50` THEN `Stars(rawTenths=10)` (UI 1.0); вычисление: `(50*20+50)/100 = 10`
23. GIVEN EASY attempt с `percentScore=75` THEN `Stars(rawTenths=15)` (UI 1.5)
24. GIVEN EASY attempt с `percentScore=33` THEN `Stars(rawTenths=7)` (UI 0.7); вычисление: `(33*20+50)/100 = 710/100 = 7`
25. GIVEN EASY attempt с `percentScore=0` THEN `Stars(rawTenths=0)` (UI 0.0)
26. GIVEN HARD attempt с `percentScore=100` THEN `Stars(rawTenths=30)` (UI 3.0)
27. GIVEN HARD attempt с `percentScore=50` THEN `Stars(rawTenths=25)` (UI 2.5); вычисление: `20 + (50*10+50)/100 = 20+5 = 25`
28. GIVEN HARD attempt с `percentScore=80` THEN `Stars(rawTenths=28)` (UI 2.8)
29. GIVEN HARD attempt с `percentScore=0` THEN `Stars(rawTenths=20)` (UI 2.0; HARD floor)


exec
/bin/bash -lc "sed -n '946,1033p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## State Matrix

### Matrix 1: Score 0-9 за один ответ по типу

| Тип | Полностью верно (digit=9) | Частично верно (2..8) | Полностью неверно / timeout (digit=1) |
|-----|---------------------------|------------------------|---------------------------------------|
| SingleChoice | `selected == correctOptionId` | N/A | `selected != correctOptionId` или selected=null |
| MultipleChoice | `picked == correctOptionIds && picked.size == correctOptionIds.size` | Jaccard formula | `correct_picked == 0` (никаких пересечений) |
| Ordering | все позиции совпали | `matched_positions / total × 8 + 1` | `matched_positions == 0` |
| FillBlank | все blanks верны | `filled_correct / total × 8 + 1` | ни один blank не угадан |

Не показанные в этой попытке (out of subset) → `'0'`.

### Matrix 2: Stars per attempt formula

| Mode | percentScore | rawTenths | UI display |
|------|-------------|-----------|------------|
| EASY | 0 | 0 | 0.0 |
| EASY | 50 | 10 | 1.0 |
| EASY | 75 | 15 | 1.5 |
| EASY | 100 | 20 | 2.0 |
| HARD | 0 | 20 | 2.0 |
| HARD | 50 | 25 | 2.5 |
| HARD | 80 | 28 | 2.8 |
| HARD | 100 | 30 | 3.0 |

Формулы (integer math): `EASY: rawTenths = (percentScore * 20 + 50) / 100`; `HARD: rawTenths = 20 + (percentScore * 10 + 50) / 100`. Round half up через `+ 50` (constant).

### Matrix 3: bestStars per lesson (max по своим попыткам)

| Состояние истории своих попыток | bestStars | hardUnlocked |
|---------------------------------|-----------|--------------|
| Нет попыток | Stars(0) | false |
| Все EASY попытки с percentScore=0 | Stars(0) | false |
| Хотя бы одна EASY попытка с percentScore > 0 без allShownAnswersAre9 | Stars(rawTenths ∈ [1..20]; near-perfect rounding может дать 20) | **false** |
| Хотя бы одна EASY с allShownAnswersAre9=true | Stars(rawTenths ≥ 20) | **true** |
| Хотя бы одна HARD попытка (всегда требует prior unlock) | Stars(rawTenths ≥ 20) | true (унаследовано из EASY perfect) |

`hardUnlocked` зависит **только от** наличия EASY-попытки с `allShownAnswersAre9`. HARD attempt ничего не unlocks (он сам возможен только когда уже unlocked).

### Matrix 4: Когда писать attempt в Room

Унифицированная семантика abort: **all unanswered questions внутри selected subset get `'1'`; out-of-subset (в eligibleQuestions, но не в subset) get `'0'`** (`'0'` уже инициализировано при создании codeAnswer). Mode совпадает с mode попытки.

| Событие | Записать? | Что в `codeAnswer` | mode |
|---------|-----------|---------------------|------|
| Полное прохождение (все subset позиции отвечены) | Да | scores `'1'..'9'` per subset position; `'0'` для out-of-subset | EASY/HARD (как у попытки) |
| Exit через onResume диалог «Выйти» | Да | отвеченные subset = `'1'..'9'`; неотвеченные subset = `'1'`; out-of-subset = `'0'` | EASY/HARD |
| Крестик во время прохождения → подтвердил «Уверен?» | Да | то же что Exit | EASY/HARD |
| Save IO error | Нет → `SaveFailed` state | (попытка не записана) | EASY/HARD |
| Process kill | Нет | (попытка теряется) | — |
| Configuration change (rotation) | Нет | (state preserved через instanceKeeper) | — |

### Matrix 5: Rating prompt visibility

| Условия | Показать? |
|---------|-----------|
| `attempt.codeAnswer.allShownAnswersAre9 == true` AND `!ratingsRepo.hasSubmitted(userId, lessonId).first()` | Да |
| `attempt.codeAnswer.allShownAnswersAre9 == true` AND `ratingsRepo.hasSubmitted(userId, lessonId).first()` | Нет |
| `attempt.allShownAnswersAre9 == false` | Нет |

### Matrix 6: Sworn-fold / onResume / abort

| Событие | Текущий вопрос | UI после возврата |
|---------|----------------|--------------------|
| onStop | auto-random scored, in-memory state updated | таймер остановлен |
| onResume | — | блокирующий диалог «Продолжить?» |
| Диалог «Продолжить» | — | следующий вопрос, новый таймер; FLAG_SECURE остаётся (если HARD) |
| Диалог «Выйти» | — | save attempt → возврат в `LessonListComponent` |
| Process kill (без onStop первым? OOM) | (state lost) | (запуск с нуля) |

### Matrix 7: Timer per вопрос

| Mode | Коэффициент | charsCount=100 (sec) | charsCount=165 (sec) | charsCount=300 (sec) |
|------|-------------|---------------------|----------------------|----------------------|
| EASY | k_easy ≈ 0.18 | 18 | 30 | 54 |
| HARD | k_hard ≈ 0.12 | 12 | 20 | 36 |

### Matrix 8: Pool selection

| eligibleQuestions(mode).size | pool (subset) size | codeAnswer length | non-zero positions |
|------------------------------|---------------------|-------------------|--------------------|
| 0 | — | — | InitFailed(EmptyPool) |
| 5 | 5 (все) | 5 | 5 |
| 20 | 20 | 20 | 20 |
| 50 | 20 (random subset) | 50 | 20 (другие 30 = '0') |
| 100 | 20 | 100 | 20 (другие 80 = '0') |


exec
/bin/bash -lc "rg -n \"lesson-runner|lesson_runner|lessonRunner|LessonRunner|DifficultyConverter|RunnerUiState\\.Result|TopParticipant\" docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
exec
/bin/bash -lc "rg -n \"lessonRunner|LessonRunner|questionSchemaModule|lessonRunnerDataModule|lessonRunnerDomainKoinAdapter|LessonRunnerComponentFactory|DifficultyConverter|lesson_runner|quizzes_screen|RunnerUiState\\.Result|data class Result|FLAG_SECURE|setFlags|clearFlags\" apps android shared platform -g '*.kt' -g '*.kts'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:31:import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:35:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:36:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:118:                questionSchemaModule,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:119:                lessonRunnerDataModule,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:120:                lessonRunnerDomainKoinAdapter,
apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:121:                lessonRunnerPresentationModule,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeClock.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionContentParser.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeAuthRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:17:val lessonRunnerPresentationModule =
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:20:            DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:25:                    LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:34:            ) as LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:37:        single<LessonRunnerComponentFactory> {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:38:            LessonRunnerComponentFactory { ctx, lessonId, mode ->
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:9:interface LessonRunnerRootComponent {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:20:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:21:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:22:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:23:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:45:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:49:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:50:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:51:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:52:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:53:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:61:import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:62:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:63:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:64:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:65:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:66:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:67:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:68:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:69:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:70:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:212:        single<LessonRunnerComponentFactory> {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:213:            LessonRunnerComponentFactory { _, _, _ -> error("Not wired in KoinModuleWiringTest") }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:482:     * IT-09a: GIVEN lessonRunnerDataModule WHEN get<LessonAttemptRepository>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:487:    fun `it09a lessonAttemptRepository resolves from lessonRunnerDataModule`() {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:489:            modules(testLessonAttemptDaoStub, testLessonRatingLocalDaoStub, lessonRunnerDataModule)
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:497:     * IT-09b: GIVEN lessonRunnerDataModule WHEN get<LessonRatingRepository>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:502:    fun `it09b lessonRatingRepository resolves from lessonRunnerDataModule`() {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:504:            modules(testLessonAttemptDaoStub, testLessonRatingLocalDaoStub, lessonRunnerDataModule)
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:512:     * IT-09c: GIVEN lessonRunnerDataModule WHEN get<AttemptIdProvider> / RandomSeedProvider / RatingIdProvider
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:518:        startKoin { modules(lessonRunnerDataModule) }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:527:     * IT-09d: GIVEN lessonRunnerDomainKoinAdapter WHEN get<CompleteAttemptUseCase> / AbortAttemptUseCase / SubmitLessonRatingUseCase
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:538:                lessonRunnerDataModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:539:                lessonRunnerDomainKoinAdapter,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:540:                questionSchemaModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:551:     * IT-09e: GIVEN lessonRunnerPresentationModule + deps WHEN get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), EASY))
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:552:     * THEN resolves to DefaultLessonRunnerRootComponent without exception.
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:556:    fun `it09e lessonRunnerRootComponent resolves with parametersOf ctx lessonId mode`() {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:566:                lessonRunnerDataModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:567:                lessonRunnerDomainKoinAdapter,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:568:                lessonRunnerPresentationModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:569:                questionSchemaModule,
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:574:            val component = getKoin().get<LessonRunnerRootComponent> {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:578:            assertTrue(component is DefaultLessonRunnerRootComponent)
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:588:     * IT-09f: GIVEN questionSchemaModule WHEN get<QuestionContentParser>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:594:        startKoin { modules(questionSchemaModule) }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:605:     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:620:     * IT-09h: GIVEN lessonRunnerPresentationModule WHEN get<LessonRunnerComponentFactory>()
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:625:    fun `it09h lessonRunnerComponentFactory resolves as single binding`() {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:626:        startKoin { modules(lessonRunnerPresentationModule) }
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:627:        val factory = getKoin().get<LessonRunnerComponentFactory>()
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:12:    ): LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:7:class LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toEventSaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUiReason
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:21:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:22:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:23:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:24:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:36:class DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:40:    private val useCases: LessonRunnerUseCases,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:45:) : ComponentContext by componentContext, LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:210:    ): RunnerUiState.Result {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:220:        return RunnerUiState.Result(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.evaluateAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:24:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:25:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:26:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/BestStarsHardUnlockedTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:22:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:30:    state: RunnerUiState.Result,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:96:                RunnerUiState.Result(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:127:                RunnerUiState.Result(
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TimerComputeTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:23:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:24:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:27:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:28:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:29:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:30:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:33:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:46:        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:48:            if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:55:fun LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:56:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:67:            is RunnerUiState.Result -> s.mode == Difficulty.HARD
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:105:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:122:        is RunnerUiState.Result ->
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:149:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:183:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:271:private fun LessonRunnerScreenLoadingPreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:273:        LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:274:            component = PreviewLessonRunnerComponent(RunnerUiState.Loading),
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:284:private fun LessonRunnerScreenInitFailedPreview() {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:286:        LessonRunnerScreen(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:288:                PreviewLessonRunnerComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:297:private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:21:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:22:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:20:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:21:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt:13: * Note: MT-05 (DifficultyConverter) removed per ADR-LR-18 — converter deleted, mapper handles Difficulty↔Int.
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeBackDispatcher
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:16:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:18:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:19:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:20:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:21:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:78:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:20:                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.SaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError as DomainSaveError
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toDrillItem
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeAuthRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation.QuizzesNavigator
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeBackDispatcher.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toDrillItem
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Score
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:15:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:16:    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeStackNavigation.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyItemUi.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toQuestDisplayItem
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt:4:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.di
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:14: * LessonRunnerDomainKoinAdapter (data/androidMain) bridges providers → use case function types.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:15: * This module is NOT registered in production — use lessonRunnerDomainKoinAdapter instead (ADR-LR-09 C1).
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:17:val lessonRunnerDomainModule = module {
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ThemeListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeThemeListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt:4:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt:4:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:24:    data class Result(
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:15:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:37:    private val lessonRunnerFactory: LessonRunnerComponentFactory,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:101:                is QuizzesConfig.LessonRunner -> cfg.titles.size
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:155:            is QuizzesConfig.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:156:                QuizzesChild.LessonRunner(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:157:                    lessonRunnerFactory.create(ctx, LessonId(config.lessonId), config.mode),
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:78:            QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:139:     * Spec: LL-U-04 — onLessonClick with hardUnlocked=false pushes LessonRunner with EASY mode.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:143:    fun `onLessonClick pushes LessonRunner with EASY mode when hardUnlocked is false`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:150:        assertIs<QuizzesConfig.LessonRunner>(pushed)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:160:    fun `onLessonClick pushes LessonRunner with HARD mode when hardUnlocked and isHardChecked`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:166:        val pushed = fakeNavigation.pushedConfigs.last() as QuizzesConfig.LessonRunner
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:8:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeStackNavigation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt:37:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt:38:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:5:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.SectionListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:6:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeSectionListComponent.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:25:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.SectionListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt:27:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:111:    // ── SER-06 — QuizzesConfig.LessonRunner ──────────────────────────────────
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:114:     * Spec: SER-06 — LessonRunner preserves lessonId, mode, and titles.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:116:     * Also covers: Ser-02 (tests.md §Ser-02) — LessonRunner EASY mode round-trip.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:119:    fun `QuizzesConfig LessonRunner round-trip preserves lessonId and mode`() {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:120:        val original = QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:127:        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:132:    // ── Ser-01 (Phase-06) — LessonRunner HARD round-trip ────────────────────────
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:135:     * Phase-06 Ser-01: LessonRunner(mode=HARD) preserves mode after round-trip.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:139:    fun `QuizzesConfig LessonRunner HARD mode round-trip`() {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:140:        val original = QuizzesConfig.LessonRunner(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt:147:        val runner = assertIs<QuizzesConfig.LessonRunner>(decoded)
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:24:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:25:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:25:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ThemeListComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt:27:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/DrillItemMapperTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt:3:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:20:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapperTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Score.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:18:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:42:            is QuizzesChild.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:43:                LessonRunnerScreen(
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:15:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:16:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:17:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:18:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:19:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:86:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/lesson-runner/presentation/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.android.feature.lesson_runner.presentation"
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswerDraft.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt:26:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestCardMenuTest.kt:27:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/CodeAnswer.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/SaveError.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreenTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswer.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:36:    data class LessonRunner(
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt:10:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesRotationTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuizzesComponent
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerDuration.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:1:package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:11:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionListComponent
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreenTest.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Stars.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/quizzes-screen/presentation/build.gradle.kts:7:    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeClock.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionContentParser.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:16:) : LessonRunnerRootComponent {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRatingRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerCoefficients.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAttemptFixtures.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/InitFailureReason.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAuthRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.RunFakeComponent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:29:class LessonRunnerScreenTest {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:69:    ) = RunnerUiState.Result(
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:94:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:113:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:131:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:146:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:162:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:182:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:190:    // --- CT-11: GIVEN hard Question WHEN rendered THEN FLAG_SECURE set in window ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:200:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:206:        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:209:    // --- CT-12: GIVEN hard state transitions to Loading WHEN rendered THEN FLAG_SECURE cleared ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:218:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:223:        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:228:        assertEquals(0, composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:231:    // --- CT-13: GIVEN easy Question WHEN rendered THEN FLAG_SECURE NOT set ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:241:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:246:        assertEquals(0, composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:264:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:289:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:306:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:325:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:343:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:358:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:374:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:391:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:400:    @Ignore("Phase-06 scope: belongs to LessonItemCard, not LessonRunnerScreen")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:430:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:447:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:462:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:478:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:489:    // --- CT-29: GIVEN hard mode rendered WHEN activity recreated THEN FLAG_SECURE still set ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:499:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:504:        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:512:                LessonRunnerScreen(fakeComponent, onNavigateBack = {}, onSegmentClick = {})
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:517:        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:529:                LessonRunnerScreen(fakeComponent, onNavigateBack = { navCalled = true }, onSegmentClick = {})
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeStartLessonAttemptUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeStartLessonAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeQuestionRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RandomSeedProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeSubmitLessonRatingUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
shared/feature/lesson-runner/domain/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.domain"
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAbortAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAttemptFixtures
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeClock
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeCompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonAttemptRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRatingRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:17:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeStartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:18:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:26:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:27:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:28:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:29:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:30:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:31:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:32:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:33:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:34:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:35:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:53: * Presentation unit tests for [DefaultLessonRunnerRootComponent].
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:59: * Open Question: RunnerUiState.Result is expected to contain bestStarsRawTenths: Int
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:64:class DefaultLessonRunnerRootComponentTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:107:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:111:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:115:            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:207:    private fun advanceToResultState(component: DefaultLessonRunnerRootComponent) {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:277:        assertIs<RunnerUiState.Result>(component.uiState.value)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:313:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:334:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:353:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:370:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:379:    // Open Question: RunnerUiState.Result must have bestStarsRawTenths: Int and hardUnlocked: Boolean
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:389:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:414:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:437:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:711:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:729:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:747:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:764:        assertIs<RunnerUiState.Result>(component.uiState.value)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:789:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:815:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:834:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:860:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:924:        val result = component.uiState.value as RunnerUiState.Result
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:943:        assertIs<RunnerUiState.Result>(component.uiState.value)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeCompleteAttemptUseCase.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAbortAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeAttemptFixtures
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeClock
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeCompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonAttemptRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRatingRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeLessonRepository
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:17:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeStartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:18:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake.FakeSubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:25:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:26:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:27:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:28:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:41: * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:51:class LessonRunnerIntegrationTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:98:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:106:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:110:            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAbortAttemptUseCase.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeAbortAttemptUseCase.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/FakeLessonAttemptRepository.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:22:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonAttemptDao.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/fake/FakeLessonRatingLocalDao.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonRatingLocalDao
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonRatingRepositoryImplTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/AttemptMapperTest.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/LessonAttemptRepositoryImplTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:55:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:56:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.QuizzesScreen
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRandomSeedProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultAttemptIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/data/src/jvmAndAndroidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/provider/DefaultRatingIdProvider.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt:12:val lessonRunnerDomainKoinAdapter = module {
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/data/build.gradle.kts:6:    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.data"
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt:16:val lessonRunnerDataModule = module {
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt:9:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:12:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:13:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt:14:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:1:package com.tpov.schoolquiz.shared.feature.lesson_runner.data
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/DefaultProvidersTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:28:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:29:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:30:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:31:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:32:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:33:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:40:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:45:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:46:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:269:                lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:35:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:36:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt:37:import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt:7:val questionSchemaModule = module {

exec
/bin/bash -lc "nl -ba apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt | sed -n '1,170p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt | sed -n '1,140p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.apps.android_next
     2	
     3	import android.app.Application
     4	import androidx.work.Configuration
     5	import androidx.work.Constraints
     6	import androidx.work.ExistingPeriodicWorkPolicy
     7	import androidx.work.ExistingWorkPolicy
     8	import androidx.work.NetworkType
     9	import androidx.work.OneTimeWorkRequestBuilder
    10	import androidx.work.PeriodicWorkRequestBuilder
    11	import androidx.work.WorkManager
    12	import androidx.work.WorkerFactory
    13	import com.google.firebase.auth.FirebaseAuth
    14	import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
    15	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
    16	import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
    17	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
    18	import com.tpov.schoolquiz.apps.android_next.di.syncModule
    19	import com.tpov.schoolquiz.platform.android_services.sync.SyncWorker
    20	import com.tpov.schoolquiz.platform.firebase.di.firebaseCatalogModule
    21	import com.tpov.schoolquiz.platform.firebase.di.firebaseLessonModule
    22	import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
    23	import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestModule
    24	import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestionModule
    25	import com.tpov.schoolquiz.platform.firebase.di.firebaseSectionModule
    26	import com.tpov.schoolquiz.platform.firebase.di.firebaseThemeModule
    27	import com.tpov.schoolquiz.platform.firebase.initializeFirebaseSecurity
    28	import com.tpov.schoolquiz.shared.core.catalog.data.di.catalogDataModule
    29	import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
    30	import com.tpov.schoolquiz.shared.core.persistence.di.persistenceModule
    31	import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
    32	import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
    33	import com.tpov.schoolquiz.shared.feature.lesson.data.di.lessonDataModule
    34	import com.tpov.schoolquiz.shared.feature.lesson.domain.di.lessonDomainModule
    35	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
    36	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
    37	import com.tpov.schoolquiz.shared.feature.quest.data.di.questDataModule
    38	import com.tpov.schoolquiz.shared.feature.quest.domain.di.questDomainModule
    39	import com.tpov.schoolquiz.shared.feature.question.data.di.questionDataModule
    40	import com.tpov.schoolquiz.shared.feature.question.domain.di.questionDomainModule
    41	import com.tpov.schoolquiz.shared.feature.section.data.di.sectionDataModule
    42	import com.tpov.schoolquiz.shared.feature.section.domain.di.sectionDomainModule
    43	import com.tpov.schoolquiz.shared.feature.theme.data.di.themeDataModule
    44	import com.tpov.schoolquiz.shared.feature.theme.domain.di.themeDomainModule
    45	import kotlinx.coroutines.CoroutineScope
    46	import kotlinx.coroutines.Dispatchers
    47	import kotlinx.coroutines.SupervisorJob
    48	import kotlinx.coroutines.channels.awaitClose
    49	import kotlinx.coroutines.flow.SharingStarted
    50	import kotlinx.coroutines.flow.callbackFlow
    51	import kotlinx.coroutines.flow.shareIn
    52	import org.koin.android.ext.koin.androidContext
    53	import org.koin.core.context.GlobalContext
    54	import org.koin.core.context.startKoin
    55	
    56	class AppApplication : Application(), Configuration.Provider {
    57	    /**
    58	     * Application-scoped CoroutineScope for shared hot Flows (auth UID, etc.).
    59	     * Survives the entire process lifetime. Dispatchers.Default — non-blocking work
    60	     * (Firebase auth listener trampolines internally; explicit dispatcher avoids
    61	     * relying on kotlinx-coroutines fallback semantics).
    62	     */
    63	    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    64	
    65	    override val workManagerConfiguration: Configuration
    66	        get() =
    67	            Configuration.Builder()
    68	                .setWorkerFactory(GlobalContext.get().get<WorkerFactory>())
    69	                .build()
    70	
    71	    override fun onCreate() {
    72	        super.onCreate()
    73	        initializeFirebaseSecurity(this)
    74	        val auth = FirebaseAuth.getInstance()
    75	        // Cold callbackFlow: each collect would register a new AuthStateListener.
    76	        val coldAuthUidFlow =
    77	            callbackFlow<String?> {
    78	                val listener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser?.uid) }
    79	                auth.addAuthStateListener(listener)
    80	                awaitClose { auth.removeAuthStateListener(listener) }
    81	            }
    82	        // Codex Round 4 N2 fix: shareIn(WhileSubscribed) → single AuthStateListener
    83	        // shared across all consumers (UserStatsRepositoryImpl + AuthRepositoryImpl + future
    84	        // MyQuestsViewModel). Replay 1 ensures late subscribers get the current UID immediately.
    85	        val sharedAuthUidFlow =
    86	            coldAuthUidFlow.shareIn(
    87	                scope = appScope,
    88	                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
    89	                replay = 1,
    90	            )
    91	        startKoin {
    92	            androidContext(this@AppApplication)
    93	            modules(
    94	                persistenceModule,
    95	                firebaseModule,
    96	                firebaseCatalogModule,
    97	                firebaseQuestModule,
    98	                firebaseSectionModule,
    99	                firebaseThemeModule,
   100	                firebaseLessonModule,
   101	                firebaseQuestionModule,
   102	                appShellDataModule { sharedAuthUidFlow },
   103	                appShellPresentationModule,
   104	                questPresentationModule,
   105	                quizzesPresentationModule,
   106	                catalogDataModule,
   107	                catalogDomainModule,
   108	                questDataModule,
   109	                questDomainModule,
   110	                sectionDataModule,
   111	                sectionDomainModule,
   112	                themeDataModule,
   113	                themeDomainModule,
   114	                lessonDataModule,
   115	                lessonDomainModule,
   116	                questionDataModule,
   117	                questionDomainModule,
   118	                questionSchemaModule,
   119	                lessonRunnerDataModule,
   120	                lessonRunnerDomainKoinAdapter,
   121	                lessonRunnerPresentationModule,
   122	                syncModule,
   123	            )
   124	        }
   125	        val workManager = WorkManager.getInstance(this)
   126	        workManager.enqueueUniquePeriodicWork(
   127	            SyncWorker.WORK_NAME_PERIODIC,
   128	            ExistingPeriodicWorkPolicy.KEEP,
   129	            PeriodicWorkRequestBuilder<SyncWorker>(
   130	                SyncWorker.PERIODIC_INTERVAL.first,
   131	                SyncWorker.PERIODIC_INTERVAL.second,
   132	            )
   133	                .setConstraints(
   134	                    Constraints.Builder()
   135	                        .setRequiredNetworkType(NetworkType.CONNECTED)
   136	                        .build(),
   137	                )
   138	                .build(),
   139	        )
   140	        // Bootstrap one-shot sync: runs ONCE after install (first-ever launch).
   141	        // Subsequent sync is handled by periodic WorkManager schedule + manual Sync Now.
   142	        val prefs = getSharedPreferences("sync_state", MODE_PRIVATE)
   143	        if (!prefs.getBoolean("bootstrap_done", false)) {
   144	            workManager.enqueueUniqueWork(
   145	                SyncWorker.WORK_NAME_BOOTSTRAP,
   146	                ExistingWorkPolicy.REPLACE,
   147	                OneTimeWorkRequestBuilder<SyncWorker>()
   148	                    .setConstraints(
   149	                        Constraints.Builder()
   150	                            .setRequiredNetworkType(NetworkType.CONNECTED)
   151	                            .build(),
   152	                    )
   153	                    .build(),
   154	            )
   155	            prefs.edit().putBoolean("bootstrap_done", true).apply()
   156	        }
   157	    }
   158	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
     5	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
     6	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
     7	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
     8	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     9	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
    14	import org.koin.core.parameter.parametersOf
    15	import org.koin.dsl.module
    16	
    17	val lessonRunnerPresentationModule =
    18	    module {
    19	        factory { (ctx: ComponentContext, lessonId: LessonId, mode: Difficulty) ->
    20	            DefaultLessonRunnerRootComponent(
    21	                componentContext = ctx,
    22	                lessonId = lessonId,
    23	                mode = mode,
    24	                useCases =
    25	                    LessonRunnerUseCases(
    26	                        startAttempt = get<StartLessonAttemptUseCase>()::invoke,
    27	                        completeAttempt = get<CompleteAttemptUseCase>()::invoke,
    28	                        abortAttempt = get<AbortAttemptUseCase>()::invoke,
    29	                        submitRating = get<SubmitLessonRatingUseCase>()::invoke,
    30	                    ),
    31	                lessonRepository = get(),
    32	                attemptRepository = get(),
    33	                clock = get(),
    34	            ) as LessonRunnerRootComponent
    35	        }
    36	
    37	        single<LessonRunnerComponentFactory> {
    38	            LessonRunnerComponentFactory { ctx, lessonId, mode ->
    39	                get { parametersOf(ctx, lessonId, mode) }
    40	            }
    41	        }
    42	    }

exec
/bin/bash -lc "nl -ba shared/core/question-schema/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/di/QuestionSchemaModule.kt | sed -n '1,120p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.question_schema.di
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
     4	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
     5	import org.koin.dsl.module
     6	
     7	val questionSchemaModule = module {
     8	    single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }
     9	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
    13	import kotlinx.datetime.Clock
    14	import org.koin.dsl.module
    15	
    16	val lessonRunnerDataModule = module {
    17	    single<AttemptIdProvider> { DefaultAttemptIdProvider() }
    18	    single<RandomSeedProvider> { DefaultRandomSeedProvider() }
    19	    single<RatingIdProvider> { DefaultRatingIdProvider() }
    20	    single<Clock> { Clock.System }
    21	    single<LessonAttemptRepository> { LessonAttemptRepositoryImpl(attemptDao = get()) }
    22	    single<LessonRatingRepository> { LessonRatingRepositoryImpl(ratingLocalDao = get()) }
    23	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
     5	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
     6	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
     7	import org.koin.dsl.module
     8	
     9	val quizzesPresentationModule =
    10	    module {
    11	        factory<QuizzesComponent> { (ctx: ComponentContext) ->
    12	            DefaultQuizzesComponent(
    13	                componentContext = ctx,
    14	                questRepository = get(),
    15	                sectionRepository = get(),
    16	                themeRepository = get(),
    17	                lessonRepository = get(),
    18	                lessonAttemptRepository = get(),
    19	                authRepository = get(),
    20	                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
    21	            )
    22	        }
    23	    }

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
    10	import org.koin.dsl.module
    11	
    12	val lessonRunnerDomainKoinAdapter = module {
    13	    factory<StartLessonAttemptUseCase> {
    14	        StartLessonAttemptUseCase(
    15	            questionRepository = get(),
    16	            lessonRepository = get(),
    17	            parser = get(),
    18	            authRepository = get(),
    19	            clock = get(),
    20	            randomSeedProvider = get<RandomSeedProvider>()::next,
    21	        )
    22	    }
    23	    factory<CompleteAttemptUseCase> {
    24	        CompleteAttemptUseCase(
    25	            attemptRepository = get(),
    26	            ratingRepository = get(),
    27	            clock = get(),
    28	            attemptIdProvider = get<AttemptIdProvider>()::next,
    29	        )
    30	    }
    31	    factory<AbortAttemptUseCase> {
    32	        AbortAttemptUseCase(
    33	            attemptRepository = get(),
    34	            clock = get(),
    35	            attemptIdProvider = get<AttemptIdProvider>()::next,
    36	        )
    37	    }
    38	    factory<SubmitLessonRatingUseCase> {
    39	        SubmitLessonRatingUseCase(
    40	            ratingRepository = get(),
    41	            lessonRepository = get(),
    42	            clock = get(),
    43	            ratingIdProvider = get<RatingIdProvider>()::provide,
    44	        )
    45	    }
    46	}

exec
/bin/bash -lc "rg -n \"LessonRunnerRootComponent|LessonRunnerComponentFactory\" android/core/navigation android/feature/lesson-runner/presentation android/feature/quizzes-screen/presentation -g '*.kt' -g '*.kts'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:20:                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:20:            DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:34:            ) as LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:37:        single<LessonRunnerComponentFactory> {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:38:            LessonRunnerComponentFactory { ctx, lessonId, mode ->
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:9:interface LessonRunnerRootComponent {
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:16:    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:12:    ): LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:37:    private val lessonRunnerFactory: LessonRunnerComponentFactory,
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:86:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:36:class DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:45:) : ComponentContext by componentContext, LessonRunnerRootComponent {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:15:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:78:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:27:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:56:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:105:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:149:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:183:    component: LessonRunnerRootComponent,
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:297:private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:16:) : LessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:41: * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:98:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:106:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:53: * Presentation unit tests for [DefaultLessonRunnerRootComponent].
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:64:class DefaultLessonRunnerRootComponentTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:107:    ): DefaultLessonRunnerRootComponent {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:111:        return DefaultLessonRunnerRootComponent(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:207:    private fun advanceToResultState(component: DefaultLessonRunnerRootComponent) {

exec
/bin/bash -lc "rg -n \"single<LessonRunnerComponentFactory>|factory<LessonRunnerComponentFactory>|LessonRunnerComponentFactory \\{\" apps android shared -g '*.kt' -g '*.kts'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:37:        single<LessonRunnerComponentFactory> {
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:38:            LessonRunnerComponentFactory { ctx, lessonId, mode ->
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:212:        single<LessonRunnerComponentFactory> {
apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:213:            LessonRunnerComponentFactory { _, _, _ -> error("Not wired in KoinModuleWiringTest") }
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:7:fun interface LessonRunnerComponentFactory {
android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt:269:                lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt:78:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt:86:            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },

exec
/bin/bash -lc "rg -n \"com\\.tpov\\.schoolquiz\\.android\\.feature\\.lesson_runner|lesson_runner\\.presentation|LessonRunner\" android/feature/quizzes-screen/presentation/src/main -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:20:                lessonRunnerFactory = get<LessonRunnerComponentFactory>(),
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:16:    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:36:    data class LessonRunner(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:42:            is QuizzesChild.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:43:                LessonRunnerScreen(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:37:    private val lessonRunnerFactory: LessonRunnerComponentFactory,
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:101:                is QuizzesConfig.LessonRunner -> cfg.titles.size
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:155:            is QuizzesConfig.LessonRunner ->
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:156:                QuizzesChild.LessonRunner(
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:78:            QuizzesConfig.LessonRunner(

exec
/bin/bash -lc "rg -n \"com\\.tpov\\.schoolquiz\\.android\\.feature\\.quizzes_screen|quizzes_screen\" android/feature/lesson-runner/presentation shared/feature/lesson-runner -g '*.kt' -g '*.kts'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
exec
/bin/bash -lc "rg -n \"data class Result|sealed.*Result|attempt: Attempt|userId|codeAnswer|attemptId|RunnerUiState\\.Result\" android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:24:    data class Result(

exec
/bin/bash -lc "rg -n \"DifficultyConverter\" . -g '*.kt' -g '*.kts' -g '*.md'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
./apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:605:     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
./docs/features/lesson-runner/plan/README.md:112:- `shared/core/persistence/`: `LessonAttemptEntity.kt`, `LessonRatingSubmittedLocalEntity.kt`, `LessonAttemptDao.kt`, `LessonRatingLocalDao.kt`, `DifficultyConverter.kt`, `TopParticipantListConverter.kt`, `Migration3to4.kt`
./docs/features/lesson-runner/plan/phase-02/tests.md:58:- **Given:** `DifficultyConverter`; `Difficulty.EASY`
./docs/features/lesson-runner/plan/phase-02/backend.md:101:## New Converter: `DifficultyConverter`
./docs/features/lesson-runner/plan/phase-02/backend.md:103:- **Файл:** `shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/converter/DifficultyConverter.kt`
./docs/features/lesson-runner/plan/phase-02/backend.md:105:- **Сигнатура:** `@ProvidedTypeConverter class DifficultyConverter`
./docs/features/lesson-runner/plan/phase-02/backend.md:111:  - `@ProvidedTypeConverter` — добавляется через `.addTypeConverter(DifficultyConverter())` в Room builder
./docs/features/lesson-runner/plan/phase-02/backend.md:172:- **Сигнатура:** `@Database(entities = [...existing..., LessonAttemptEntity::class, LessonRatingSubmittedLocalEntity::class], version = 4, exportSchema = true) @TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class) abstract class AppDatabase : RoomDatabase()`
./docs/features/lesson-runner/plan/phase-02/backend.md:177:  - `@TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class)` — per Room KMP pattern
./docs/features/lesson-runner/plan/phase-02/backend.md:198:  - Добавить `.addTypeConverter(DifficultyConverter())`
./docs/features/lesson-runner/plan/phase-02/backend.md:205:- **Depends on:** `MIGRATION_3_4`, `DifficultyConverter`, `TopParticipantListConverter`, `LessonAttemptDao`, `LessonRatingLocalDao`
./docs/features/lesson-runner/plan/phase-02/overview.md:72:- `shared/core/persistence/src/commonMain/kotlin/.../DifficultyConverter.kt` — per ADR-LR-18 (Room KMP rejects unused TypeConverters; mapper handles Difficulty↔Int)
./docs/features/lesson-runner/plan/phase-02/overview.md:86:6. MT-05: ~~`DifficultyConverter` round-trip~~ — **REMOVED (ADR-LR-18)**, converter удалён
./docs/features/lesson-runner/plan/phase-02/overview.md:128:- **REMOVED (ADR-LR-18)**: `DifficultyConverter` удалён — Room KMP 2.7+ отклоняет converter без Entity-binding (`IllegalArgumentException: Unexpected type converter`). Mapper обрабатывает `Difficulty ↔ Int`: `isHard = if (mode == HARD) 1 else 0`. Plan инвариант про `DifficultyConverter` superseded ADR-LR-18.
./docs/features/lesson-runner/quality-scorecard.md:39:- ADR-LR-18: DifficultyConverter removed (mapper-based conversion, phase-02)
./shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt:13: * Note: MT-05 (DifficultyConverter) removed per ADR-LR-18 — converter deleted, mapper handles Difficulty↔Int.
./docs/features/lesson-runner/plan/phase-07/tests.md:62:- **Then:** no Room exception about missing TypeConverter; `DifficultyConverter` and `TopParticipantListConverter` registered
./docs/features/lesson-runner/plan/phase-07/overview.md:78:8. IT-09g: `AppDatabase` builder — `DifficultyConverter` + `TopParticipantListConverter` registered (Room не падает при query)
./docs/features/lesson-runner/implementation.md:16:| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
./docs/features/lesson-runner/implementation.md:63:Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
./docs/features/lesson-runner/implementation.md:99:| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
./docs/features/lesson-runner/06-api-contract.md:714:### DifficultyConverter
./docs/features/lesson-runner/06-api-contract.md:718:class DifficultyConverter {
./docs/features/lesson-runner/01-architecture.md:306:    class DifficultyConverter {
./docs/features/lesson-runner/03-decisions.md:481:**TypeConverters**: `DifficultyConverter` и `TopParticipantListConverter` оба `@ProvidedTypeConverter` + `.addTypeConverter()` в Room builder (паттерн из `StringSetConverter` / `PersistenceModule.kt:24`).
./docs/features/lesson-runner/03-decisions.md:677:## ADR-LR-18 — DifficultyConverter удалён: mapper-based conversion вместо TypeConverter
./docs/features/lesson-runner/03-decisions.md:683:Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.
./docs/features/lesson-runner/03-decisions.md:689:**Удалить `DifficultyConverter.kt` полностью.** Конвертация `Difficulty ↔ Int` выполняется в mapper слое:
./docs/features/lesson-runner/03-decisions.md:698:`DifficultyConverter` не регистрируется ни в `@TypeConverters`, ни в Room builder. `TypeConvertersPhase02Test.kt` MT-05 тесты удалены.
./docs/features/lesson-runner/03-decisions.md:700:Plan invariant `overview.md §Pattern Invariants` строка про `DifficultyConverter` — superseded этим ADR.
./docs/features/lesson-runner/03-decisions.md:704:- **Оставить DifficultyConverter + изменить Entity на `Difficulty` тип** — нарушает spec AC#52 ("isHard: Int для safer migration validation"). Отклонено: spec mandates Int.
./docs/features/lesson-runner/03-decisions.md:709:- `DifficultyConverter.kt` удалён
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:33:- ADR-LR-18: DifficultyConverter removed (mapper-based)
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:593:| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:640:Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:676:| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:933:/bin/bash -lc 'rg -n "''^## |''^### |AC-|Acceptance|Feature Domain Contract|C[0-9]+|ADR-LR|RunnerUiState|PII|DifficultyConverter|LessonRunnerRootComponent|LessonRunnerComponentFactory|lessonRunnerDomainKoinAdapter|quizzes-screen" docs/features/lesson-runner/0-spec.md docs/features/lesson-runner/03-decisions.md docs/features/lesson-runner/implementation.md' in /home/Programming/Android/schoolquiz4.0
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:938:docs/features/lesson-runner/implementation.md:16:| Phase-02 | Persistence — Migration v3→v4 | PASS (5/5 reviewers) | ADR-LR-18: DifficultyConverter removed |
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:949:docs/features/lesson-runner/implementation.md:63:Phase-02 deletion: DifficultyConverter.kt + MT-05 tests (ADR-LR-18 supersedes).
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:955:docs/features/lesson-runner/implementation.md:99:| LR-18 | 02 | DifficultyConverter removed; mapper-based Difficulty↔Int conversion in data layer | Room KMP 2.7+ rejects unused converters; entity uses Int isHard, mapper handles conversion. Plan invariant overview.md:129 superseded. |
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1044:docs/features/lesson-runner/03-decisions.md:481:**TypeConverters**: `DifficultyConverter` и `TopParticipantListConverter` оба `@ProvidedTypeConverter` + `.addTypeConverter()` в Room builder (паттерн из `StringSetConverter` / `PersistenceModule.kt:24`).
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1075:docs/features/lesson-runner/03-decisions.md:677:## ADR-LR-18 — DifficultyConverter удалён: mapper-based conversion вместо TypeConverter
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1077:docs/features/lesson-runner/03-decisions.md:683:Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1079:docs/features/lesson-runner/03-decisions.md:689:**Удалить `DifficultyConverter.kt` полностью.** Конвертация `Difficulty ↔ Int` выполняется в mapper слое:
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1080:docs/features/lesson-runner/03-decisions.md:698:`DifficultyConverter` не регистрируется ни в `@TypeConverters`, ни в Room builder. `TypeConvertersPhase02Test.kt` MT-05 тесты удалены.
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1081:docs/features/lesson-runner/03-decisions.md:700:Plan invariant `overview.md §Pattern Invariants` строка про `DifficultyConverter` — superseded этим ADR.
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1083:docs/features/lesson-runner/03-decisions.md:704:- **Оставить DifficultyConverter + изменить Entity на `Difficulty` тип** — нарушает spec AC#52 ("isHard: Int для safer migration validation"). Отклонено: spec mandates Int.
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1085:docs/features/lesson-runner/03-decisions.md:709:- `DifficultyConverter.kt` удалён
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1822:/bin/bash -lc "rg -n \"lesson-runner|lesson_runner|lessonRunner|LessonRunner|DifficultyConverter|RunnerUiState\\.Result|TopParticipant\" docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1825:/bin/bash -lc "rg -n \"lessonRunner|LessonRunner|questionSchemaModule|lessonRunnerDataModule|lessonRunnerDomainKoinAdapter|LessonRunnerComponentFactory|DifficultyConverter|lesson_runner|quizzes_screen|RunnerUiState\\.Result|data class Result|FLAG_SECURE|setFlags|clearFlags\" apps android shared platform -g '*.kt' -g '*.kts'" in /home/Programming/Android/schoolquiz4.0
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:1962:apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt:605:     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
./docs/features/lesson-runner/_codex-review/cross-phase/codex-output.md:2124:shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt:13: * Note: MT-05 (DifficultyConverter) removed per ADR-LR-18 — converter deleted, mapper handles Difficulty↔Int.
./docs/features/lesson-runner/04-testing.md:207:| IT-09g | `AppDatabase` builder: `DifficultyConverter` + `TopParticipantListConverter` оба registered через `addTypeConverter` (Room не падает при query) | AC-53 |
./docs/features/lesson-runner/04-testing.md:222:| MT-05 | DifficultyConverter roundtrip: EASY → "EASY" → EASY |
./docs/features/lesson-runner/08-storage-model.md:130:@TypeConverters(DifficultyConverter::class, TopParticipantListConverter::class)
./docs/features/lesson-runner/08-storage-model.md:138:**PersistenceModule.kt**: `addMigrations(MIGRATION_3_4)` + `addTypeConverter(DifficultyConverter())` + `addTypeConverter(TopParticipantListConverter())`.
./docs/features/lesson-runner/_codex-review/round-2/architect.md:17:- C11 ✗ STILL WRONG — IT-09 does not cover parser/converters. `04-testing.md:195-199` only covers data repos, providers, domain adapter use cases, presentation module. `MT-05` only covers `DifficultyConverter` at `04-testing.md:214`; no `TopParticipantListConverter` or parser wiring test.

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
     2	
     3	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     4	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
     6	
     7	sealed interface RunnerUiState {
     8	    data object Loading : RunnerUiState
     9	
    10	    data class InitFailed(
    11	        val reason: InitFailureReason,
    12	    ) : RunnerUiState
    13	
    14	    data class Question(
    15	        val questionUiState: QuestionUiState,
    16	        val indexInPool: Int,
    17	        val totalInPool: Int,
    18	        val deadlineMs: Long,
    19	        val isPaused: Boolean,
    20	        val isHard: Boolean,
    21	        val showExitConfirmDialog: Boolean,
    22	    ) : RunnerUiState
    23	
    24	    data class Result(
    25	        val percentScore: PercentScore,
    26	        val mode: Difficulty,
    27	        val completedAt: Long,
    28	        val hardUnlocked: Boolean,
    29	        val bestStarsRawTenths: Int,
    30	        val lessonAverageRating: Float?,
    31	        val lessonRatingCount: Int,
    32	        val top3: List<TopParticipant>,
    33	        val userAttemptCount: Int,
    34	        val userAveragePercentScore: Int,
    35	        val showRatingPrompt: Boolean,
    36	        val saveWarning: Boolean,
    37	    ) : RunnerUiState
    38	
    39	    enum class InitFailureReason {
    40	        AuthRequired,
    41	        LessonNotFound,
    42	        EmptyPool,
    43	        NoValidQuestions,
    44	    }
    45	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper
     2	
     3	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.SaveError
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
     5	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
     6	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     7	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
     8	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     9	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    14	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError as DomainSaveError
    15	
    16	fun RunnerState.Ready.toQuestionUiState(): RunnerUiState.Question {
    17	    val question = playOrder[indexInPool]
    18	    return RunnerUiState.Question(
    19	        questionUiState = question.content.toQuestionUiState(currentDraftAnswer),
    20	        indexInPool = indexInPool,
    21	        totalInPool = playOrder.size,
    22	        deadlineMs = deadlineMs,
    23	        isPaused = isPaused,
    24	        isHard = mode == Difficulty.HARD,
    25	        showExitConfirmDialog = false,
    26	    )
    27	}
    28	
    29	fun QuestionContent.toQuestionUiState(draft: UserAnswerDraft? = null): QuestionUiState =
    30	    when (this) {
    31	        is QuestionContent.SingleChoice -> {
    32	            val selectedId = (draft as? UserAnswerDraft.SingleChoiceDraft)?.selected?.raw
    33	            QuestionUiState.SingleChoice(
    34	                questionText = text,
    35	                hasImage = imageUrl != null,
    36	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    37	                options = options.map { OptionUi(id = it.id.raw, text = it.text) },
    38	                selectedOptionId = selectedId,
    39	            )
    40	        }
    41	        is QuestionContent.MultipleChoice -> {
    42	            val selectedIds =
    43	                (draft as? UserAnswerDraft.MultipleChoiceDraft)
    44	                    ?.selected?.map { it.raw }?.toSet() ?: emptySet()
    45	            QuestionUiState.MultipleChoice(
    46	                questionText = text,
    47	                hasImage = imageUrl != null,
    48	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    49	                options = options.map { OptionUi(id = it.id.raw, text = it.text) },
    50	                selectedIds = selectedIds,
    51	            )
    52	        }
    53	        is QuestionContent.Ordering -> {
    54	            val draftOrder = (draft as? UserAnswerDraft.OrderingDraft)?.order
    55	            val itemById = items.associateBy { it.id }
    56	            val orderedItems =
    57	                if (draftOrder != null && draftOrder.size == items.size) {
    58	                    draftOrder.mapNotNull { id -> itemById[id]?.let { OptionUi(it.id.raw, it.text) } }
    59	                        .takeIf { it.size == items.size } ?: items.map { OptionUi(it.id.raw, it.text) }
    60	                } else {
    61	                    items.map { OptionUi(it.id.raw, it.text) }
    62	                }
    63	            QuestionUiState.Ordering(
    64	                questionText = text,
    65	                hasImage = imageUrl != null,
    66	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    67	                items = orderedItems,
    68	            )
    69	        }
    70	        is QuestionContent.FillBlank -> {
    71	            val blankIdToIndex = blanks.mapIndexed { idx, blank -> blank.id to idx }.toMap()
    72	            val candidateById = candidates.associateBy { it.id }
    73	            val filledValues =
    74	                (draft as? UserAnswerDraft.FillBlankDraft)
    75	                    ?.filled
    76	                    ?.mapNotNull { (blankId, candidateId) ->
    77	                        val idx = blankIdToIndex[blankId] ?: return@mapNotNull null
    78	                        val text = candidateId?.let { candidateById[it]?.text } ?: return@mapNotNull null
    79	                        idx to text
    80	                    }
    81	                    ?.toMap() ?: emptyMap()
    82	            QuestionUiState.FillBlank(
    83	                questionText = text,
    84	                hasImage = imageUrl != null,
    85	                imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    86	                templateParts = parseTemplateParts(text, blanks.size),
    87	                filledValues = filledValues,
    88	            )
    89	        }
    90	    }
    91	
    92	fun UserAnswerDraft.toUserAnswer(): UserAnswer =
    93	    when (this) {
    94	        is UserAnswerDraft.SingleChoiceDraft -> UserAnswer.SingleChoiceAnswer(selected)
    95	        is UserAnswerDraft.MultipleChoiceDraft -> UserAnswer.MultipleChoiceAnswer(selected)
    96	        is UserAnswerDraft.OrderingDraft -> UserAnswer.OrderingAnswer(order)
    97	        is UserAnswerDraft.FillBlankDraft -> UserAnswer.FillBlankAnswer(filled)
    98	    }
    99	
   100	fun InitFailureReason.toUiReason(): RunnerUiState.InitFailureReason =
   101	    when (this) {
   102	        InitFailureReason.AuthRequired -> RunnerUiState.InitFailureReason.AuthRequired
   103	        InitFailureReason.LessonNotFound -> RunnerUiState.InitFailureReason.LessonNotFound
   104	        InitFailureReason.EmptyPool -> RunnerUiState.InitFailureReason.EmptyPool
   105	        InitFailureReason.NoValidQuestions -> RunnerUiState.InitFailureReason.NoValidQuestions
   106	    }
   107	
   108	fun DomainSaveError.toEventSaveError(): SaveError =
   109	    when (this) {
   110	        is DomainSaveError.IoFailure -> SaveError.IoError
   111	        is DomainSaveError.UnknownError -> SaveError.Unknown
   112	    }
   113	
   114	private fun parseTemplateParts(
   115	    text: String,
   116	    blankCount: Int,
   117	): List<TemplatePart> {
   118	    val segments = text.split("___")
   119	    val result = mutableListOf<TemplatePart>()
   120	    segments.forEachIndexed { idx, segment ->
   121	        if (segment.isNotEmpty()) result.add(TemplatePart.Text(segment))
   122	        if (idx < segments.size - 1 && idx < blankCount) {
   123	            result.add(TemplatePart.Blank(index = idx, placeholder = "___"))
   124	        }
   125	    }
   126	    if (result.isEmpty()) result.add(TemplatePart.Text(text))
   127	    return result
   128	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt | sed -n '1,120p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
     2	
     3	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
     6	import kotlinx.coroutines.flow.Flow
     7	import kotlinx.coroutines.flow.StateFlow
     8	
     9	interface LessonRunnerRootComponent {
    10	    val uiState: StateFlow<RunnerUiState>
    11	    val events: Flow<RunnerEvent>
    12	
    13	    fun onAnswer(answer: UserAnswerDraft)
    14	
    15	    fun onTimeout()
    16	
    17	    fun onContinue()
    18	
    19	    fun onExit()
    20	
    21	    fun onCrossButtonTap()
    22	
    23	    fun onCrossConfirmed()
    24	
    25	    fun onCrossCancelled()
    26	
    27	    fun onSubmitRating(rating: Int)
    28	
    29	    fun onFinish()
    30	
    31	    fun onBack()
    32	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt | sed -n '1,120p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt | sed -n '1,120p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     5	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     6	
     7	fun interface LessonRunnerComponentFactory {
     8	    fun create(
     9	        componentContext: ComponentContext,
    10	        lessonId: LessonId,
    11	        mode: Difficulty,
    12	    ): LessonRunnerRootComponent
    13	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import kotlinx.serialization.Serializable
     5	
     6	@Serializable
     7	sealed class QuizzesConfig {
     8	    @Serializable
     9	    data object Idle : QuizzesConfig()
    10	
    11	    @Serializable
    12	    data class QuestList(
    13	        val catalogId: String,
    14	        val titles: List<String>,
    15	    ) : QuizzesConfig()
    16	
    17	    @Serializable
    18	    data class SectionList(
    19	        val questId: String,
    20	        val titles: List<String>,
    21	    ) : QuizzesConfig()
    22	
    23	    @Serializable
    24	    data class ThemeList(
    25	        val sectionId: String,
    26	        val titles: List<String>,
    27	    ) : QuizzesConfig()
    28	
    29	    @Serializable
    30	    data class LessonList(
    31	        val themeId: String,
    32	        val titles: List<String>,
    33	    ) : QuizzesConfig()
    34	
    35	    @Serializable
    36	    data class LessonRunner(
    37	        val lessonId: String,
    38	        val mode: Difficulty,
    39	        val titles: List<String>,
    40	    ) : QuizzesConfig()
    41	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt | sed -n '1,230p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.arkivanov.decompose.router.stack.ChildStack
     5	import com.arkivanov.decompose.router.stack.StackNavigation
     6	import com.arkivanov.decompose.router.stack.childStack
     7	import com.arkivanov.decompose.router.stack.pop
     8	import com.arkivanov.decompose.router.stack.popTo
     9	import com.arkivanov.decompose.router.stack.popToFirst
    10	import com.arkivanov.decompose.router.stack.pushNew
    11	import com.arkivanov.decompose.value.Value
    12	import com.arkivanov.essenty.backhandler.BackCallback
    13	import com.arkivanov.essenty.statekeeper.SerializableContainer
    14	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
    15	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    16	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
    17	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
    18	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    19	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    20	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    21	import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
    22	import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
    23	import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
    24	import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
    25	import kotlinx.coroutines.CoroutineDispatcher
    26	import kotlinx.coroutines.Dispatchers
    27	import kotlinx.serialization.builtins.ListSerializer
    28	
    29	class DefaultQuizzesComponent(
    30	    componentContext: ComponentContext,
    31	    private val questRepository: QuestRepository,
    32	    private val sectionRepository: SectionRepository,
    33	    private val themeRepository: ThemeRepository,
    34	    private val lessonRepository: LessonRepository,
    35	    private val lessonAttemptRepository: LessonAttemptRepository,
    36	    private val authRepository: AuthRepository,
    37	    private val lessonRunnerFactory: LessonRunnerComponentFactory,
    38	    private val mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    39	) : ComponentContext by componentContext, QuizzesComponent {
    40	    private val navigation = StackNavigation<QuizzesConfig>()
    41	
    42	    // saveStack/restoreStack overload: runCatching wraps SerializationException on corrupted
    43	    // process-death state so the stack falls back to [Idle] instead of crashing on launch.
    44	    // Spec: docs/features/quizzes-screen/plan/phase-03/overview.md Problem 6 / AC#21.
    45	    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
    46	        childStack(
    47	            source = navigation,
    48	            initialStack = { listOf(QuizzesConfig.Idle) },
    49	            saveStack = { stack ->
    50	                SerializableContainer(
    51	                    value = stack,
    52	                    strategy = ListSerializer(QuizzesConfig.serializer()),
    53	                )
    54	            },
    55	            restoreStack = { container ->
    56	                runCatching {
    57	                    container.consume(ListSerializer(QuizzesConfig.serializer()))
    58	                }.getOrNull()
    59	            },
    60	            handleBackButton = false,
    61	            childFactory = ::createChild,
    62	        )
    63	
    64	    // priority = 100: PRIORITY_OVERLAY absent in Essenty 2.1.0 (ADR-QS-12, Pattern Invariant 5)
    65	    private val backCallback =
    66	        BackCallback(priority = 100, isEnabled = false) {
    67	            navigation.pop()
    68	        }
    69	
    70	    init {
    71	        backHandler.register(backCallback)
    72	        childStack.subscribe { stack ->
    73	            backCallback.isEnabled = stack.backStack.isNotEmpty()
    74	        }
    75	    }
    76	
    77	    override fun openQuestList(
    78	        catalogId: CatalogId,
    79	        catalogName: String,
    80	    ) {
    81	        navigation.pushNew(QuizzesConfig.QuestList(catalogId.value, listOf("Каталоги", catalogName)))
    82	    }
    83	
    84	    override fun openSectionList(
    85	        questId: QuestId,
    86	        titles: List<String>,
    87	    ) {
    88	        navigation.pushNew(QuizzesConfig.SectionList(questId.value, titles))
    89	    }
    90	
    91	    override fun popToLevel(uiLevel: Int) {
    92	        if (uiLevel < 0) return
    93	        val active = childStack.value.active
    94	        val titlesSize =
    95	            when (val cfg = active.configuration) {
    96	                is QuizzesConfig.Idle -> 0
    97	                is QuizzesConfig.QuestList -> cfg.titles.size
    98	                is QuizzesConfig.SectionList -> cfg.titles.size
    99	                is QuizzesConfig.ThemeList -> cfg.titles.size
   100	                is QuizzesConfig.LessonList -> cfg.titles.size
   101	                is QuizzesConfig.LessonRunner -> cfg.titles.size
   102	            }
   103	        // In MyQuests entry path, SectionList is pushed directly (no QuestList in stack).
   104	        // titles may contain virtual "decoration" segments that have no stack entry.
   105	        // virtualCount = how many leading titles have no corresponding stack entry.
   106	        val virtualCount = (titlesSize + 1 - childStack.value.items.size).coerceAtLeast(0)
   107	        val adjustedLevel = uiLevel - virtualCount
   108	        if (adjustedLevel < 0) {
   109	            // Clicked a virtual breadcrumb (e.g., catalog name from MyQuests path) — dismiss overlay
   110	            navigation.popToFirst()
   111	        } else {
   112	            navigation.popTo(adjustedLevel + 1)
   113	        }
   114	    }
   115	
   116	    override fun popCurrentChild() {
   117	        navigation.pop()
   118	    }
   119	
   120	    override fun dismissQuizzes() {
   121	        navigation.popToFirst()
   122	    }
   123	
   124	    private fun createChild(
   125	        config: QuizzesConfig,
   126	        ctx: ComponentContext,
   127	    ): QuizzesChild =
   128	        when (config) {
   129	            is QuizzesConfig.Idle ->
   130	                QuizzesChild.Idle
   131	            is QuizzesConfig.QuestList ->
   132	                QuizzesChild.QuestList(
   133	                    DefaultQuestListComponent(ctx, config, questRepository, navigation, mainContext),
   134	                )
   135	            is QuizzesConfig.SectionList ->
   136	                QuizzesChild.SectionList(
   137	                    DefaultSectionListComponent(ctx, config, sectionRepository, navigation, mainContext),
   138	                )
   139	            is QuizzesConfig.ThemeList ->
   140	                QuizzesChild.ThemeList(
   141	                    DefaultThemeListComponent(ctx, config, themeRepository, navigation, mainContext),
   142	                )
   143	            is QuizzesConfig.LessonList ->
   144	                QuizzesChild.LessonList(
   145	                    DefaultLessonListComponent(
   146	                        ctx,
   147	                        config,
   148	                        lessonRepository,
   149	                        lessonAttemptRepository,
   150	                        authRepository,
   151	                        navigation,
   152	                        mainContext,
   153	                    ),
   154	                )
   155	            is QuizzesConfig.LessonRunner ->
   156	                QuizzesChild.LessonRunner(
   157	                    lessonRunnerFactory.create(ctx, LessonId(config.lessonId), config.mode),
   158	                )
   159	        }
   160	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.arkivanov.decompose.router.stack.StackNavigation
     5	import com.arkivanov.decompose.router.stack.pushNew
     6	import com.arkivanov.decompose.value.MutableValue
     7	import com.arkivanov.decompose.value.Value
     8	import com.arkivanov.essenty.lifecycle.doOnDestroy
     9	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    10	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    11	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
    12	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    13	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
    14	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
    15	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    16	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
    17	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
    18	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
    19	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    20	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
    21	import kotlinx.coroutines.CoroutineDispatcher
    22	import kotlinx.coroutines.CoroutineScope
    23	import kotlinx.coroutines.Dispatchers
    24	import kotlinx.coroutines.ExperimentalCoroutinesApi
    25	import kotlinx.coroutines.SupervisorJob
    26	import kotlinx.coroutines.flow.MutableStateFlow
    27	import kotlinx.coroutines.flow.catch
    28	import kotlinx.coroutines.flow.combine
    29	import kotlinx.coroutines.flow.flatMapLatest
    30	import kotlinx.coroutines.flow.flowOf
    31	import kotlinx.coroutines.flow.update
    32	import kotlinx.coroutines.launch
    33	
    34	@OptIn(ExperimentalCoroutinesApi::class)
    35	class DefaultLessonListComponent(
    36	    componentContext: ComponentContext,
    37	    config: QuizzesConfig.LessonList,
    38	    private val lessonRepository: LessonRepository,
    39	    private val attemptRepository: LessonAttemptRepository,
    40	    private val authRepository: AuthRepository,
    41	    private val navigation: StackNavigation<QuizzesConfig>,
    42	    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    43	) : ComponentContext by componentContext, LessonListComponent {
    44	    private val componentJob = SupervisorJob()
    45	    private val scope = CoroutineScope(componentJob + coroutineContext)
    46	
    47	    private val themeId = ThemeId(config.themeId)
    48	    override val titles: List<String> = config.titles
    49	
    50	    private val _uiState = MutableValue<LessonListUiState>(LessonListUiState.Loading)
    51	    override val uiState: Value<LessonListUiState> = _uiState
    52	
    53	    private val hardCheckedSet: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    54	
    55	    init {
    56	        scope.launch {
    57	            combine(
    58	                lessonRepository.observeByTheme(themeId),
    59	                authRepository.observeUid().flatMapLatest { uid ->
    60	                    if (uid == null) flowOf(emptyList()) else attemptRepository.observeAllByUser(uid)
    61	                },
    62	                hardCheckedSet,
    63	            ) { lessons, attempts, checkedSet ->
    64	                mapToUi(lessons, attempts, checkedSet)
    65	            }
    66	                .catch { /* log */ }
    67	                .collect { _uiState.value = it }
    68	        }
    69	        lifecycle.doOnDestroy {
    70	            componentJob.cancel()
    71	            hardCheckedSet.value = emptySet()
    72	        }
    73	    }
    74	
    75	    override fun onLessonClick(lesson: LessonItemUi) {
    76	        val mode = if (lesson.hardUnlocked && lesson.isHardChecked) Difficulty.HARD else Difficulty.EASY
    77	        navigation.pushNew(
    78	            QuizzesConfig.LessonRunner(
    79	                lessonId = lesson.id,
    80	                mode = mode,
    81	                titles = titles + lesson.title,
    82	            ),
    83	        )
    84	    }
    85	
    86	    override fun onHardCheckToggled(lessonId: String) {
    87	        val item = (_uiState.value as? LessonListUiState.Loaded)?.items?.find { it.id == lessonId }
    88	        if (item?.hardUnlocked == true) {
    89	            hardCheckedSet.update { current ->
    90	                if (lessonId in current) current - lessonId else current + lessonId
    91	            }
    92	        }
    93	    }
    94	
    95	    private fun mapToUi(
    96	        lessons: List<Lesson>,
    97	        attempts: List<Attempt>,
    98	        checkedSet: Set<String>,
    99	    ): LessonListUiState {
   100	        if (lessons.isEmpty()) return LessonListUiState.Empty("Нет уроков")
   101	        val items =
   102	            lessons.map { lesson ->
   103	                val lessonAttempts = attempts.filter { it.lessonId == lesson.id }
   104	                LessonItemUi(
   105	                    id = lesson.id.value,
   106	                    title = lesson.title,
   107	                    orderLabel = "${lesson.order + 1}.",
   108	                    bestStarsRawTenths = computeBestStars(lessonAttempts).rawTenths,
   109	                    hardUnlocked = computeHardUnlocked(lessonAttempts),
   110	                    isHardChecked = lesson.id.value in checkedSet,
   111	                )
   112	            }
   113	        return LessonListUiState.Loaded(items)
   114	    }
   115	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component
     2	
     3	import com.arkivanov.decompose.value.Value
     4	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
     5	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
     6	
     7	interface LessonListComponent {
     8	    val uiState: Value<LessonListUiState>
     9	    val titles: List<String>
    10	
    11	    fun onLessonClick(lesson: LessonItemUi)
    12	
    13	    fun onHardCheckToggled(lessonId: String)
    14	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.foundation.layout.Box
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.fillMaxSize
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.foundation.lazy.LazyColumn
     8	import androidx.compose.foundation.lazy.items
     9	import androidx.compose.foundation.lazy.rememberLazyListState
    10	import androidx.compose.material3.CircularProgressIndicator
    11	import androidx.compose.material3.MaterialTheme
    12	import androidx.compose.material3.Text
    13	import androidx.compose.runtime.Composable
    14	import androidx.compose.runtime.getValue
    15	import androidx.compose.ui.Alignment
    16	import androidx.compose.ui.Modifier
    17	import androidx.compose.ui.tooling.preview.Preview
    18	import androidx.compose.ui.unit.dp
    19	import com.arkivanov.decompose.extensions.compose.subscribeAsState
    20	import com.arkivanov.decompose.value.MutableValue
    21	import com.arkivanov.decompose.value.Value
    22	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    23	import com.tpov.schoolquiz.android.core.designsystem.components.BreadcrumbBar
    24	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
    25	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    26	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
    27	
    28	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    29	@Composable
    30	fun LessonListScreen(
    31	    component: LessonListComponent,
    32	    onSegmentClick: (Int) -> Unit,
    33	) {
    34	    val uiState by component.uiState.subscribeAsState()
    35	    val lazyListState = rememberLazyListState()
    36	
    37	    Column(modifier = Modifier.fillMaxSize()) {
    38	        BreadcrumbBar(titles = component.titles, onSegmentClick = onSegmentClick)
    39	        when (val state = uiState) {
    40	            is LessonListUiState.Loading ->
    41	                Box(modifier = Modifier.fillMaxSize()) {
    42	                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    43	                }
    44	            is LessonListUiState.Empty ->
    45	                Box(modifier = Modifier.fillMaxSize()) {
    46	                    Text(
    47	                        text = state.levelLabel,
    48	                        modifier = Modifier.align(Alignment.Center),
    49	                        style = MaterialTheme.typography.titleMedium,
    50	                    )
    51	                }
    52	            is LessonListUiState.Loaded ->
    53	                LazyColumn(state = lazyListState) {
    54	                    items(state.items, key = { it.id }) { item ->
    55	                        LessonItemCard(
    56	                            item = item,
    57	                            onClick = { component.onLessonClick(item) },
    58	                            onHardCheckChanged = { component.onHardCheckToggled(item.id) },
    59	                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    60	                        )
    61	                    }
    62	                }
    63	        }
    64	    }
    65	}
    66	
    67	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    68	@Preview(showBackground = true)
    69	@Composable
    70	private fun LessonListScreenLoadingPreview() {
    71	    SchoolQuizTheme {
    72	        LessonListScreen(
    73	            component =
    74	                object : LessonListComponent {
    75	                    override val uiState: Value<LessonListUiState> = MutableValue(LessonListUiState.Loading)
    76	                    override val titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1")
    77	
    78	                    override fun onLessonClick(lesson: LessonItemUi) = Unit
    79	
    80	                    override fun onHardCheckToggled(lessonId: String) = Unit
    81	                },
    82	            onSegmentClick = {},
    83	        )
    84	    }
    85	}
    86	
    87	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    88	@Preview(showBackground = true)
    89	@Composable
    90	private fun LessonListScreenLoadedPreview() {
    91	    SchoolQuizTheme {
    92	        LessonListScreen(
    93	            component =
    94	                object : LessonListComponent {
    95	                    override val uiState: Value<LessonListUiState> =
    96	                        MutableValue(
    97	                            LessonListUiState.Loaded(
    98	                                listOf(
    99	                                    LessonItemUi(
   100	                                        id = "l1",
   101	                                        title = "Урок 1 — Введение",
   102	                                        orderLabel = "1.",
   103	                                        bestStarsRawTenths = 20,
   104	                                    ),
   105	                                    LessonItemUi(
   106	                                        id = "l2",
   107	                                        title = "Урок 2 — Практика",
   108	                                        orderLabel = "2.",
   109	                                        bestStarsRawTenths = 0,
   110	                                        hardUnlocked = true,
   111	                                        isHardChecked = false,
   112	                                    ),
   113	                                ),
   114	                            ),
   115	                        )
   116	                    override val titles = listOf("Математика", "Квест 1", "Секция 1", "Тема 1")
   117	
   118	                    override fun onLessonClick(lesson: LessonItemUi) = Unit
   119	
   120	                    override fun onHardCheckToggled(lessonId: String) = Unit
   121	                },
   122	            onSegmentClick = {},
   123	        )
   124	    }
   125	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "rg --files android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation | rg 'LessonItem|LessonListUi|Lesson'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
     2	
     3	data class LessonItemUi(
     4	    val id: String,
     5	    val title: String,
     6	    val orderLabel: String? = null,
     7	    val subtitleCount: String? = null,
     8	    val bestStarsRawTenths: Int = 0,
     9	    val hardUnlocked: Boolean = false,
    10	    val isHardChecked: Boolean = false,
    11	)

 succeeded in 0ms:
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate
     2	
     3	sealed interface LessonListUiState {
     4	    data object Loading : LessonListUiState
     5	
     6	    data class Empty(val levelLabel: String) : LessonListUiState
     7	
     8	    data class Loaded(val items: List<LessonItemUi>) : LessonListUiState
     9	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.foundation.clickable
     4	import androidx.compose.foundation.layout.Arrangement
     5	import androidx.compose.foundation.layout.Column
     6	import androidx.compose.foundation.layout.Row
     7	import androidx.compose.foundation.layout.fillMaxWidth
     8	import androidx.compose.foundation.layout.padding
     9	import androidx.compose.material3.Checkbox
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Text
    12	import androidx.compose.runtime.Composable
    13	import androidx.compose.ui.Alignment
    14	import androidx.compose.ui.Modifier
    15	import androidx.compose.ui.text.style.TextOverflow
    16	import androidx.compose.ui.tooling.preview.Preview
    17	import androidx.compose.ui.unit.dp
    18	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    19	import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
    20	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
    21	
    22	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    23	@Composable
    24	fun LessonItemCard(
    25	    item: LessonItemUi,
    26	    onClick: () -> Unit,
    27	    onHardCheckChanged: (Boolean) -> Unit,
    28	    modifier: Modifier = Modifier,
    29	) {
    30	    Row(
    31	        modifier =
    32	            modifier
    33	                .fillMaxWidth()
    34	                .clickable(onClick = onClick)
    35	                .padding(horizontal = 16.dp, vertical = 12.dp),
    36	        verticalAlignment = Alignment.CenterVertically,
    37	        horizontalArrangement = Arrangement.SpaceBetween,
    38	    ) {
    39	        Column(modifier = Modifier.weight(1f)) {
    40	            if (item.orderLabel != null) {
    41	                Text(
    42	                    text = item.orderLabel,
    43	                    style = MaterialTheme.typography.labelSmall,
    44	                    color = MaterialTheme.colorScheme.onSurfaceVariant,
    45	                )
    46	            }
    47	            Text(
    48	                text = item.title,
    49	                style = MaterialTheme.typography.bodyLarge,
    50	                maxLines = 2,
    51	                overflow = TextOverflow.Ellipsis,
    52	            )
    53	            if (item.subtitleCount != null) {
    54	                Text(
    55	                    text = item.subtitleCount,
    56	                    style = MaterialTheme.typography.bodySmall,
    57	                    color = MaterialTheme.colorScheme.onSurfaceVariant,
    58	                )
    59	            }
    60	        }
    61	        StarRating(rating = item.bestStarsRawTenths / 10f)
    62	        if (item.hardUnlocked) {
    63	            Checkbox(
    64	                checked = item.isHardChecked,
    65	                onCheckedChange = onHardCheckChanged,
    66	            )
    67	        }
    68	    }
    69	}
    70	
    71	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    72	@Preview(showBackground = true)
    73	@Composable
    74	private fun LessonItemCardBasicPreview() {
    75	    SchoolQuizTheme {
    76	        LessonItemCard(
    77	            item =
    78	                LessonItemUi(
    79	                    id = "l1",
    80	                    title = "Урок 1 — Введение в алгебру",
    81	                    orderLabel = "1.",
    82	                    bestStarsRawTenths = 20,
    83	                    hardUnlocked = false,
    84	                ),
    85	            onClick = {},
    86	            onHardCheckChanged = {},
    87	        )
    88	    }
    89	}
    90	
    91	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    92	@Preview(showBackground = true)
    93	@Composable
    94	private fun LessonItemCardHardUnlockedPreview() {
    95	    SchoolQuizTheme {
    96	        LessonItemCard(
    97	            item =
    98	                LessonItemUi(
    99	                    id = "l2",
   100	                    title = "Урок 2 — Сложные задачи с длинным названием которое не помещается",
   101	                    orderLabel = "2.",
   102	                    subtitleCount = "5 вопросов",
   103	                    bestStarsRawTenths = 27,
   104	                    hardUnlocked = true,
   105	                    isHardChecked = true,
   106	                ),
   107	            onClick = {},
   108	            onHardCheckChanged = {},
   109	        )
   110	    }
   111	}
   112	
   113	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   114	@Preview(showBackground = true)
   115	@Composable
   116	private fun LessonItemCardZeroStarsPreview() {
   117	    SchoolQuizTheme {
   118	        LessonItemCard(
   119	            item =
   120	                LessonItemUi(
   121	                    id = "l3",
   122	                    title = "Урок 3 — Новый урок (нет попыток)",
   123	                    bestStarsRawTenths = 0,
   124	                    hardUnlocked = false,
   125	                ),
   126	            onClick = {},
   127	            onHardCheckChanged = {},
   128	        )
   129	    }
   130	}

codex
The one-way presentation boundary checks look good at first pass: `quizzes-screen` imports the public lesson-runner interface/screen/factory, and I’m not seeing a reverse import from lesson-runner back into quizzes-screen. Now I’m going deeper into behavior: state transitions, save failures, lifecycle pause, timer and result projection.
exec
/bin/bash -lc "rg --files shared/feature/lesson-runner/domain/src/commonMain/kotlin android/feature/lesson-runner/presentation/src/main/kotlin shared/feature/lesson-runner/data/src/commonMain/kotlin shared/feature/lesson-runner/data/src/androidMain/kotlin shared/core/persistence/src/commonMain/kotlin shared/core/persistence/src/androidMain/kotlin shared/core/question-schema/src/commonMain/kotlin | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/CandidateId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/Difficulty.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/OptionId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/BlankId.kt
shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContentParser.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonRatingRepository.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/LessonAttemptRepository.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDomainKoinAdapter.kt
shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/LessonRunnerDataModule.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerCoefficients.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/InitFailureReason.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Score.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswerDraft.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/CodeAnswer.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/SaveError.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/UserAnswer.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/TimerDuration.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Stars.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RunnerQuestion.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RandomSeedProvider.kt
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt
shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/StringSetConverter.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonDao.kt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ThemeEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/TopParticipantListConverter.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogEntity.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDao.kt
shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestionEntity.kt
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration3to4.kt

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
     6	
     7	class LessonRunnerUseCases(
     8	    val startAttempt: suspend (LessonId, Difficulty) -> RunnerState,
     9	    val completeAttempt: suspend (RunnerState.Ready) -> RunnerState,
    10	    val abortAttempt: suspend (RunnerState.Ready) -> RunnerState,
    11	    val submitRating: suspend (String, LessonId, Int) -> Result<Unit>,
    12	)

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt | sed -n '1,360p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt | sed -n '1,360p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
     2	
     3	import com.arkivanov.essenty.instancekeeper.InstanceKeeper
     4	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
     6	import kotlinx.coroutines.CoroutineScope
     7	import kotlinx.coroutines.Dispatchers
     8	import kotlinx.coroutines.SupervisorJob
     9	import kotlinx.coroutines.cancel
    10	import kotlinx.coroutines.flow.MutableStateFlow
    11	import kotlin.coroutines.CoroutineContext
    12	
    13	class RunnerStateHolder(
    14	    mainContext: CoroutineContext = Dispatchers.Main.immediate,
    15	) : InstanceKeeper.Instance {
    16	    val scope = CoroutineScope(SupervisorJob() + mainContext)
    17	    val uiState: MutableStateFlow<RunnerUiState> = MutableStateFlow(RunnerUiState.Loading)
    18	    var domainState: RunnerState = RunnerState.Loading
    19	
    20	    override fun onDestroy() {
    21	        scope.cancel()
    22	        uiState.value = RunnerUiState.Loading
    23	        domainState = RunnerState.Loading
    24	    }
    25	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
     2	
     3	import com.arkivanov.decompose.ComponentContext
     4	import com.arkivanov.essenty.instancekeeper.getOrCreate
     5	import com.arkivanov.essenty.lifecycle.doOnDestroy
     6	import com.arkivanov.essenty.lifecycle.doOnStop
     7	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
     8	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
     9	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toEventSaveError
    10	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
    11	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUiReason
    12	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
    13	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
    14	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    15	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    16	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    17	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
    18	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
    19	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
    20	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
    21	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
    22	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    23	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    24	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    25	import kotlinx.coroutines.channels.Channel
    26	import kotlinx.coroutines.flow.Flow
    27	import kotlinx.coroutines.flow.StateFlow
    28	import kotlinx.coroutines.flow.asStateFlow
    29	import kotlinx.coroutines.flow.first
    30	import kotlinx.coroutines.flow.receiveAsFlow
    31	import kotlinx.coroutines.launch
    32	import kotlinx.datetime.Clock
    33	import kotlin.coroutines.CoroutineContext
    34	
    35	@Suppress("LongParameterList")
    36	class DefaultLessonRunnerRootComponent(
    37	    componentContext: ComponentContext,
    38	    private val lessonId: LessonId,
    39	    private val mode: Difficulty,
    40	    private val useCases: LessonRunnerUseCases,
    41	    private val lessonRepository: LessonRepository,
    42	    private val attemptRepository: LessonAttemptRepository,
    43	    private val clock: Clock,
    44	    mainContext: CoroutineContext = kotlinx.coroutines.Dispatchers.Main.immediate,
    45	) : ComponentContext by componentContext, LessonRunnerRootComponent {
    46	    private val stateHolder =
    47	        instanceKeeper.getOrCreate("runner_state_holder") {
    48	            RunnerStateHolder(mainContext)
    49	        }
    50	
    51	    private val scope get() = stateHolder.scope
    52	
    53	    override val uiState: StateFlow<RunnerUiState> = stateHolder.uiState.asStateFlow()
    54	
    55	    private val _events = Channel<RunnerEvent>(capacity = Channel.BUFFERED)
    56	    override val events: Flow<RunnerEvent> = _events.receiveAsFlow()
    57	
    58	    init {
    59	        lifecycle.doOnDestroy { _events.close() }
    60	        lifecycle.doOnStop { handlePause() }
    61	
    62	        if (stateHolder.uiState.value is RunnerUiState.Loading) {
    63	            scope.launch { triggerStart() }
    64	        }
    65	    }
    66	
    67	    override fun onAnswer(answer: UserAnswerDraft) {
    68	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
    69	        if (domainState.indexInPool >= domainState.playOrder.size) return
    70	        val userAnswer = answer.toUserAnswer()
    71	        val nowMs = clock.now().toEpochMilliseconds()
    72	        val newState = submitAnswer(domainState, userAnswer, nowMs)
    73	        stateHolder.domainState = newState
    74	        if (newState.indexInPool >= newState.playOrder.size) {
    75	            scope.launch { triggerComplete(newState) }
    76	        } else {
    77	            stateHolder.uiState.value = newState.toQuestionUiState()
    78	        }
    79	    }
    80	
    81	    override fun onTimeout() {
    82	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
    83	        if (domainState.indexInPool >= domainState.playOrder.size) return
    84	        val nowMs = clock.now().toEpochMilliseconds()
    85	        val newState = autoAnswerOnTimeout(domainState, domainState.seed, nowMs)
    86	        stateHolder.domainState = newState
    87	        if (newState.indexInPool >= newState.playOrder.size) {
    88	            scope.launch { triggerComplete(newState) }
    89	        } else {
    90	            stateHolder.uiState.value = newState.toQuestionUiState()
    91	        }
    92	    }
    93	
    94	    override fun onContinue() {
    95	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
    96	        if (!domainState.isPaused) return
    97	        val resumedState = domainState.copy(isPaused = false)
    98	        stateHolder.domainState = resumedState
    99	        stateHolder.uiState.value = resumedState.toQuestionUiState()
   100	    }
   101	
   102	    override fun onExit() {
   103	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   104	        scope.launch { triggerAbort(domainState) }
   105	    }
   106	
   107	    override fun onCrossButtonTap() {
   108	        val current = stateHolder.uiState.value
   109	        if (current is RunnerUiState.Question) {
   110	            stateHolder.uiState.value = current.copy(showExitConfirmDialog = true)
   111	        }
   112	    }
   113	
   114	    override fun onCrossConfirmed() {
   115	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   116	        scope.launch { triggerAbort(domainState) }
   117	    }
   118	
   119	    override fun onCrossCancelled() {
   120	        val current = stateHolder.uiState.value
   121	        if (current is RunnerUiState.Question) {
   122	            stateHolder.uiState.value = current.copy(showExitConfirmDialog = false)
   123	        }
   124	    }
   125	
   126	    override fun onSubmitRating(rating: Int) {
   127	        val domainState = stateHolder.domainState
   128	        val userId =
   129	            when (domainState) {
   130	                is RunnerState.Completed -> domainState.attempt.userId
   131	                is RunnerState.Aborted -> domainState.attempt.userId
   132	                else -> return
   133	            }
   134	        scope.launch {
   135	            val result = useCases.submitRating(userId, lessonId, rating)
   136	            if (result.isFailure) {
   137	                emitEvent(RunnerEvent.SaveRatingFailed)
   138	            }
   139	        }
   140	    }
   141	
   142	    override fun onFinish() {
   143	        emitEvent(RunnerEvent.NavigateBack)
   144	    }
   145	
   146	    override fun onBack() {
   147	        emitEvent(RunnerEvent.NavigateBack)
   148	    }
   149	
   150	    private fun handlePause() {
   151	        val domainState = stateHolder.domainState as? RunnerState.Ready ?: return
   152	        if (domainState.isPaused) return
   153	        if (domainState.indexInPool >= domainState.playOrder.size) return
   154	        val nowMs = clock.now().toEpochMilliseconds()
   155	        val newState = autoAnswerOnTimeout(domainState, domainState.seed, nowMs)
   156	        if (newState.indexInPool >= newState.playOrder.size) {
   157	            scope.launch { triggerComplete(newState) }
   158	        } else {
   159	            val pausedState = newState.copy(isPaused = true)
   160	            stateHolder.domainState = pausedState
   161	            stateHolder.uiState.value = pausedState.toQuestionUiState()
   162	        }
   163	    }
   164	
   165	    private suspend fun triggerStart() {
   166	        val result = useCases.startAttempt(lessonId, mode)
   167	        stateHolder.domainState = result
   168	        stateHolder.uiState.value =
   169	            when (result) {
   170	                is RunnerState.Ready -> result.toQuestionUiState()
   171	                is RunnerState.InitFailed -> RunnerUiState.InitFailed(result.reason.toUiReason())
   172	                else -> RunnerUiState.Loading
   173	            }
   174	    }
   175	
   176	    private suspend fun triggerComplete(readyState: RunnerState.Ready) {
   177	        val result = useCases.completeAttempt(readyState)
   178	        stateHolder.domainState = result
   179	        when (result) {
   180	            is RunnerState.Completed -> {
   181	                stateHolder.uiState.value =
   182	                    buildResultUiState(result.attempt, result.ratingPrompt, saveWarning = false)
   183	            }
   184	            is RunnerState.SaveFailed -> {
   185	                emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
   186	                stateHolder.uiState.value =
   187	                    buildResultUiState(
   188	                        result.attempt,
   189	                        showRatingPrompt = false,
   190	                        saveWarning = true,
   191	                    )
   192	            }
   193	            else -> {}
   194	        }
   195	    }
   196	
   197	    private suspend fun triggerAbort(readyState: RunnerState.Ready) {
   198	        val result = useCases.abortAttempt(readyState)
   199	        stateHolder.domainState = result
   200	        if (result is RunnerState.SaveFailed) {
   201	            emitEvent(RunnerEvent.SaveAttemptFailed(result.error.toEventSaveError()))
   202	        }
   203	        emitEvent(RunnerEvent.NavigateBack)
   204	    }
   205	
   206	    private suspend fun buildResultUiState(
   207	        attempt: Attempt,
   208	        showRatingPrompt: Boolean,
   209	        saveWarning: Boolean,
   210	    ): RunnerUiState.Result {
   211	        val lesson = lessonRepository.getById(attempt.lessonId)
   212	        val userAttempts = attemptRepository.observeByLesson(attempt.userId, attempt.lessonId).first()
   213	        val userAttemptCount = userAttempts.size
   214	        val userAveragePercentScore =
   215	            if (userAttempts.isEmpty()) {
   216	                0
   217	            } else {
   218	                userAttempts.sumOf { it.percentScore.raw } / userAttempts.size
   219	            }
   220	        return RunnerUiState.Result(
   221	            percentScore = attempt.percentScore,
   222	            mode = attempt.mode,
   223	            completedAt = attempt.completedAt,
   224	            hardUnlocked = computeHardUnlocked(userAttempts),
   225	            bestStarsRawTenths = computeBestStars(userAttempts).rawTenths,
   226	            lessonAverageRating = lesson?.averageRating,
   227	            lessonRatingCount = lesson?.ratingCount ?: 0,
   228	            top3 = lesson?.top3 ?: emptyList(),
   229	            userAttemptCount = userAttemptCount,
   230	            userAveragePercentScore = userAveragePercentScore,
   231	            showRatingPrompt = showRatingPrompt,
   232	            saveWarning = saveWarning,
   233	        )
   234	    }
   235	
   236	    private fun emitEvent(event: RunnerEvent) {
   237	        _events.trySend(event)
   238	    }
   239	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt | sed -n '1,380p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import android.app.Activity
     4	import android.view.WindowManager
     5	import androidx.compose.foundation.layout.Box
     6	import androidx.compose.foundation.layout.Column
     7	import androidx.compose.foundation.layout.fillMaxSize
     8	import androidx.compose.foundation.layout.padding
     9	import androidx.compose.material3.CircularProgressIndicator
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Scaffold
    12	import androidx.compose.material3.SnackbarHost
    13	import androidx.compose.material3.SnackbarHostState
    14	import androidx.compose.material3.Text
    15	import androidx.compose.material3.TextButton
    16	import androidx.compose.runtime.Composable
    17	import androidx.compose.runtime.DisposableEffect
    18	import androidx.compose.runtime.LaunchedEffect
    19	import androidx.compose.runtime.collectAsState
    20	import androidx.compose.runtime.getValue
    21	import androidx.compose.runtime.remember
    22	import androidx.compose.ui.Alignment
    23	import androidx.compose.ui.Modifier
    24	import androidx.compose.ui.platform.LocalContext
    25	import androidx.compose.ui.tooling.preview.Preview
    26	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    27	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
    28	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
    29	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
    30	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
    31	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    32	import com.tpov.schoolquiz.shared.core.question_schema.OptionId
    33	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    34	import kotlinx.coroutines.flow.Flow
    35	import kotlinx.coroutines.flow.MutableStateFlow
    36	import kotlinx.coroutines.flow.StateFlow
    37	import kotlinx.coroutines.flow.emptyFlow
    38	
    39	@Composable
    40	fun rememberFlagSecure(enabled: Boolean) {
    41	    val context = LocalContext.current
    42	    DisposableEffect(enabled) {
    43	        val window =
    44	            (context as? Activity)?.window
    45	                ?: return@DisposableEffect onDispose {}
    46	        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    47	        onDispose {
    48	            if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    49	        }
    50	    }
    51	}
    52	
    53	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    54	@Composable
    55	fun LessonRunnerScreen(
    56	    component: LessonRunnerRootComponent,
    57	    onNavigateBack: () -> Unit,
    58	    onSegmentClick: (Int) -> Unit,
    59	    modifier: Modifier = Modifier,
    60	) {
    61	    val state by component.uiState.collectAsState()
    62	    val snackbarHostState = remember { SnackbarHostState() }
    63	
    64	    val flagSecureEnabled =
    65	        when (val s = state) {
    66	            is RunnerUiState.Question -> s.isHard
    67	            is RunnerUiState.Result -> s.mode == Difficulty.HARD
    68	            else -> false
    69	        }
    70	    rememberFlagSecure(enabled = flagSecureEnabled)
    71	
    72	    LaunchedEffect(component) {
    73	        component.events.collect { event ->
    74	            when (event) {
    75	                is RunnerEvent.SaveAttemptFailed ->
    76	                    snackbarHostState.showSnackbar("Не удалось сохранить результат")
    77	                RunnerEvent.SaveRatingFailed ->
    78	                    snackbarHostState.showSnackbar("Не удалось сохранить оценку")
    79	                RunnerEvent.NavigateBack -> onNavigateBack()
    80	            }
    81	        }
    82	    }
    83	
    84	    val scaffoldColor =
    85	        when (val s = state) {
    86	            is RunnerUiState.Question ->
    87	                if (s.isHard) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.background
    88	            else -> MaterialTheme.colorScheme.background
    89	        }
    90	    Scaffold(
    91	        modifier = modifier.fillMaxSize(),
    92	        snackbarHost = { SnackbarHost(snackbarHostState) },
    93	        containerColor = scaffoldColor,
    94	    ) { innerPadding ->
    95	        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    96	            RunnerStateContent(state = state, component = component)
    97	        }
    98	    }
    99	}
   100	
   101	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   102	@Composable
   103	private fun RunnerStateContent(
   104	    state: RunnerUiState,
   105	    component: LessonRunnerRootComponent,
   106	) {
   107	    when (state) {
   108	        RunnerUiState.Loading ->
   109	            Box(modifier = Modifier.fillMaxSize()) {
   110	                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
   111	            }
   112	        is RunnerUiState.InitFailed ->
   113	            Box(modifier = Modifier.fillMaxSize()) {
   114	                InitFailedContent(
   115	                    reason = state.reason,
   116	                    onBack = { component.onBack() },
   117	                    modifier = Modifier.align(Alignment.Center),
   118	                )
   119	            }
   120	        is RunnerUiState.Question ->
   121	            QuestionStateContent(state = state, component = component)
   122	        is RunnerUiState.Result ->
   123	            ResultContent(
   124	                state = state,
   125	                onSubmitRating = { component.onSubmitRating(it) },
   126	                onFinish = { component.onFinish() },
   127	                modifier = Modifier.fillMaxSize(),
   128	            )
   129	    }
   130	}
   131	
   132	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   133	@Composable
   134	private fun InitFailedContent(
   135	    reason: RunnerUiState.InitFailureReason,
   136	    onBack: () -> Unit,
   137	    modifier: Modifier = Modifier,
   138	) {
   139	    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
   140	        Text(text = reason.displayMessage(), style = MaterialTheme.typography.bodyLarge)
   141	        TextButton(onClick = onBack) { Text("Назад") }
   142	    }
   143	}
   144	
   145	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   146	@Composable
   147	private fun QuestionStateContent(
   148	    state: RunnerUiState.Question,
   149	    component: LessonRunnerRootComponent,
   150	) {
   151	    Column(modifier = Modifier.fillMaxSize()) {
   152	        QuestionProgressHeader(
   153	            indexInPool = state.indexInPool,
   154	            totalInPool = state.totalInPool,
   155	            deadlineMs = state.deadlineMs,
   156	            isPaused = state.isPaused,
   157	            isHard = state.isHard,
   158	            onCrossClick = { component.onCrossButtonTap() },
   159	            onTimeout = { component.onTimeout() },
   160	        )
   161	        Box(modifier = Modifier.weight(1f)) {
   162	            QuestionTypeContent(qState = state.questionUiState, component = component)
   163	        }
   164	    }
   165	    if (state.isPaused) {
   166	        BlockingResumeDialog(
   167	            onContinue = { component.onContinue() },
   168	            onExit = { component.onExit() },
   169	        )
   170	    }
   171	    if (state.showExitConfirmDialog) {
   172	        ExitConfirmDialog(
   173	            onConfirm = { component.onCrossConfirmed() },
   174	            onDismiss = { component.onCrossCancelled() },
   175	        )
   176	    }
   177	}
   178	
   179	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
   180	@Composable
   181	private fun QuestionTypeContent(
   182	    qState: QuestionUiState,
   183	    component: LessonRunnerRootComponent,
   184	) {
   185	    when (qState) {
   186	        is QuestionUiState.SingleChoice ->
   187	            SingleChoiceContent(
   188	                state = qState,
   189	                onOptionSelected = { optionId ->
   190	                    component.onAnswer(UserAnswerDraft.SingleChoiceDraft(OptionId(optionId)))
   191	                },
   192	                modifier = Modifier.fillMaxSize(),
   193	            )
   194	        is QuestionUiState.MultipleChoice ->
   195	            MultipleChoiceContent(
   196	                state = qState,
   197	                onOptionToggled = { optionId ->
   198	                    val newSelected =
   199	                        if (optionId in qState.selectedIds) {
   200	                            qState.selectedIds - optionId
   201	                        } else {
   202	                            qState.selectedIds + optionId
   203	                        }
   204	                    component.onAnswer(
   205	                        UserAnswerDraft.MultipleChoiceDraft(newSelected.map { OptionId(it) }.toSet()),
   206	                    )
   207	                },
   208	                onSubmit = {
   209	                    component.onAnswer(
   210	                        UserAnswerDraft.MultipleChoiceDraft(qState.selectedIds.map { OptionId(it) }.toSet()),
   211	                    )
   212	                },
   213	                modifier = Modifier.fillMaxSize(),
   214	            )
   215	        is QuestionUiState.Ordering ->
   216	            OrderingContent(
   217	                state = qState,
   218	                onMoveUp = { index ->
   219	                    if (index > 0) {
   220	                        val items =
   221	                            qState.items.toMutableList().also {
   222	                                val tmp = it[index]
   223	                                it[index] = it[index - 1]
   224	                                it[index - 1] = tmp
   225	                            }
   226	                        component.onAnswer(UserAnswerDraft.OrderingDraft(items.map { OptionId(it.id) }))
   227	                    }
   228	                },
   229	                onMoveDown = { index ->
   230	                    if (index < qState.items.lastIndex) {
   231	                        val items =
   232	                            qState.items.toMutableList().also {
   233	                                val tmp = it[index]
   234	                                it[index] = it[index + 1]
   235	                                it[index + 1] = tmp
   236	                            }
   237	                        component.onAnswer(UserAnswerDraft.OrderingDraft(items.map { OptionId(it.id) }))
   238	                    }
   239	                },
   240	                onSubmit = {
   241	                    component.onAnswer(UserAnswerDraft.OrderingDraft(qState.items.map { OptionId(it.id) }))
   242	                },
   243	                modifier = Modifier.fillMaxSize(),
   244	            )
   245	        is QuestionUiState.FillBlank ->
   246	            // Open Question: FillBlankDraft requires Map<BlankId, CandidateId?> but
   247	            // QuestionUiState.FillBlank.filledValues is Map<Int, String> with no BlankId/CandidateId
   248	            // mapping. Wiring blocked until mapper exposes blankId/candidateId per TemplatePart.Blank.
   249	            FillBlankContent(
   250	                state = qState,
   251	                candidates = emptyList(),
   252	                onCandidateSelected = {},
   253	                onBlankCleared = {},
   254	                onSubmit = { component.onAnswer(UserAnswerDraft.FillBlankDraft(emptyMap())) },
   255	                modifier = Modifier.fillMaxSize(),
   256	            )
   257	    }
   258	}
   259	
   260	private fun RunnerUiState.InitFailureReason.displayMessage(): String =
   261	    when (this) {
   262	        RunnerUiState.InitFailureReason.AuthRequired -> "Требуется авторизация"
   263	        RunnerUiState.InitFailureReason.LessonNotFound -> "Урок не найден"
   264	        RunnerUiState.InitFailureReason.EmptyPool -> "Нет доступных вопросов"
   265	        RunnerUiState.InitFailureReason.NoValidQuestions -> "Вопросы недействительны"
   266	    }
   267	
   268	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   269	@Preview(showBackground = true)
   270	@Composable
   271	private fun LessonRunnerScreenLoadingPreview() {
   272	    SchoolQuizTheme {
   273	        LessonRunnerScreen(
   274	            component = PreviewLessonRunnerComponent(RunnerUiState.Loading),
   275	            onNavigateBack = {},
   276	            onSegmentClick = {},
   277	        )
   278	    }
   279	}
   280	
   281	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   282	@Preview(showBackground = true)
   283	@Composable
   284	private fun LessonRunnerScreenInitFailedPreview() {
   285	    SchoolQuizTheme {
   286	        LessonRunnerScreen(
   287	            component =
   288	                PreviewLessonRunnerComponent(
   289	                    RunnerUiState.InitFailed(RunnerUiState.InitFailureReason.AuthRequired),
   290	                ),
   291	            onNavigateBack = {},
   292	            onSegmentClick = {},
   293	        )
   294	    }
   295	}
   296	
   297	private class PreviewLessonRunnerComponent(initialState: RunnerUiState) : LessonRunnerRootComponent {
   298	    override val uiState: StateFlow<RunnerUiState> = MutableStateFlow(initialState)
   299	    override val events: Flow<RunnerEvent> = emptyFlow()
   300	
   301	    override fun onAnswer(answer: UserAnswerDraft) = Unit
   302	
   303	    override fun onTimeout() = Unit
   304	
   305	    override fun onContinue() = Unit
   306	
   307	    override fun onExit() = Unit
   308	
   309	    override fun onCrossButtonTap() = Unit
   310	
   311	    override fun onCrossConfirmed() = Unit
   312	
   313	    override fun onCrossCancelled() = Unit
   314	
   315	    override fun onSubmitRating(rating: Int) = Unit
   316	
   317	    override fun onFinish() = Unit
   318	
   319	    override fun onBack() = Unit
   320	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt | sed -n '1,320p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.fillMaxWidth
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.foundation.rememberScrollState
     8	import androidx.compose.foundation.verticalScroll
     9	import androidx.compose.material3.Button
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Text
    12	import androidx.compose.runtime.Composable
    13	import androidx.compose.ui.Alignment
    14	import androidx.compose.ui.Modifier
    15	import androidx.compose.ui.tooling.preview.Preview
    16	import androidx.compose.ui.unit.dp
    17	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    18	import com.tpov.schoolquiz.android.core.designsystem.components.StarRating
    19	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
    20	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
    21	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    22	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
    23	
    24	private const val PERFECT_SCORE = 100
    25	private const val STARS_DIVISOR = 10
    26	
    27	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    28	@Composable
    29	fun ResultContent(
    30	    state: RunnerUiState.Result,
    31	    onSubmitRating: (Int) -> Unit,
    32	    onFinish: () -> Unit,
    33	    modifier: Modifier = Modifier,
    34	) {
    35	    val subtitle =
    36	        when {
    37	            state.mode == Difficulty.EASY && state.percentScore.raw == PERFECT_SCORE && state.hardUnlocked ->
    38	                "Поздравляем! Сложные вопросы доступны"
    39	            state.mode == Difficulty.HARD && state.percentScore.raw == PERFECT_SCORE ->
    40	                "100% сложные! Вы прошли урок полностью"
    41	            else -> "Урок завершён"
    42	        }
    43	
    44	    Column(
    45	        modifier =
    46	            modifier
    47	                .fillMaxWidth()
    48	                .verticalScroll(rememberScrollState())
    49	                .padding(16.dp),
    50	        horizontalAlignment = Alignment.CenterHorizontally,
    51	        verticalArrangement = Arrangement.spacedBy(16.dp),
    52	    ) {
    53	        Text(
    54	            text = "${state.percentScore.raw}%",
    55	            style = MaterialTheme.typography.displayLarge,
    56	        )
    57	        Text(
    58	            text = subtitle,
    59	            style = MaterialTheme.typography.bodyLarge,
    60	        )
    61	        StarRating(rating = state.bestStarsRawTenths / STARS_DIVISOR.toFloat())
    62	        Text(
    63	            text = "Попыток: ${state.userAttemptCount} | Средний %: ${state.userAveragePercentScore}",
    64	            style = MaterialTheme.typography.bodyMedium,
    65	            color = MaterialTheme.colorScheme.onSurfaceVariant,
    66	        )
    67	        if (state.saveWarning) {
    68	            Text(
    69	                text = "⚠ Результат не сохранён",
    70	                color = MaterialTheme.colorScheme.error,
    71	                style = MaterialTheme.typography.bodyMedium,
    72	            )
    73	        }
    74	        if (state.showRatingPrompt) {
    75	            RatingPromptSection(onSubmitRating = onSubmitRating)
    76	        }
    77	        if (state.top3.isNotEmpty()) {
    78	            Top3Section(top3 = state.top3)
    79	        }
    80	        Button(
    81	            onClick = onFinish,
    82	            modifier = Modifier.fillMaxWidth(),
    83	        ) {
    84	            Text("Завершить")
    85	        }
    86	    }
    87	}
    88	
    89	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    90	@Preview(showBackground = true)
    91	@Composable
    92	private fun ResultContentPreview() {
    93	    SchoolQuizTheme {
    94	        ResultContent(
    95	            state =
    96	                RunnerUiState.Result(
    97	                    percentScore = PercentScore(85),
    98	                    mode = Difficulty.EASY,
    99	                    completedAt = 0L,
   100	                    hardUnlocked = false,
   101	                    bestStarsRawTenths = 20,
   102	                    lessonAverageRating = 2.5f,
   103	                    lessonRatingCount = 10,
   104	                    top3 =
   105	                        listOf(
   106	                            TopParticipant("Alice", null, 95),
   107	                            TopParticipant("Bob", null, 88),
   108	                        ),
   109	                    userAttemptCount = 3,
   110	                    userAveragePercentScore = 75,
   111	                    showRatingPrompt = true,
   112	                    saveWarning = false,
   113	                ),
   114	            onSubmitRating = {},
   115	            onFinish = {},
   116	        )
   117	    }
   118	}
   119	
   120	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
   121	@Preview(showBackground = true)
   122	@Composable
   123	private fun ResultContentSaveWarningPreview() {
   124	    SchoolQuizTheme {
   125	        ResultContent(
   126	            state =
   127	                RunnerUiState.Result(
   128	                    percentScore = PercentScore(60),
   129	                    mode = Difficulty.HARD,
   130	                    completedAt = 0L,
   131	                    hardUnlocked = false,
   132	                    bestStarsRawTenths = 10,
   133	                    lessonAverageRating = null,
   134	                    lessonRatingCount = 0,
   135	                    top3 = emptyList(),
   136	                    userAttemptCount = 1,
   137	                    userAveragePercentScore = 60,
   138	                    showRatingPrompt = false,
   139	                    saveWarning = true,
   140	                ),
   141	            onSubmitRating = {},
   142	            onFinish = {},
   143	        )
   144	    }
   145	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event
     2	
     3	enum class SaveError { IoError, Unknown }
     4	
     5	sealed interface RunnerEvent {
     6	    data class SaveAttemptFailed(val error: SaveError) : RunnerEvent
     7	
     8	    data object SaveRatingFailed : RunnerEvent
     9	
    10	    data object NavigateBack : RunnerEvent
    11	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Row
     5	import androidx.compose.foundation.layout.fillMaxWidth
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.material3.MaterialTheme
     8	import androidx.compose.material3.Text
     9	import androidx.compose.runtime.Composable
    10	import androidx.compose.runtime.LaunchedEffect
    11	import androidx.compose.runtime.getValue
    12	import androidx.compose.runtime.mutableLongStateOf
    13	import androidx.compose.runtime.remember
    14	import androidx.compose.runtime.setValue
    15	import androidx.compose.ui.Alignment
    16	import androidx.compose.ui.Modifier
    17	import androidx.compose.ui.tooling.preview.Preview
    18	import androidx.compose.ui.unit.dp
    19	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    20	import kotlinx.coroutines.delay
    21	import kotlinx.coroutines.isActive
    22	
    23	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    24	@Composable
    25	fun QuestionProgressHeader(
    26	    indexInPool: Int,
    27	    totalInPool: Int,
    28	    deadlineMs: Long,
    29	    isPaused: Boolean,
    30	    isHard: Boolean,
    31	    onCrossClick: () -> Unit,
    32	    onTimeout: () -> Unit,
    33	    modifier: Modifier = Modifier,
    34	) {
    35	    var remainingMs by remember(indexInPool, deadlineMs) {
    36	        mutableLongStateOf((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0))
    37	    }
    38	
    39	    LaunchedEffect(indexInPool, deadlineMs, isPaused) {
    40	        if (isPaused) return@LaunchedEffect
    41	        while (isActive) {
    42	            val remaining = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0)
    43	            remainingMs = remaining
    44	            if (remaining == 0L) {
    45	                onTimeout()
    46	                break
    47	            }
    48	            delay(100L)
    49	        }
    50	    }
    51	
    52	    val timerColor =
    53	        if (isHard) {
    54	            MaterialTheme.colorScheme.error
    55	        } else {
    56	            MaterialTheme.colorScheme.onSurface
    57	        }
    58	
    59	    Row(
    60	        modifier =
    61	            modifier
    62	                .fillMaxWidth()
    63	                .padding(horizontal = 16.dp, vertical = 8.dp),
    64	        verticalAlignment = Alignment.CenterVertically,
    65	        horizontalArrangement = Arrangement.SpaceBetween,
    66	    ) {
    67	        CrossButton(onClick = onCrossClick)
    68	        Text(
    69	            text = "${indexInPool + 1} / $totalInPool",
    70	            style = MaterialTheme.typography.titleMedium,
    71	        )
    72	        Text(
    73	            text = "${(remainingMs + 999L) / 1000L}s",
    74	            style = MaterialTheme.typography.titleMedium,
    75	            color = timerColor,
    76	        )
    77	    }
    78	}
    79	
    80	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    81	@Preview(showBackground = true)
    82	@Composable
    83	private fun QuestionProgressHeaderPreview() {
    84	    SchoolQuizTheme {
    85	        QuestionProgressHeader(
    86	            indexInPool = 2,
    87	            totalInPool = 10,
    88	            deadlineMs = System.currentTimeMillis() + 30_000L,
    89	            isPaused = false,
    90	            isHard = false,
    91	            onCrossClick = {},
    92	            onTimeout = {},
    93	        )
    94	    }
    95	}
    96	
    97	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    98	@Preview(showBackground = true)
    99	@Composable
   100	private fun QuestionProgressHeaderHardPreview() {
   101	    SchoolQuizTheme {
   102	        QuestionProgressHeader(
   103	            indexInPool = 0,
   104	            totalInPool = 5,
   105	            deadlineMs = System.currentTimeMillis() + 10_000L,
   106	            isPaused = false,
   107	            isHard = true,
   108	            onCrossClick = {},
   109	            onTimeout = {},
   110	        )
   111	    }
   112	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Row
     6	import androidx.compose.foundation.layout.fillMaxWidth
     7	import androidx.compose.foundation.layout.padding
     8	import androidx.compose.foundation.lazy.grid.GridCells
     9	import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
    10	import androidx.compose.foundation.lazy.grid.items
    11	import androidx.compose.material3.Button
    12	import androidx.compose.material3.Checkbox
    13	import androidx.compose.material3.MaterialTheme
    14	import androidx.compose.material3.Text
    15	import androidx.compose.runtime.Composable
    16	import androidx.compose.ui.Alignment
    17	import androidx.compose.ui.Modifier
    18	import androidx.compose.ui.tooling.preview.Preview
    19	import androidx.compose.ui.unit.dp
    20	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    21	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
    22	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
    23	
    24	private const val SINGLE_COLUMN_MAX_OPTIONS = 5
    25	
    26	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    27	@Composable
    28	fun MultipleChoiceContent(
    29	    state: QuestionUiState.MultipleChoice,
    30	    onOptionToggled: (String) -> Unit,
    31	    onSubmit: () -> Unit,
    32	    modifier: Modifier = Modifier,
    33	) {
    34	    val columns = if (state.options.size <= SINGLE_COLUMN_MAX_OPTIONS) 1 else 2
    35	
    36	    Column(
    37	        modifier =
    38	            modifier
    39	                .fillMaxWidth()
    40	                .padding(16.dp),
    41	        horizontalAlignment = Alignment.CenterHorizontally,
    42	        verticalArrangement = Arrangement.spacedBy(12.dp),
    43	    ) {
    44	        Text(
    45	            text = state.questionText,
    46	            style = MaterialTheme.typography.titleMedium,
    47	        )
    48	        if (state.hasImage && state.imageUrl != null) {
    49	            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
    50	        }
    51	        LazyVerticalGrid(
    52	            columns = GridCells.Fixed(columns),
    53	            horizontalArrangement = Arrangement.spacedBy(8.dp),
    54	            verticalArrangement = Arrangement.spacedBy(4.dp),
    55	            modifier = Modifier.fillMaxWidth(),
    56	        ) {
    57	            items(state.options) { option ->
    58	                Row(
    59	                    verticalAlignment = Alignment.CenterVertically,
    60	                    modifier = Modifier.fillMaxWidth(),
    61	                ) {
    62	                    Checkbox(
    63	                        checked = option.id in state.selectedIds,
    64	                        onCheckedChange = { onOptionToggled(option.id) },
    65	                    )
    66	                    Text(
    67	                        text = option.text,
    68	                        style = MaterialTheme.typography.bodyMedium,
    69	                    )
    70	                }
    71	            }
    72	        }
    73	        Button(
    74	            onClick = onSubmit,
    75	            modifier = Modifier.fillMaxWidth(),
    76	        ) {
    77	            Text("Ответить")
    78	        }
    79	    }
    80	}
    81	
    82	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    83	@Preview(showBackground = true)
    84	@Composable
    85	private fun MultipleChoiceContentPreview() {
    86	    SchoolQuizTheme {
    87	        MultipleChoiceContent(
    88	            state =
    89	                QuestionUiState.MultipleChoice(
    90	                    questionText = "Выберите все правильные утверждения",
    91	                    hasImage = false,
    92	                    imageUrl = null,
    93	                    options =
    94	                        listOf(
    95	                            OptionUi("1", "Kotlin — JVM язык"),
    96	                            OptionUi("2", "Java — скриптовый язык"),
    97	                            OptionUi("3", "Kotlin поддерживает null safety"),
    98	                        ),
    99	                    selectedIds = setOf("1"),
   100	                ),
   101	            onOptionToggled = {},
   102	            onSubmit = {},
   103	        )
   104	    }
   105	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Row
     6	import androidx.compose.foundation.layout.fillMaxWidth
     7	import androidx.compose.foundation.layout.padding
     8	import androidx.compose.material.icons.Icons
     9	import androidx.compose.material.icons.filled.KeyboardArrowDown
    10	import androidx.compose.material.icons.filled.KeyboardArrowUp
    11	import androidx.compose.material3.Button
    12	import androidx.compose.material3.Icon
    13	import androidx.compose.material3.IconButton
    14	import androidx.compose.material3.MaterialTheme
    15	import androidx.compose.material3.Text
    16	import androidx.compose.runtime.Composable
    17	import androidx.compose.ui.Alignment
    18	import androidx.compose.ui.Modifier
    19	import androidx.compose.ui.draw.alpha
    20	import androidx.compose.ui.tooling.preview.Preview
    21	import androidx.compose.ui.unit.dp
    22	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    23	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
    24	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
    25	
    26	private const val DISABLED_ALPHA = 0.3f
    27	
    28	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    29	@Composable
    30	fun OrderingContent(
    31	    state: QuestionUiState.Ordering,
    32	    onMoveUp: (Int) -> Unit,
    33	    onMoveDown: (Int) -> Unit,
    34	    onSubmit: () -> Unit,
    35	    modifier: Modifier = Modifier,
    36	) {
    37	    val lastIndex = state.items.lastIndex
    38	
    39	    Column(
    40	        modifier =
    41	            modifier
    42	                .fillMaxWidth()
    43	                .padding(16.dp),
    44	        verticalArrangement = Arrangement.spacedBy(8.dp),
    45	    ) {
    46	        Text(
    47	            text = state.questionText,
    48	            style = MaterialTheme.typography.titleMedium,
    49	        )
    50	        if (state.hasImage && state.imageUrl != null) {
    51	            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
    52	        }
    53	        state.items.forEachIndexed { index, item ->
    54	            Row(
    55	                verticalAlignment = Alignment.CenterVertically,
    56	                modifier = Modifier.fillMaxWidth(),
    57	            ) {
    58	                IconButton(
    59	                    onClick = { onMoveUp(index) },
    60	                    enabled = index > 0,
    61	                    modifier = Modifier.alpha(if (index > 0) 1f else DISABLED_ALPHA),
    62	                ) {
    63	                    Icon(
    64	                        imageVector = Icons.Default.KeyboardArrowUp,
    65	                        contentDescription = "Переместить вверх",
    66	                    )
    67	                }
    68	                Text(
    69	                    text = item.text,
    70	                    modifier = Modifier.weight(1f),
    71	                    style = MaterialTheme.typography.bodyMedium,
    72	                )
    73	                IconButton(
    74	                    onClick = { onMoveDown(index) },
    75	                    enabled = index < lastIndex,
    76	                    modifier = Modifier.alpha(if (index < lastIndex) 1f else DISABLED_ALPHA),
    77	                ) {
    78	                    Icon(
    79	                        imageVector = Icons.Default.KeyboardArrowDown,
    80	                        contentDescription = "Переместить вниз",
    81	                    )
    82	                }
    83	            }
    84	        }
    85	        Button(
    86	            onClick = onSubmit,
    87	            modifier = Modifier.fillMaxWidth(),
    88	        ) {
    89	            Text("Готово")
    90	        }
    91	    }
    92	}
    93	
    94	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    95	@Preview(showBackground = true)
    96	@Composable
    97	private fun OrderingContentPreview() {
    98	    SchoolQuizTheme {
    99	        OrderingContent(
   100	            state =
   101	                QuestionUiState.Ordering(
   102	                    questionText = "Расставьте в правильном порядке",
   103	                    hasImage = false,
   104	                    imageUrl = null,
   105	                    items =
   106	                        listOf(
   107	                            OptionUi("1", "Первый шаг"),
   108	                            OptionUi("2", "Второй шаг"),
   109	                            OptionUi("3", "Третий шаг"),
   110	                        ),
   111	                ),
   112	            onMoveUp = {},
   113	            onMoveDown = {},
   114	            onSubmit = {},
   115	        )
   116	    }
   117	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt | sed -n '1,300p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.fillMaxWidth
     6	import androidx.compose.foundation.layout.padding
     7	import androidx.compose.foundation.lazy.grid.GridCells
     8	import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
     9	import androidx.compose.foundation.lazy.grid.items
    10	import androidx.compose.material3.Button
    11	import androidx.compose.material3.ButtonDefaults
    12	import androidx.compose.material3.MaterialTheme
    13	import androidx.compose.material3.Text
    14	import androidx.compose.runtime.Composable
    15	import androidx.compose.ui.Alignment
    16	import androidx.compose.ui.Modifier
    17	import androidx.compose.ui.tooling.preview.Preview
    18	import androidx.compose.ui.unit.dp
    19	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    20	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
    21	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
    22	
    23	private const val SINGLE_COLUMN_MAX_OPTIONS = 5
    24	
    25	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    26	@Composable
    27	fun SingleChoiceContent(
    28	    state: QuestionUiState.SingleChoice,
    29	    onOptionSelected: (String) -> Unit,
    30	    modifier: Modifier = Modifier,
    31	) {
    32	    val columns = if (state.options.size <= SINGLE_COLUMN_MAX_OPTIONS) 1 else 2
    33	
    34	    Column(
    35	        modifier =
    36	            modifier
    37	                .fillMaxWidth()
    38	                .padding(16.dp),
    39	        horizontalAlignment = Alignment.CenterHorizontally,
    40	        verticalArrangement = Arrangement.spacedBy(12.dp),
    41	    ) {
    42	        Text(
    43	            text = state.questionText,
    44	            style = MaterialTheme.typography.titleMedium,
    45	        )
    46	        if (state.hasImage && state.imageUrl != null) {
    47	            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
    48	        }
    49	        LazyVerticalGrid(
    50	            columns = GridCells.Fixed(columns),
    51	            horizontalArrangement = Arrangement.spacedBy(8.dp),
    52	            verticalArrangement = Arrangement.spacedBy(8.dp),
    53	            modifier = Modifier.fillMaxWidth(),
    54	        ) {
    55	            items(state.options) { option ->
    56	                val isSelected = option.id == state.selectedOptionId
    57	                Button(
    58	                    onClick = { onOptionSelected(option.id) },
    59	                    enabled = state.selectedOptionId == null,
    60	                    colors =
    61	                        if (isSelected) {
    62	                            ButtonDefaults.buttonColors(
    63	                                containerColor = MaterialTheme.colorScheme.primary,
    64	                            )
    65	                        } else {
    66	                            ButtonDefaults.buttonColors(
    67	                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
    68	                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    69	                            )
    70	                        },
    71	                    modifier = Modifier.fillMaxWidth(),
    72	                ) {
    73	                    Text(text = option.text)
    74	                }
    75	            }
    76	        }
    77	    }
    78	}
    79	
    80	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    81	@Preview(showBackground = true)
    82	@Composable
    83	private fun SingleChoiceContentPreview() {
    84	    SchoolQuizTheme {
    85	        SingleChoiceContent(
    86	            state =
    87	                QuestionUiState.SingleChoice(
    88	                    questionText = "Какой язык программирования создан в JetBrains?",
    89	                    hasImage = false,
    90	                    imageUrl = null,
    91	                    options =
    92	                        listOf(
    93	                            OptionUi("1", "Kotlin"),
    94	                            OptionUi("2", "Java"),
    95	                            OptionUi("3", "Scala"),
    96	                            OptionUi("4", "Groovy"),
    97	                        ),
    98	                    selectedOptionId = null,
    99	                ),
   100	            onOptionSelected = {},
   101	        )
   102	    }
   103	}
   104	
   105	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   106	@Preview(showBackground = true)
   107	@Composable
   108	private fun SingleChoiceContentTwoColumnsPreview() {
   109	    SchoolQuizTheme {
   110	        SingleChoiceContent(
   111	            state =
   112	                QuestionUiState.SingleChoice(
   113	                    questionText = "Выберите правильный ответ",
   114	                    hasImage = false,
   115	                    imageUrl = null,
   116	                    options =
   117	                        listOf(
   118	                            OptionUi("1", "Ответ 1"),
   119	                            OptionUi("2", "Ответ 2"),
   120	                            OptionUi("3", "Ответ 3"),
   121	                            OptionUi("4", "Ответ 4"),
   122	                            OptionUi("5", "Ответ 5"),
   123	                            OptionUi("6", "Ответ 6"),
   124	                        ),
   125	                    selectedOptionId = null,
   126	                ),
   127	            onOptionSelected = {},
   128	        )
   129	    }
   130	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.ExperimentalLayoutApi
     6	import androidx.compose.foundation.layout.FlowRow
     7	import androidx.compose.foundation.layout.Spacer
     8	import androidx.compose.foundation.layout.fillMaxWidth
     9	import androidx.compose.foundation.layout.height
    10	import androidx.compose.foundation.layout.padding
    11	import androidx.compose.foundation.lazy.grid.GridCells
    12	import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
    13	import androidx.compose.foundation.lazy.grid.itemsIndexed
    14	import androidx.compose.material3.Button
    15	import androidx.compose.material3.ButtonDefaults
    16	import androidx.compose.material3.MaterialTheme
    17	import androidx.compose.material3.Text
    18	import androidx.compose.runtime.Composable
    19	import androidx.compose.ui.Modifier
    20	import androidx.compose.ui.draw.alpha
    21	import androidx.compose.ui.tooling.preview.Preview
    22	import androidx.compose.ui.unit.dp
    23	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    24	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
    25	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
    26	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
    27	
    28	private const val CONSUMED_ALPHA = 0.4f
    29	private const val CANDIDATES_COLUMNS = 5
    30	
    31	@OptIn(ExperimentalLayoutApi::class)
    32	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    33	@Composable
    34	fun FillBlankContent(
    35	    state: QuestionUiState.FillBlank,
    36	    candidates: List<OptionUi>,
    37	    onCandidateSelected: (candidateText: String) -> Unit,
    38	    onBlankCleared: (blankIndex: Int) -> Unit,
    39	    onSubmit: () -> Unit,
    40	    modifier: Modifier = Modifier,
    41	) {
    42	    val usedTexts = state.filledValues.values.toSet()
    43	
    44	    Column(
    45	        modifier =
    46	            modifier
    47	                .fillMaxWidth()
    48	                .padding(16.dp),
    49	        verticalArrangement = Arrangement.spacedBy(12.dp),
    50	    ) {
    51	        if (state.hasImage && state.imageUrl != null) {
    52	            QuestionImage(url = state.imageUrl, modifier = Modifier.fillMaxWidth())
    53	        }
    54	        FlowRow(
    55	            horizontalArrangement = Arrangement.spacedBy(4.dp),
    56	            verticalArrangement = Arrangement.spacedBy(4.dp),
    57	        ) {
    58	            state.templateParts.forEach { part ->
    59	                when (part) {
    60	                    is TemplatePart.Text -> {
    61	                        Text(
    62	                            text = part.content,
    63	                            style = MaterialTheme.typography.bodyLarge,
    64	                        )
    65	                    }
    66	                    is TemplatePart.Blank -> {
    67	                        val filledText = state.filledValues[part.index]
    68	                        Button(
    69	                            onClick = { if (filledText != null) onBlankCleared(part.index) },
    70	                            colors =
    71	                                if (filledText != null) {
    72	                                    ButtonDefaults.buttonColors(
    73	                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
    74	                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    75	                                    )
    76	                                } else {
    77	                                    ButtonDefaults.buttonColors(
    78	                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    79	                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    80	                                    )
    81	                                },
    82	                        ) {
    83	                            Text(text = filledText ?: "___")
    84	                        }
    85	                    }
    86	                }
    87	            }
    88	        }
    89	        Spacer(modifier = Modifier.height(8.dp))
    90	        if (candidates.isNotEmpty()) {
    91	            LazyVerticalGrid(
    92	                columns = GridCells.Fixed(CANDIDATES_COLUMNS),
    93	                horizontalArrangement = Arrangement.spacedBy(4.dp),
    94	                verticalArrangement = Arrangement.spacedBy(4.dp),
    95	                modifier = Modifier.fillMaxWidth(),
    96	            ) {
    97	                itemsIndexed(candidates) { _, candidate ->
    98	                    val isUsed = candidate.text in usedTexts
    99	                    Button(
   100	                        onClick = { if (!isUsed) onCandidateSelected(candidate.text) },
   101	                        enabled = !isUsed,
   102	                        modifier = Modifier.alpha(if (isUsed) CONSUMED_ALPHA else 1f),
   103	                        colors =
   104	                            ButtonDefaults.buttonColors(
   105	                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
   106	                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
   107	                            ),
   108	                    ) {
   109	                        Text(text = candidate.text)
   110	                    }
   111	                }
   112	            }
   113	        }
   114	        Button(
   115	            onClick = onSubmit,
   116	            modifier = Modifier.fillMaxWidth(),
   117	        ) {
   118	            Text("Готово")
   119	        }
   120	    }
   121	}
   122	
   123	@OptIn(ExperimentalLayoutApi::class)
   124	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
   125	@Preview(showBackground = true)
   126	@Composable
   127	private fun FillBlankContentPreview() {
   128	    SchoolQuizTheme {
   129	        FillBlankContent(
   130	            state =
   131	                QuestionUiState.FillBlank(
   132	                    questionText = "Kotlin ___ JVM ___",
   133	                    hasImage = false,
   134	                    imageUrl = null,
   135	                    templateParts =
   136	                        listOf(
   137	                            TemplatePart.Text("Kotlin "),
   138	                            TemplatePart.Blank(0, "___"),
   139	                            TemplatePart.Text(" JVM "),
   140	                            TemplatePart.Blank(1, "___"),
   141	                        ),
   142	                    filledValues = mapOf(0 to "работает"),
   143	                ),
   144	            candidates =
   145	                listOf(
   146	                    OptionUi("1", "работает"),
   147	                    OptionUi("2", "на"),
   148	                    OptionUi("3", "платформе"),
   149	                ),
   150	            onCandidateSelected = {},
   151	            onBlankCleared = {},
   152	            onSubmit = {},
   153	        )
   154	    }
   155	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
     2	
     3	sealed interface QuestionUiState {
     4	    val questionText: String
     5	    val hasImage: Boolean
     6	
     7	    data class SingleChoice(
     8	        override val questionText: String,
     9	        override val hasImage: Boolean,
    10	        val imageUrl: String?,
    11	        val options: List<OptionUi>,
    12	        val selectedOptionId: String?,
    13	    ) : QuestionUiState
    14	
    15	    data class MultipleChoice(
    16	        override val questionText: String,
    17	        override val hasImage: Boolean,
    18	        val imageUrl: String?,
    19	        val options: List<OptionUi>,
    20	        val selectedIds: Set<String>,
    21	    ) : QuestionUiState
    22	
    23	    data class Ordering(
    24	        override val questionText: String,
    25	        override val hasImage: Boolean,
    26	        val imageUrl: String?,
    27	        val items: List<OptionUi>,
    28	    ) : QuestionUiState
    29	
    30	    data class FillBlank(
    31	        override val questionText: String,
    32	        override val hasImage: Boolean,
    33	        val imageUrl: String?,
    34	        val templateParts: List<TemplatePart>,
    35	        val filledValues: Map<Int, String>,
    36	    ) : QuestionUiState
    37	}
    38	
    39	data class OptionUi(val id: String, val text: String)
    40	
    41	sealed interface TemplatePart {
    42	    data class Text(val content: String) : TemplatePart
    43	
    44	    data class Blank(val index: Int, val placeholder: String) : TemplatePart
    45	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt | sed -n '1,420p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Score
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerDuration
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
    14	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
    15	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    16	import kotlin.random.Random
    17	
    18	// ── Public pure functions ──────────────────────────────────────────────────
    19	
    20	/**
    21	 * Records [answer] score for current question and advances [indexInPool].
    22	 * When the last question is answered, returns Ready with indexInPool == playOrder.size (sentinel).
    23	 * Component checks this sentinel and calls CompleteAttemptUseCase.
    24	 */
    25	fun submitAnswer(state: RunnerState.Ready, answer: UserAnswer, nowMs: Long): RunnerState.Ready {
    26	    val currentQuestion = state.playOrder[state.indexInPool]
    27	    val score = evaluateAnswer(currentQuestion.content, answer)
    28	
    29	    val codeAnswerChars = state.codeAnswer.raw.toCharArray()
    30	    codeAnswerChars[currentQuestion.codeAnswerIndex] = ('0' + score.raw)
    31	    val newCodeAnswer = CodeAnswer(String(codeAnswerChars))
    32	
    33	    val nextIndex = state.indexInPool + 1
    34	    val newDeadlineMs = if (nextIndex < state.playOrder.size) {
    35	        val nextQuestion = state.playOrder[nextIndex]
    36	        val duration = computeTimer(nextQuestion.content, state.mode, TimerCoefficients.Default)
    37	        nowMs + duration.seconds * 1000L
    38	    } else {
    39	        state.deadlineMs
    40	    }
    41	
    42	    return state.copy(
    43	        indexInPool = nextIndex,
    44	        codeAnswer = newCodeAnswer,
    45	        deadlineMs = newDeadlineMs,
    46	        currentDraftAnswer = null,
    47	    )
    48	}
    49	
    50	/**
    51	 * Generates a deterministic random [UserAnswer] from [randomSeed] and delegates to [submitAnswer].
    52	 * Called on timer expiry or onStop.
    53	 */
    54	fun autoAnswerOnTimeout(state: RunnerState.Ready, randomSeed: Long, nowMs: Long): RunnerState.Ready {
    55	    val currentQuestion = state.playOrder[state.indexInPool]
    56	    val answer = generateRandomAnswer(currentQuestion.content, randomSeed)
    57	    return submitAnswer(state, answer, nowMs)
    58	}
    59	
    60	/**
    61	 * Evaluates [answer] against [content] and returns a [Score] in 1..9.
    62	 * Score formula: digit = round(correct_share × 8) + 1 (integer math, round-half-up).
    63	 */
    64	fun evaluateAnswer(content: QuestionContent, answer: UserAnswer): Score {
    65	    return when {
    66	        content is QuestionContent.SingleChoice && answer is UserAnswer.SingleChoiceAnswer -> {
    67	            val validIds = content.options.map { it.id }.toSet()
    68	            val selected = answer.selected?.takeIf { it in validIds }
    69	            if (selected == content.correctOptionId) Score(9) else Score(1)
    70	        }
    71	        content is QuestionContent.MultipleChoice && answer is UserAnswer.MultipleChoiceAnswer -> {
    72	            val validIds = content.options.map { it.id }.toSet()
    73	            val picked = answer.selected.intersect(validIds)
    74	            val correctPicked = picked.intersect(content.correctOptionIds).size
    75	            val wrongPicked = (picked - content.correctOptionIds).size
    76	            val missed = (content.correctOptionIds - picked).size
    77	            scoreDigit(correctPicked, correctPicked + wrongPicked + missed)
    78	        }
    79	        content is QuestionContent.Ordering && answer is UserAnswer.OrderingAnswer -> {
    80	            val correctOrder = content.items.map { it.id }
    81	            val isValidPerm = answer.order.size == correctOrder.size &&
    82	                answer.order.toSet() == correctOrder.toSet()
    83	            if (!isValidPerm) {
    84	                Score(1)
    85	            } else {
    86	                val matched = correctOrder.indices.count { i -> answer.order[i] == correctOrder[i] }
    87	                scoreDigit(matched, correctOrder.size)
    88	            }
    89	        }
    90	        content is QuestionContent.FillBlank && answer is UserAnswer.FillBlankAnswer -> {
    91	            val validCandidates = content.candidates.map { it.id }.toSet()
    92	            val filledCorrect = content.blanks.count { blank ->
    93	                val c = answer.filled[blank.id]
    94	                c != null && c in validCandidates && c == blank.correctCandidateId
    95	            }
    96	            scoreDigit(filledCorrect, content.blanks.size)
    97	        }
    98	        else -> Score(1)
    99	    }
   100	}
   101	
   102	/**
   103	 * Computes [Stars] for a single attempt.
   104	 * EASY: rawTenths = (percentScore × 20 + 50) / 100 → [0..20].
   105	 * HARD: rawTenths = 20 + (percentScore × 10 + 50) / 100 → [20..30].
   106	 * Integer math, round-half-up via +50 constant.
   107	 */
   108	fun computeStars(percentScore: PercentScore, mode: Difficulty): Stars {
   109	    val rawTenths = when (mode) {
   110	        Difficulty.EASY -> (percentScore.raw * 20 + 50) / 100
   111	        Difficulty.HARD -> 20 + (percentScore.raw * 10 + 50) / 100
   112	    }
   113	    return Stars(rawTenths)
   114	}
   115	
   116	/**
   117	 * Returns max [Stars] across all [attempts] for a lesson.
   118	 * Returns Stars(0) if list is empty.
   119	 */
   120	fun computeBestStars(attempts: List<Attempt>): Stars {
   121	    if (attempts.isEmpty()) return Stars(0)
   122	    return Stars(attempts.maxOf { computeStars(it.percentScore, it.mode).rawTenths })
   123	}
   124	
   125	/**
   126	 * Returns true if [attempts] contain at least one EASY attempt with allShownAnswersAre9.
   127	 * String-based, not percent-based. HARD attempts never unlock HARD.
   128	 */
   129	fun computeHardUnlocked(attempts: List<Attempt>): Boolean =
   130	    attempts.any { it.mode == Difficulty.EASY && it.codeAnswer.allShownAnswersAre9 }
   131	
   132	/**
   133	 * Computes per-question timer in seconds.
   134	 * Formula: max(5, round(charsCount × k)); +100 chars if image present.
   135	 */
   136	fun computeTimer(
   137	    content: QuestionContent,
   138	    mode: Difficulty,
   139	    coefficients: TimerCoefficients,
   140	): TimerDuration {
   141	    val charsCount = computeCharsCount(content)
   142	    val k = when (mode) {
   143	        Difficulty.EASY -> coefficients.kEasy
   144	        Difficulty.HARD -> coefficients.kHard
   145	    }
   146	    val seconds = maxOf(5, (charsCount * k + 0.5).toInt())
   147	    return TimerDuration(seconds)
   148	}
   149	
   150	/**
   151	 * Selects a random subset of [eligible] using [seed].
   152	 * Size = min([poolSize], eligible.size). Returned list order is shuffled (caller sorts for playOrder).
   153	 */
   154	fun selectSubset(
   155	    eligible: List<RunnerQuestion.Valid>,
   156	    poolSize: Int,
   157	    seed: Long,
   158	): List<RunnerQuestion.Valid> {
   159	    if (eligible.isEmpty()) return emptyList()
   160	    val count = minOf(poolSize, eligible.size)
   161	    return eligible.shuffled(Random(seed)).take(count)
   162	}
   163	
   164	/**
   165	 * Builds final [CodeAnswer] for an aborted attempt.
   166	 * Unanswered subset positions (indexInPool and beyond) → '1'.
   167	 * Out-of-subset positions remain '0'. Already answered positions stay as-is.
   168	 */
   169	fun buildCodeAnswerOnAbort(state: RunnerState.Ready): CodeAnswer {
   170	    val chars = state.codeAnswer.raw.toCharArray()
   171	    for (i in state.indexInPool until state.playOrder.size) {
   172	        chars[state.playOrder[i].codeAnswerIndex] = '1'
   173	    }
   174	    return CodeAnswer(String(chars))
   175	}
   176	
   177	// ── Internal helpers ───────────────────────────────────────────────────────
   178	
   179	/**
   180	 * Derives [PercentScore] from [codeAnswer] using integer division.
   181	 * Formula: sum((digit-1)*100/8) for non-zero digits / count of non-zero digits.
   182	 */
   183	internal fun computePercentScore(codeAnswer: CodeAnswer): PercentScore {
   184	    val nonZero = codeAnswer.raw.filter { it != '0' }
   185	    if (nonZero.isEmpty()) return PercentScore(0)
   186	    val sum = nonZero.sumOf { (it.digitToInt() - 1) * 100 / 8 }
   187	    return PercentScore(sum / nonZero.length)
   188	}
   189	
   190	// ── Private helpers ────────────────────────────────────────────────────────
   191	
   192	/**
   193	 * Score digit via integer round-half-up: (num * 8 + den/2) / den + 1.
   194	 * Returns Score(1) if denominator is zero.
   195	 */
   196	private fun scoreDigit(numerator: Int, denominator: Int): Score {
   197	    if (denominator == 0) return Score(1)
   198	    val digit = (numerator * 8 + denominator / 2) / denominator + 1
   199	    return Score(digit.coerceIn(1, 9))
   200	}
   201	
   202	/** Total character count for timer formula: text + option/item/candidate texts + optional +100. */
   203	private fun computeCharsCount(content: QuestionContent): Int {
   204	    val imageBonus = if (content.imageUrl != null) 100 else 0
   205	    val optionChars = when (content) {
   206	        is QuestionContent.SingleChoice -> content.options.sumOf { it.text.length }
   207	        is QuestionContent.MultipleChoice -> content.options.sumOf { it.text.length }
   208	        is QuestionContent.Ordering -> content.items.sumOf { it.text.length }
   209	        is QuestionContent.FillBlank -> content.candidates.sumOf { it.text.length }
   210	    }
   211	    return content.text.length + optionChars + imageBonus
   212	}
   213	
   214	/** Generates a deterministic random answer for [content] using [seed]. */
   215	private fun generateRandomAnswer(content: QuestionContent, seed: Long): UserAnswer {
   216	    val random = Random(seed)
   217	    return when (content) {
   218	        is QuestionContent.SingleChoice -> {
   219	            UserAnswer.SingleChoiceAnswer(content.options.random(random).id)
   220	        }
   221	        is QuestionContent.MultipleChoice -> {
   222	            val count = content.correctOptionIds.size
   223	            val selected = content.options.shuffled(random).take(count).map { it.id }.toSet()
   224	            UserAnswer.MultipleChoiceAnswer(selected)
   225	        }
   226	        is QuestionContent.Ordering -> {
   227	            UserAnswer.OrderingAnswer(content.items.shuffled(random).map { it.id })
   228	        }
   229	        is QuestionContent.FillBlank -> {
   230	            val filled = content.blanks.associate { blank ->
   231	                blank.id to content.candidates.random(random).id
   232	            }
   233	            UserAnswer.FillBlankAnswer(filled)
   234	        }
   235	    }
   236	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
     5	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
     6	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     7	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
    11	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
    12	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
    13	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
    14	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    15	import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
    16	import kotlinx.coroutines.flow.first
    17	import kotlinx.datetime.Clock
    18	
    19	/**
    20	 * Builds [RunnerState.Ready] from a lesson and mode selection.
    21	 *
    22	 * Happy path: auth → lesson snapshot → question snapshot → parse + filter →
    23	 *   selectSubset → assign codeAnswerIndex → RunnerState.Ready.
    24	 *
    25	 * Failures (no throw): InitFailed(AuthRequired | LessonNotFound | EmptyPool | NoValidQuestions).
    26	 */
    27	class StartLessonAttemptUseCase(
    28	    private val questionRepository: QuestionRepository,
    29	    private val lessonRepository: LessonRepository,
    30	    private val parser: QuestionContentParser,
    31	    private val authRepository: AuthRepository,
    32	    private val clock: Clock,
    33	    private val randomSeedProvider: () -> Long,
    34	    private val timerCoefficients: TimerCoefficients = TimerCoefficients.Default,
    35	) {
    36	    suspend operator fun invoke(lessonId: LessonId, mode: Difficulty): RunnerState {
    37	        val userId = authRepository.currentUid()
    38	            ?: return RunnerState.InitFailed(InitFailureReason.AuthRequired)
    39	
    40	        val lesson = lessonRepository.getById(lessonId)
    41	            ?: return RunnerState.InitFailed(InitFailureReason.LessonNotFound)
    42	
    43	        val questions = questionRepository.observeByLesson(lessonId).first()
    44	        val activeQuestions = questions.filter { !it.archived }
    45	
    46	        val valids = activeQuestions.mapNotNull { q ->
    47	            parser.parse(q.payload).getOrNull()?.let { content ->
    48	                RunnerQuestion.Valid(
    49	                    sourceId = q.id,
    50	                    order = q.order,
    51	                    codeAnswerIndex = -1,
    52	                    content = content,
    53	                )
    54	            }
    55	        }
    56	
    57	        // Priority 1: active questions exist but all payloads are invalid
    58	        if (activeQuestions.isNotEmpty() && valids.isEmpty()) {
    59	            return RunnerState.InitFailed(InitFailureReason.NoValidQuestions)
    60	        }
    61	
    62	        // Priority 2: no eligible questions for selected difficulty
    63	        val eligible = valids.filter { it.content.difficulty == mode }
    64	        if (eligible.isEmpty()) {
    65	            return RunnerState.InitFailed(InitFailureReason.EmptyPool)
    66	        }
    67	
    68	        // Assign codeAnswerIndex per sorted eligible position (full pool, not subset)
    69	        val sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
    70	        val indexed = sorted.mapIndexed { idx, rq -> rq.copy(codeAnswerIndex = idx) }
    71	        val eligibleSize = indexed.size
    72	
    73	        // Random subset (deterministic via seed)
    74	        val seed = randomSeedProvider()
    75	        val subset = selectSubset(indexed, POOL_SIZE, seed)
    76	
    77	        // Stable play order: sort subset by (order, sourceId)
    78	        val playOrder = subset.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
    79	
    80	        val initialCodeAnswer = CodeAnswer("0".repeat(eligibleSize))
    81	
    82	        val nowMs = clock.now().toEpochMilliseconds()
    83	        val firstDuration = computeTimer(playOrder.first().content, mode, timerCoefficients)
    84	        val deadlineMs = nowMs + firstDuration.seconds * 1000L
    85	
    86	        return RunnerState.Ready(
    87	            userId = userId,
    88	            lessonId = lessonId,
    89	            lessonVersion = lesson.version,
    90	            mode = mode,
    91	            playOrder = playOrder,
    92	            eligibleSize = eligibleSize,
    93	            indexInPool = 0,
    94	            codeAnswer = initialCodeAnswer,
    95	            deadlineMs = deadlineMs,
    96	            seed = seed,
    97	            currentDraftAnswer = null,
    98	            isPaused = false,
    99	        )
   100	    }
   101	
   102	    companion object {
   103	        const val POOL_SIZE = 20
   104	    }
   105	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
     8	import kotlinx.datetime.Clock
     9	
    10	/**
    11	 * Submits a lesson rating from the result screen.
    12	 *
    13	 * [userId] is passed explicitly (from completed attempt snapshot) to avoid auth race.
    14	 * [rating] must be in 1..3.
    15	 * lessonVersion fetched fresh from [lessonRepository] at submit time (for analytics).
    16	 *
    17	 * Returns [Result.failure] on error; local hasSubmitted flag not set on failure.
    18	 */
    19	class SubmitLessonRatingUseCase(
    20	    private val ratingRepository: LessonRatingRepository,
    21	    private val lessonRepository: LessonRepository,
    22	    private val clock: Clock,
    23	    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
    24	) {
    25	    suspend operator fun invoke(
    26	        userId: String,
    27	        lessonId: LessonId,
    28	        rating: Int,
    29	    ): Result<Unit> {
    30	        if (rating !in 1..3) {
    31	            return Result.failure(IllegalArgumentException("rating must be in 1..3, got $rating"))
    32	        }
    33	
    34	        val lesson = lessonRepository.getById(lessonId)
    35	            ?: return Result.failure(IllegalStateException("Lesson not found: ${lessonId.value}"))
    36	
    37	        val ratingId = ratingIdProvider(userId, lessonId)
    38	        val nowMs = clock.now().toEpochMilliseconds()
    39	
    40	        val lessonRating = LessonRating(
    41	            id = ratingId,
    42	            userId = userId,
    43	            lessonId = lessonId,
    44	            lessonVersion = lesson.version,
    45	            rating = rating,
    46	            ratedAt = nowMs,
    47	        )
    48	
    49	        return ratingRepository.submit(lessonRating)
    50	    }
    51	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    10	import kotlinx.datetime.Clock
    11	
    12	/**
    13	 * Aborts an in-progress attempt and persists a partial record.
    14	 *
    15	 * userId from [state.userId] — no auth read.
    16	 * Unanswered subset positions → '1'; out-of-subset positions remain '0'.
    17	 *
    18	 * Returns [RunnerState.Aborted] or [RunnerState.SaveFailed] on Room IO error.
    19	 */
    20	class AbortAttemptUseCase(
    21	    private val attemptRepository: LessonAttemptRepository,
    22	    private val clock: Clock,
    23	    private val attemptIdProvider: () -> AttemptId,
    24	) {
    25	    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
    26	        val nowMs = clock.now().toEpochMilliseconds()
    27	        val finalCodeAnswer = buildCodeAnswerOnAbort(state)
    28	        val percentScore = computePercentScore(finalCodeAnswer)
    29	
    30	        val attempt = Attempt(
    31	            id = attemptIdProvider(),
    32	            userId = state.userId,
    33	            lessonId = state.lessonId,
    34	            lessonVersion = state.lessonVersion,
    35	            mode = state.mode,
    36	            completedAt = nowMs,
    37	            codeAnswer = finalCodeAnswer,
    38	            percentScore = percentScore,
    39	        )
    40	
    41	        val saveResult = attemptRepository.save(attempt)
    42	        if (saveResult.isFailure) {
    43	            val error = saveResult.exceptionOrNull()
    44	                ?.let { SaveError.IoFailure(it) }
    45	                ?: SaveError.UnknownError(Exception("Unknown save error"))
    46	            return RunnerState.SaveFailed(attempt = attempt, error = error)
    47	        }
    48	
    49	        return RunnerState.Aborted(attempt = attempt)
    50	    }
    51	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computePercentScore
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.allShownAnswersAre9
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
    11	import kotlinx.coroutines.flow.first
    12	import kotlinx.datetime.Clock
    13	
    14	/**
    15	 * Finalizes a completed attempt and persists it to Room.
    16	 *
    17	 * userId taken from [state.userId] — no repeated auth read.
    18	 * ratingPrompt = codeAnswer.allShownAnswersAre9 AND !hasSubmittedRating.
    19	 *
    20	 * Returns [RunnerState.Completed] or [RunnerState.SaveFailed] on Room IO error.
    21	 */
    22	class CompleteAttemptUseCase(
    23	    private val attemptRepository: LessonAttemptRepository,
    24	    private val ratingRepository: LessonRatingRepository,
    25	    private val clock: Clock,
    26	    private val attemptIdProvider: () -> AttemptId,
    27	) {
    28	    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
    29	        val nowMs = clock.now().toEpochMilliseconds()
    30	        val percentScore = computePercentScore(state.codeAnswer)
    31	
    32	        val attempt = Attempt(
    33	            id = attemptIdProvider(),
    34	            userId = state.userId,
    35	            lessonId = state.lessonId,
    36	            lessonVersion = state.lessonVersion,
    37	            mode = state.mode,
    38	            completedAt = nowMs,
    39	            codeAnswer = state.codeAnswer,
    40	            percentScore = percentScore,
    41	        )
    42	
    43	        val saveResult = attemptRepository.save(attempt)
    44	        if (saveResult.isFailure) {
    45	            val error = saveResult.exceptionOrNull()
    46	                ?.let { SaveError.IoFailure(it) }
    47	                ?: SaveError.UnknownError(Exception("Unknown save error"))
    48	            return RunnerState.SaveFailed(attempt = attempt, error = error)
    49	        }
    50	
    51	        val ratingPrompt = state.codeAnswer.allShownAnswersAre9 &&
    52	            !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()
    53	
    54	        return RunnerState.Completed(attempt = attempt, ratingPrompt = ratingPrompt)
    55	    }
    56	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Row
     6	import androidx.compose.foundation.layout.fillMaxWidth
     7	import androidx.compose.material.icons.Icons
     8	import androidx.compose.material.icons.filled.Star
     9	import androidx.compose.material.icons.outlined.StarOutline
    10	import androidx.compose.material3.Icon
    11	import androidx.compose.material3.IconButton
    12	import androidx.compose.material3.MaterialTheme
    13	import androidx.compose.material3.Text
    14	import androidx.compose.runtime.Composable
    15	import androidx.compose.runtime.getValue
    16	import androidx.compose.runtime.mutableIntStateOf
    17	import androidx.compose.runtime.mutableStateOf
    18	import androidx.compose.runtime.remember
    19	import androidx.compose.runtime.setValue
    20	import androidx.compose.ui.Alignment
    21	import androidx.compose.ui.Modifier
    22	import androidx.compose.ui.tooling.preview.Preview
    23	import androidx.compose.ui.unit.dp
    24	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    25	
    26	private const val MAX_RATING = 3
    27	
    28	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    29	@Composable
    30	fun RatingPromptSection(
    31	    onSubmitRating: (Int) -> Unit,
    32	    modifier: Modifier = Modifier,
    33	) {
    34	    var selectedRating by remember { mutableIntStateOf(0) }
    35	    var submitted by remember { mutableStateOf(false) }
    36	
    37	    Column(
    38	        modifier = modifier.fillMaxWidth(),
    39	        horizontalAlignment = Alignment.CenterHorizontally,
    40	        verticalArrangement = Arrangement.spacedBy(8.dp),
    41	    ) {
    42	        Text(
    43	            text = "Оцените урок",
    44	            style = MaterialTheme.typography.titleMedium,
    45	        )
    46	        Row(
    47	            horizontalArrangement = Arrangement.spacedBy(4.dp),
    48	            verticalAlignment = Alignment.CenterVertically,
    49	        ) {
    50	            for (i in 1..MAX_RATING) {
    51	                IconButton(
    52	                    onClick = {
    53	                        if (!submitted) {
    54	                            selectedRating = i
    55	                            submitted = true
    56	                            onSubmitRating(i)
    57	                        }
    58	                    },
    59	                    enabled = !submitted,
    60	                ) {
    61	                    Icon(
    62	                        imageVector =
    63	                            if (i <= selectedRating) {
    64	                                Icons.Filled.Star
    65	                            } else {
    66	                                Icons.Outlined.StarOutline
    67	                            },
    68	                        contentDescription = "$i звезды",
    69	                        tint = MaterialTheme.colorScheme.primary,
    70	                    )
    71	                }
    72	            }
    73	        }
    74	    }
    75	}
    76	
    77	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    78	@Preview(showBackground = true)
    79	@Composable
    80	private fun RatingPromptSectionPreview() {
    81	    SchoolQuizTheme {
    82	        RatingPromptSection(onSubmitRating = {})
    83	    }
    84	}

exec
/bin/bash -lc "nl -ba shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/KotlinxSerializationQuestionContentParser.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.question_schema
     2	
     3	import kotlinx.serialization.SerializationException
     4	import kotlinx.serialization.json.Json
     5	
     6	class KotlinxSerializationQuestionContentParser : QuestionContentParser {
     7	
     8	    private val json = Json { ignoreUnknownKeys = true }
     9	
    10	    override fun parse(payload: String): Result<QuestionContent> {
    11	        return try {
    12	            Result.success(json.decodeFromString<QuestionContent>(payload))
    13	        } catch (e: SerializationException) {
    14	            Result.failure(e)
    15	        } catch (e: IllegalArgumentException) {
    16	            Result.failure(e)
    17	        }
    18	    }
    19	}

exec
/bin/bash -lc "nl -ba shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt | sed -n '1,360p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.question_schema
     2	
     3	import kotlinx.serialization.SerialName
     4	import kotlinx.serialization.Serializable
     5	
     6	/**
     7	 * Parsed question content from ADR-0003 JSON payload.
     8	 *
     9	 * Invariants are enforced in each subtype's init block.
    10	 * Domain question-runner imports only this sealed type and [QuestionContentParser].
    11	 */
    12	@Serializable
    13	sealed interface QuestionContent {
    14	
    15	    val id: String
    16	    val difficulty: Difficulty
    17	    val text: String
    18	    val imageUrl: String?
    19	
    20	    @Serializable
    21	    data class Option(val id: OptionId, val text: String)
    22	
    23	    @Serializable
    24	    data class OrderItem(val id: OptionId, val text: String)
    25	
    26	    @Serializable
    27	    data class Blank(val id: BlankId, val correctCandidateId: CandidateId)
    28	
    29	    @Serializable
    30	    data class Candidate(val id: CandidateId, val text: String)
    31	
    32	    /**
    33	     * Single-choice question. Exactly one correct answer.
    34	     * Invariant: options.size in 2..8.
    35	     */
    36	    @Serializable
    37	    @SerialName("SingleChoice")
    38	    data class SingleChoice(
    39	        override val id: String,
    40	        override val difficulty: Difficulty,
    41	        override val text: String,
    42	        override val imageUrl: String?,
    43	        val options: List<Option>,
    44	        val correctOptionId: OptionId,
    45	    ) : QuestionContent {
    46	        init {
    47	            require(id.isNotBlank()) { "SingleChoice.id must not be blank" }
    48	            require(text.isNotBlank()) { "SingleChoice.text must not be blank" }
    49	            require(options.size in 2..8) { "SingleChoice.options.size must be in 2..8, got ${options.size}" }
    50	            require(options.map { it.id }.toSet().size == options.size) { "SingleChoice.options must have unique ids" }
    51	            require(correctOptionId in options.map { it.id }) {
    52	                "SingleChoice.correctOptionId must be in options, got $correctOptionId"
    53	            }
    54	        }
    55	    }
    56	
    57	    /**
    58	     * Multiple-choice question. Two or more correct answers.
    59	     * Invariant: options.size in 2..8; correctOptionIds.size >= 2.
    60	     */
    61	    @Serializable
    62	    @SerialName("MultipleChoice")
    63	    data class MultipleChoice(
    64	        override val id: String,
    65	        override val difficulty: Difficulty,
    66	        override val text: String,
    67	        override val imageUrl: String?,
    68	        val options: List<Option>,
    69	        val correctOptionIds: Set<OptionId>,
    70	    ) : QuestionContent {
    71	        init {
    72	            require(id.isNotBlank()) { "MultipleChoice.id must not be blank" }
    73	            require(text.isNotBlank()) { "MultipleChoice.text must not be blank" }
    74	            require(options.size in 2..8) { "MultipleChoice.options.size must be in 2..8, got ${options.size}" }
    75	            require(options.map { it.id }.toSet().size == options.size) { "MultipleChoice.options must have unique ids" }
    76	            require(correctOptionIds.size >= 2) { "MultipleChoice.correctOptionIds.size must be >= 2, got ${correctOptionIds.size}" }
    77	            val optionIds = options.map { it.id }.toSet()
    78	            require(correctOptionIds.all { it in optionIds }) {
    79	                "MultipleChoice.correctOptionIds must all be in options, got missing: ${correctOptionIds - optionIds}"
    80	            }
    81	        }
    82	    }
    83	
    84	    /**
    85	     * Ordering question. Correct order = order of items in [items] list.
    86	     * Invariant: items.size in 2..8.
    87	     */
    88	    @Serializable
    89	    @SerialName("Ordering")
    90	    data class Ordering(
    91	        override val id: String,
    92	        override val difficulty: Difficulty,
    93	        override val text: String,
    94	        override val imageUrl: String?,
    95	        val items: List<OrderItem>,
    96	    ) : QuestionContent {
    97	        init {
    98	            require(id.isNotBlank()) { "Ordering.id must not be blank" }
    99	            require(text.isNotBlank()) { "Ordering.text must not be blank" }
   100	            require(items.size in 2..8) { "Ordering.items.size must be in 2..8, got ${items.size}" }
   101	        }
   102	    }
   103	
   104	    /**
   105	     * Fill-in-the-blank question. Text contains blank markers; candidates are word pool.
   106	     * Invariant: blanks.size in 1..3; candidates.size == 5 or 10.
   107	     */
   108	    @Serializable
   109	    @SerialName("FillBlank")
   110	    data class FillBlank(
   111	        override val id: String,
   112	        override val difficulty: Difficulty,
   113	        override val text: String,
   114	        override val imageUrl: String?,
   115	        val blanks: List<Blank>,
   116	        val candidates: List<Candidate>,
   117	    ) : QuestionContent {
   118	        init {
   119	            require(id.isNotBlank()) { "FillBlank.id must not be blank" }
   120	            require(text.isNotBlank()) { "FillBlank.text must not be blank" }
   121	            require(blanks.size in 1..3) { "FillBlank.blanks.size must be in 1..3, got ${blanks.size}" }
   122	            require(candidates.size == 5 || candidates.size == 10) {
   123	                "FillBlank.candidates.size must be 5 or 10, got ${candidates.size}"
   124	            }
   125	            require(candidates.map { it.id }.toSet().size == candidates.size) { "FillBlank.candidates must have unique ids" }
   126	            require(blanks.map { it.id }.toSet().size == blanks.size) { "FillBlank.blanks must have unique ids" }
   127	            val candidateIds = candidates.map { it.id }.toSet()
   128	            require(blanks.all { it.correctCandidateId in candidateIds }) {
   129	                "FillBlank.blanks.correctCandidateId must all be in candidates"
   130	            }
   131	        }
   132	    }
   133	}

exec
/bin/bash -lc "nl -ba shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/Difficulty.kt | sed -n '1,80p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.question_schema
     2	
     3	import kotlinx.serialization.Serializable
     4	
     5	@Serializable
     6	enum class Difficulty {
     7	    EASY,
     8	    HARD,
     9	}

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.Database
     4	import androidx.room.RoomDatabase
     5	import androidx.room.TypeConverters
     6	
     7	@Database(
     8	    entities = [
     9	        UserStatsEntity::class,
    10	        CatalogEntity::class,
    11	        QuestEntity::class,
    12	        SectionEntity::class,
    13	        ThemeEntity::class,
    14	        LessonEntity::class,
    15	        QuestionEntity::class,
    16	        LessonAttemptEntity::class,
    17	        LessonRatingSubmittedLocalEntity::class,
    18	    ],
    19	    version = 4,
    20	    exportSchema = true,
    21	)
    22	@TypeConverters(StringSetConverter::class, TopParticipantListConverter::class)
    23	abstract class AppDatabase : RoomDatabase() {
    24	    abstract fun userStatsDao(): UserStatsDao
    25	    abstract fun catalogDao(): CatalogDao
    26	    abstract fun questDao(): QuestDao
    27	    abstract fun sectionDao(): SectionDao
    28	    abstract fun themeDao(): ThemeDao
    29	    abstract fun lessonDao(): LessonDao
    30	    abstract fun questionDao(): QuestionDao
    31	    abstract fun lessonAttemptDao(): LessonAttemptDao
    32	    abstract fun lessonRatingLocalDao(): LessonRatingLocalDao
    33	}

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.Dao
     4	import androidx.room.Insert
     5	import androidx.room.OnConflictStrategy
     6	import androidx.room.Query
     7	import kotlinx.coroutines.flow.Flow
     8	
     9	@Dao
    10	interface LessonAttemptDao {
    11	
    12	    @Insert(onConflict = OnConflictStrategy.REPLACE)
    13	    suspend fun upsert(entity: LessonAttemptEntity): Long
    14	
    15	    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
    16	    fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>
    17	
    18	    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
    19	    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>
    20	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ColumnInfo
     4	import androidx.room.Entity
     5	import androidx.room.Index
     6	import androidx.room.PrimaryKey
     7	
     8	@Entity(
     9	    tableName = "lesson_attempts",
    10	    indices = [
    11	        Index(value = ["user_id"], name = "idx_lesson_attempts_user_id"),
    12	        Index(value = ["lesson_id"], name = "idx_lesson_attempts_lesson_id"),
    13	    ],
    14	)
    15	data class LessonAttemptEntity(
    16	    @PrimaryKey
    17	    @ColumnInfo(name = "attempt_id")
    18	    val attemptId: String,
    19	    @ColumnInfo(name = "user_id") val userId: String,
    20	    @ColumnInfo(name = "lesson_id") val lessonId: String,
    21	    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    22	    @ColumnInfo(name = "is_hard") val isHard: Int,
    23	    @ColumnInfo(name = "code_answer") val codeAnswer: String,
    24	    @ColumnInfo(name = "percent_score") val percentScore: Int,
    25	    @ColumnInfo(name = "completed_at") val completedAt: Long,
    26	)

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence.di
     2	
     3	import androidx.room.Room
     4	import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
     5	import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
     6	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
     7	import com.tpov.schoolquiz.shared.core.persistence.LessonDao
     8	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
     9	import com.tpov.schoolquiz.shared.core.persistence.QuestionDao
    10	import com.tpov.schoolquiz.shared.core.persistence.QuestDao
    11	import com.tpov.schoolquiz.shared.core.persistence.SectionDao
    12	import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
    13	import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
    14	import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
    15	import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
    16	import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_3_4
    17	import org.koin.android.ext.koin.androidContext
    18	import org.koin.dsl.module
    19	
    20	val persistenceModule = module {
    21	    single<AppDatabase> {
    22	        Room.databaseBuilder(
    23	            androidContext(),
    24	            AppDatabase::class.java,
    25	            "schoolquiz.db",
    26	        )
    27	            .addMigrations(MIGRATION_3_4)
    28	            .addTypeConverter(StringSetConverter())
    29	            .addTypeConverter(TopParticipantListConverter())
    30	            .build()
    31	    }
    32	    single<UserStatsDao> { get<AppDatabase>().userStatsDao() }
    33	    single<CatalogDao> { get<AppDatabase>().catalogDao() }
    34	    single<QuestDao> { get<AppDatabase>().questDao() }
    35	    single<SectionDao> { get<AppDatabase>().sectionDao() }
    36	    single<ThemeDao> { get<AppDatabase>().themeDao() }
    37	    single<LessonDao> { get<AppDatabase>().lessonDao() }
    38	    single<QuestionDao> { get<AppDatabase>().questionDao() }
    39	    single<LessonAttemptDao> { get<AppDatabase>().lessonAttemptDao() }
    40	    single<LessonRatingLocalDao> { get<AppDatabase>().lessonRatingLocalDao() }
    41	}

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ColumnInfo
     4	import androidx.room.Entity
     5	
     6	@Entity(
     7	    tableName = "lesson_rating_submitted_local",
     8	    primaryKeys = ["user_id", "lesson_id"],
     9	)
    10	data class LessonRatingSubmittedLocalEntity(
    11	    @ColumnInfo(name = "user_id") val userId: String,
    12	    @ColumnInfo(name = "lesson_id") val lessonId: String,
    13	    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
    14	)

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.Dao
     4	import androidx.room.Insert
     5	import androidx.room.OnConflictStrategy
     6	import androidx.room.Query
     7	import kotlinx.coroutines.flow.Flow
     8	
     9	@Dao
    10	interface LessonRatingLocalDao {
    11	
    12	    @Insert(onConflict = OnConflictStrategy.REPLACE)
    13	    suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long
    14	
    15	    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
    16	    fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>
    17	}

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/Migration3to4.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence.migrations
     2	
     3	import androidx.room.migration.Migration
     4	import androidx.sqlite.db.SupportSQLiteDatabase
     5	
     6	val MIGRATION_3_4 = object : Migration(3, 4) {
     7	    override fun migrate(db: SupportSQLiteDatabase) {
     8	        db.execSQL(
     9	            """
    10	            CREATE TABLE IF NOT EXISTS lesson_attempts (
    11	                attempt_id      TEXT    NOT NULL PRIMARY KEY,
    12	                user_id         TEXT    NOT NULL,
    13	                lesson_id       TEXT    NOT NULL,
    14	                lesson_version  INTEGER NOT NULL,
    15	                is_hard         INTEGER NOT NULL DEFAULT 0,
    16	                code_answer     TEXT    NOT NULL,
    17	                percent_score   INTEGER NOT NULL,
    18	                completed_at    INTEGER NOT NULL
    19	            )
    20	            """.trimIndent(),
    21	        )
    22	        db.execSQL("CREATE INDEX idx_lesson_attempts_user_id ON lesson_attempts (user_id)")
    23	        db.execSQL("CREATE INDEX idx_lesson_attempts_lesson_id ON lesson_attempts (lesson_id)")
    24	
    25	        db.execSQL(
    26	            """
    27	            CREATE TABLE IF NOT EXISTS lesson_rating_submitted_local (
    28	                user_id         TEXT    NOT NULL,
    29	                lesson_id       TEXT    NOT NULL,
    30	                submitted_at    INTEGER NOT NULL,
    31	                PRIMARY KEY (user_id, lesson_id)
    32	            )
    33	            """.trimIndent(),
    34	        )
    35	
    36	        db.execSQL("ALTER TABLE lessons ADD COLUMN average_rating REAL")
    37	        db.execSQL("ALTER TABLE lessons ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0")
    38	        db.execSQL("ALTER TABLE lessons ADD COLUMN top3 TEXT NOT NULL DEFAULT '[]'")
    39	    }
    40	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonAttemptRepositoryImpl.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
     9	import kotlinx.coroutines.flow.Flow
    10	import kotlinx.coroutines.flow.map
    11	
    12	class LessonAttemptRepositoryImpl(
    13	    private val attemptDao: LessonAttemptDao,
    14	) : LessonAttemptRepository {
    15	
    16	    override suspend fun save(attempt: Attempt): Result<Unit> = runCatching {
    17	        val rowId = attemptDao.upsert(attempt.toEntity())
    18	        check(rowId > 0) { "upsert returned unexpected rowId: $rowId" }
    19	    }
    20	
    21	    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
    22	        attemptDao.observeByLesson(userId, lessonId.value)
    23	            .map { list -> list.map { it.toDomain() } }
    24	
    25	    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
    26	        attemptDao.observeAllByUser(userId)
    27	            .map { list -> list.map { it.toDomain() } }
    28	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/LessonRatingRepositoryImpl.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
     8	import kotlinx.coroutines.flow.Flow
     9	
    10	class LessonRatingRepositoryImpl(
    11	    private val ratingLocalDao: LessonRatingLocalDao,
    12	) : LessonRatingRepository {
    13	
    14	    override suspend fun submit(rating: LessonRating): Result<Unit> = runCatching {
    15	        val rowId = ratingLocalDao.upsert(rating.toEntity())
    16	        check(rowId > 0) { "upsert returned unexpected rowId: $rowId" }
    17	    }
    18	
    19	    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
    20	        ratingLocalDao.hasSubmitted(userId, lessonId.value)
    21	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonRatingMapper.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
     5	
     6	fun LessonRating.toEntity(): LessonRatingSubmittedLocalEntity = LessonRatingSubmittedLocalEntity(
     7	    userId = userId,
     8	    lessonId = lessonId.value,
     9	    submittedAt = ratedAt,
    10	)

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/TopParticipantListConverter.kt | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/mapper/LessonAttemptMapper.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ProvidedTypeConverter
     4	import androidx.room.TypeConverter
     5	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     6	import kotlinx.serialization.encodeToString
     7	import kotlinx.serialization.json.Json
     8	
     9	@ProvidedTypeConverter
    10	class TopParticipantListConverter {
    11	
    12	    @TypeConverter
    13	    fun toDb(list: List<TopParticipant>): String = Json.encodeToString(list)
    14	
    15	    @TypeConverter
    16	    fun fromDb(json: String): List<TopParticipant> = try {
    17	        Json.decodeFromString(json)
    18	    } catch (e: Exception) {
    19	        emptyList()
    20	    }
    21	}

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
     4	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     5	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
    10	
    11	fun Attempt.toEntity(): LessonAttemptEntity = LessonAttemptEntity(
    12	    attemptId = id.value,
    13	    userId = userId,
    14	    lessonId = lessonId.value,
    15	    lessonVersion = lessonVersion,
    16	    isHard = if (mode == Difficulty.HARD) 1 else 0,
    17	    codeAnswer = codeAnswer.raw,
    18	    percentScore = percentScore.raw,
    19	    completedAt = completedAt,
    20	)
    21	
    22	fun LessonAttemptEntity.toDomain(): Attempt = Attempt(
    23	    id = AttemptId(attemptId),
    24	    userId = userId,
    25	    lessonId = LessonId(lessonId),
    26	    lessonVersion = lessonVersion,
    27	    mode = if (isHard != 0) Difficulty.HARD else Difficulty.EASY,
    28	    completedAt = completedAt,
    29	    codeAnswer = CodeAnswer(codeAnswer),
    30	    percentScore = PercentScore(percentScore),
    31	)

exec
/bin/bash -lc "nl -ba shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
     4	import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
     5	
     6	object LessonDtoMapper {
     7	    fun LessonDto.toEntity(): LessonEntity = LessonEntity(
     8	        id = id,
     9	        themeId = themeId,
    10	        title = title,
    11	        order = order,
    12	        version = version,
    13	        contentsVersion = contentsVersion,
    14	        lastModifiedAt = lastModifiedAt,
    15	        archived = archived,
    16	        averageRating = averageRating,
    17	        ratingCount = ratingCount ?: 0,
    18	        top3 = top3 ?: emptyList(),
    19	    )
    20	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.data.mapper
     2	
     3	import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
     5	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     6	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
     7	
     8	object LessonMapper {
     9	    fun LessonEntity.toDomain(): Lesson = Lesson(
    10	        id = LessonId(id),
    11	        themeId = ThemeId(themeId),
    12	        title = title,
    13	        order = order,
    14	        version = version,
    15	        contentsVersion = contentsVersion,
    16	        lastModifiedAt = lastModifiedAt,
    17	        archived = archived,
    18	        averageRating = averageRating,
    19	        ratingCount = ratingCount,
    20	        top3 = top3,
    21	    )
    22	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.domain.model
     2	
     3	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     4	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
     5	
     6	/**
     7	 * Domain entity representing a lesson within a [Theme].
     8	 *
     9	 * A lesson is the direct parent of questions. Stored in Firestore collection
    10	 * `lessons/{lessonId}` with a [themeId] reference.
    11	 *
    12	 * Spec: docs/features/home-and-my-quests/0-spec.md
    13	 *   FR#12, Feature Domain Contract — Lesson invariants (scenario 18).
    14	 *   [Codex fix #5] archived: consistent soft-delete across all hierarchy levels.
    15	 */
    16	data class Lesson(
    17	    val id: LessonId,
    18	    val themeId: ThemeId,
    19	    val title: String,
    20	    /**
    21	     * Display order within the parent theme. Non-negative.
    22	     * Invariant: >= 0.
    23	     */
    24	    val order: Int,
    25	    /**
    26	     * Monotonically-increasing version of this lesson's own fields.
    27	     * Invariant: >= 1.
    28	     */
    29	    val version: Long,
    30	    /**
    31	     * Version counter tracking changes in child questions.
    32	     * Invariant: >= 0.
    33	     */
    34	    val contentsVersion: Long,
    35	    /**
    36	     * Server-set timestamp (Unix millis) of the last write.
    37	     * Used as delta-sync cursor: `where('lastModifiedAt', '>', localCursor)`.
    38	     * Set by Firestore FieldValue.serverTimestamp() at write time.
    39	     * Invariant: >= 0.
    40	     */
    41	    val lastModifiedAt: Long,
    42	    /**
    43	     * Soft-delete flag. When `true`, the client deletes the local lesson entry.
    44	     * Default `false` for backward compatibility.
    45	     */
    46	    val archived: Boolean = false,
    47	    val averageRating: Float? = null,
    48	    val ratingCount: Int = 0,
    49	    val top3: List<TopParticipant> = emptyList(),
    50	) {
    51	    init {
    52	        require(title.isNotBlank()) { "Lesson.title must not be blank" }
    53	        require(order >= 0) { "Lesson.order must be >= 0, got $order" }
    54	        require(version >= 1) { "Lesson.version must be >= 1, got $version" }
    55	        require(contentsVersion >= 0) { "Lesson.contentsVersion must be >= 0, got $contentsVersion" }
    56	        require(lastModifiedAt >= 0) { "Lesson.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    57	        require(top3.size <= 3) { "Lesson.top3.size must be <= 3, got ${top3.size}" }
    58	    }
    59	}

exec
/bin/bash -lc "nl -ba platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.platform.firebase.lesson
     2	
     3	import com.google.firebase.firestore.DocumentSnapshot
     4	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     5	import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
     6	
     7	fun DocumentSnapshot.toLessonDto(): LessonDto =
     8	    LessonDto(
     9	        id = id,
    10	        themeId = getString("themeId") ?: "",
    11	        title = getString("title") ?: "",
    12	        order = getLong("order")?.toInt() ?: 0,
    13	        version = getLong("version") ?: 1L,
    14	        contentsVersion = getLong("contentsVersion") ?: 0L,
    15	        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
    16	        archived = getBoolean("archived") ?: false,
    17	        averageRating = getDouble("averageRating")?.toFloat(),
    18	        ratingCount = getLong("ratingCount")?.toInt(),
    19	        top3 = parseTop3(this),
    20	    )
    21	
    22	@Suppress("UNCHECKED_CAST")
    23	private fun parseTop3(doc: DocumentSnapshot): List<TopParticipant>? {
    24	    val raw = doc.get("top3") as? List<*> ?: return null
    25	    return raw.mapNotNull { item ->
    26	        val map = item as? Map<*, *> ?: return@mapNotNull null
    27	        val nickname = map["nickname"] as? String ?: return@mapNotNull null
    28	        val avatarUrl = (map["avatarUrl"] as? String)?.takeIf { it.startsWith("https://") }
    29	        val percent = (map["percent"] as? Long)?.toInt() ?: return@mapNotNull null
    30	        TopParticipant(nickname = nickname, avatarUrl = avatarUrl, percent = percent)
    31	    }
    32	}

exec
/bin/bash -lc "nl -ba shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.core.persistence
     2	
     3	import androidx.room.ColumnInfo
     4	import androidx.room.Entity
     5	import androidx.room.ForeignKey
     6	import androidx.room.Index
     7	import androidx.room.PrimaryKey
     8	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     9	
    10	@Entity(
    11	    tableName = "lessons",
    12	    foreignKeys = [
    13	        ForeignKey(
    14	            entity = ThemeEntity::class,
    15	            parentColumns = ["id"],
    16	            childColumns = ["themeId"],
    17	            onDelete = ForeignKey.CASCADE,
    18	        ),
    19	    ],
    20	    indices = [
    21	        Index(value = ["themeId"]),
    22	        Index(value = ["lastModifiedAt"]),
    23	    ],
    24	)
    25	data class LessonEntity(
    26	    @PrimaryKey val id: String,
    27	    val themeId: String,
    28	    val title: String,
    29	    val order: Int,
    30	    val version: Long,
    31	    val contentsVersion: Long,
    32	    val lastModifiedAt: Long,
    33	    val archived: Boolean,
    34	    @ColumnInfo(name = "average_rating") val averageRating: Float? = null,
    35	    @ColumnInfo(name = "rating_count") val ratingCount: Int = 0,
    36	    @ColumnInfo(name = "top3") val top3: List<TopParticipant> = emptyList(),
    37	)

 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.data.dto
     2	
     3	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
     4	
     5	data class LessonDto(
     6	    val id: String,
     7	    val themeId: String,
     8	    val title: String,
     9	    val order: Int,
    10	    val version: Long,
    11	    val contentsVersion: Long,
    12	    val lastModifiedAt: Long,
    13	    val archived: Boolean,
    14	    val averageRating: Float? = null,
    15	    val ratingCount: Int? = null,
    16	    val top3: List<TopParticipant>? = null,
    17	)

exec
/bin/bash -lc "sed -n '130,190p' docs/features/lesson-runner/0-spec.md && sed -n '600,650p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:

27. **bestStars per lesson** = `Stars(max(rawTenths) for each attempt)`. Integer max по всем своим попыткам этого урока. — [USER DECIDED]

28. **HARD unlock** = `∃ EASY-попытка с allShownAnswersAre9 == true` (string-based проверка codeAnswer, не percent). Это derived state, не отдельный флаг в БД. — [USER DECIDED]

29. **Карточка урока** в `LessonListComponent` (фича `quizzes-screen`) расширяется:
   - Bound `bestStars: Stars` (показывается через `StarRating(rating = bestStars.rawTenths / 10f)` — existing API в `android/core/designsystem/.../StarRating.kt`).
   - Conditional `Checkbox` для HARD mode — visible если `hardUnlocked == true` (НЕ `bestStars.rawTenths >= 20`) + tap-handler меняет state «следующий тап на урок открывает HARD-режим».
   — [USER DECIDED] checkbox visibility = hardUnlocked.

#### Финальный экран результата

30. **Содержимое экрана результата** (порядок сверху вниз):
   1. **Большая цифра процентов** (`percentScore` крупным шрифтом, центр).
   2. **Подпись** в зависимости от исхода:
      - Впервые получил 2★ (perfect EASY): «Поздравляем! Сложные вопросы доступны».
      - Впервые получил 3★ (perfect HARD): «100% сложные! Вы прошли урок полностью».
      - Иначе — нейтрально (например «Урок завершён»).
   3. **Звёзды** (`StarRating` с этой попытки stars value).
   4. **Статистика** (краткая, своих попыток): `общее число попыток`, `средний %` по своим попыткам этого урока (читается из Room).
   5. **Опрос «Оцените урок»** (1/2/3 целых звезды, не fractional) — показывается **только**: (а) если `attempt.codeAnswer.allShownAnswersAre9 == true` (perfect EASY ИЛИ perfect HARD), (б) если пользователь ещё не оценивал этот урок (нет местного флага). Один раз в жизни на (userId, lessonId). — [USER DECIDED]
   6. **Топ-3** лучших участников этого урока (из `Lesson.top3` field, агрегируется на сервере). Каждый элемент: аватар (Coil URL — placeholder если не закеширован), nickname, percent. Если top3 пуст — секция скрыта.
   7. **Кнопка «Завершить»** — если rating был выбран в опросе → вызов `SubmitLessonRatingUseCase.invoke(state.userId, lessonId, rating)`, сбрасывает FLAG_SECURE если был, возврат на список уроков (pop ChildStack до `LessonListComponent`).
   — [USER DECIDED] полный список из user request.

31. **Sworn-fold логика** (Matrix 6): см. требования #14-16.

#### Server-side data model (для cascade sync infrastructure)

32. **Новая Firestore коллекция `lesson_attempts/{attemptId}`** (sync logic — не наша фича, описывается контракт):
   - `userId: String`
   - `lessonId: String`
   - `lessonVersion: Long`
   - `hardQuestion: Boolean` (true=HARD, false=EASY)
   - `completedAt: serverTimestamp`
   - `codeAnswer: String`
   - `percentScore: Int`
   - `lastModifiedAt: serverTimestamp` (для cascade sync cursor)
   - `version: Long = 1` (immutable после создания)
   — [USER DECIDED] поле `hardQuestion` (не `hardQuiz`) — по уточнению.

33. **Новая Firestore коллекция `lesson_ratings/{ratingId}`** (write-only с клиента; сервер агрегирует — отдельная задача):
   - `userId: String`
   - `lessonId: String`
   - `lessonVersion: Long`
   - `rating: Int` (1, 2 или 3)
   - `ratedAt: serverTimestamp`
   - `lastModifiedAt: serverTimestamp`
   - `version: Long = 1`
   — [USER DECIDED]

34. **Расширение `Lesson` (Firestore + domain)**:
   - `averageRating: Float?` — средняя оценка (1..3) по всем `lesson_ratings`. Сервер агрегирует.
   - `ratingCount: Int?` — количество оценок.
   - `top3: List<TopParticipant>` (size ≤ 3) — `{ nickname, avatarUrl, percent }`. Сервер агрегирует из `lesson_attempts` для текущей `lessonVersion`.
   — [USER DECIDED] поле в Lesson, агрегация серверная (отдельная задача).

   > **Amendment 2026-04-26 (design phase, user-approved)**: `Lesson.ratingCount` изменён с `Int?` на `Int = 0` (non-nullable, default 0) — align с Quest.averageRatingCount pattern (`shared/feature/quest/domain/.../model/Quest.kt:69`). Семантика: missing field = 0 (нет оценок), null более не используется. Resolution applied per `docs/features/lesson-runner/03-decisions.md ADR-LR-15`.

#### DI и module structure

   - `'0'` = вопрос НЕ был показан в этой попытке (out-of-subset)
   - `'1'`..`'9'` для показанных или попытавшихся показаться:
     - `'1'` = 0% правильности (full miss / timeout без угадывания / invalid payload)
     - `'5'` = 50%
     - `'9'` = 100%
   - Формула: `digit = round(correct_share × 8) + 1`, где `correct_share ∈ [0, 1]`.
8. **Score formula per type**:
   - SingleChoice: `correct_share = if (selected == correctOptionId) 1.0 else 0.0`. Guard: `selected ∈ options.map { it.id }` или null. Если selected ∉ options → trait как null (timeout).
   - MultipleChoice (Jaccard): `correct_share = correct_picked / (correct_picked + wrong_picked + missed)` — эквивалентно `|picked ∩ correctOptionIds| / |picked ∪ correctOptionIds|`. Если denominator == 0 (=пустой union: невозможно поскольку correctOptionIds.size ≥ 2 по ADR) → 0.0. Guard: `picked ⊆ options.map { it.id }`. Любые foreign IDs игнорируются (treat as wrong по умолчанию через filter).
   - Ordering: `correct_share = matched_positions / total_positions`, `matched_positions = items.indices.count { i -> userOrder.getOrNull(i) == correctOrder[i].id }`. Guard: `userOrder.size == items.size` и `userOrder.toSet() == items.map{it.id}.toSet()` (perm check); если invalid → score 0% (`'1'`).
   - FillBlank: `correct_share = filled_correct / total_blanks`. `filled_correct = blanks.count { filled[it.id] == it.correctCandidateId }`. Guard: `filled.values.filterNotNull() ⊆ candidates.map { it.id }`. null/foreign → wrong.
9. **Auto-answer на timeout**:
   - SingleChoice: `selected = options.random(seedFor(question))`
   - MultipleChoice: `selected = correctOptionIds.size случайных options` (детерминированно через seed)
   - Ordering: `userOrder = items.shuffled(seedFor(question))`
   - FillBlank: каждый blank → `candidates.random(seedFor(question))` (с возвратом — same candidate может попасть в несколько blanks; design phase решит UI блокировку, но domain-level fine)
   - Score фиксируется по тем же правилам.
10. **percentScore (Int)**: `nonZero = codeAnswer.count { it != '0' }`. Если `nonZero == 0` → 0. Иначе: `sum = codeAnswer.filter { it != '0' }.sumOf { (it.digitToInt() - 1) * 100 / 8 }`; `percentScore = sum / nonZero` (integer division — детерминирует).
11. **Perfect attempt detection** (для unlock и rating prompt): `attempt.allShownAnswersAre9 = codeAnswer.all { it == '0' || it == '9' }` AND `codeAnswer.any { it == '9' }`. Это **string-based**, НЕ percent-based — нет Float precision issues.
12. **Stars per attempt formula** (derived value class `Stars(rawTenths: Int 0..30)`):
    - EASY: `rawTenths = (percentScore * 20 + 50) / 100` → `[0..20]`. Эквивалент `stars = (percentScore / 100) × 2.0` с round half up.
    - HARD: `rawTenths = 20 + round(percentScore * 1 / 10)` → `[20..30]`. Точно: `tenths = 20 + (percentScore * 10 + 50) / 100`.
    - Все математика integer. Float только при UI rendering.
13. **bestStars per lesson** = `max(rawTenths per attempt)`. Integer max по всем своим попыткам этого `lessonId`. Если нет попыток — `Stars(0)`.
14. **HARD unlock condition** = `attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`. String-based, не percent. **НЕ** через bestStars (HARD attempt с percentScore=0 даёт `Stars(rawTenths=20)` floor, но НЕ unlocks).
15. **Checkbox visibility on lesson card**: `hardUnlocked == true`. **НЕ** через bestStars (HARD attempt floor `Stars(rawTenths=20)` не unlocks).
16. **Rating prompt visibility** = `attempt.allShownAnswersAre9 && !ratingsRepo.hasSubmitted(userId, lessonId)`. **String-based**, не percent-based.
17. **lessonVersion fixation**: `attempt.lessonVersion = lesson.version` на момент `StartLessonAttemptUseCase` invocation (snapshot когда state ещё `Loading`). Если `Lesson` Flow emit-ит обновление между UseCase invocation и завершением — игнорируется. `rating.lessonVersion = lesson.version` на момент `submit` (свежий read).
18. **Attempt save = только в конце**: один `LessonAttemptRepository.save(attempt)` в:
    - `CompleteAttemptUseCase` (последний вопрос отвечен) → `RunnerState.Completed`
    - `AbortAttemptUseCase` (exit-via-dialog или confirmed cross) → `RunnerState.Aborted`
    - **НЕ при process kill** (lost).
    - При Room exception → `RunnerState.SaveFailed(attempt, error)`. UI показывает result с warning. No auto-retry в MVP.
19. **No incremental save** — runner state живёт только in-memory + Decompose `instanceKeeper` для rotation.
20. **Subset stability**: subset через seed фиксируется на старте попытки.
21. **Timer formula**:
    ```
    charsCount = chars(text) + sum(chars(option_or_item_or_candidate text)) + (if hasImage then 100 else 0)
    seconds = max(5, round(charsCount × k))
    ```
    `k_easy ≈ 0.18`, `k_hard ≈ k_easy / 1.5 ≈ 0.12`. Min floor 5 секунд. Точные коэффициенты и +100-bonus — config константы в `lesson-runner/domain/.../config/TimerCoefficients.kt`.
22. **FLAG_SECURE**: enabled только в HARD-mode runner (toggle on `RunnerState.Ready` enter с mode=HARD; untoggle on `Completed/Aborted/SaveFailed/InitFailed` exit).
23. **Attempt immutability** — после save Attempt в Room не редактируется (только создание новых).
24. **Rating uniqueness — lifetime per (userId, lessonId)** (упрощено для consistency):
    - Local Room PK: compound `(userId, lessonId)` в таблице `lesson_rating_submitted_local`. Один раз поставил → больше не предлагается.
    - Remote ID: deterministic `sha256("$userId:$lessonId")` (БЕЗ lessonVersion в ключе). Cloud Function dedupe через document-id collision (existing → ignore new).
    - `lessonVersion` сохраняется в payload документа `lesson_ratings` для server-side analytics (понимать какая версия оценивалась), но НЕ участвует в uniqueness key.
    - Если автор кардинально поменяет урок и захочет «обнулить» все оценки — это server-side policy (отдельная задача, не наша фича).
25. **lessonVersion в `LessonRating` payload** — analytics поле, не uniqueness key. Фиксируется на момент submit.

### Repository Interfaces (фиксируется явно для Phase 3.8)

exec
/bin/bash -lc "sed -n '1138,1148p' docs/features/lesson-runner/0-spec.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
- value object guards (62-71)
- failure semantics (72-74a)
- subset determinism (75-77)
- state transitions (78-82) — test 83 исключён из domain (см. ниже комментарий к нему)
Итого ~89 domain test scenarios (с учётом 39a/39b/61a/61b/61c/35a/74a/strikethrough 83). Скрипт renumber применяется в Phase 3.8b — test-dev волен переписать в монотонной последовательности 1..N если предпочитает.
61. [ ] Tests: JVM unit-тесты для каждого presentation Component через fakes
62. [ ] Tests: Compose UI тесты для key scenarios (per-type вопросы, timer, dialog onResume, FLAG_SECURE toggle)
63. [ ] Build: `./gradlew :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache` зелёный (Walking Skeleton acceptance)
64. [ ] Build: `./gradlew assemble --no-configuration-cache` зелёный после реализации
65. [ ] Build: `./gradlew test --no-configuration-cache` и `./gradlew allTests --no-configuration-cache` зелёные


exec
/bin/bash -lc "rg -n \"@Ignore|Ignore\\(\" android/feature/lesson-runner/presentation/src android/feature/quizzes-screen/presentation/src shared/feature/lesson-runner -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:172:    @Ignore("OQ-CT10: production seam missing — QuestionProgressHeader does not call component.onTimeout()")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:400:    @Ignore("Phase-06 scope: belongs to LessonItemCard, not LessonRunnerScreen")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:407:    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:414:    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")

exec
/bin/bash -lc "rg -n \"FillBlank|MultipleChoice|Ordering|onOptionToggled|onMoveUp|showRatingPrompt|SaveAttemptFailed|SaveFailed|FLAG_SECURE|flagSecure|hardUnlocked|bestStarsRawTenths\" android/feature/lesson-runner/presentation/src/test android/feature/lesson-runner/presentation/src/androidTest android/feature/quizzes-screen/presentation/src/test android/feature/quizzes-screen/presentation/src/androidTest -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:22: * CT-22: StarRating renders with bestStarsRawTenths=15 (smoke — no test tags on stars)
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:23: * CT-23: hardUnlocked=false → no Checkbox
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:24: * CT-24: hardUnlocked=true  → Checkbox visible and unchecked
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:33:    // GIVEN LessonItemUi(bestStarsRawTenths=15, hardUnlocked=false)
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:37:    fun ct22_lessonItemCard_bestStarsRawTenths15_rendersWithoutCrash() {
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:42:            bestStarsRawTenths = 15,
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:43:            hardUnlocked = false,
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:54:    // GIVEN LessonItemUi(hardUnlocked=false)
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:56:    // THEN Checkbox is not present (hardUnlocked=false → Checkbox branch skipped)
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:58:    fun ct23_lessonItemCard_hardUnlocked_false_noCheckbox() {
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:62:            hardUnlocked = false,
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:73:    // GIVEN LessonItemUi(hardUnlocked=true, isHardChecked=false)
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:77:    fun ct24_lessonItemCard_hardUnlocked_true_checkboxVisible() {
android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt:81:            hardUnlocked = true,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:63:        hardUnlocked: Boolean = false,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:64:        bestStarsRawTenths: Int = 16,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:66:        showRatingPrompt: Boolean = false,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:73:        hardUnlocked = hardUnlocked,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:74:        bestStarsRawTenths = bestStarsRawTenths,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:80:        showRatingPrompt = showRatingPrompt,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:190:    // --- CT-11: GIVEN hard Question WHEN rendered THEN FLAG_SECURE set in window ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:193:    fun ct11_hard_mode_flagSecure_set() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:206:        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:209:    // --- CT-12: GIVEN hard state transitions to Loading WHEN rendered THEN FLAG_SECURE cleared ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:212:    fun ct12_exit_hard_mode_flagSecure_cleared() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:223:        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:228:        assertEquals(0, composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:231:    // --- CT-13: GIVEN easy Question WHEN rendered THEN FLAG_SECURE NOT set ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:234:    fun ct13_easy_mode_no_flagSecure() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:246:        assertEquals(0, composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:335:    // --- CT-18: GIVEN showRatingPrompt=true WHEN rendered THEN rating section visible ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:338:    fun ct18_ratingPrompt_showRatingPrompt_true_visible() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:339:        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(showRatingPrompt = true)))
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:350:    // --- CT-19: GIVEN showRatingPrompt=false WHEN rendered THEN rating section absent ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:354:        val fakeComponent = RunFakeComponent(MutableStateFlow(resultState(showRatingPrompt = false)))
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:402:    fun ct22_bestStarsRawTenths_15_starRating_1_5() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:407:    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:409:    fun ct23_hardUnlocked_false_checkbox_absent() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:414:    @Ignore("Phase-06 scope: hardUnlocked checkbox in LessonItemCard")
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:416:    fun ct24_hardUnlocked_true_checkbox_visible() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:489:    // --- CT-29: GIVEN hard mode rendered WHEN activity recreated THEN FLAG_SECURE still set ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:492:    fun ct29_hardMode_activityRecreate_flagSecure_remains() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:504:        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:517:        assertTrue(composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:35: * bestStars / hardUnlocked computation from [LessonAttemptRepository].
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:124:     * THEN bestStarsRawTenths == 0 AND hardUnlocked == false.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:128:    fun `pt15 no attempts defaults to zero stars and hardUnlocked false`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:137:        assertEquals(0, items[0].bestStarsRawTenths, "no attempts → Stars(0) → rawTenths=0")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:138:        assertFalse(items[0].hardUnlocked, "no attempts → hardUnlocked=false")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:145:     * THEN hardUnlocked == true.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:169:        assertTrue(items[0].hardUnlocked, "EASY perfect attempt (allShownAnswersAre9=true) → hardUnlocked=true")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:176:     * THEN hardUnlocked == false, even if rawTenths == 20.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:200:        assertFalse(items[0].hardUnlocked, "EASY attempt with '8' digit → hardUnlocked=false")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:207:     * THEN bestStarsRawTenths == 15.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:231:        assertEquals(15, items[0].bestStarsRawTenths, "EASY 75% → Stars rawTenths=15")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:237:     * PT-35: GIVEN hardUnlocked=false WHEN onHardCheckToggled called
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:246:        // No attempts → hardUnlocked=false
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:254:        assertFalse(items[0].isHardChecked, "toggle ignored when hardUnlocked=false → isHardChecked stays false")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:260:     * PT-36: GIVEN hardUnlocked=true AND isHardChecked=false WHEN onHardCheckToggled called
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:282:        assertTrue(loadedItems(component)[0].hardUnlocked, "precondition: hardUnlocked=true")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt:290:            "toggle when hardUnlocked=true → isHardChecked=true",
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:94:        hardUnlocked: Boolean = false,
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:96:    ) = LessonItemUi(id = id, title = title, hardUnlocked = hardUnlocked, isHardChecked = isHardChecked)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:139:     * Spec: LL-U-04 — onLessonClick with hardUnlocked=false pushes LessonRunner with EASY mode.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:143:    fun `onLessonClick pushes LessonRunner with EASY mode when hardUnlocked is false`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:145:        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A", hardUnlocked = false, isHardChecked = false)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:152:        assertEquals(Difficulty.EASY, pushed.mode, "mode must be EASY when hardUnlocked=false")
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:157:     * Spec: LL-U-04 edge case — onLessonClick with hardUnlocked=true + isHardChecked=true pushes HARD mode.
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:160:    fun `onLessonClick pushes LessonRunner with HARD mode when hardUnlocked and isHardChecked`() = runTest(testScheduler) {
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:162:        val lessonItem = lessonItemFixture(id = "l-1", title = "Lesson A", hardUnlocked = true, isHardChecked = true)
android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt:167:        assertEquals(Difficulty.HARD, pushed.mode, "mode must be HARD when hardUnlocked=true and isHardChecked=true")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:59: * Open Question: RunnerUiState.Result is expected to contain bestStarsRawTenths: Int
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:60: * and hardUnlocked: Boolean fields for PT-15..PT-17 and PT-34..PT-36 (per AC-21..23, AC-47..49).
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:216:    fun `EASY_loading_to_ready_no_flagSecure`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:318:    // ── PT-12: EASY 50% → Stars(10); hardUnlocked=false ──────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:321:    //        WHEN Completed THEN computeStars gives rawTenths=10; hardUnlocked=false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:323:    fun `EASY_50percent_computeStars_gives_Stars10_hardUnlocked_false`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:337:        // hardUnlocked requires allShownAnswersAre9 → codeAnswer "15111" is false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:338:        assertFalse(result.hardUnlocked, "hardUnlocked must be false when codeAnswer has non-9 digits")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:375:    // ── PT-15: No prior attempts → bestStarsRawTenths=0, hardUnlocked=false ──
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:378:    //        WHEN Result state THEN bestStarsRawTenths=0, hardUnlocked=false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:379:    // Open Question: RunnerUiState.Result must have bestStarsRawTenths: Int and hardUnlocked: Boolean
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:381:    fun `no_attempts_bestStarsRawTenths_zero_hardUnlocked_false`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:390:        assertEquals(0, result.bestStarsRawTenths, "No prior attempts → bestStarsRawTenths must be 0")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:391:        assertFalse(result.hardUnlocked, "No prior attempts → hardUnlocked must be false")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:394:    // ── PT-16: EASY perfect → hardUnlocked=true ──────────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:397:    //        WHEN Result state THEN hardUnlocked=true
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:399:    fun `EASY_perfect_attempt_hardUnlocked_true`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:415:        assertTrue(result.hardUnlocked, "After EASY perfect attempt, hardUnlocked must be true")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:418:    // ── PT-17: EASY imperfect → hardUnlocked=false ───────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:421:    //        WHEN Result state THEN hardUnlocked=false even if rawTenths=20
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:423:    fun `EASY_imperfect_hardUnlocked_false`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:438:        assertFalse(result.hardUnlocked, "EASY imperfect attempt must not unlock HARD mode")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:697:    // ── PT-29: allShown9=true && !hasSubmitted → showRatingPrompt=true ────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:700:    //        WHEN Result state THEN showRatingPrompt=true
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:702:    fun `allShown9_not_submitted_showRatingPrompt_true`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:712:        assertTrue(result.showRatingPrompt, "showRatingPrompt must be true when ratingPrompt=true")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:715:    // ── PT-30: hasSubmitted → showRatingPrompt=false ──────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:718:    //        WHEN Result state THEN showRatingPrompt=false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:720:    fun `hasSubmitted_showRatingPrompt_false`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:730:        assertFalse(result.showRatingPrompt, "showRatingPrompt must be false when ratingPrompt=false")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:733:    // ── PT-31: allShown9=false → showRatingPrompt=false ──────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:736:    //        WHEN Result state THEN showRatingPrompt=false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:738:    fun `allShown9_false_showRatingPrompt_false`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:748:        assertFalse(result.showRatingPrompt, "showRatingPrompt must be false when allShownAnswersAre9=false")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:794:    // ── PT-34: bestStars.rawTenths=15 → bestStarsRawTenths=15 ────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:797:    //        WHEN Result state THEN bestStarsRawTenths=15
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:799:    fun `bestStars_15_bestStarsRawTenths_15`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:817:        assertEquals(15, result.bestStarsRawTenths, "bestStarsRawTenths must be 15 from prior EASY 75% attempt")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:820:    // ── PT-35: hardUnlocked=false → toggle not shown ──────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:822:    // PT-35: GIVEN no perfect EASY attempt → hardUnlocked=false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:823:    //        WHEN Result state THEN hardUnlocked=false
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:825:    fun `hardUnlocked_false_no_perfect_attempt`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:835:        assertFalse(result.hardUnlocked, "hardUnlocked must be false when no EASY perfect attempt exists")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:838:    // ── PT-36: hardUnlocked=true → toggle visible ─────────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:841:    //        WHEN Result state THEN hardUnlocked=true
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:843:    fun `hardUnlocked_true_after_EASY_perfect_attempt`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:861:        assertTrue(result.hardUnlocked, "hardUnlocked must be true after EASY perfect prior attempt")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:908:    // ── PT-40: CompleteUseCase returns SaveFailed → saveWarning=true + event ──
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:910:    // PT-40: GIVEN FakeComplete returns SaveFailed
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:911:    //        WHEN complete THEN uiState.Result.saveWarning=true + SaveAttemptFailed event
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:913:    fun `completeSaveFailed_saveWarning_true_and_event_emitted`() = runTest {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:915:        fakeComplete.result = RunnerState.SaveFailed(
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:925:        assertTrue(result.saveWarning, "saveWarning must be true when CompleteUseCase returns SaveFailed")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:926:        assertIs<RunnerEvent.SaveAttemptFailed>(deferred.await(), "SaveAttemptFailed event must be emitted")

exec
/bin/bash -lc "rg -n \"Walking|AttemptId\\(|RatingId\\(|\\.raw|\\.value|raw→value|AttemptId|RatingId\" docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch shared/feature/lesson-runner/domain/src/commonTest shared/feature/lesson-runner/domain/src/commonMain -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:51:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:52:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:68:+        _uiState.value = state
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:97:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:98:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:115:+        _uiState.value = state
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:136:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:137:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:155:+        _childStack.value = ChildStack(
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:166:+        setQuestListActive(catalogId.value, catalogName)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:172:+        _childStack.value = idleStack()
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:188:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:189:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:205:+        _uiState.value = state
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:220:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:221:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:237:+        _uiState.value = state
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:860:+        val configBefore = fakeComponent.childStack.value.active.configuration
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:883:+        val configAfter = fakeComponent.childStack.value.active.configuration
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:907:+        val configAfter = fakeComponent.childStack.value.active.configuration
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1079:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1080:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1121:+                .collect { _uiState.value = it }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1169:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1170:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1211:+                .collect { _uiState.value = it }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1219:+                questId = quest.id.value,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1245:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1306:+        navigation.pushNew(QuizzesConfig.QuestList(catalogId.value, listOf("Каталоги", catalogName)))
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1310:+        navigation.pushNew(QuizzesConfig.SectionList(questId.value, titles))
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1315:+        val active = childStack.value.active
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1327:+        val virtualCount = (titlesSize + 1 - childStack.value.items.size).coerceAtLeast(0)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1376:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1377:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1418:+                .collect { _uiState.value = it }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1443:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1444:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1485:+                .collect { _uiState.value = it }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1507:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1537:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1572:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1594:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1611:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1703:+    id = id.value,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1739:+    id = id.value,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1756:+    id = id.value,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1802:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1803:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1996:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:1997:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2038:+                itemsIndexed(state.quests, key = { _, quest -> quest.id.value }) { index, quest ->
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2222:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2223:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2331:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2332:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2546:+        childStack.value.backStack.size + 1
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2605:+            component.childStack.value.active.instance,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2652:+            component.childStack.value.active.instance,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2671:+        assertIs<QuizzesChild.Idle>(component.childStack.value.active.instance)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2683:+        val stack = component.childStack.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2703:+        val active = component.childStack.value.active
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2714:+     * Observable guarantee: no exception on lifecycle.destroy(), and childStack.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2727:+        assertNotNull(component.childStack.value, "childStack.value must not throw after destroy")
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2747:+            component.childStack.value.active.instance,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2771:+            component.childStack.value.active.instance,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:2790:+        assertIs<QuizzesChild.QuestList>(component.childStack.value.active.instance)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3119:+        childStack.value.backStack.size + 1
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3162:+        val activeConfig = restored.childStack.value.active.configuration
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3185:+            restored.childStack.value.active.configuration,
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3229:+        val backStack = restored.childStack.value.backStack
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3341:+        assertIs<HierarchyListUiState.Loading>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3355:+        assertIs<HierarchyListUiState.Loaded>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3368:+        assertIs<HierarchyListUiState.Empty>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3624:+        assertIs<QuestListUiState.Loading>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3643:+        val state = component.uiState.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3659:+        assertIs<QuestListUiState.Empty>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3765:+        val state = component.uiState.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3768:+        assertEquals("q-visible", state.quests.first().id.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3874:+        assertIs<HierarchyListUiState.Loading>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3893:+        val state = component.uiState.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3908:+        val state = component.uiState.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3958:+        assertIs<HierarchyListUiState.Loaded>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3959:+        assertEquals(1, (component.uiState.value as HierarchyListUiState.Loaded).items.size)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:3969:+        assertEquals(2, (component.uiState.value as HierarchyListUiState.Loaded).items.size)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4106:+        assertIs<HierarchyListUiState.Loading>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4120:+        val state = component.uiState.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4135:+        assertIs<HierarchyListUiState.Empty>(component.uiState.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4207:+    fun emit(lessons: List<Lesson>) { store.value = lessons }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4212:+    override suspend fun getById(id: LessonId): Lesson? = store.value.find { it.id == id }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4257:+    override suspend fun getById(id: QuestId): Quest? = store.value.find { it.id == id }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4266:+    fun emit(quests: List<Quest>) { store.value = quests }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4287:+    fun emit(sections: List<Section>) { store.value = sections }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4292:+    override suspend fun getById(id: SectionId): Section? = store.value.find { it.id == id }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4363:+    fun emit(themes: List<Theme>) { store.value = themes }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4368:+    override suspend fun getById(id: ThemeId): Theme? = store.value.find { it.id == id }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4457:+     * Spec: MAP-01 — Section.toDrillItem().id == section.id.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4970:+import com.arkivanov.decompose.value.MutableValue
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:4971:+import com.arkivanov.decompose.value.Value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5210:+        uidSource.value = null
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5265:+            override suspend fun currentStats(): UserStats = fakeStats.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5338:+            override suspend fun currentStats(): UserStats = fakeStats.value
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5775:+        local.observeByTheme(themeId.value).map { list -> list.map { it.toDomain() } }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5778:+        local.findById(id.value)?.toDomain()
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5781:+        local.getLocalContentsVersion(id.value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:5786:+            val dtos = remote.fetchChangedByParents(themeIds.map { it.value }.toSet(), cursor)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6072:+            _flow.value = store.values.toList()
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6079:+        _flow.value = store.values.toList()
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6089:+        _flow.value = store.values.toList()
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6367:+        assertEquals("l1", LessonId("l1").value)
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6586:+            map.values.filter { it.themeId == themeId }.sortedBy { it.order }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6589:+    override suspend fun getById(id: LessonId): Lesson? = cache.value[id]
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6630:+    override suspend fun getLocalContentsVersion(id: LessonId): Long? = cache.value[id]?.contentsVersion
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6634:+    fun seed(lessons: List<Lesson>) { cache.value = lessons.associateBy { it.id } }
docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch:6637:+    fun snapshot(): List<Lesson> = cache.value.values.sortedBy { it.id.value }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:29:    val codeAnswerChars = state.codeAnswer.raw.toCharArray()
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:30:    codeAnswerChars[currentQuestion.codeAnswerIndex] = ('0' + score.raw)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:110:        Difficulty.EASY -> (percentScore.raw * 20 + 50) / 100
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:111:        Difficulty.HARD -> 20 + (percentScore.raw * 10 + 50) / 100
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:122:    return Stars(attempts.maxOf { computeStars(it.percentScore, it.mode).rawTenths })
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:170:    val chars = state.codeAnswer.raw.toCharArray()
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:184:    val nonZero = codeAnswer.raw.filter { it != '0' }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:13: * Providers: data module registers single<AttemptIdProvider>, single<RandomSeedProvider>, single<RatingIdProvider>.
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/AbortAttemptUseCase.kt:23:    private val attemptIdProvider: () -> AttemptId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:69:        val sorted = eligible.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/StartLessonAttemptUseCase.kt:78:        val playOrder = subset.sortedWith(compareBy({ it.order }, { it.sourceId.value }))
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/CompleteAttemptUseCase.kt:26:    private val attemptIdProvider: () -> AttemptId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Score.kt:10:        require(raw in 0..9) { "Score.raw must be in 0..9, got $raw" }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:23:    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/use_case/SubmitLessonRatingUseCase.kt:35:            ?: return Result.failure(IllegalStateException("Lesson not found: ${lessonId.value}"))
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt:4:value class RatingId(val value: String) {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt:6:        require(value.isNotBlank()) { "RatingId must not be blank" }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt:4:value class AttemptId(val value: String) {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt:6:        require(value.isNotBlank()) { "AttemptId must not be blank" }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Attempt.kt:11:    val id: AttemptId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/PercentScore.kt:11:        require(raw in 0..100) { "PercentScore.raw must be in 0..100, got $raw" }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/LessonRating.kt:11:    val id: RatingId,
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/Stars.kt:10:        require(rawTenths in 0..30) { "Stars.rawTenths must be in 0..30, got $rawTenths" }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:28:    private val idProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:65:        assertEquals("lesson7", saved.lessonId.value, "lessonId must match state.lessonId")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:68:        assertEquals("9999", saved.codeAnswer.raw, "codeAnswer must match state.codeAnswer")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:96:        assertEquals(20, savedAttempt.codeAnswer.raw.length, "codeAnswer.length == eligibleSize == 20")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:98:        val firstThree = savedAttempt.codeAnswer.raw.substring(0, 3)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SaveAttemptTest.kt:101:        val last17 = savedAttempt.codeAnswer.raw.substring(3)
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:6:interface RatingIdProvider {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/RatingIdProvider.kt:7:    fun provide(userId: String, lessonId: LessonId): RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:8:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/RatingPromptTest.kt:29:    private val idProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:40:    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/LessonVersionTest.kt:41:    private val ratingIdProvider = { userId: String, lessonId: LessonId -> RatingId("$userId:${lessonId.value}") }
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:3:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:5:interface AttemptIdProvider {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/provider/AttemptIdProvider.kt:6:    fun next(): AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerTest.kt:73:        assertEquals("1234567890", ca.raw)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:31:            resultState.codeAnswer.raw[state.playOrder[currentIndex].codeAnswerIndex] in '1'..'9',
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:63:            resultState.codeAnswer.raw[codeAnswerIndex] in '1'..'9',
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:81:            resultState.codeAnswer.raw[codeAnswerIndex] in '1'..'9',
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:95:            result1.codeAnswer.raw,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/AutoAnswerTest.kt:96:            result2.codeAnswer.raw,
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:93:        assertEquals(1, result.codeAnswer.raw.length, "codeAnswer.length == eligibleSize == 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:112:        assertEquals(original.codeAnswer.raw, restored.codeAnswer.raw, "codeAnswer preserved")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:134:        // All 4 fit in pool (pool size = 20); playOrder sorted by (order, sourceId.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:135:        val playIds = result.playOrder.map { it.sourceId.value }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:181:        assertEquals('9', result.raw[0], "position 0: answered")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:182:        assertEquals('0', result.raw[1], "position 1: out-of-subset → '0'")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:183:        assertEquals('1', result.raw[2], "position 2: unanswered subset → '1'")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/EdgeCasesTest.kt:184:        assertEquals('0', result.raw[3], "position 3: out-of-subset → '0'")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:64:        assertEquals(5, state.codeAnswer.raw.length, "Spec scenario #13: codeAnswer.length = 5")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:74:            currentState.codeAnswer.raw.none { it == '0' },
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:90:        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #14: codeAnswer.length = 50")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:100:        val nonZero = currentState.codeAnswer.raw.count { it != '0' }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:101:        val zeros = currentState.codeAnswer.raw.count { it == '0' }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:118:        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #15: codeAnswer.length=50")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/CodeAnswerConstructionTest.kt:134:        assertEquals(50, state.codeAnswer.raw.length, "Spec scenario #16: codeAnswer.length=50")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:24:        assertEquals(20, stars.rawTenths, "Spec scenario #21")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:31:        assertEquals(10, stars.rawTenths, "Spec scenario #22")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:38:        assertEquals(15, stars.rawTenths, "Spec scenario #23")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:45:        assertEquals(7, stars.rawTenths, "Spec scenario #24")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:52:        assertEquals(0, stars.rawTenths, "Spec scenario #25")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:61:        assertEquals(30, stars.rawTenths, "Spec scenario #26")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:68:        assertEquals(25, stars.rawTenths, "Spec scenario #27")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:75:        assertEquals(28, stars.rawTenths, "Spec scenario #28")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StarsComputeTest.kt:82:        assertEquals(20, stars.rawTenths, "Spec scenario #29: HARD floor is 2.0 stars")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:52:            subset1.map { it.sourceId.value },
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/SelectSubsetTest.kt:53:            subset2.map { it.sourceId.value },
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:4:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:6:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:7:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:12: * Phase-01 compile-time check #13: AttemptIdProvider and RatingIdProvider interfaces exist
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:13: * and are usable with the renamed AttemptId.value / RatingId.value (post-rename).
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:20:    fun `given AttemptIdProvider interface when instantiated as anonymous object then compilable`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:22:        val provider: AttemptIdProvider = object : AttemptIdProvider {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:23:            override fun next(): AttemptId = AttemptId("test-attempt-id")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:29:    fun `given RatingIdProvider interface when instantiated as anonymous object then compilable`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:31:        val provider: RatingIdProvider = object : RatingIdProvider {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:32:            override fun provide(userId: String, lessonId: LessonId): RatingId =
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ProviderInterfaceTest.kt:33:                RatingId("rating-$userId")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:40:    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:172:        assertEquals('9', result.codeAnswer.raw[2], "Score written at codeAnswerIndex=2")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/StateMachineTest.kt:173:        assertEquals('0', result.codeAnswer.raw[0], "Other positions unchanged")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:23:        assertEquals(9, score.raw, "Spec scenario #1: correct single choice → digit 9")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:31:        assertEquals(1, score.raw, "Spec scenario #2: wrong single choice → digit 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:45:        assertEquals(6, score.raw, "Spec scenario #3: Jaccard 2/(2+0+1) = 0.667 → digit 6")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:57:        assertEquals(9, score.raw, "Spec scenario #4: all correct → digit 9")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:69:        assertEquals(1, score.raw, "Spec scenario #5: 0 correct → digit 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:81:        assertEquals(1, score.raw, "Spec scenario #6: empty selection → digit 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:92:        assertEquals(9, score.raw, "Spec scenario #7: all positions match → digit 9")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:101:        assertEquals(5, score.raw, "Spec scenario #8: 2/4 match → digit 5")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:110:        assertEquals(1, score.raw, "Spec scenario #9: 0 position matches → digit 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:126:        assertEquals(9, score.raw, "Spec scenario #10: all blanks correct → digit 9")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:140:        assertEquals(4, score.raw, "Spec scenario #11: 1/3 blanks correct → digit 4")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:154:        assertEquals(1, score.raw, "Spec scenario #12: wrong blank → digit 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ScoreFormulaTest.kt:164:        assertEquals(1, score.raw, "timeout/null selection → digit 1")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:25:        assertEquals(100, result.raw, "Spec scenario #17")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:32:        assertEquals(50, result.raw, "Spec scenario #18")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:40:        assertEquals(75, result.raw, "Spec scenario #19")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:47:        assertEquals(0, result.raw, "Spec scenario #20")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/PercentScoreComputeTest.kt:54:        assertEquals(0, result.raw, "all zeros → 0 percent")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/TestFixtures.kt:172:    id = AttemptId("attempt-$userId-$lessonId"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:42:    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/FailureSemanticsTest.kt:43:    private val ratingIdProvider = { userId: String, lessonId: LessonId -> RatingId("$userId:${lessonId.value}") }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:57:                id = RatingId("r1"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:72:                id = RatingId("r1"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:107:                id = AttemptId("a1"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:124:                id = AttemptId("a1"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:141:                id = AttemptId("a1"),
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:153:    // ── AttemptId rename regression (Group C) ─────────────────────────────────
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:155:    // These tests verify the rename compiled correctly (.raw must no longer exist).
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:158:    fun `given AttemptId when access value field then returns the wrapped string`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:160:        val id = AttemptId("abc-123")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:161:        assertEquals("abc-123", id.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:165:    fun `given RatingId when access value field then returns the wrapped string`() {
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:167:        val id = RatingId("sha256hash")
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/ValueObjectGuardTest.kt:168:        assertEquals("sha256hash", id.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionRepository.kt:16:        questions.value = list
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionRepository.kt:20:        questions.value = questions.value + question
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeQuestionRepository.kt:29:        questions.value.find { it.id == id }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:21:            submitted.value = submitted.value + (rating.userId to rating.lessonId.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:28:        submitted.map { set -> (userId to lessonId.value) in set }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRatingRepository.kt:31:        submitted.value = submitted.value + (userId to lessonId.value)
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:18:        lessons[lesson.id.value] = lesson
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:19:        store.value = lessons.toMap()
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:23:        store.map { map -> map.values.filter { it.themeId == themeId }.sortedBy { it.order } }
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:25:    override suspend fun getById(id: LessonId): Lesson? = lessons[id.value]
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonRepository.kt:30:    override suspend fun getLocalContentsVersion(id: LessonId): Long? = lessons[id.value]?.contentsVersion
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:16:    val savedAttempts: List<Attempt> get() = stored.value
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeLessonAttemptRepository.kt:21:            stored.value = stored.value + attempt
shared/feature/lesson-runner/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/fake/FakeAuthRepository.kt:17:        uidFlow.value = newUid

exec
/bin/bash -lc "nl -ba apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt | sed -n '1,280p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.apps.android_next
     2	
     3	import com.arkivanov.decompose.Child
     4	import com.arkivanov.decompose.DefaultComponentContext
     5	import com.arkivanov.decompose.router.stack.ChildStack
     6	import com.arkivanov.decompose.value.MutableValue
     7	import com.arkivanov.decompose.value.Value
     8	import com.arkivanov.essenty.lifecycle.LifecycleRegistry
     9	import com.arkivanov.essenty.lifecycle.destroy
    10	import com.arkivanov.essenty.lifecycle.resume
    11	import com.arkivanov.essenty.lifecycle.stop
    12	import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
    13	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
    14	import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
    15	import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
    16	import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
    17	import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
    18	import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
    19	import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
    20	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
    21	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
    22	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    23	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
    24	import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
    25	import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
    26	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
    27	import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
    28	import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
    29	import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
    30	import com.tpov.schoolquiz.shared.core.stats.RawUserStats
    31	import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
    32	import com.tpov.schoolquiz.shared.feature.app_shell.data.AuthRepositoryImpl
    33	import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
    34	import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
    35	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
    36	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
    37	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
    38	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
    39	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
    40	import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
    41	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
    42	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
    43	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
    44	import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
    45	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
    46	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
    47	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
    48	import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
    49	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
    50	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
    51	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
    52	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
    53	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
    54	import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
    55	import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
    56	import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
    57	import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
    58	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
    59	import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
    60	import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
    61	import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
    62	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
    63	import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
    64	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
    65	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
    66	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
    67	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
    68	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
    69	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
    70	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
    71	import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
    72	import com.tpov.schoolquiz.shared.feature.quest.domain.di.questDomainModule
    73	import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
    74	import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
    75	import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
    76	import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
    77	import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
    78	import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
    79	import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
    80	import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
    81	import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
    82	import androidx.work.WorkManager
    83	import kotlinx.coroutines.ExperimentalCoroutinesApi
    84	import kotlinx.coroutines.flow.Flow
    85	import kotlinx.coroutines.flow.MutableStateFlow
    86	import kotlinx.coroutines.flow.asStateFlow
    87	import kotlinx.coroutines.flow.emptyFlow
    88	import kotlinx.coroutines.flow.flowOf
    89	import kotlinx.coroutines.flow.toList
    90	import kotlinx.coroutines.launch
    91	import kotlinx.coroutines.test.TestCoroutineScheduler
    92	import kotlinx.coroutines.test.UnconfinedTestDispatcher
    93	import kotlinx.coroutines.test.resetMain
    94	import kotlinx.coroutines.test.runTest
    95	import kotlinx.coroutines.test.setMain
    96	import org.junit.After
    97	import org.junit.Before
    98	import org.junit.Test
    99	import org.koin.core.context.startKoin
   100	import org.koin.core.context.stopKoin
   101	import org.koin.core.parameter.parametersOf
   102	import org.koin.dsl.module
   103	import org.koin.test.KoinTest
   104	import org.mockito.Mockito.mock
   105	import kotlin.test.assertEquals
   106	import kotlin.test.assertNotNull
   107	import kotlin.test.assertNull
   108	import kotlin.test.assertTrue
   109	
   110	/**
   111	 * Smoke tests: Koin module graph resolves without errors.
   112	 *
   113	 * D2 coverage: firebaseModule replaced by testDataSourceModule + appShellDataModule.
   114	 * H5 fix (phase-01): appShellPresentationModule created in phase-04.
   115	 * Phase-07: full-stack wiring (all 3 modules) verified here.
   116	 */
   117	@OptIn(ExperimentalCoroutinesApi::class)
   118	class KoinModuleWiringTest : KoinTest {
   119	
   120	    private val testScheduler = TestCoroutineScheduler()
   121	    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
   122	
   123	    private val testDataSourceModule = module {
   124	        single<UserStatsDataSource> {
   125	            object : UserStatsDataSource {
   126	                override fun observeRaw() = emptyFlow<RawUserStats>()
   127	                override suspend fun fetchRaw() = RawUserStats()
   128	            }
   129	        }
   130	    }
   131	
   132	    private val testDaoModule = module {
   133	        single<UserStatsDao> {
   134	            object : UserStatsDao {
   135	                override fun observeByUid(uid: String) = emptyFlow<UserStatsEntity?>()
   136	                override suspend fun findByUid(uid: String): UserStatsEntity? = null
   137	                override suspend fun upsert(entity: UserStatsEntity) {}
   138	                override suspend fun updateDeveloperLevel(uid: String, value: Int) {}
   139	            }
   140	        }
   141	    }
   142	
   143	    private val testWorkManagerModule = module {
   144	        single<WorkManager> { mock(WorkManager::class.java) }
   145	    }
   146	
   147	    private val testRepositoryStubsModule = module {
   148	        single<QuestRepository> {
   149	            object : QuestRepository {
   150	                override fun observeMyQuests(authorUid: String, catalogId: CatalogId?) =
   151	                    flowOf(emptyList<Quest>())
   152	                override fun observeByCatalog(catalogId: CatalogId, shelf: String) =
   153	                    flowOf(emptyList<Quest>())
   154	                override fun observeByShelf(shelf: String) = flowOf(emptyList<Quest>())
   155	                override suspend fun getById(id: QuestId): Quest? = null
   156	                override suspend fun refreshFromRemote(
   157	                    currentUserUid: String?,
   158	                    availableShelves: Set<String>,
   159	                    catalogIdsToSync: Set<CatalogId>,
   160	                    cursor: Long,
   161	                ): Result<Set<QuestId>> = Result.success(emptySet())
   162	            }
   163	        }
   164	        single<CatalogRepository> {
   165	            object : CatalogRepository {
   166	                override fun observeAll() = flowOf(emptyList<Catalog>())
   167	                override suspend fun refreshFromRemote(): Result<Set<CatalogId>> =
   168	                    Result.success(emptySet())
   169	                override suspend fun getById(id: CatalogId): Catalog? = null
   170	            }
   171	        }
   172	        single<SectionRepository> {
   173	            object : SectionRepository {
   174	                override fun observeByQuest(questId: QuestId) = flowOf(emptyList<Section>())
   175	                override suspend fun getById(id: SectionId): Section? = null
   176	                override suspend fun refreshByParents(
   177	                    questIds: Set<QuestId>,
   178	                    cursor: Long,
   179	                ): Result<Set<SectionId>> = Result.success(emptySet())
   180	                override suspend fun getLocalContentsVersion(id: SectionId): Long? = null
   181	            }
   182	        }
   183	        single<ThemeRepository> {
   184	            object : ThemeRepository {
   185	                override fun observeBySection(sectionId: SectionId) = flowOf(emptyList<Theme>())
   186	                override suspend fun getById(id: ThemeId): Theme? = null
   187	                override suspend fun refreshByParents(
   188	                    sectionIds: Set<SectionId>,
   189	                    cursor: Long,
   190	                ): Result<Set<ThemeId>> = Result.success(emptySet())
   191	                override suspend fun getLocalContentsVersion(id: ThemeId): Long? = null
   192	            }
   193	        }
   194	        single<LessonRepository> {
   195	            object : LessonRepository {
   196	                override fun observeByTheme(themeId: ThemeId) = flowOf(emptyList<Lesson>())
   197	                override suspend fun getById(id: LessonId): Lesson? = null
   198	                override suspend fun refreshByParents(
   199	                    themeIds: Set<ThemeId>,
   200	                    cursor: Long,
   201	                ): Result<Set<LessonId>> = Result.success(emptySet())
   202	                override suspend fun getLocalContentsVersion(id: LessonId): Long? = null
   203	            }
   204	        }
   205	        single<LessonAttemptRepository> {
   206	            object : LessonAttemptRepository {
   207	                override suspend fun save(attempt: Attempt): Result<Unit> = Result.success(Unit)
   208	                override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())
   209	                override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
   210	            }
   211	        }
   212	        single<LessonRunnerComponentFactory> {
   213	            LessonRunnerComponentFactory { _, _, _ -> error("Not wired in KoinModuleWiringTest") }
   214	        }
   215	    }
   216	
   217	    @Before
   218	    fun setUp() {
   219	        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
   220	    }
   221	
   222	    @After
   223	    fun tearDown() {
   224	        stopKoin()
   225	        kotlinx.coroutines.Dispatchers.resetMain()
   226	    }
   227	
   228	    // -----------------------------------------------------------------------
   229	    // Phase-01 D2: appShellDataModule resolves UserStatsRepository
   230	    // -----------------------------------------------------------------------
   231	
   232	    @Test
   233	    fun `D2 appShellDataModule resolves UserStatsRepository given UserStatsDataSource`() {
   234	        startKoin {
   235	            modules(testDataSourceModule, testDaoModule, appShellDataModule())
   236	        }
   237	
   238	        val repo = getKoin().get<UserStatsRepository>()
   239	        assertNotNull(repo)
   240	    }
   241	
   242	    @Test
   243	    fun `D2 UserStatsRepository resolves as UserStatsRepositoryImpl`() {
   244	        startKoin {
   245	            modules(testDataSourceModule, testDaoModule, appShellDataModule())
   246	        }
   247	
   248	        val repo = getKoin().get<UserStatsRepository>()
   249	        assertNotNull(repo)
   250	        assertTrue(repo is UserStatsRepositoryImpl)
   251	    }
   252	
   253	    // -----------------------------------------------------------------------
   254	    // Phase-01 (home-and-my-quests Decision #42 + Codex Round 3 B3): AuthRepository
   255	    // -----------------------------------------------------------------------
   256	
   257	    @Test
   258	    fun `appShellDataModule resolves AuthRepository given currentUidFlow`() {
   259	        startKoin {
   260	            modules(testDataSourceModule, testDaoModule, appShellDataModule())
   261	        }
   262	
   263	        val repo = getKoin().get<AuthRepository>()
   264	        assertNotNull(repo)
   265	        assertTrue(repo is AuthRepositoryImpl)
   266	    }
   267	
   268	    @Test
   269	    fun `appShellDataModule binds AuthRepository as AuthRepositoryImpl with shared currentUidFlow`() = runTest {
   270	        val uidSource = MutableStateFlow<String?>("user-A")
   271	        startKoin {
   272	            modules(testDataSourceModule, testDaoModule, appShellDataModule { uidSource.asStateFlow() })
   273	        }
   274	
   275	        val auth = getKoin().get<AuthRepository>()
   276	        // currentUid() returns the real Firebase UID without LOCAL_UID substitution
   277	        assertEquals("user-A", auth.currentUid())
   278	
   279	        // observeUid() reflects the same source as UserStatsRepositoryImpl.currentUidFlow
   280	        uidSource.value = null

exec
/bin/bash -lc "nl -ba apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt | sed -n '520,680p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   520	        assertNotNull(getKoin().get<RandomSeedProvider>())
   521	        assertNotNull(getKoin().get<RatingIdProvider>())
   522	    }
   523	
   524	    // ── IT-09d ────────────────────────────────────────────────────────────────
   525	
   526	    /**
   527	     * IT-09d: GIVEN lessonRunnerDomainKoinAdapter WHEN get<CompleteAttemptUseCase> / AbortAttemptUseCase / SubmitLessonRatingUseCase
   528	     * THEN each resolves without exception.
   529	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09d
   530	     */
   531	    @Test
   532	    fun `it09d useCases CompleteAttemptUseCase AbortAttemptUseCase SubmitLessonRatingUseCase resolve`() {
   533	        startKoin {
   534	            modules(
   535	                testLessonAttemptDaoStub,
   536	                testLessonRatingLocalDaoStub,
   537	                testRunnerRepositoryStubs,
   538	                lessonRunnerDataModule,
   539	                lessonRunnerDomainKoinAdapter,
   540	                questionSchemaModule,
   541	            )
   542	        }
   543	        assertNotNull(getKoin().get<CompleteAttemptUseCase>())
   544	        assertNotNull(getKoin().get<AbortAttemptUseCase>())
   545	        assertNotNull(getKoin().get<SubmitLessonRatingUseCase>())
   546	    }
   547	
   548	    // ── IT-09e ────────────────────────────────────────────────────────────────
   549	
   550	    /**
   551	     * IT-09e: GIVEN lessonRunnerPresentationModule + deps WHEN get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), EASY))
   552	     * THEN resolves to DefaultLessonRunnerRootComponent without exception.
   553	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09e
   554	     */
   555	    @Test
   556	    fun `it09e lessonRunnerRootComponent resolves with parametersOf ctx lessonId mode`() {
   557	        val lifecycle = LifecycleRegistry()
   558	        lifecycle.resume()
   559	        val ctx = DefaultComponentContext(lifecycle)
   560	
   561	        startKoin {
   562	            modules(
   563	                testLessonAttemptDaoStub,
   564	                testLessonRatingLocalDaoStub,
   565	                testRunnerRepositoryStubs,
   566	                lessonRunnerDataModule,
   567	                lessonRunnerDomainKoinAdapter,
   568	                lessonRunnerPresentationModule,
   569	                questionSchemaModule,
   570	            )
   571	        }
   572	
   573	        try {
   574	            val component = getKoin().get<LessonRunnerRootComponent> {
   575	                parametersOf(ctx, LessonId("l1"), Difficulty.EASY)
   576	            }
   577	            assertNotNull(component)
   578	            assertTrue(component is DefaultLessonRunnerRootComponent)
   579	        } finally {
   580	            lifecycle.stop()
   581	            lifecycle.destroy()
   582	        }
   583	    }
   584	
   585	    // ── IT-09f ────────────────────────────────────────────────────────────────
   586	
   587	    /**
   588	     * IT-09f: GIVEN questionSchemaModule WHEN get<QuestionContentParser>()
   589	     * THEN resolves to KotlinxSerializationQuestionContentParser.
   590	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09f
   591	     */
   592	    @Test
   593	    fun `it09f questionContentParser resolves to KotlinxSerializationQuestionContentParser`() {
   594	        startKoin { modules(questionSchemaModule) }
   595	        val parser = getKoin().get<QuestionContentParser>()
   596	        assertNotNull(parser)
   597	        assertTrue(parser is KotlinxSerializationQuestionContentParser)
   598	    }
   599	
   600	    // ── IT-09g ────────────────────────────────────────────────────────────────
   601	
   602	    /**
   603	     * IT-09g: TypeConverter classes instantiate without exception (JVM structural check).
   604	     * persistenceModule uses androidContext() — AppDatabase cannot be built in JVM tests.
   605	     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
   606	     * TopParticipantListConverter and StringSetConverter registered in persistenceModule.
   607	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09g
   608	     */
   609	    @Test
   610	    fun `it09g appDatabase typeConverters TopParticipantListConverter StringSetConverter are instantiatable`() {
   611	        val topParticipantListConverter = TopParticipantListConverter()
   612	        val stringSetConverter = StringSetConverter()
   613	        assertNotNull(topParticipantListConverter)
   614	        assertNotNull(stringSetConverter)
   615	    }
   616	
   617	    // ── IT-09h ────────────────────────────────────────────────────────────────
   618	
   619	    /**
   620	     * IT-09h: GIVEN lessonRunnerPresentationModule WHEN get<LessonRunnerComponentFactory>()
   621	     * THEN resolves without NoBeanDefinitionFoundException (single binding from presentation module).
   622	     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09h
   623	     */
   624	    @Test
   625	    fun `it09h lessonRunnerComponentFactory resolves as single binding`() {
   626	        startKoin { modules(lessonRunnerPresentationModule) }
   627	        val factory = getKoin().get<LessonRunnerComponentFactory>()
   628	        assertNotNull(factory)
   629	    }
   630	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/build.gradle.kts | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/build.gradle.kts | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.android.compose.library")
     3	}
     4	
     5	android {
     6	    namespace = "com.tpov.schoolquiz.android.feature.lesson_runner.presentation"
     7	}
     8	
     9	dependencies {
    10	    implementation(project(":shared:feature:lesson-runner:domain"))
    11	    implementation(project(":shared:feature:lesson:domain"))
    12	    implementation(project(":shared:core:question-schema"))
    13	    implementation(project(":shared:core:leaderboard"))
    14	    implementation(project(":android:core:designsystem"))
    15	
    16	    implementation(libs.kotlinx.datetime)
    17	
    18	    implementation(platform(libs.compose.bom))
    19	    implementation(libs.bundles.compose.ui)
    20	    implementation(libs.bundles.decompose)
    21	    implementation(libs.bundles.koin.android)
    22	
    23	    testImplementation(libs.junit4)
    24	    testImplementation(libs.kotlin.test.junit)
    25	    testImplementation(libs.kotlinx.coroutines.test)
    26	    testImplementation(project(":shared:feature:theme:domain"))
    27	    testImplementation(project(":shared:feature:question:domain"))
    28	    testImplementation(project(":shared:feature:app-shell:domain"))
    29	
    30	    androidTestImplementation(platform(libs.compose.bom))
    31	    androidTestImplementation(libs.compose.ui.test.junit4)
    32	    androidTestImplementation(libs.androidx.test.ext.junit)
    33	    debugImplementation(libs.compose.ui.test.manifest)
    34	}

 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.android.compose.library")
     3	    alias(libs.plugins.kotlin.serialization)
     4	}
     5	
     6	android {
     7	    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
     8	}
     9	
    10	dependencies {
    11	    implementation(project(":android:core:designsystem"))
    12	    implementation(project(":android:feature:lesson-runner:presentation"))
    13	    implementation(project(":shared:feature:quest:domain"))
    14	    implementation(project(":shared:feature:section:domain"))
    15	    implementation(project(":shared:feature:theme:domain"))
    16	    implementation(project(":shared:feature:lesson:domain"))
    17	    implementation(project(":shared:feature:lesson-runner:domain"))
    18	    implementation(project(":shared:feature:app-shell:domain"))
    19	    implementation(project(":shared:core:catalog:domain"))
    20	    implementation(project(":shared:core:question-schema"))
    21	
    22	    implementation(platform(libs.compose.bom))
    23	    implementation(libs.bundles.compose.ui)
    24	    implementation(libs.bundles.decompose)
    25	    implementation(libs.bundles.koin.android)
    26	    implementation(libs.kotlinx.serialization.json)
    27	
    28	    testImplementation(libs.junit4)
    29	    testImplementation(libs.kotlin.test.junit)
    30	    testImplementation(libs.kotlinx.coroutines.test)
    31	    testImplementation(libs.mockk)
    32	    testImplementation(project(":shared:feature:lesson-runner:domain"))
    33	    testImplementation(project(":shared:feature:app-shell:domain"))
    34	
    35	    androidTestImplementation(platform(libs.compose.bom))
    36	    androidTestImplementation(libs.compose.ui.test)
    37	    androidTestImplementation(libs.compose.ui.test.junit4)
    38	    androidTestImplementation(libs.androidx.test.ext.junit)
    39	    androidTestImplementation(libs.kotlin.test.junit)
    40	    androidTestImplementation(libs.androidx.test.espresso.intents)
    41	    debugImplementation(libs.compose.ui.test.manifest)
    42	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/data/build.gradle.kts | sed -n '1,240p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.kmp.library")
     3	}
     4	
     5	android {
     6	    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.data"
     7	}
     8	
     9	kotlin {
    10	    sourceSets {
    11	        val jvmAndAndroidMain by creating {
    12	            dependsOn(commonMain.get())
    13	        }
    14	        jvmMain.get().dependsOn(jvmAndAndroidMain)
    15	        androidMain.get().dependsOn(jvmAndAndroidMain)
    16	
    17	        commonMain.dependencies {
    18	            implementation(libs.kotlinx.coroutines.core)
    19	            implementation(libs.kotlinx.datetime)
    20	            implementation(project(":shared:feature:lesson-runner:domain"))
    21	            implementation(project(":shared:feature:lesson:domain"))
    22	            implementation(project(":shared:core:question-schema"))
    23	            implementation(project(":shared:core:persistence"))
    24	        }
    25	        androidMain.dependencies {
    26	            implementation(libs.koin.core)
    27	            implementation(project(":shared:feature:question:domain"))
    28	            implementation(project(":shared:feature:app-shell:domain"))
    29	        }
    30	        commonTest.dependencies {
    31	            implementation(libs.kotlinx.coroutines.test)
    32	            implementation(libs.junit4)
    33	        }
    34	    }
    35	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/build.gradle.kts | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.kmp.library")
     3	}
     4	
     5	android {
     6	    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.domain"
     7	}
     8	
     9	kotlin {
    10	    sourceSets {
    11	        commonMain.dependencies {
    12	            implementation(libs.kotlinx.coroutines.core)
    13	            implementation(libs.kotlinx.datetime)
    14	            implementation(libs.koin.core)
    15	            implementation(project(":shared:core:leaderboard"))
    16	            implementation(project(":shared:core:question-schema"))
    17	            implementation(project(":shared:feature:lesson:domain"))
    18	            implementation(project(":shared:feature:question:domain"))
    19	            implementation(project(":shared:feature:app-shell:domain"))
    20	        }
    21	        commonTest.dependencies {
    22	            implementation(libs.kotlinx.coroutines.test)
    23	            implementation(project(":shared:feature:theme:domain"))
    24	        }
    25	    }
    26	}

exec
/bin/bash -lc "nl -ba shared/core/question-schema/build.gradle.kts | sed -n '1,180p'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc "nl -ba apps/android-next/build.gradle.kts | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.kmp.library")
     3	    alias(libs.plugins.kotlin.serialization)
     4	}
     5	
     6	android {
     7	    namespace = "com.tpov.schoolquiz.shared.core.question_schema"
     8	}
     9	
    10	kotlin {
    11	    sourceSets {
    12	        getByName("commonMain") {
    13	            dependencies {
    14	                implementation(libs.kotlinx.serialization.json)
    15	            }
    16	        }
    17	        androidMain.dependencies {
    18	            implementation(libs.koin.core)
    19	        }
    20	    }
    21	}

 succeeded in 0ms:
     1	plugins {
     2	    id("schoolquiz.android.compose.application")
     3	    alias(libs.plugins.google.services)
     4	}
     5	
     6	android {
     7	    namespace = "com.tpov.schoolquiz.apps.android_next"
     8	
     9	    defaultConfig {
    10	        applicationId = "com.tpov.schoolquiz.next"
    11	        versionCode = 1
    12	        versionName = "0.1.0"
    13	    }
    14	
    15	    buildFeatures {
    16	        buildConfig = true
    17	    }
    18	}
    19	
    20	dependencies {
    21	    // Feature modules
    22	    implementation(project(":shared:feature:app-shell:domain"))
    23	    implementation(project(":shared:feature:app-shell:data"))
    24	    implementation(project(":android:feature:app-shell:presentation"))
    25	    implementation(project(":android:feature:quest:presentation"))
    26	    implementation(project(":android:feature:quizzes-screen:presentation"))
    27	    implementation(project(":android:core:navigation"))
    28	    implementation(project(":android:core:designsystem"))
    29	    implementation(project(":platform:firebase"))
    30	    implementation(platform(libs.firebase.bom))
    31	    implementation(libs.firebase.auth.ktx)
    32	    implementation(project(":shared:core:persistence"))
    33	    implementation(project(":shared:core:catalog:data"))
    34	    implementation(project(":shared:core:catalog:domain"))
    35	    implementation(project(":shared:feature:quest:data"))
    36	    implementation(project(":shared:feature:quest:domain"))
    37	    implementation(project(":shared:feature:section:data"))
    38	    implementation(project(":shared:feature:section:domain"))
    39	    implementation(project(":shared:feature:theme:data"))
    40	    implementation(project(":shared:feature:theme:domain"))
    41	    implementation(project(":shared:feature:lesson:data"))
    42	    implementation(project(":shared:feature:lesson:domain"))
    43	    implementation(project(":shared:feature:question:data"))
    44	    implementation(project(":shared:feature:question:domain"))
    45	    implementation(project(":shared:core:question-schema"))
    46	    implementation(project(":shared:feature:lesson-runner:data"))
    47	    implementation(project(":android:feature:lesson-runner:presentation"))
    48	    implementation(project(":platform:android-services"))
    49	    implementation(libs.androidx.work.runtime.ktx)
    50	    implementation(project(":shared:core:sync"))
    51	
    52	    // Compose
    53	    implementation(platform(libs.compose.bom))
    54	    implementation(libs.bundles.compose.ui)
    55	    implementation(libs.bundles.compose.ui.tooling)
    56	    implementation(libs.androidx.activity.compose)
    57	
    58	    // Decompose
    59	    implementation(libs.bundles.decompose)
    60	
    61	    // Koin
    62	    implementation(libs.bundles.koin.android)
    63	
    64	    // Lifecycle
    65	    implementation(libs.bundles.androidx.lifecycle)
    66	    implementation(libs.bundles.androidx.lifecycle.compose)
    67	
    68	    // Base
    69	    implementation(libs.bundles.androidx.ui.base)
    70	
    71	    // Tests
    72	    testImplementation(libs.junit4)
    73	    testImplementation(libs.koin.core)
    74	    testImplementation(libs.koin.test)
    75	    testImplementation(libs.koin.test.junit4)
    76	    testImplementation(libs.kotlin.test.junit)
    77	    testImplementation(libs.kotlinx.coroutines.test)
    78	    testImplementation(libs.mockito.core)
    79	    testImplementation(project(":shared:core:stats"))
    80	    testImplementation(project(":shared:core:question-schema"))
    81	    testImplementation(project(":shared:feature:lesson-runner:domain"))
    82	    testImplementation(project(":android:feature:lesson-runner:presentation"))
    83	    androidTestImplementation(libs.bundles.testing.instrumented)
    84	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt | sed -n '1,200p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
     4	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
     7	import org.koin.dsl.module
     8	
     9	/**
    10	 * Koin domain module for lesson-runner.
    11	 *
    12	 * Clock: data module registers single<Clock> { Clock.System }.
    13	 * Providers: data module registers single<AttemptIdProvider>, single<RandomSeedProvider>, single<RatingIdProvider>.
    14	 * LessonRunnerDomainKoinAdapter (data/androidMain) bridges providers → use case function types.
    15	 * This module is NOT registered in production — use lessonRunnerDomainKoinAdapter instead (ADR-LR-09 C1).
    16	 */
    17	val lessonRunnerDomainModule = module {
    18	    factory {
    19	        StartLessonAttemptUseCase(
    20	            questionRepository = get(),
    21	            lessonRepository = get(),
    22	            parser = get(),
    23	            authRepository = get(),
    24	            clock = get(),
    25	            randomSeedProvider = get(),
    26	        )
    27	    }
    28	    factory {
    29	        CompleteAttemptUseCase(
    30	            attemptRepository = get(),
    31	            ratingRepository = get(),
    32	            clock = get(),
    33	            attemptIdProvider = get(),
    34	        )
    35	    }
    36	    factory {
    37	        AbortAttemptUseCase(
    38	            attemptRepository = get(),
    39	            clock = get(),
    40	            attemptIdProvider = get(),
    41	        )
    42	    }
    43	    factory {
    44	        SubmitLessonRatingUseCase(
    45	            ratingRepository = get(),
    46	            lessonRepository = get(),
    47	            clock = get(),
    48	            ratingIdProvider = get(),
    49	        )
    50	    }
    51	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt | sed -n '1,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson.domain.di
     2	
     3	import com.tpov.schoolquiz.shared.feature.lesson.domain.use_case.SyncLessonsUseCase
     4	import org.koin.dsl.module
     5	
     6	val lessonDomainModule = module {
     7	    factory { SyncLessonsUseCase(get()) }
     8	}

exec
/bin/bash -lc "rg -n \"org\\.koin|val .*DomainModule|module \\{\" shared/feature/*/domain/src/commonMain shared/core/catalog/domain/src/commonMain -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/di/CatalogDomainModule.kt:4:import org.koin.dsl.module
shared/core/catalog/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/catalog/domain/di/CatalogDomainModule.kt:6:val catalogDomainModule = module {
shared/feature/theme/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/theme/domain/di/ThemeDomainModule.kt:4:import org.koin.dsl.module
shared/feature/theme/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/theme/domain/di/ThemeDomainModule.kt:6:val themeDomainModule = module {
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt:4:import org.koin.dsl.module
shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/di/LessonDomainModule.kt:6:val lessonDomainModule = module {
shared/feature/question/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/question/domain/di/QuestionDomainModule.kt:4:import org.koin.dsl.module
shared/feature/question/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/question/domain/di/QuestionDomainModule.kt:6:val questionDomainModule = module {
shared/feature/section/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/section/domain/di/SectionDomainModule.kt:4:import org.koin.dsl.module
shared/feature/section/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/section/domain/di/SectionDomainModule.kt:6:val sectionDomainModule = module {
shared/feature/quest/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/domain/di/QuestDomainModule.kt:5:import org.koin.dsl.module
shared/feature/quest/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest/domain/di/QuestDomainModule.kt:7:val questDomainModule = module {
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:7:import org.koin.dsl.module
shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/di/LessonRunnerDomainModule.kt:17:val lessonRunnerDomainModule = module {

exec
/bin/bash -lc "python3 - <<'PY'
from pathlib import Path
p=Path('docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch')
for i,line in enumerate(p.open(errors='replace'),1):
    if line.startswith('diff --git') and 'shared/feature/lesson-runner/domain' in line:
        print(i, line.strip())
PY" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
exec
/bin/bash -lc "sed -n '1,120p' docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch && tail -n 80 docs/features/lesson-runner/_codex-review/cross-phase/lesson-runner-diff.patch" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
diff --git a/android/feature/quizzes-screen/presentation/build.gradle.kts b/android/feature/quizzes-screen/presentation/build.gradle.kts
new file mode 100644
index 00000000..45566d51
--- /dev/null
+++ b/android/feature/quizzes-screen/presentation/build.gradle.kts
@@ -0,0 +1,36 @@
+plugins {
+    id("schoolquiz.android.compose.library")
+    alias(libs.plugins.kotlin.serialization)
+}
+
+android {
+    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
+}
+
+dependencies {
+    implementation(project(":android:core:designsystem"))
+    implementation(project(":shared:feature:quest:domain"))
+    implementation(project(":shared:feature:section:domain"))
+    implementation(project(":shared:feature:theme:domain"))
+    implementation(project(":shared:feature:lesson:domain"))
+    implementation(project(":shared:core:catalog:domain"))
+
+    implementation(platform(libs.compose.bom))
+    implementation(libs.bundles.compose.ui)
+    implementation(libs.bundles.decompose)
+    implementation(libs.bundles.koin.android)
+    implementation(libs.kotlinx.serialization.json)
+
+    testImplementation(libs.junit4)
+    testImplementation(libs.kotlin.test.junit)
+    testImplementation(libs.kotlinx.coroutines.test)
+    testImplementation(libs.mockk)
+
+    androidTestImplementation(platform(libs.compose.bom))
+    androidTestImplementation(libs.compose.ui.test)
+    androidTestImplementation(libs.compose.ui.test.junit4)
+    androidTestImplementation(libs.androidx.test.ext.junit)
+    androidTestImplementation(libs.kotlin.test.junit)
+    androidTestImplementation(libs.androidx.test.espresso.intents)
+    debugImplementation(libs.compose.ui.test.manifest)
+}
diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
new file mode 100644
index 00000000..d7f2aee1
--- /dev/null
+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
@@ -0,0 +1,26 @@
+package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
+
+import com.arkivanov.decompose.value.MutableValue
+import com.arkivanov.decompose.value.Value
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
+
+class FakeLessonListComponent(
+    initialState: HierarchyListUiState,
+    override val titles: List<String> = emptyList(),
+) : LessonListComponent {
+
+    private val _uiState = MutableValue(initialState)
+    override val uiState: Value<HierarchyListUiState> get() = _uiState
+
+    var onLessonClickCalled: HierarchyItemUi? = null
+
+    fun setState(state: HierarchyListUiState) {
+        _uiState.value = state
+    }
+
+    override fun onLessonClick(lesson: HierarchyItemUi) {
+        onLessonClickCalled = lesson
+    }
+}
diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
new file mode 100644
index 00000000..9f38cdcc
--- /dev/null
+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
@@ -0,0 +1,8 @@
+package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
+
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonPlaceholderComponent
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonPlaceholderUiState
+
+class FakeLessonPlaceholderComponent(
+    override val uiState: LessonPlaceholderUiState,
+) : LessonPlaceholderComponent
diff --git a/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
new file mode 100644
index 00000000..7345ce60
--- /dev/null
+++ b/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuestListComponent.kt
@@ -0,0 +1,31 @@
+package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake
+
+import com.arkivanov.decompose.value.MutableValue
+import com.arkivanov.decompose.value.Value
+import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
+import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
+
+class FakeQuestListComponent(
+    initialState: QuestListUiState,
+    override val titles: List<String> = emptyList(),
+) : QuestListComponent {
+
+    private val _uiState = MutableValue(initialState)
+    override val uiState: Value<QuestListUiState> get() = _uiState
+
+    var onQuestClickCalled: QuestDisplayItem? = null
+    var onShareClickCalled: QuestDisplayItem? = null
+
+    fun setState(state: QuestListUiState) {
+        _uiState.value = state
+    }
+
+    override fun onQuestClick(quest: QuestDisplayItem) {
+        onQuestClickCalled = quest
+    }
+ *  - Items with lastModifiedAt <= cursor → skip.
+ *  - Items with `archived=true` and higher version → local delete.
+ *  - Items with incoming.version <= local.version → skip (version guard).
+ *  - Otherwise → upsert.
+ *
+ * [lastCursor] tracks the cursor advanced after each [refreshByParents] call.
+ *
+ * Spec: docs/features/home-and-my-quests/0-spec.md — AC#6.
+ *   Domain Test Scenario 43.
+ */
+class FakeLessonRepository(
+    initial: List<Lesson> = emptyList(),
+) : LessonRepository {
+
+    private val cache = MutableStateFlow<Map<LessonId, Lesson>>(
+        initial.associateBy { it.id },
+    )
+
+    private var pendingRemote: List<Lesson>? = null
+    private var nextRefreshFailure: Throwable? = null
+
+    /** The cursor value advanced by the last [refreshByParents] call. */
+    var lastCursor: Long = 0L
+        private set
+
+    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> =
+        cache.map { map ->
+            map.values.filter { it.themeId == themeId }.sortedBy { it.order }
+        }
+
+    override suspend fun getById(id: LessonId): Lesson? = cache.value[id]
+
+    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> {
+        lastCursor = cursor
+        val failure = nextRefreshFailure
+        if (failure != null) {
+            nextRefreshFailure = null
+            return Result.failure(failure)
+        }
+        val remote = pendingRemote
+        val processedIds = mutableSetOf<LessonId>()
+        if (remote != null) {
+            pendingRemote = null
+            cache.update { current ->
+                val mutable = current.toMutableMap()
+                var newMaxLastMod = cursor
+                for (incoming in remote) {
+                    if (incoming.themeId !in themeIds) continue
+                    // Cursor guard
+                    if (incoming.lastModifiedAt <= cursor) continue
+                    newMaxLastMod = maxOf(newMaxLastMod, incoming.lastModifiedAt)
+                    processedIds.add(incoming.id)
+                    val existing = mutable[incoming.id]
+                    when {
+                        incoming.archived && (existing == null || incoming.version > existing.version) -> {
+                            mutable.remove(incoming.id)
+                        }
+                        !incoming.archived -> {
+                            if (existing == null || incoming.version > existing.version) {
+                                mutable[incoming.id] = incoming
+                            }
+                        }
+                    }
+                }
+                lastCursor = newMaxLastMod
+                mutable
+            }
+        }
+        return Result.success(processedIds)
+    }
+
+    override suspend fun getLocalContentsVersion(id: LessonId): Long? = cache.value[id]?.contentsVersion
+
+    // ── Test helpers ──────────────────────────────────────────────────────────
+
+    fun seed(lessons: List<Lesson>) { cache.value = lessons.associateBy { it.id } }
+    fun simulateRemoteLessons(lessons: List<Lesson>) { pendingRemote = lessons }
+    fun setNextRefreshFailure(error: Throwable) { nextRefreshFailure = error }
+    fun snapshot(): List<Lesson> = cache.value.values.sortedBy { it.id.value }
+}

exec
/bin/bash -lc 'git status --short && git rev-parse --show-toplevel' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
 M .claude/PROJECT-CONTEXT.md
 M .claude/agents/architect-component.md
 M .claude/agents/architect-reviewer.md
 M .claude/agents/code-analyst.md
 M .claude/agents/codebase-researcher.md
 M .claude/agents/domain-designer.md
 M .claude/agents/frontend-dev.md
 M .claude/agents/integration-tester.md
 M .claude/agents/planner.md
 M .claude/agents/test-dev.md
 M .claude/commands/feature-debug.md
 M .claude/commands/feature-design.md
 M .claude/commands/feature-implement.md
 M .claude/commands/feature-plan.md
 M .claude/commands/feature-research.md
 M .claude/commands/feature-spec.md
 M .claude/rules/agent-communication.md
 M .claude/rules/clean-architecture.md
 M .claude/rules/di-patterns.md
 M .claude/rules/domain-models.md
 M .claude/rules/kotlin-conventions.md
 M .claude/rules/lifecycle.md
 M .claude/rules/navigation.md
 M .claude/rules/room-database.md
 M .claude/rules/testing.md
 M .claude/rules/use-cases.md
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/BreadcrumbBar.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogGrid.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/CatalogSpinner.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/HierarchyItemCard.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/StarRating.kt
 M android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/CatalogDisplayItem.kt
 M android/feature/app-shell/presentation/build.gradle.kts
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/di/AppShellPresentationModule.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerContent.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerFooter.kt
 M android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/drawer/DrawerSectionList.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/DefaultRootComponentTest.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/DevModeActivationIntegrationTest.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/QuizzesRootIntegrationTest.kt
 M android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/fake/StubQuestComponents.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponent.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultMyQuestsComponent.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/HomeQuestsComponent.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/di/QuestPresentationModule.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/mapper/QuestToDisplayItem.kt
 M android/feature/quest/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/ui/MyQuestsScreen.kt
 M android/feature/quest/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quest/presentation/DefaultHomeQuestsComponentTest.kt
 M android/feature/quizzes-screen/presentation/build.gradle.kts
 M android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonListComponent.kt
 D android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt
 M android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeQuizzesComponent.kt
 D android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuestListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultSectionListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultThemeListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonListComponent.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuestListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/SectionListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ThemeListComponent.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/LessonDrillMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/QuestToDisplayItemMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/SectionDrillMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/mapper/ThemeDrillMapper.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/navigation/QuizzesNavigator.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuestListScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/SectionListScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ThemeListScreen.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/HierarchyListUiState.kt
 D android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt
 M android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/QuestListUiState.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/DefaultQuizzesComponentTest.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt
 M android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt
 D android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt
 M apps/android-next/build.gradle.kts
 M apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt
 M apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt
 M apps/android-next/src/test/java/com/tpov/schoolquiz/apps/android_next/KoinModuleWiringTest.kt
 M build.gradle.kts
 M config/detekt/detekt.yml
 M docs/architecture/0003-question-schema.md
 M docs/features/quizzes-screen/03-decisions.md
 M docs/invariants.md
 M gradle.properties
 M platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncWorker.kt
 M platform/android-services/src/main/kotlin/com/tpov/schoolquiz/platform/android_services/sync/SyncWorkerFactory.kt
 M platform/firebase/build.gradle.kts
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseUserStatsDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/catalog/FirebaseCatalogRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseCatalogModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseLessonModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseQuestModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseQuestionModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseSectionModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/di/FirebaseThemeModule.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirebaseLessonRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/lesson/FirestoreLessonDtoMapper.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest/FirebaseQuestRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/question/FirebaseQuestionRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/question/FirestoreQuestionDtoMapper.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/section/FirebaseSectionRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/section/FirestoreSectionDtoMapper.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/theme/FirebaseThemeRemoteDataSource.kt
 M platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/theme/FirestoreThemeDtoMapper.kt
 M settings.gradle.kts
 M shared/core/persistence/build.gradle.kts
 M shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/di/PersistenceModule.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseMigrationTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseSchemaValidationTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/CatalogDaoTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/QuestDaoBoundaryTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/SectionDaoBoundaryTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsDaoTest.kt
 D shared/core/persistence/src/androidTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/dao/QuestDaoByCatalogTest.kt
 M shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt
 M shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonEntity.kt
 M shared/core/question-schema/build.gradle.kts
 M shared/feature/lesson/data/build.gradle.kts
 M shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/dto/LessonDto.kt
 M shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonDtoMapper.kt
 M shared/feature/lesson/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/mapper/LessonMapper.kt
 M shared/feature/lesson/domain/build.gradle.kts
 M shared/feature/lesson/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/model/Lesson.kt
?? AGENTS.md
?? android/feature/lesson-runner/
?? android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCardTest.kt
?? android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt
?? android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt
?? android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonListUiState.kt
?? android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentPhase06Test.kt
?? android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeAuthRepository.kt
?? android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonAttemptRepository.kt
?? docs/features/lesson-runner/
?? shared/core/leaderboard/
?? shared/core/persistence/schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase/4.json
?? shared/core/persistence/src/androidInstrumentedTest/
?? shared/core/persistence/src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations/
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptDao.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonAttemptEntity.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingLocalDao.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/LessonRatingSubmittedLocalEntity.kt
?? shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/TopParticipantListConverter.kt
?? shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/TypeConvertersPhase02Test.kt
?? shared/core/question-schema/src/androidMain/kotlin/com/
?? shared/core/question-schema/src/commonMain/kotlin/com/
?? shared/core/question-schema/src/commonTest/kotlin/com/
?? shared/feature/lesson-runner/
?? shared/feature/lesson/data/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/data/LessonMapperPhase02Test.kt
?? shared/feature/lesson/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson/domain/LessonNewFieldsTest.kt
/home/Programming/Android/schoolquiz4.0

exec
/bin/bash -lc 'rg -n "lesson-runner|leaderboard|question-schema" settings.gradle.kts' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
31:include(":shared:core:leaderboard")
32:include(":shared:core:question-schema")
53:include(":shared:feature:lesson-runner:domain")
54:include(":shared:feature:lesson-runner:data")
63:include(":shared:feature:internet:leaderboard:domain")
64:include(":shared:feature:internet:leaderboard:data")
80:include(":android:feature:lesson-runner:presentation")
85:include(":android:feature:internet:leaderboard:presentation")
100:include(":server:workers:leaderboard")

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesStateKeeperRestoreTest.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation
     2	
     3	import com.arkivanov.decompose.DefaultComponentContext
     4	import com.arkivanov.essenty.backhandler.BackDispatcher
     5	import com.arkivanov.essenty.lifecycle.LifecycleRegistry
     6	import com.arkivanov.essenty.lifecycle.destroy
     7	import com.arkivanov.essenty.lifecycle.resume
     8	import com.arkivanov.essenty.lifecycle.stop
     9	import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
    10	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
    11	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
    12	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
    13	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
    14	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
    15	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonAttemptRepository
    16	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonRepository
    17	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestRepository
    18	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionRepository
    19	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeThemeRepository
    20	import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
    21	import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
    22	import kotlinx.coroutines.Dispatchers
    23	import org.junit.After
    24	import org.junit.Test
    25	import kotlin.test.assertEquals
    26	import kotlin.test.assertIs
    27	import kotlin.test.assertTrue
    28	
    29	/**
    30	 * JVM unit tests for [DefaultQuizzesComponent] StateKeeper save/restore contract.
    31	 *
    32	 * Simulates process death restoration using [StateKeeperDispatcher]:
    33	 *   1. Build component and navigate to a multi-level stack.
    34	 *   2. Save state: stateHolder.save()
    35	 *   3. Restore: new DefaultComponentContext(stateKeeper = StateKeeperDispatcher(savedState))
    36	 *   4. Assert stack is identical to original.
    37	 *
    38	 * NOTE: This is NOT a real process death test.
    39	 * Real process death (adb shell am kill + cold start) requires UIAutomator (excluded from MVP).
    40	 * StateKeeperDispatcher serializes/deserializes via ListSerializer(QuizzesConfig.serializer())
    41	 * which is the same mechanism used by Decompose on real process death.
    42	 *
    43	 * Spec: docs/features/quizzes-screen/0-spec.md — AC#21
    44	 * Design: docs/features/quizzes-screen/04-testing.md §12
    45	 * Phase: 03
    46	 *
    47	 * PD-01..05 — stack restore, titles preserved, active config, backCallback, Idle anchor.
    48	 *
    49	 * No Dispatchers.setMain: DefaultQuizzesComponent has SupervisorJob but no CoroutineScope;
    50	 * all Decompose navigation and Value.subscribe calls are synchronous on the calling thread.
    51	 */
    52	class QuizzesStateKeeperRestoreTest {
    53	
    54	    private val lifecycles = mutableListOf<LifecycleRegistry>()
    55	
    56	    @After
    57	    fun tearDown() {
    58	        lifecycles.forEach { lc ->
    59	            lc.stop()
    60	            lc.destroy()
    61	        }
    62	        lifecycles.clear()
    63	    }
    64	
    65	    // ── helpers ──────────────────────────────────────────────────────────────────
    66	
    67	    private fun buildComponent(
    68	        stateKeeper: StateKeeperDispatcher,
    69	        backDispatcher: BackDispatcher = BackDispatcher(),
    70	    ): DefaultQuizzesComponent {
    71	        val lifecycle = LifecycleRegistry().also { lifecycles += it }
    72	        lifecycle.resume()
    73	        val ctx = DefaultComponentContext(
    74	            lifecycle = lifecycle,
    75	            stateKeeper = stateKeeper,
    76	            backHandler = backDispatcher,
    77	        )
    78	        return DefaultQuizzesComponent(
    79	            componentContext = ctx,
    80	            questRepository = FakeQuestRepository(),
    81	            sectionRepository = FakeSectionRepository(),
    82	            themeRepository = FakeThemeRepository(),
    83	            lessonRepository = FakeLessonRepository(),
    84	            lessonAttemptRepository = FakeLessonAttemptRepository(),
    85	            authRepository = FakeAuthRepository(),
    86	            lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _ -> error("Not expected") },
    87	            mainContext = Dispatchers.Unconfined,
    88	        )
    89	    }
    90	
    91	    /** Total items in Decompose ChildStack (backStack + active). */
    92	    private fun DefaultQuizzesComponent.stackSize(): Int =
    93	        childStack.value.backStack.size + 1
    94	
    95	    // ── PD-01 — Stack size preserved ─────────────────────────────────────────
    96	
    97	    /**
    98	     * Spec: PD-01 — stack [Idle, QuestList, SectionList] saved and restored.
    99	     * After restoration, childStack has 3 items.
   100	     */
   101	    @Test
   102	    fun `stack Idle QuestList SectionList saved and restored via StateKeeper`() {
   103	        val stateHolder = StateKeeperDispatcher(null)
   104	        val original = buildComponent(stateHolder)
   105	
   106	        original.openQuestList(CatalogId("cat-1"), "Math")
   107	        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
   108	        assertEquals(3, original.stackSize(), "stack must be 3 before save")
   109	
   110	        val savedState = stateHolder.save()
   111	
   112	        val stateHolder2 = StateKeeperDispatcher(savedState)
   113	        val restored = buildComponent(stateHolder2)
   114	
   115	        assertEquals(3, restored.stackSize(), "restored stack must have 3 items")
   116	    }
   117	
   118	    // ── PD-02 — Titles preserved ──────────────────────────────────────────────
   119	
   120	    /**
   121	     * Spec: PD-02 — titles from SectionList config are preserved after restoration.
   122	     * Breadcrumb data must survive process death exactly as originally set.
   123	     */
   124	    @Test
   125	    fun `titles preserved after restoration`() {
   126	        val originalTitles = listOf("Math", "Quest 1")
   127	        val stateHolder = StateKeeperDispatcher(null)
   128	        val original = buildComponent(stateHolder)
   129	
   130	        original.openQuestList(CatalogId("cat-1"), "Math")
   131	        original.openSectionList(QuestId("q-1"), originalTitles)
   132	
   133	        val savedState = stateHolder.save()
   134	        val restored = buildComponent(StateKeeperDispatcher(savedState))
   135	
   136	        val activeConfig = restored.childStack.value.active.configuration
   137	        assertIs<QuizzesConfig.SectionList>(activeConfig)
   138	        assertEquals(originalTitles, activeConfig.titles, "titles must be preserved after restore")
   139	    }
   140	
   141	    // ── PD-03 — Active config is SectionList ─────────────────────────────────
   142	
   143	    /**
   144	     * Spec: PD-03 — restored active config is SectionList, not QuestList or Idle.
   145	     * Verifies that Decompose restores the correct top-of-stack config.
   146	     */
   147	    @Test
   148	    fun `restored active config is SectionList`() {
   149	        val stateHolder = StateKeeperDispatcher(null)
   150	        val original = buildComponent(stateHolder)
   151	
   152	        original.openQuestList(CatalogId("cat-1"), "Math")
   153	        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
   154	
   155	        val savedState = stateHolder.save()
   156	        val restored = buildComponent(StateKeeperDispatcher(savedState))
   157	
   158	        assertIs<QuizzesConfig.SectionList>(
   159	            restored.childStack.value.active.configuration,
   160	            "active config must be SectionList after restore",
   161	        )
   162	    }
   163	
   164	    // ── PD-04 — BackCallback enabled after restoration ────────────────────────
   165	
   166	    /**
   167	     * Spec: PD-04 — backCallback is enabled after restoration because backStack is non-empty.
   168	     * backDispatcher.isEnabled reflects the registered backCallback's enabled state.
   169	     */
   170	    @Test
   171	    fun `backCallback enabled after restoration`() {
   172	        val stateHolder = StateKeeperDispatcher(null)
   173	        val original = buildComponent(stateHolder)
   174	        original.openQuestList(CatalogId("cat-1"), "Math")
   175	        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
   176	
   177	        val savedState = stateHolder.save()
   178	        val backDispatcher = BackDispatcher()
   179	        val restored = buildComponent(StateKeeperDispatcher(savedState), backDispatcher)
   180	
   181	        assertTrue(
   182	            backDispatcher.isEnabled,
   183	            "backCallback must be enabled after restore (backStack is non-empty)",
   184	        )
   185	    }
   186	
   187	    // ── PD-05 — Idle anchor at stack[0] ──────────────────────────────────────
   188	
   189	    /**
   190	     * Spec: PD-05 — Idle anchor is always at stack[0] after restoration.
   191	     * backStack[0] must be QuizzesConfig.Idle regardless of how deep the stack goes.
   192	     */
   193	    @Test
   194	    fun `Idle anchor always at stack index 0 after restoration`() {
   195	        val stateHolder = StateKeeperDispatcher(null)
   196	        val original = buildComponent(stateHolder)
   197	        original.openQuestList(CatalogId("cat-1"), "Math")
   198	        original.openSectionList(QuestId("q-1"), listOf("Math", "Quest 1"))
   199	
   200	        val savedState = stateHolder.save()
   201	        val restored = buildComponent(StateKeeperDispatcher(savedState))
   202	
   203	        val backStack = restored.childStack.value.backStack
   204	        assertTrue(backStack.isNotEmpty(), "backStack must have items (Idle + QuestList)")
   205	        assertIs<QuizzesConfig.Idle>(
   206	            backStack.first().configuration,
   207	            "first backStack entry must be QuizzesConfig.Idle anchor",
   208	        )
   209	    }
   210	}

exec
/bin/bash -lc "nl -ba android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt | sed -n '1,120p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen
     2	
     3	import androidx.compose.foundation.background
     4	import androidx.compose.foundation.clickable
     5	import androidx.compose.foundation.interaction.MutableInteractionSource
     6	import androidx.compose.foundation.layout.Box
     7	import androidx.compose.foundation.layout.fillMaxSize
     8	import androidx.compose.material3.MaterialTheme
     9	import androidx.compose.runtime.Composable
    10	import androidx.compose.runtime.getValue
    11	import androidx.compose.runtime.remember
    12	import androidx.compose.ui.Modifier
    13	import androidx.compose.ui.tooling.preview.Preview
    14	import com.arkivanov.decompose.extensions.compose.subscribeAsState
    15	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    16	import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
    17	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
    18	import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
    19	
    20	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    21	@Composable
    22	fun QuizzesScreen(component: QuizzesComponent) {
    23	    val stack by component.childStack.subscribeAsState()
    24	
    25	    Box(
    26	        modifier =
    27	            Modifier
    28	                .fillMaxSize()
    29	                .background(MaterialTheme.colorScheme.background)
    30	                .clickable(
    31	                    enabled = false,
    32	                    indication = null,
    33	                    interactionSource = remember { MutableInteractionSource() },
    34	                ) {},
    35	    ) {
    36	        when (val active = stack.active.instance) {
    37	            is QuizzesChild.Idle -> Unit
    38	            is QuizzesChild.QuestList -> QuestListScreen(active.component, onSegmentClick = component::popToLevel)
    39	            is QuizzesChild.SectionList -> SectionListScreen(active.component, onSegmentClick = component::popToLevel)
    40	            is QuizzesChild.ThemeList -> ThemeListScreen(active.component, onSegmentClick = component::popToLevel)
    41	            is QuizzesChild.LessonList -> LessonListScreen(active.component, onSegmentClick = component::popToLevel)
    42	            is QuizzesChild.LessonRunner ->
    43	                LessonRunnerScreen(
    44	                    component = active.component,
    45	                    onNavigateBack = { component.popCurrentChild() },
    46	                    onSegmentClick = component::popToLevel,
    47	                )
    48	        }
    49	    }
    50	}
    51	
    52	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    53	@Preview(showBackground = true)
    54	@Composable
    55	private fun QuizzesScreenPreview() {
    56	    SchoolQuizTheme {
    57	        // Preview requires a real component — shown via child screens instead
    58	    }
    59	}

exec
/bin/bash -lc "nl -ba apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/MainActivity.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.apps.android_next
     2	
     3	import android.content.Intent
     4	import android.os.Bundle
     5	import androidx.activity.compose.setContent
     6	import androidx.appcompat.app.AppCompatActivity
     7	import com.arkivanov.decompose.defaultComponentContext
     8	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
     9	import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
    10	import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
    11	import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
    12	import org.koin.android.ext.android.get
    13	import org.koin.core.parameter.parametersOf
    14	
    15	class MainActivity : AppCompatActivity() {
    16	    private lateinit var rootComponent: DefaultRootComponent
    17	
    18	    override fun onCreate(savedInstanceState: Bundle?) {
    19	        super.onCreate(savedInstanceState)
    20	
    21	        rootComponent = get { parametersOf(defaultComponentContext()) }
    22	
    23	        setContent {
    24	            SchoolQuizTheme {
    25	                AppShellScreen(
    26	                    rootComponent = rootComponent,
    27	                    appVersionName = BuildConfig.VERSION_NAME,
    28	                    isDebugBuild = BuildConfig.DEBUG,
    29	                )
    30	            }
    31	        }
    32	    }
    33	
    34	    override fun onNewIntent(intent: Intent) {
    35	        super.onNewIntent(intent)
    36	        val uri = intent.dataString ?: return
    37	        if (!uri.startsWith("schoolquiz://")) return
    38	        rootComponent.onDeepLink(DeepLink(uri))
    39	    }
    40	}

exec
/bin/bash -lc "rg -n \"rotation|recreate|onStop|doOnStop|isPaused|preserve|timeout|handlePause|lifecycle\\.stop|onContinue\" android/feature/lesson-runner/presentation/src/test android/feature/lesson-runner/presentation/src/androidTest -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:35:    var timeoutCount: Int = 0
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:47:        timeoutCount++
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/fake/RunFakeComponent.kt:50:    override fun onContinue() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:39:        isPaused: Boolean = false,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:55:        isPaused = isPaused,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:177:            MutableStateFlow(singleChoiceQuestion(deadlineMs = expiredDeadline, isPaused = false)),
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:187:        assertTrue(fakeComponent.timeoutCount > 0)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:249:    // --- CT-14: GIVEN isPaused=true WHEN rendered and time advances THEN timer display unchanged ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:256:                    isPaused = true,
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:279:    // --- CT-15: GIVEN isPaused=true WHEN rendered THEN blocking resume dialog displayed ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:284:            MutableStateFlow(singleChoiceQuestion(isPaused = true)),
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:299:    fun ct16_continue_button_calls_onContinue() {
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:301:            MutableStateFlow(singleChoiceQuestion(isPaused = true)),
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:320:            MutableStateFlow(singleChoiceQuestion(isPaused = true, showExitConfirmDialog = false)),
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:489:    // --- CT-29: GIVEN hard mode rendered WHEN activity recreated THEN FLAG_SECURE still set ---
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:506:        // Simulate configuration change (e.g. rotation)
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:507:        composeTestRule.activityRule.scenario.recreate()
android/feature/lesson-runner/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerScreenTest.kt:509:        // Re-set content on the recreated activity
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:7:import com.arkivanov.essenty.lifecycle.stop
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:93:            lifecycle.stop()
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:172:        isPaused: Boolean = false,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:185:        isPaused = isPaused,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:514:    // ── PT-22: lifecycle.doOnStop → isPaused=true ─────────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:516:    // PT-22: GIVEN state = Ready(isPaused=false, 2 questions)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:517:    //        WHEN lifecycle.stop() THEN uiState.isPaused == true
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:519:    fun `lifecycle_doOnStop_state_isPaused_true`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:522:        assertFalse((component.uiState.value as RunnerUiState.Question).isPaused)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:524:        lifecycle.stop()
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:527:        assertTrue(uiState.isPaused, "lifecycle.doOnStop must set isPaused=true")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:530:    // ── PT-23: isPaused=true in state → Question.isPaused=true ───────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:532:    // PT-23: GIVEN FakeStart returns Ready(isPaused=true)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:533:    //        WHEN observe uiState THEN Question.isPaused == true
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:535:    fun `isPaused_true_question_uiState_isPaused_true`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:536:        fakeStart.result = makeReadyState(isPaused = true)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:540:        assertTrue(uiState.isPaused, "isPaused=true in domain state must map to Question.isPaused=true")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:543:    // ── PT-24: onContinue → isPaused=false, same indexInPool ─────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:545:    // PT-24: GIVEN state = Ready(isPaused=true, indexInPool=3)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:546:    //        WHEN onContinue() THEN isPaused=false; indexInPool==3
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:548:    fun `onContinue_isPaused_false_sameIndex`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:552:            isPaused = true,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:556:        assertTrue((component.uiState.value as RunnerUiState.Question).isPaused)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:558:        component.onContinue()
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:561:        assertFalse(uiState.isPaused, "onContinue must clear isPaused")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:562:        assertEquals(3, uiState.indexInPool, "indexInPool must remain 3 after onContinue")
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/DefaultLessonRunnerRootComponentTest.kt:651:            isPaused = false,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:8:import com.arkivanov.essenty.lifecycle.stop
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:41: * Integration tests for DefaultLessonRunnerRootComponent — state holder rotation and lifecycle.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:44: * IT-02: rotation reuses RunnerStateHolder via same instanceKeeper (AC-35)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:45: * IT-03: non-rotation destroy creates fresh state in new component (AC-36)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:48: * the Essenty lifecycle, matching Decompose's rotation behavior.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:81:                lifecycle.stop()
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:169:        isPaused = false,
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:172:    // ── IT-02: rotation reuses RunnerStateHolder ──────────────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:176:     *        WHEN simulate rotation (LifecycleRegistry destroy + new LifecycleRegistry, same instanceKeeper)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:177:     *        THEN new component has uiState.indexInPool == 3 (state preserved via shared stateHolder)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:178:     *        AND fakeStart.callCount == 1 (no duplicate StartLessonAttemptUseCase call on rotation)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:180:     * AC-35: Configuration change (rotation) preserves in-progress attempt state.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:183:    fun `rotation_component_reuses_runnerStateHolder`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:197:        // Simulate rotation: destroy lifecycle1 (NOT instanceKeeper)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:212:            "startAttemptUseCase must NOT be called again on rotation (instanceKeeper reuse)",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:216:    // ── IT-03: non-rotation destroy → state NOT preserved ─────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:220:     *        WHEN instanceKeeper.destroy() called (non-rotation: process navigation back)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:221:     *        THEN new component with fresh instanceKeeper calls startAttemptUseCase again (state not preserved)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:238:        // Full destroy (non-rotation): lifecycle + instanceKeeper both destroyed
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:252:            "startAttemptUseCase must be called again when instanceKeeper is fresh (non-rotation)",
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:256:    // ── IT-02b: rotation preserves indexInPool exactly ───────────────────────
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:259:     * IT-02b: Verifies that the exact indexInPool value from before rotation is preserved.
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:266:    fun `rotation_indexInPool_preserved_via_same_instanceKeeper`() {
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:281:        // component2 must see the preserved state (indexInPool ≥ 2, no fresh start)
android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt:286:            "startAttemptUseCase must be called only once total across rotation",

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt | sed -n '60,230p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
    60	    private lateinit var fakeLessonRepo: FakeLessonRepository
    61	    private lateinit var fakeAttemptRepo: FakeLessonAttemptRepository
    62	    private lateinit var fakeRatingRepo: FakeLessonRatingRepository
    63	
    64	    @Before
    65	    fun setUp() {
    66	        fakeStart = FakeStartLessonAttemptUseCase()
    67	        fakeComplete = FakeCompleteAttemptUseCase()
    68	        fakeAbort = FakeAbortAttemptUseCase()
    69	        fakeSubmitRating = FakeSubmitLessonRatingUseCase()
    70	        fakeClock = FakeClock()
    71	        fakeLessonRepo = FakeLessonRepository()
    72	        fakeAttemptRepo = FakeLessonAttemptRepository()
    73	        fakeRatingRepo = FakeLessonRatingRepository()
    74	        fakeLessonRepo.addLesson(defaultLesson())
    75	    }
    76	
    77	    @After
    78	    fun tearDown() {
    79	        lifecycles.forEach { lifecycle ->
    80	            if (lifecycle.state != com.arkivanov.essenty.lifecycle.Lifecycle.State.DESTROYED) {
    81	                lifecycle.stop()
    82	                lifecycle.destroy()
    83	            }
    84	        }
    85	        lifecycles.clear()
    86	    }
    87	
    88	    // ── Build helpers ──────────────────────────────────────────────────────────
    89	
    90	    private fun buildComponent(
    91	        instanceKeeper: InstanceKeeperDispatcher,
    92	        lessonId: LessonId = LessonId("lesson1"),
    93	        mode: Difficulty = Difficulty.EASY,
    94	        startAttemptUseCase: suspend (LessonId, Difficulty) -> RunnerState = fakeStart::invoke,
    95	        completeAttemptUseCase: suspend (RunnerState.Ready) -> RunnerState = fakeComplete::invoke,
    96	        abortAttemptUseCase: suspend (RunnerState.Ready) -> RunnerState = fakeAbort::invoke,
    97	        submitRatingUseCase: suspend (String, LessonId, Int) -> Result<Unit> = fakeSubmitRating::invoke,
    98	    ): DefaultLessonRunnerRootComponent {
    99	        val lifecycle = LifecycleRegistry()
   100	        lifecycles.add(lifecycle)
   101	        lifecycle.resume()
   102	        val ctx = DefaultComponentContext(
   103	            lifecycle = lifecycle,
   104	            instanceKeeper = instanceKeeper,
   105	        )
   106	        return DefaultLessonRunnerRootComponent(
   107	            componentContext = ctx,
   108	            lessonId = lessonId,
   109	            mode = mode,
   110	            useCases = com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases(
   111	                startAttempt = startAttemptUseCase,
   112	                completeAttempt = completeAttemptUseCase,
   113	                abortAttempt = abortAttemptUseCase,
   114	                submitRating = submitRatingUseCase,
   115	            ),
   116	            lessonRepository = fakeLessonRepo,
   117	            attemptRepository = fakeAttemptRepo,
   118	            clock = fakeClock,
   119	            mainContext = Dispatchers.Unconfined,
   120	        )
   121	    }
   122	
   123	    private fun defaultLesson() = Lesson(
   124	        id = LessonId("lesson1"),
   125	        themeId = ThemeId("theme1"),
   126	        title = "Test Lesson",
   127	        order = 0,
   128	        version = 5L,
   129	        contentsVersion = 1L,
   130	        lastModifiedAt = 1000L,
   131	    )
   132	
   133	    private fun optId(s: String) = OptionId(s)
   134	
   135	    private fun singleChoiceDraft() = UserAnswerDraft.SingleChoiceDraft(optId("A"))
   136	
   137	    private fun makeRunnerQuestion(
   138	        id: String = "q1",
   139	        codeAnswerIndex: Int = 0,
   140	    ) = RunnerQuestion.Valid(
   141	        sourceId = QuestionId(id),
   142	        order = codeAnswerIndex,
   143	        codeAnswerIndex = codeAnswerIndex,
   144	        content = QuestionContent.SingleChoice(
   145	            id = id,
   146	            difficulty = Difficulty.EASY,
   147	            text = "Q",
   148	            imageUrl = null,
   149	            options = listOf("A", "B").map { QuestionContent.Option(optId(it), it) },
   150	            correctOptionId = optId("A"),
   151	        ),
   152	    )
   153	
   154	    private fun makeReadyState(
   155	        questions: Int = 5,
   156	        indexInPool: Int = 0,
   157	    ) = RunnerState.Ready(
   158	        userId = "user1",
   159	        lessonId = LessonId("lesson1"),
   160	        lessonVersion = 5L,
   161	        mode = Difficulty.EASY,
   162	        playOrder = (0 until questions).map { makeRunnerQuestion("q${it + 1}", it) },
   163	        eligibleSize = questions,
   164	        indexInPool = indexInPool,
   165	        codeAnswer = CodeAnswer("0".repeat(questions)),
   166	        deadlineMs = 9_000_000L,
   167	        seed = 12345L,
   168	        currentDraftAnswer = null,
   169	        isPaused = false,
   170	    )
   171	
   172	    // ── IT-02: rotation reuses RunnerStateHolder ──────────────────────────────
   173	
   174	    /**
   175	     * IT-02: GIVEN component with FakeStart returning Ready; answers advance to indexInPool=3
   176	     *        WHEN simulate rotation (LifecycleRegistry destroy + new LifecycleRegistry, same instanceKeeper)
   177	     *        THEN new component has uiState.indexInPool == 3 (state preserved via shared stateHolder)
   178	     *        AND fakeStart.callCount == 1 (no duplicate StartLessonAttemptUseCase call on rotation)
   179	     *
   180	     * AC-35: Configuration change (rotation) preserves in-progress attempt state.
   181	     */
   182	    @Test
   183	    fun `rotation_component_reuses_runnerStateHolder`() {
   184	        fakeStart.result = makeReadyState(questions = 5, indexInPool = 0)
   185	        val instanceKeeper = InstanceKeeperDispatcher()
   186	
   187	        // Create component1, start use case runs → Ready(indexInPool=0)
   188	        val component1 = buildComponent(instanceKeeper = instanceKeeper)
   189	        assertIs<RunnerUiState.Question>(component1.uiState.value)
   190	        assertEquals(1, fakeStart.callCount, "startAttemptUseCase must be called once on init")
   191	
   192	        // Advance to indexInPool=3 by answering 3 questions
   193	        repeat(3) { component1.onAnswer(singleChoiceDraft()) }
   194	        val indexAfterAnswers = (component1.uiState.value as RunnerUiState.Question).indexInPool
   195	        assertEquals(3, indexAfterAnswers, "indexInPool must be 3 after answering 3 questions")
   196	
   197	        // Simulate rotation: destroy lifecycle1 (NOT instanceKeeper)
   198	        val lifecycle1 = lifecycles.last()
   199	        lifecycle1.destroy()
   200	
   201	        // Create component2 with the SAME instanceKeeper
   202	        val component2 = buildComponent(instanceKeeper = instanceKeeper)
   203	
   204	        // component2 should reuse RunnerStateHolder → same indexInPool (or adjusted after auto-answer on stop)
   205	        val uiState2 = component2.uiState.value
   206	        assertIs<RunnerUiState.Question>(uiState2)
   207	
   208	        // Key assertion: startAttemptUseCase must NOT have been called again
   209	        assertEquals(
   210	            1,
   211	            fakeStart.callCount,
   212	            "startAttemptUseCase must NOT be called again on rotation (instanceKeeper reuse)",
   213	        )
   214	    }
   215	
   216	    // ── IT-03: non-rotation destroy → state NOT preserved ─────────────────────
   217	
   218	    /**
   219	     * IT-03: GIVEN component in Ready state (indexInPool=3)
   220	     *        WHEN instanceKeeper.destroy() called (non-rotation: process navigation back)
   221	     *        THEN new component with fresh instanceKeeper calls startAttemptUseCase again (state not preserved)
   222	     *        AND fakeStart.callCount == 2 (fresh start)
   223	     *
   224	     * AC-36: Process kill or full navigation back clears in-progress state.
   225	     * Negative: proves state is NOT shared when instanceKeeper is destroyed and replaced.
   226	     */
   227	    @Test
   228	    fun `stateHolder_onDestroy_clears_state_for_new_component`() {
   229	        fakeStart.result = makeReadyState(questions = 5, indexInPool = 0)
   230	        val instanceKeeper1 = InstanceKeeperDispatcher()

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerIntegrationTest.kt | sed -n '230,320p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   230	        val instanceKeeper1 = InstanceKeeperDispatcher()
   231	
   232	        // Create component1 → startAttemptUseCase runs once
   233	        val component1 = buildComponent(instanceKeeper = instanceKeeper1)
   234	        assertEquals(1, fakeStart.callCount)
   235	        repeat(3) { component1.onAnswer(singleChoiceDraft()) }
   236	        assertEquals(3, (component1.uiState.value as RunnerUiState.Question).indexInPool)
   237	
   238	        // Full destroy (non-rotation): lifecycle + instanceKeeper both destroyed
   239	        val lifecycle1 = lifecycles.last()
   240	        lifecycle1.stop()
   241	        lifecycle1.destroy()
   242	        instanceKeeper1.destroy() // calls RunnerStateHolder.onDestroy() → state cleared
   243	
   244	        // Create component2 with a FRESH instanceKeeper (no shared state)
   245	        val instanceKeeper2 = InstanceKeeperDispatcher()
   246	        buildComponent(instanceKeeper = instanceKeeper2)
   247	
   248	        // component2 must call startAttemptUseCase again (fresh start)
   249	        assertEquals(
   250	            2,
   251	            fakeStart.callCount,
   252	            "startAttemptUseCase must be called again when instanceKeeper is fresh (non-rotation)",
   253	        )
   254	    }
   255	
   256	    // ── IT-02b: rotation preserves indexInPool exactly ───────────────────────
   257	
   258	    /**
   259	     * IT-02b: Verifies that the exact indexInPool value from before rotation is preserved.
   260	     * Same as IT-02 but also verifies indexInPool numerically after accounting for
   261	     * any auto-answer that may occur when lifecycle stops.
   262	     *
   263	     * Uses fakeStart returning Ready(indexInPool=2) directly to avoid startup timing issues.
   264	     */
   265	    @Test
   266	    fun `rotation_indexInPool_preserved_via_same_instanceKeeper`() {
   267	        // Use a state with indexInPool=2 already set (no answers needed)
   268	        fakeStart.result = makeReadyState(questions = 5, indexInPool = 2)
   269	        val instanceKeeper = InstanceKeeperDispatcher()
   270	
   271	        val component1 = buildComponent(instanceKeeper = instanceKeeper)
   272	        val indexBefore = (component1.uiState.value as RunnerUiState.Question).indexInPool
   273	        assertEquals(2, indexBefore, "component1 must start at indexInPool=2")
   274	
   275	        // Rotation: destroy lifecycle only (not instanceKeeper)
   276	        lifecycles.last().destroy()
   277	
   278	        // Create component2 with same instanceKeeper
   279	        val component2 = buildComponent(instanceKeeper = instanceKeeper)
   280	
   281	        // component2 must see the preserved state (indexInPool ≥ 2, no fresh start)
   282	        assertIs<RunnerUiState.Question>(component2.uiState.value)
   283	        assertEquals(
   284	            1,
   285	            fakeStart.callCount,
   286	            "startAttemptUseCase must be called only once total across rotation",
   287	        )
   288	    }
   289	}

exec
/bin/bash -lc "nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/state/RunnerState.kt | sed -n '1,220p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state
     2	
     3	import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
     4	import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
     5	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
     6	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
     7	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
     8	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
     9	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
    10	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
    11	
    12	/**
    13	 * State machine for the lesson runner lifecycle.
    14	 *
    15	 * Transitions (happy path): Loading → Ready → Completed
    16	 * Error paths: Loading → InitFailed; Ready → Aborted | SaveFailed
    17	 */
    18	sealed interface RunnerState {
    19	
    20	    /** Initial state while StartLessonAttemptUseCase is loading questions. */
    21	    data object Loading : RunnerState
    22	
    23	    /** Terminal error: attempt was not started. */
    24	    data class InitFailed(val reason: InitFailureReason) : RunnerState
    25	
    26	    /**
    27	     * Active gameplay state. Immutable snapshot of questions fixed at attempt start.
    28	     *
    29	     * [indexInPool] sentinel value [playOrder.size] means last answer was submitted;
    30	     * component must call CompleteAttemptUseCase immediately.
    31	     *
    32	     * [eligibleSize] = length of codeAnswer = total questions of [mode] difficulty
    33	     * in this lesson (after archive filter + parse filter), NOT subset size.
    34	     */
    35	    data class Ready(
    36	        val userId: String,
    37	        val lessonId: LessonId,
    38	        val lessonVersion: Long,
    39	        val mode: Difficulty,
    40	        val playOrder: List<RunnerQuestion.Valid>,
    41	        val eligibleSize: Int,
    42	        val indexInPool: Int,
    43	        val codeAnswer: CodeAnswer,
    44	        val deadlineMs: Long,
    45	        val seed: Long,
    46	        val currentDraftAnswer: UserAnswerDraft?,
    47	        val isPaused: Boolean,
    48	    ) : RunnerState
    49	
    50	    /** Terminal success: attempt saved, optional rating prompt shown. */
    51	    data class Completed(
    52	        val attempt: Attempt,
    53	        val ratingPrompt: Boolean,
    54	    ) : RunnerState
    55	
    56	    /** Terminal: user exited mid-session; attempt saved with partial codeAnswer. */
    57	    data class Aborted(val attempt: Attempt) : RunnerState
    58	
    59	    /** Terminal: attempt constructed but Room write failed. */
    60	    data class SaveFailed(
    61	        val attempt: Attempt,
    62	        val error: SaveError,
    63	    ) : RunnerState
    64	}

exec
/bin/bash -lc 'nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/AttemptId.kt && nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/RatingId.kt && nl -ba shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/CodeAnswer.kt' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	@JvmInline
     4	value class AttemptId(val value: String) {
     5	    init {
     6	        require(value.isNotBlank()) { "AttemptId must not be blank" }
     7	    }
     8	}
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	@JvmInline
     4	value class RatingId(val value: String) {
     5	    init {
     6	        require(value.isNotBlank()) { "RatingId must not be blank" }
     7	    }
     8	}
     1	package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model
     2	
     3	/**
     4	 * Encoded per-question score string.
     5	 * Length = eligibleQuestions(mode).size.
     6	 * Each char ∈ '0'..'9': '0' = not shown; '1'..'9' = shown with correctness digit.
     7	 */
     8	@JvmInline
     9	value class CodeAnswer(val raw: String) {
    10	    init {
    11	        require(raw.isNotEmpty()) { "CodeAnswer must not be empty" }
    12	        require(raw.all { it in '0'..'9' }) { "CodeAnswer chars must be in '0'..'9'" }
    13	    }
    14	}
    15	
    16	/**
    17	 * True if all shown positions (non-'0') are '9' AND at least one '9' exists.
    18	 * String-based — no Float precision issues.
    19	 */
    20	val CodeAnswer.allShownAnswersAre9: Boolean
    21	    get() = raw.all { it == '0' || it == '9' } && raw.any { it == '9' }

exec
/bin/bash -lc 'rg -n "''^import (android'"\\.|androidx\\.|io\\.livekit|com\\.google\\.firebase|androidx\\.room|kotlinx\\.serialization)\" shared/feature/lesson-runner/domain/src/commonMain -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
exec
/bin/bash -lc 'rg -n "''^import .*lesson_runner|shared'"\\.feature\\.lesson_runner|android\\.feature\\.lesson_runner\" shared/feature/lesson/domain android/feature/quizzes-screen/presentation/src/main android/feature/lesson-runner/presentation/src/main -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
 succeeded in 0ms:
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/CrossButton.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:24:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:25:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/FillBlankContent.kt:26:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:19:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:22:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/BlockingResumeDialog.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ExitConfirmDialog.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:23:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/OrderingContent.kt:24:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:21:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/MultipleChoiceContent.kt:22:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:27:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:28:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:29:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:30:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:33:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:20:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/SingleChoiceContent.kt:21:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/LessonRunnerUseCases.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:8:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:9:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toEventSaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:10:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toQuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:11:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUiReason
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:12:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper.toUserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:13:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:21:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:22:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:23:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:24:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/QuestionUiState.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt:5:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.mapper
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.SaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:4:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:5:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:6:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:7:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:10:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:11:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswer
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:12:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:13:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/mapper/RunnerStateMapper.kt:14:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError as DomainSaveError
android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/event/RunnerEvent.kt:1:package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt:3:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt:16:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.LessonRunnerScreen
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:14:import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:20:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:16:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:17:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:18:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:19:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository

exec
/bin/bash -lc "rg -n \"LessonPlaceholder\" android/feature/quizzes-screen/presentation/src/main android/feature/quizzes-screen/presentation/src/test android/feature/quizzes-screen/presentation/src/androidTest -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
exec
/bin/bash -lc "rg -n \"Hilt|Dagger|@Inject|@Module|@InstallIn|Firebase|Firestore|com\\.google\\.firebase\" android/feature/lesson-runner shared/feature/lesson-runner -g '*.kt'" in /home/Programming/Android/schoolquiz4.0
 exited 1 in 0ms:
exec
/bin/bash -lc "sed -n '628,666p' docs/features/lesson-runner/03-decisions.md" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
## ADR-LR-17 — Compose composition exception: quizzes-screen рендерит LessonRunnerScreen через ChildStack

**Status**: Accepted  
**Date**: 2026-04-27  
**Context**: `QuizzesScreen.kt` содержит exhaustive `when(active)` dispatch block для `ChildStack<QuizzesConfig, QuizzesChild>`. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.

`clean-architecture.md:55` запрещает: `android/feature/A/presentation → android/feature/B/presentation: NO by default`. Правило написано до full Decompose adoption и не учитывало ChildStack UI rendering pattern, в котором parent screen обязан знать `@Composable` функции дочерних screens.

**Precedent (verified grep)**: `android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt:53-56` содержит cross-feature imports `HomeQuestsScreen`, `MyQuestsScreen`, `QuizzesScreen` из sibling features — established project pattern до lesson-runner.

**Связь с ADR-LR-16**: `LessonRunnerRootComponent` interface живёт в `lesson-runner/presentation` (не в `core/navigation` — отменено из-за cycle, per ADR-LR-16). `QuizzesScreen.kt` получает `child.component: LessonRunnerRootComponent` из ChildStack и передаёт его в `LessonRunnerScreen`. Таким образом ADR-LR-17 расширяет разрешение ADR-LR-16: тот же модуль `lesson-runner/presentation`, дополнительный artifact — `@Composable` screen function.

**Decision**: Разрешить **одностороннее** Compose rendering import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation` для:
- `LessonRunnerScreen` — `@Composable` top-level screen function
- `LessonRunnerRootComponent` interface — тип параметра `LessonRunnerScreen` (уже разрешён ADR-LR-16, упомянут здесь для полноты)

Обратное направление `android/feature/lesson-runner/presentation → android/feature/quizzes-screen/presentation` остаётся blocker — нарушение = blocker независимо от типа символа.

Детали условий (одностороннее + только `@Composable` + ADR required + grep) — в `clean-architecture.md` ChildStack Compose rendering exception note.

**Alternatives Considered**:

- **(A) Slot pattern** — `LessonRunnerNavigationSlot @Composable` extension в `android/core/navigation/`; quizzes-screen вызывает slot; каждая фича регистрирует свой slot в centralised registry. Отклонён: overhead slot registration; registry требует централизованного места без compile-time гарантии что slot зарегистрирован; не следует Decompose idiomatic pattern.
- **(B) Централизовать в app-shell / RootComponent** — нарушает navigation hierarchy: lesson-runner живёт внутри quiz drill-down (Quest→Section→Theme→Lesson→Runner), не как top-level destination. AppShell — неправильный хост для gameplay экрана.
- **(C) Одностороннее Compose rendering import (CHOSEN)** — minimal change, follows Decompose convention (parent screen knows child @Composable functions), backed by established project precedent.

**Constraints**:
- Импортируется **только** `@Composable` screen function + `LessonRunnerRootComponent` — NOT use cases, NOT repositories, NOT internal sealed interfaces, NOT component implementation classes
- Verifiable через grep:
  ```bash
  rg "^import com\.tpov\.schoolquiz\.android\.feature\.lesson_runner\.presentation" \
    android/feature/quizzes-screen/presentation/src/main -g "*.kt"
  # Допустимые совпадения: только LessonRunnerScreen и LessonRunnerRootComponent
  ```
- Reverse blocker (всегда пусто):
  ```bash
  rg "^import com\.tpov\.schoolquiz\.android\.feature\.quizzes_screen\.presentation" \
    android/feature/lesson-runner/presentation/src/main -g "*.kt"
  # Expected: empty

exec
/bin/bash -lc 'rg -n "LessonAttempt|lesson-runner|LR-17|QS-16|hardUnlocked|bestStars" docs/features/quizzes-screen/03-decisions.md docs/features/lesson-runner/03-decisions.md' in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
docs/features/lesson-runner/03-decisions.md:4:feature: lesson-runner
docs/features/lesson-runner/03-decisions.md:11:## ADR-LR-01: lesson-runner/domain → lesson:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:17:Walking Skeleton `shared/feature/lesson-runner/domain/` уже содержит прямые импорты из `shared/feature/lesson/domain/`:
docs/features/lesson-runner/03-decisions.md:18:- `LessonId` — ключ entity для `Attempt.lessonId`, `LessonAttemptRepository`, `LessonRatingRepository`
docs/features/lesson-runner/03-decisions.md:19:- `LessonRepository` — `StartLessonAttemptUseCase` читает `Lesson.version` (snapshot) и через `getById` для проверки LessonNotFound guard
docs/features/lesson-runner/03-decisions.md:21:**Verified** `[shared/feature/lesson-runner/domain/build.gradle.kts]`: `commonMain` зависит от `:shared:feature:lesson:domain`.
docs/features/lesson-runner/03-decisions.md:27:**Разрешить one-way import** `lesson-runner:domain → lesson:domain` для `LessonId` и `LessonRepository`.
docs/features/lesson-runner/03-decisions.md:29:Направление строго one-way: `lesson:domain` не импортирует `lesson-runner:domain`. `LessonId` — корневой идентификатор entity; без него `Attempt` и `LessonAttemptRepository` теряют typed identity. `LessonRepository.getById` — единственный путь получить `Lesson.version` snapshot на старте попытки (see `0-spec.md §21`: `lessonVersion = lesson.version` at attempt start).
docs/features/lesson-runner/03-decisions.md:33:**Вариант B — Использовать raw `String` lessonId в lesson-runner:domain**:
docs/features/lesson-runner/03-decisions.md:36:- (-) Нарушает DDD принцип: lesson-runner без связи с Lesson — бессмысленная фича
docs/features/lesson-runner/03-decisions.md:44:- Граф зависимостей: `lesson-runner:domain → lesson:domain` one-way ✓
docs/features/lesson-runner/03-decisions.md:45:- Обратный import `lesson:domain → lesson-runner:domain` запрещён — будет blocker при architect-reviewer grep check
docs/features/lesson-runner/03-decisions.md:54:## ADR-LR-02: lesson-runner/domain → question:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:62:- `QuestionRepository` — `StartLessonAttemptUseCase` получает questions через `observeByLesson(lessonId).first()`
docs/features/lesson-runner/03-decisions.md:65:**Verified** `[shared/feature/lesson-runner/domain/build.gradle.kts]`: `:shared:feature:question:domain` в commonMain.
docs/features/lesson-runner/03-decisions.md:69:**Разрешить one-way import** `lesson-runner:domain → question:domain` для `QuestionId`, `QuestionRepository`, `Question`.
docs/features/lesson-runner/03-decisions.md:81:- Граф: `lesson-runner:domain → question:domain` one-way ✓
docs/features/lesson-runner/03-decisions.md:91:## ADR-LR-03: lesson-runner/domain → app-shell:domain (cross-feature import)
docs/features/lesson-runner/03-decisions.md:97:`StartLessonAttemptUseCase` требует текущий Firebase Auth UID для `RunnerState.Ready.userId` snapshot. `AuthRepository` — `shared/feature/app-shell/domain/`. Walking Skeleton уже импортирует его.
docs/features/lesson-runner/03-decisions.md:99:**Verified** `[shared/feature/lesson-runner/domain/build.gradle.kts]`: `:shared:feature:app-shell:domain` в commonMain.
docs/features/lesson-runner/03-decisions.md:103:**Разрешить one-way import** `lesson-runner:domain → app-shell:domain` для `AuthRepository`.
docs/features/lesson-runner/03-decisions.md:109:**Вариант B — Передавать userId как String параметр в StartLessonAttemptUseCase**:
docs/features/lesson-runner/03-decisions.md:115:- Граф: `lesson-runner:domain → app-shell:domain` one-way ✓
docs/features/lesson-runner/03-decisions.md:120:Если `AuthRepository` переезжает из `app-shell:domain` в `shared/core/auth/` — нужно обновить import в lesson-runner:domain и DI module. Низкая стоимость reversal (1 import change + Koin module update).
docs/features/lesson-runner/03-decisions.md:124:## ADR-LR-04: lesson-runner/domain → shared/core/question-schema (core import)
docs/features/lesson-runner/03-decisions.md:130:`lesson-runner:domain` использует из `shared/core/question-schema/`:
docs/features/lesson-runner/03-decisions.md:132:- `QuestionContentParser` interface — `StartLessonAttemptUseCase.parser`
docs/features/lesson-runner/03-decisions.md:139:**Разрешить import** `lesson-runner:domain → shared/core/question-schema`.
docs/features/lesson-runner/03-decisions.md:147:**Вариант B — Дублировать QuestionContent типы в lesson-runner:domain**:
docs/features/lesson-runner/03-decisions.md:158:Если `QuestionContent` schema нужно расширить (5-й тип вопроса) — изменение в shared/core ломает только evaluateAnswer в lesson-runner (compile-time catch). Если parser меняет serialization format — backward compatibility с существующими вопросами в Room нужно тестировать. Средняя стоимость.
docs/features/lesson-runner/03-decisions.md:170:shared/feature/lesson-runner/domain/src/commonMain/.../model/TopParticipant.kt:3
docs/features/lesson-runner/03-decisions.md:174:`lesson-runner:domain` уже импортирует `lesson:domain` (ADR-LR-01). Если `lesson:domain` добавит `Lesson.top3: List<TopParticipant>` — возникнет обратный import `lesson:domain → lesson-runner:domain`. Это **bidirectional coupling** — blocker по Invariant 3 (`docs/invariants.md:25`).
docs/features/lesson-runner/03-decisions.md:176:Verified via `[shared/feature/lesson-runner/domain/build.gradle.kts]`: lesson-runner зависит от lesson. Reverse зависимость создаст circular dependency в Gradle.
docs/features/lesson-runner/03-decisions.md:192:- `lesson-runner:domain` — зависит от `:shared:core:leaderboard` → может читать `TopParticipant` в ResultScreen logic
docs/features/lesson-runner/03-decisions.md:193:- Bidirectional coupling `lesson:domain ↔ lesson-runner:domain` устранён ✓
docs/features/lesson-runner/03-decisions.md:195:`TopParticipant` — универсальная ranking концепция (nickname + avatar + percent), не специфичная для lesson-runner. Будущие features (leaderboard экран, user profiles) могут использовать тот же type. Размещение в core — правильный bounded context.
docs/features/lesson-runner/03-decisions.md:204:**Вариант C — Отдельный `TopParticipantsRepository` в lesson-runner:domain, НЕ в Lesson**:
docs/features/lesson-runner/03-decisions.md:211:- `TopParticipant.kt` **удаляется** из `lesson-runner:domain` и создаётся в `shared/core/leaderboard/`
docs/features/lesson-runner/03-decisions.md:214:- `lesson-runner:domain/build.gradle.kts` добавляет `:shared:core:leaderboard` dep (backend-dev)
docs/features/lesson-runner/03-decisions.md:219:Если leaderboard concept нужно расширить (rankPosition, badgeEmoji, streak) — нужно менять `TopParticipant` в `shared/core/leaderboard/` и обновлять все consumers (lesson:domain + lesson-runner:domain + result screen). Координированное изменение через core — управляемый blast radius. Стоимость reversal: низкая.
docs/features/lesson-runner/03-decisions.md:263:- Существующие consumers `Difficulty` (lesson-runner domain, question-schema, quiz-creation) — без изменений
docs/features/lesson-runner/03-decisions.md:309:- (-) Нет реального usecase для fallback: если lesson-runner недоступен, тап на урок просто не должен работать
docs/features/lesson-runner/03-decisions.md:319:- Обновление `docs/features/quizzes-screen/03-decisions.md`: добавить ADR-QS-15 (push consumer side) и ADR-QS-16 (LessonAttemptRepository import)
docs/features/lesson-runner/03-decisions.md:323:Если lesson-runner реализация задерживается или отменяется — нет fallback: тап на урок не работает. Reversal = восстановить `LessonPlaceholder*` файлы из git history + откатить ADR-LR-07. Стоимость reversal: средняя, но git history доступен. Атомарная замена правильная ставка для новой фичи без production saved state.
docs/features/lesson-runner/03-decisions.md:327:> ⚠️ Этот раздел описывал первоначальный подход через `android/core/navigation/`. Codex CLI plan-round-1 выявил, что этот подход создаёт circular Gradle dependency: `core/navigation → lesson-runner/presentation` (для RunnerUiState/RunnerEvent типов) + `lesson-runner/presentation → core/navigation` (для interface implementation). Cycle = build failure. Актуальное решение — **ADR-LR-16**: interface и factory живут в `lesson-runner/presentation`, прямой импорт из `quizzes-screen/presentation`.
docs/features/lesson-runner/03-decisions.md:331:## ADR-LR-16: LessonRunnerRootComponent interface location — lesson-runner/presentation
docs/features/lesson-runner/03-decisions.md:340:core/navigation → lesson-runner/presentation   (RunnerUiState, RunnerEvent типы для interface)
docs/features/lesson-runner/03-decisions.md:341:lesson-runner/presentation → core/navigation   (interface implementation)
docs/features/lesson-runner/03-decisions.md:346:`LessonRunnerComponentFactory` fun interface с тем же планом в `core/navigation` имела идентичную проблему: return type `LessonRunnerRootComponent` обязывает `core/navigation → lesson-runner/presentation`.
docs/features/lesson-runner/03-decisions.md:350:**Оба артефакта живут в `android/feature/lesson-runner/presentation/`**:
docs/features/lesson-runner/03-decisions.md:354:| `LessonRunnerRootComponent` interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt` |
docs/features/lesson-runner/03-decisions.md:355:| `LessonRunnerComponentFactory` fun interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt` |
docs/features/lesson-runner/03-decisions.md:357:`quizzes-screen/presentation` импортирует оба напрямую. Направление строго одностороннее: `quizzes-screen/presentation → lesson-runner/presentation`. Reverse direction запрещён.
docs/features/lesson-runner/03-decisions.md:366:- (-) Создаёт cycle: `core/navigation → lesson-runner/presentation` + `lesson-runner/presentation → core/navigation`. Gradle build failure.
docs/features/lesson-runner/03-decisions.md:371:- (+) `core/navigation` остаётся без dep на `lesson-runner/presentation`
docs/features/lesson-runner/03-decisions.md:384:- `quizzes-screen/presentation` Gradle module добавляет `:android:feature:lesson-runner:presentation` в deps (backend-dev scope, `build.gradle.kts`).
docs/features/lesson-runner/03-decisions.md:388:- Validation grep после phase-04 implementation: `rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src` — ожидается 1 match.
docs/features/lesson-runner/03-decisions.md:392:Если появится второй consumer `lesson-runner/presentation` вне `quizzes-screen` — паттерн прямого импорта повторяется с новым ADR (per `clean-architecture.md` ChildStack Compose rendering exception — требует ADR). Если второй consumer в `android/core/` — оба артефакта переезжают в `core` и dep direction разворачивается. Стоимость reversal: средняя (2 файла + Gradle deps update + import changes в consumers). Текущее решение оптимально для одного consumer.
docs/features/lesson-runner/03-decisions.md:403:**Context**: `StartLessonAttemptUseCase` зависит от `QuestionContentParser`. Production impl `KotlinxSerializationQuestionContentParser` не существует (Grounding Problem 1). Два кандидата: (A) `shared/core/question-schema/src/commonMain/` или (B) `shared/feature/lesson-runner/data/`.
docs/features/lesson-runner/03-decisions.md:407:**Rationale**: Парсер — shared core infrastructure, не feature-specific data. `kotlinx.serialization` уже является зависимостью `shared/core/question-schema/build.gradle.kts:13`. Размещение в core: (1) позволяет другим фичам переиспользовать без транзитивной зависимости на lesson-runner/data; (2) следует паттерну "интерфейс и реализация в одном модуле" для утилитных core компонентов.
docs/features/lesson-runner/03-decisions.md:413:- AC #54 (domain purity): `lesson-runner/domain` НЕ импортирует `kotlinx.serialization` напрямую — только через `QuestionContentParser` interface из question-schema.
docs/features/lesson-runner/03-decisions.md:416:- **Option B** (`lesson-runner/data`) — создаёт ненужную зависимость `data → question-schema` для любого consumer, которому нужен парсер. Отклонено.
docs/features/lesson-runner/03-decisions.md:420:Если parser нужно переместить в `lesson-runner/data` (например специфическая логика только для этой фичи) — перемещение требует обновить Koin module (`questionSchemaModule` → `lessonRunnerDataModule`) и все tests. Умеренная стоимость. Если `Question` schema добавит 5-й тип — parser нужно обновить в `shared/core/question-schema/` с exhaustive `when`; compile-time catch гарантирован.
docs/features/lesson-runner/03-decisions.md:440:**Decision**: **Option C** — wrapper interfaces в `shared/feature/lesson-runner/domain/src/commonMain/…/provider/` (C1 fix: domain — единственный потребитель; interfaces принадлежат слою который их использует):
docs/features/lesson-runner/03-decisions.md:444:Default implementations (`DefaultAttemptIdProvider`, `DefaultRandomSeedProvider`, `DefaultRatingIdProvider`) — `shared/feature/lesson-runner/data/src/androidMain/…/provider/`.
docs/features/lesson-runner/03-decisions.md:446:Koin adapter (`lessonRunnerDomainKoinAdapter`) — `shared/feature/lesson-runner/data/src/androidMain/…/di/` (C1 fix: adapter в data, не в domain/androidMain):
docs/features/lesson-runner/03-decisions.md:503:- C: добавить `bestStars: Float?, hardUnlocked: Boolean, isHardChecked: Boolean` напрямую в `HierarchyItemCard`
docs/features/lesson-runner/03-decisions.md:507:**Rationale**: (1) Lesson card — единственный уровень drill-down с gameplay state. Section/Theme/Quest cards — чистые navigation items без game state. (2) Designsystem не должен знать о gameplay-специфичных концепциях (hardUnlocked, Stars). (3) Изолирует изменение от Section/Theme/Quest screens.
docs/features/lesson-runner/03-decisions.md:510:**LessonItemUi** (новый): `LessonItemUi(id, title, orderLabel: String? = null, subtitleCount: String? = null, bestStarsRawTenths: Int = 0, hardUnlocked: Boolean = false, isHardChecked: Boolean = false)`
docs/features/lesson-runner/03-decisions.md:511:(Canonical SSoT: `06-api-contract.md §LR-12`. `orderLabel` nullable per `HierarchyItemUi.kt:6`. `bestStarsRawTenths` per `Stars(rawTenths: Int)` domain type.)
docs/features/lesson-runner/03-decisions.md:534:**Compile gate** (C13): `./gradlew :shared:feature:lesson-runner:domain:jvmTest` должен быть зелёным после rename. Если тесты ломаются — rename не завершён (не "почти готово").
docs/features/lesson-runner/03-decisions.md:586:> **Amendment 2026-04-26 (lesson-runner)**: Прохождение EASY НЕ прерывается на ошибке или таймауте — продолжается до конца pool. Звёзды считаются по итоговому `percentScore`. Прерывание, упомянутое в первоначальном тексте, — отменено.
docs/features/lesson-runner/03-decisions.md:589:> **Amendment 2026-04-26 (lesson-runner)**: На EASY и HARD — после ответа сразу переход к следующему вопросу, без раскрытия правильного ответа. Feedback — отдельная фича.
docs/features/lesson-runner/03-decisions.md:592:> **Amendment 2026-04-26 (lesson-runner)**: `timeLimitSec` **может остаться в payload** для backward compatibility, но **runtime игнорирует его** в пользу формулы `seconds = max(5, round(charsCount × k))`. EASY k≈0.18, HARD k≈0.12.
docs/features/lesson-runner/03-decisions.md:595:> **Amendment 2026-04-26 (lesson-runner)**: Reference `shared/feature/quiz/domain` заменяется на `shared/feature/lesson-runner/domain` — фактический module для runtime gameplay.
docs/features/lesson-runner/03-decisions.md:628:## ADR-LR-17 — Compose composition exception: quizzes-screen рендерит LessonRunnerScreen через ChildStack
docs/features/lesson-runner/03-decisions.md:632:**Context**: `QuizzesScreen.kt` содержит exhaustive `when(active)` dispatch block для `ChildStack<QuizzesConfig, QuizzesChild>`. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.
docs/features/lesson-runner/03-decisions.md:636:**Precedent (verified grep)**: `android/feature/app-shell/presentation/src/main/.../ui/AppShellScreen.kt:53-56` содержит cross-feature imports `HomeQuestsScreen`, `MyQuestsScreen`, `QuizzesScreen` из sibling features — established project pattern до lesson-runner.
docs/features/lesson-runner/03-decisions.md:638:**Связь с ADR-LR-16**: `LessonRunnerRootComponent` interface живёт в `lesson-runner/presentation` (не в `core/navigation` — отменено из-за cycle, per ADR-LR-16). `QuizzesScreen.kt` получает `child.component: LessonRunnerRootComponent` из ChildStack и передаёт его в `LessonRunnerScreen`. Таким образом ADR-LR-17 расширяет разрешение ADR-LR-16: тот же модуль `lesson-runner/presentation`, дополнительный artifact — `@Composable` screen function.
docs/features/lesson-runner/03-decisions.md:640:**Decision**: Разрешить **одностороннее** Compose rendering import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation` для:
docs/features/lesson-runner/03-decisions.md:644:Обратное направление `android/feature/lesson-runner/presentation → android/feature/quizzes-screen/presentation` остаётся blocker — нарушение = blocker независимо от типа символа.
docs/features/lesson-runner/03-decisions.md:651:- **(B) Централизовать в app-shell / RootComponent** — нарушает navigation hierarchy: lesson-runner живёт внутри quiz drill-down (Quest→Section→Theme→Lesson→Runner), не как top-level destination. AppShell — неправильный хост для gameplay экрана.
docs/features/lesson-runner/03-decisions.md:665:    android/feature/lesson-runner/presentation/src/main -g "*.kt"
docs/features/lesson-runner/03-decisions.md:683:Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.
docs/features/lesson-runner/03-decisions.md:685:Verified: `LessonAttemptEntity.isHard` — `@ColumnInfo(name="is_hard") val isHard: Int` (spec AC#52 mandates Int for safer migration validation). Converter на `Difficulty` не имеет Entity-binding.
docs/features/lesson-runner/03-decisions.md:692:// LessonAttemptMapper (data layer)
docs/features/lesson-runner/03-decisions.md:713:- Mapper `LessonAttemptMapper` обрабатывает `Difficulty ↔ Int` через условный маппинг
docs/features/lesson-runner/03-decisions.md:726:`RunnerUiState.Result` использует flat-поля (`percentScore`, `mode`, `completedAt`, `hardUnlocked`, `bestStarsRawTenths`) вместо `attempt: Attempt` aggregate из `06-api-contract.md:408`.
docs/features/lesson-runner/03-decisions.md:732:Дополнительно: `hardUnlocked` и `bestStarsRawTenths` отсутствуют в domain `Attempt` — flat-подход единственно возможный без изменения domain модели. Phase-04 code-review (task #22) принял реализацию. Тесты PT-01..PT-41 зелёные.
docs/features/lesson-runner/03-decisions.md:748:- **Добавить separate `hardUnlocked: Boolean` + `bestStarsRawTenths: Int` рядом с `attempt`** — создаёт hybrid сигнатуру, сложнее чем full flat. Отклонено: flat проще и safer.
docs/features/lesson-runner/03-decisions.md:757:`single<LessonRunnerComponentFactory>` binding живёт в `lessonRunnerPresentationModule` (`android/feature/lesson-runner/presentation/src/main/.../di/LessonRunnerPresentationModule.kt:37`), а **не** inline в `AppApplication.kt startKoin` как предписывалось `06-api-contract.md:374`.
docs/features/quizzes-screen/03-decisions.md:497:## ADR-QS-15 — quizzes-screen/presentation → lesson-runner/presentation: childFactory consumer side
docs/features/quizzes-screen/03-decisions.md:499:**Status**: **SUPERSEDED by ADR-LR-16 + ADR-LR-17 (2026-04-27)**  
docs/features/quizzes-screen/03-decisions.md:502:> ⚠️ Initial design assumed factory + interface live in `android/core/navigation/`. Cycle analysis (ADR-LR-16 в `docs/features/lesson-runner/03-decisions.md`) установил что это создаёт circular Gradle dependency: `core/navigation → lesson-runner/presentation` (для `RunnerUiState`/`RunnerEvent`) + `lesson-runner/presentation → core/navigation` (для interface implementation). Корректное решение: factory + `LessonRunnerRootComponent` interface живут в `lesson-runner/presentation`. `quizzes-screen/presentation → lesson-runner/presentation` — одностороннее направление, разрешённое ADR-LR-17 (Compose composition exception). Текст ниже сохранён для исторической трассировки. Любые planner/dev refs должны идти на ADR-QS-17 + ADR-LR-16.
docs/features/quizzes-screen/03-decisions.md:542:`LessonRunnerRootComponent` interface (return type) живёт в `android/core/navigation/` — уже существующий core модуль. Это исключает прямой import `quizzes-screen/presentation → lesson-runner/presentation`. Canonical signature — architect-component зона (`06-api-contract.md §LR-9`).
docs/features/quizzes-screen/03-decisions.md:549:- `quizzes-screen/presentation` не импортирует `lesson-runner/presentation` — граница соблюдена.
docs/features/quizzes-screen/03-decisions.md:556:## ADR-QS-16 — quizzes-screen/presentation → lesson-runner:domain: LessonAttemptRepository import
docs/features/quizzes-screen/03-decisions.md:560:**Context**: `DefaultLessonListComponent` должен показывать `bestStars` и вычислять `hardUnlocked` для каждого урока в списке. Данные приходят из `LessonAttemptRepository.observeByLessonId(lessonId)` — репозиторий принадлежит `lesson-runner:domain`. Без этого импорта Lesson list не может отобразить прогресс пользователя.
docs/features/quizzes-screen/03-decisions.md:564:Existing precedent: `quizzes-screen/presentation` уже импортирует `quest:domain`, `section:domain`, `theme:domain`, `lesson:domain` — все через `shared/feature/<slug>/domain`. Добавление `lesson-runner:domain` — тот же паттерн.
docs/features/quizzes-screen/03-decisions.md:566:**Decision**: Вариант A — прямой import `shared/feature/lesson-runner/domain` в `quizzes-screen/presentation`.
docs/features/quizzes-screen/03-decisions.md:568:Направление зависимости: `quizzes-screen/presentation → lesson-runner:domain` (одностороннее). `lesson-runner:domain` не импортирует `quizzes-screen/presentation` — bidirectional coupling отсутствует.
docs/features/quizzes-screen/03-decisions.md:572:import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
docs/features/quizzes-screen/03-decisions.md:576:    private val lessonAttemptRepository: LessonAttemptRepository,  // NEW
docs/features/quizzes-screen/03-decisions.md:581:**Scope ограничен**: только `LessonAttemptRepository` (interface). Никаких imports из `lesson-runner:data` или `lesson-runner/presentation`.
docs/features/quizzes-screen/03-decisions.md:584:- **(B) Вынести LessonAttemptRepository в `shared/core/`** — отклонён: репозиторий специфичен для lesson-runner domain (знает об `Attempt`, `Stars`, `Difficulty`) — это не generic core contract. Переезд в core потянет за собой `Attempt`, `Stars`, `Difficulty` — core разбухает product-level domain concepts.
docs/features/quizzes-screen/03-decisions.md:585:- **(C) Вычислять bestStars через Lesson.top3** — отклонён: `top3` — агрегированные данные сервера (Cloud Function), недоступны offline-first; `bestStars` — локальный прогресс пользователя из Room, должен работать без сети.
docs/features/quizzes-screen/03-decisions.md:588:- `shared/feature/lesson-runner/domain` добавляется в `build.gradle.kts` зависимости `android/feature/quizzes-screen/presentation` — scaffold change для `backend-dev`.
docs/features/quizzes-screen/03-decisions.md:591:- Invariant 3 (no bidirectional coupling): `rg "^import .*quizzes_screen" shared/feature/lesson-runner -g "*.kt"` должен быть пустым (note: filesystem path uses hyphen, Kotlin package uses underscore).
docs/features/quizzes-screen/03-decisions.md:601:`QuizzesScreen.kt` содержит exhaustive `when(active)` ChildStack dispatch block. При добавлении `QuizzesChild.LessonRunner` (ADR-LR-07) нужно вызвать `LessonRunnerScreen(child.component)` — `@Composable` функция из `android/feature/lesson-runner/presentation`. Это создаёт прямой import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation`.
docs/features/quizzes-screen/03-decisions.md:615:Это три cross-feature presentation imports в production code — установленный проектный паттерн до lesson-runner.
docs/features/quizzes-screen/03-decisions.md:619:Разрешить **односторонний** import `android/feature/quizzes-screen/presentation → android/feature/lesson-runner/presentation` **исключительно** для `@Composable` rendering target (`LessonRunnerScreen` composable function).
docs/features/quizzes-screen/03-decisions.md:621:Импортируемый артефакт: только `@Composable fun LessonRunnerScreen(component: LessonRunnerRootComponent)` (или аналогичная top-level screen composable из lesson-runner/presentation).
docs/features/quizzes-screen/03-decisions.md:623:Обратное направление `android/feature/lesson-runner/presentation → android/feature/quizzes-screen/presentation` остаётся заблокировано — является blocker invariant независимо от механизма.
docs/features/quizzes-screen/03-decisions.md:627:- Импортируется **только** `@Composable` screen function — NOT component classes, NOT use cases, NOT repositories, NOT internal state types, NOT sealed interfaces internal to lesson-runner.
docs/features/quizzes-screen/03-decisions.md:633:  Допустимые совпадения: только строки содержащие `LessonRunnerScreen` (или аналогичную screen composable). Любые другие типы из `lesson-runner/presentation` = blocker.
docs/features/quizzes-screen/03-decisions.md:637:    android/feature/lesson-runner/presentation/src/main -g "*.kt"
docs/features/quizzes-screen/03-decisions.md:644:- **(B) Централизовать lesson-runner в app-shell вместо quizzes-screen** — нарушает navigation hierarchy: lesson runner живёт внутри quiz drill-down (Quest → Section → Theme → Lesson → Runner), а не как top-level destination. AppShell не является правильным хостом для gameplay screen.
docs/features/quizzes-screen/03-decisions.md:659:# ADR-QS-17: только LessonRunnerScreen import из lesson-runner/presentation
docs/features/quizzes-screen/03-decisions.md:666:  android/feature/lesson-runner/presentation/src/main -g "*.kt"

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/Top3Section.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.layout.Arrangement
     4	import androidx.compose.foundation.layout.Column
     5	import androidx.compose.foundation.layout.Row
     6	import androidx.compose.foundation.layout.Spacer
     7	import androidx.compose.foundation.layout.fillMaxWidth
     8	import androidx.compose.foundation.layout.size
     9	import androidx.compose.foundation.layout.width
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.material3.Text
    12	import androidx.compose.runtime.Composable
    13	import androidx.compose.ui.Alignment
    14	import androidx.compose.ui.Modifier
    15	import androidx.compose.ui.text.style.TextOverflow
    16	import androidx.compose.ui.tooling.preview.Preview
    17	import androidx.compose.ui.unit.dp
    18	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    19	import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
    20	
    21	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    22	@Composable
    23	fun Top3Section(
    24	    top3: List<TopParticipant>,
    25	    modifier: Modifier = Modifier,
    26	) {
    27	    Column(
    28	        modifier = modifier.fillMaxWidth(),
    29	        verticalArrangement = Arrangement.spacedBy(8.dp),
    30	    ) {
    31	        Text(
    32	            text = "Лучшие участники",
    33	            style = MaterialTheme.typography.titleMedium,
    34	        )
    35	        top3.forEach { participant ->
    36	            Row(
    37	                verticalAlignment = Alignment.CenterVertically,
    38	                modifier = Modifier.fillMaxWidth(),
    39	            ) {
    40	                ParticipantAvatar(
    41	                    avatarUrl = participant.avatarUrl,
    42	                    modifier = Modifier.size(40.dp),
    43	                )
    44	                Spacer(modifier = Modifier.width(8.dp))
    45	                Text(
    46	                    text = participant.nickname,
    47	                    modifier = Modifier.weight(1f),
    48	                    maxLines = 1,
    49	                    overflow = TextOverflow.Ellipsis,
    50	                    style = MaterialTheme.typography.bodyMedium,
    51	                )
    52	                Text(
    53	                    text = "${participant.percent}%",
    54	                    style = MaterialTheme.typography.bodyMedium,
    55	                    color = MaterialTheme.colorScheme.primary,
    56	                )
    57	            }
    58	        }
    59	    }
    60	}
    61	
    62	@Suppress("FunctionNaming", "UnusedPrivateMember", "MagicNumber", "ktlint:standard:function-naming")
    63	@Preview(showBackground = true)
    64	@Composable
    65	private fun Top3SectionPreview() {
    66	    SchoolQuizTheme {
    67	        Top3Section(
    68	            top3 =
    69	                listOf(
    70	                    TopParticipant("Alice", null, 95),
    71	                    TopParticipant("Bob", null, 88),
    72	                    TopParticipant("Carol", null, 72),
    73	                ),
    74	        )
    75	    }
    76	}

exec
/bin/bash -lc "nl -ba android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ImageComponents.kt | sed -n '1,260p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
     1	package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui
     2	
     3	import androidx.compose.foundation.background
     4	import androidx.compose.foundation.layout.Box
     5	import androidx.compose.foundation.layout.size
     6	import androidx.compose.material.icons.Icons
     7	import androidx.compose.material.icons.filled.AccountCircle
     8	import androidx.compose.material.icons.filled.Image
     9	import androidx.compose.material3.Icon
    10	import androidx.compose.material3.MaterialTheme
    11	import androidx.compose.runtime.Composable
    12	import androidx.compose.ui.Alignment
    13	import androidx.compose.ui.Modifier
    14	import androidx.compose.ui.tooling.preview.Preview
    15	import androidx.compose.ui.unit.dp
    16	import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
    17	
    18	/**
    19	 * Placeholder composable for question images.
    20	 * Replace with AsyncImage (Coil) once `libs.coil3.compose` is added to build.gradle.kts.
    21	 * Open Question: backend-dev to add `implementation(libs.coil3.compose)` to lesson-runner:presentation.
    22	 */
    23	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    24	@Composable
    25	fun QuestionImage(
    26	    url: String,
    27	    modifier: Modifier = Modifier,
    28	) {
    29	    Box(
    30	        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    31	        contentAlignment = Alignment.Center,
    32	    ) {
    33	        Icon(
    34	            imageVector = Icons.Default.Image,
    35	            contentDescription = null,
    36	            tint = MaterialTheme.colorScheme.onSurfaceVariant,
    37	        )
    38	    }
    39	}
    40	
    41	/**
    42	 * Placeholder composable for participant avatar images.
    43	 * Replace with AsyncImage (Coil) once coil3-compose is available in this module.
    44	 */
    45	@Suppress("FunctionNaming", "ktlint:standard:function-naming")
    46	@Composable
    47	fun ParticipantAvatar(
    48	    avatarUrl: String?,
    49	    modifier: Modifier = Modifier,
    50	) {
    51	    Icon(
    52	        imageVector = Icons.Default.AccountCircle,
    53	        contentDescription = null,
    54	        modifier = modifier,
    55	        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    56	    )
    57	}
    58	
    59	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    60	@Preview(showBackground = true)
    61	@Composable
    62	private fun QuestionImagePreview() {
    63	    SchoolQuizTheme {
    64	        QuestionImage(url = "https://example.com/image.png", modifier = Modifier.size(200.dp, 120.dp))
    65	    }
    66	}
    67	
    68	@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
    69	@Preview(showBackground = true)
    70	@Composable
    71	private fun ParticipantAvatarPreview() {
    72	    SchoolQuizTheme {
    73	        ParticipantAvatar(avatarUrl = null, modifier = Modifier.size(40.dp))
    74	    }
    75	}

exec
/bin/bash -lc "nl -ba docs/features/quizzes-screen/03-decisions.md | sed -n '556,584p' && nl -ba docs/features/lesson-runner/03-decisions.md | sed -n '650,662p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   556	## ADR-QS-16 — quizzes-screen/presentation → lesson-runner:domain: LessonAttemptRepository import
   557	
   558	**Status**: Accepted  
   559	**Date**: 2026-04-26  
   560	**Context**: `DefaultLessonListComponent` должен показывать `bestStars` и вычислять `hardUnlocked` для каждого урока в списке. Данные приходят из `LessonAttemptRepository.observeByLessonId(lessonId)` — репозиторий принадлежит `lesson-runner:domain`. Без этого импорта Lesson list не может отобразить прогресс пользователя.
   561	
   562	`clean-architecture.md` требует ADR для cross-feature imports `shared/feature/A → shared/feature/B`.
   563	
   564	Existing precedent: `quizzes-screen/presentation` уже импортирует `quest:domain`, `section:domain`, `theme:domain`, `lesson:domain` — все через `shared/feature/<slug>/domain`. Добавление `lesson-runner:domain` — тот же паттерн.
   565	
   566	**Decision**: Вариант A — прямой import `shared/feature/lesson-runner/domain` в `quizzes-screen/presentation`.
   567	
   568	Направление зависимости: `quizzes-screen/presentation → lesson-runner:domain` (одностороннее). `lesson-runner:domain` не импортирует `quizzes-screen/presentation` — bidirectional coupling отсутствует.
   569	
   570	```kotlin
   571	// DefaultLessonListComponent.kt
   572	import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
   573	
   574	class DefaultLessonListComponent(
   575	    // ...
   576	    private val lessonAttemptRepository: LessonAttemptRepository,  // NEW
   577	    private val authRepository: AuthRepository,                      // NEW
   578	) : ComponentContext by componentContext, LessonListComponent
   579	```
   580	
   581	**Scope ограничен**: только `LessonAttemptRepository` (interface). Никаких imports из `lesson-runner:data` или `lesson-runner/presentation`.
   582	
   583	**Alternatives Considered**:
   584	- **(B) Вынести LessonAttemptRepository в `shared/core/`** — отклонён: репозиторий специфичен для lesson-runner domain (знает об `Attempt`, `Stars`, `Difficulty`) — это не generic core contract. Переезд в core потянет за собой `Attempt`, `Stars`, `Difficulty` — core разбухает product-level domain concepts.
   650	- **(A) Slot pattern** — `LessonRunnerNavigationSlot @Composable` extension в `android/core/navigation/`; quizzes-screen вызывает slot; каждая фича регистрирует свой slot в centralised registry. Отклонён: overhead slot registration; registry требует централизованного места без compile-time гарантии что slot зарегистрирован; не следует Decompose idiomatic pattern.
   651	- **(B) Централизовать в app-shell / RootComponent** — нарушает navigation hierarchy: lesson-runner живёт внутри quiz drill-down (Quest→Section→Theme→Lesson→Runner), не как top-level destination. AppShell — неправильный хост для gameplay экрана.
   652	- **(C) Одностороннее Compose rendering import (CHOSEN)** — minimal change, follows Decompose convention (parent screen knows child @Composable functions), backed by established project precedent.
   653	
   654	**Constraints**:
   655	- Импортируется **только** `@Composable` screen function + `LessonRunnerRootComponent` — NOT use cases, NOT repositories, NOT internal sealed interfaces, NOT component implementation classes
   656	- Verifiable через grep:
   657	  ```bash
   658	  rg "^import com\.tpov\.schoolquiz\.android\.feature\.lesson_runner\.presentation" \
   659	    android/feature/quizzes-screen/presentation/src/main -g "*.kt"
   660	  # Допустимые совпадения: только LessonRunnerScreen и LessonRunnerRootComponent
   661	  ```
   662	- Reverse blocker (всегда пусто):

exec
/bin/bash -lc "nl -ba docs/features/lesson-runner/0-spec.md | sed -n '1044,1116p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
  1044	### Score / codeAnswer correctness
  1045	
  1046	6. [ ] GIVEN SingleChoice с correct=A WHEN selected=A THEN code digit = '9'
  1047	7. [ ] GIVEN SingleChoice с correct=A WHEN selected=B THEN code digit = '1'
  1048	8. [ ] GIVEN MultipleChoice (все верные picked) THEN code digit = '9'
  1049	9. [ ] GIVEN MultipleChoice (никаких верных picked, только wrong) THEN code digit = '1'
  1050	10. [ ] GIVEN MultipleChoice (Jaccard 0.5) THEN code digit = '5'
  1051	11. [ ] GIVEN Ordering все позиции верны THEN digit = '9'
  1052	12. [ ] GIVEN Ordering 3 из 6 совпало THEN digit = '5' (round(0.5*8)+1)
  1053	13. [ ] GIVEN FillBlank 3 из 3 верно THEN digit = '9'
  1054	14. [ ] GIVEN FillBlank 1 из 3 THEN digit = '4' (round(0.333*8)+1=round(2.67)+1)
  1055	15. [ ] GIVEN timeout без действий THEN auto-random выбор → digit вычислен по тем же правилам (зависит от случайного попадания)
  1056	16. [ ] GIVEN attempt completed THEN `codeAnswer.length == eligibleQuestions(mode).size`; non-zero digits.size == subset.size; positions of non-zero соответствуют orders показанных вопросов
  1057	
  1058	### Stars and progress
  1059	
  1060	17. [ ] GIVEN EASY attempt с `codeAnswer.allShownAnswersAre9 == true` (`percentScore=100`) THEN `Stars(rawTenths=20)`; bestStars обновляется; hardUnlocked становится true
  1061	18. [ ] GIVEN EASY attempt с `percentScore=50` (no allShownAnswersAre9) THEN `Stars(rawTenths=10)`; hardUnlocked остаётся false
  1062	19. [ ] GIVEN HARD attempt с `percentScore=80` THEN `Stars(rawTenths=28)`
  1063	20. [ ] GIVEN HARD attempt с `percentScore=100` (allShownAnswersAre9=true) THEN `Stars(rawTenths=30)`
  1064	21. [ ] GIVEN no attempts THEN `bestStars = Stars(0)` (UI 0.0), `hardUnlocked = false`, HARD checkbox скрыт
  1065	22. [ ] GIVEN ≥1 EASY-попытка с allShownAnswersAre9=true (все показанные = '9') THEN `hardUnlocked = true` → HARD checkbox visible; user может включить
  1066	23. [ ] GIVEN ≥1 EASY-попытка с `allShownAnswersAre9=false` (хотя бы один digit ∈ '1'..'8') THEN `hardUnlocked = false` → HARD checkbox остаётся скрытым; даже если есть HARD-attempt с floor `Stars(rawTenths=20)` — checkbox visibility = `hardUnlocked`, НЕ `bestStars.rawTenths >= 20`
  1067	
  1068	### Timer
  1069	
  1070	24. [ ] GIVEN вопрос с totalChars=165, EASY mode THEN `timer == round(165*0.18) ≈ 30 сек`
  1071	25. [ ] GIVEN тот же вопрос HARD mode THEN `timer ≈ round(165*0.12) ≈ 20 сек`
  1072	26. [ ] GIVEN таймер истекает в 0 без действий пользователя THEN auto-random выбор → score фиксируется → переход к следующему вопросу
  1073	27. [ ] GIVEN короткий вопрос (totalChars=10) THEN `timer ≥ 5 сек` (min floor delegated)
  1074	
  1075	### Lifecycle / FLAG_SECURE / dialogs
  1076	
  1077	28. [ ] GIVEN HARD-mode runner запущен THEN `WindowManager.LayoutParams.FLAG_SECURE` установлен на window (или Compose-эквивалент)
  1078	29. [ ] GIVEN HARD-mode runner exit (любым путём) THEN FLAG_SECURE снят
  1079	30. [ ] GIVEN EASY-mode runner THEN FLAG_SECURE НЕ установлен
  1080	31. [ ] GIVEN пользователь на 5-м вопросе тапает Home (onStop) THEN текущий вопрос auto-random scored; таймер остановлен
  1081	32. [ ] GIVEN пользователь возвращается (onResume) THEN отображается fullscreen блокирующий диалог «Продолжить прохождение?»
  1082	33. [ ] GIVEN диалог «Продолжить?» тап «Продолжить» THEN диалог закрыт; следующий вопрос отображён с новым таймером; предыдущий вопрос НЕ показан
  1083	34. [ ] GIVEN диалог «Продолжить?» тап «Выйти» THEN attempt saved (codeAnswer: scores отвеченных + '1' для оставшихся показанных + '0' для не показанных); возврат в `LessonListComponent`
  1084	35. [ ] GIVEN configuration change (rotation) THEN component не пересоздаётся; таймер не сбрасывается; current question + answers preserved
  1085	36. [ ] GIVEN process kill THEN ничего не записывается в Room; следующий запуск — состояние ДО попытки
  1086	
  1087	### Attempt save / Room
  1088	
  1089	37. [ ] GIVEN complete attempt THEN `LessonAttemptRepository.save` called once с Attempt(lessonVersion из старта)
  1090	38. [ ] GIVEN exit-via-dialog после 3 ответов (pool size 20, eligibleQuestions.size=50) THEN saved attempt: codeAnswer.length==50, 3 цифры реальные, 17 = '1' (показанные но непрогресированные), 30 = '0' (out of subset)
  1091	39. [ ] GIVEN attempt созданный во время `lesson.version=5` THEN `attempt.lessonVersion == 5` даже если lesson.version обновился позже sync-ом
  1092	40. [ ] GIVEN no incremental save во время прохождения THEN `LessonAttemptRepository.save` вызывается ровно 1 раз per attempt
  1093	
  1094	### Result screen
  1095	
  1096	41. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND ¬hasSubmittedRating THEN на result screen видно опрос «Оцените урок» (1/2/3 целых звезды)
  1097	42. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == true` AND hasSubmittedRating THEN опрос НЕ виден
  1098	43. [ ] GIVEN attempt с `codeAnswer.allShownAnswersAre9 == false` (any shown digit < '9') THEN опрос НЕ виден
  1099	44. [ ] GIVEN пользователь оценил урок THEN local флаг `lesson_rating_submitted_local` установлен; LessonRatingRepository.submit вызван 1 раз с lessonVersion из текущего Lesson
  1100	45. [ ] GIVEN result screen с `Lesson.top3` непустой THEN отображается секция Топ-3 с аватарками, никами и %; пустой → секция скрыта. **Note: top3 — закешированный server snapshot из Lesson document; текущая попытка пользователя ещё не успела попасть в top3 (server CF не выполнился; sync ещё не подтянул).** Список покажет state ДО этой попытки.
  1101	46. [ ] GIVEN top3 entry с avatarUrl=null или Coil не смог загрузить (offline / no cache) THEN placeholder вместо аватарки
  1102	
  1103	### HARD unlock checkbox
  1104	
  1105	47. [ ] GIVEN карточка урока в `LessonListComponent` THEN отображается `StarRating(rating = bestStars.rawTenths / 10f)` (existing API из `android/core/designsystem/.../StarRating.kt:99` принимает `rating: Float?`)
  1106	48. [ ] GIVEN `hardUnlocked == false` (нет EASY-попытки с allShownAnswersAre9) THEN HARD checkbox скрыт — независимо от bestStars value
  1107	49. [ ] GIVEN `hardUnlocked == true` THEN HARD checkbox visible; default unchecked. Логика: `hardUnlocked = attempts.any { it.mode == EASY && it.codeAnswer.allShownAnswersAre9 }`
  1108	
  1109	### Empty / error states
  1110	
  1111	50. [ ] GIVEN тап на урок с пустым eligibleQuestions(EASY) THEN empty state «В уроке пока нет вопросов» + кнопка «Назад»
  1112	51. [ ] GIVEN тап на урок с все payloads invalid THEN empty state аналогично
  1113	52. [ ] GIVEN single invalid payload среди валидных WHEN StartLessonAttemptUseCase THEN invalid отброшен на pipeline init; пользователь не видит; codeAnswer длина = только valid eligible
  1114	52a. [ ] GIVEN `LessonAttemptRepository.save` throws Room IO exception WHEN `CompleteAttemptUseCase` THEN `RunnerState.SaveFailed(attempt, error)`; result screen показывается с warning «Не удалось сохранить»; нет автоматического retry
  1115	52b. [ ] GIVEN `LessonRatingRepository.submit` throws WHEN user тапает submit rating THEN toast «Не удалось отправить оценку»; local флаг hasSubmitted остаётся false; user может попробовать снова
  1116	

exec
/bin/bash -lc "nl -ba docs/features/lesson-runner/0-spec.md | sed -n '140,160p'" in /home/Programming/Android/schoolquiz4.0
 succeeded in 0ms:
   140	#### Финальный экран результата
   141	
   142	30. **Содержимое экрана результата** (порядок сверху вниз):
   143	   1. **Большая цифра процентов** (`percentScore` крупным шрифтом, центр).
   144	   2. **Подпись** в зависимости от исхода:
   145	      - Впервые получил 2★ (perfect EASY): «Поздравляем! Сложные вопросы доступны».
   146	      - Впервые получил 3★ (perfect HARD): «100% сложные! Вы прошли урок полностью».
   147	      - Иначе — нейтрально (например «Урок завершён»).
   148	   3. **Звёзды** (`StarRating` с этой попытки stars value).
   149	   4. **Статистика** (краткая, своих попыток): `общее число попыток`, `средний %` по своим попыткам этого урока (читается из Room).
   150	   5. **Опрос «Оцените урок»** (1/2/3 целых звезды, не fractional) — показывается **только**: (а) если `attempt.codeAnswer.allShownAnswersAre9 == true` (perfect EASY ИЛИ perfect HARD), (б) если пользователь ещё не оценивал этот урок (нет местного флага). Один раз в жизни на (userId, lessonId). — [USER DECIDED]
   151	   6. **Топ-3** лучших участников этого урока (из `Lesson.top3` field, агрегируется на сервере). Каждый элемент: аватар (Coil URL — placeholder если не закеширован), nickname, percent. Если top3 пуст — секция скрыта.
   152	   7. **Кнопка «Завершить»** — если rating был выбран в опросе → вызов `SubmitLessonRatingUseCase.invoke(state.userId, lessonId, rating)`, сбрасывает FLAG_SECURE если был, возврат на список уроков (pop ChildStack до `LessonListComponent`).
   153	   — [USER DECIDED] полный список из user request.
   154	
   155	31. **Sworn-fold логика** (Matrix 6): см. требования #14-16.
   156	
   157	#### Server-side data model (для cascade sync infrastructure)
   158	
   159	32. **Новая Firestore коллекция `lesson_attempts/{attemptId}`** (sync logic — не наша фича, описывается контракт):
   160	   - `userId: String`

codex
**Findings**

**Realist**

- **BLOCKER**: MultipleChoice and Ordering submit drafts as final answers. Checkbox toggles and reorder buttons call `component.onAnswer(...)`, and `onAnswer` immediately scores and advances the pool, so users cannot build “all picked” / final ordering answers required by AC 8-12. See [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:197), [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:218), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:67), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1048).

- **BLOCKER**: FillBlank is explicitly unwired. The screen passes `candidates = emptyList()`, no-op selection handlers, and submits `FillBlankDraft(emptyMap())`, making AC 13-14 impossible through UI. See [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:245), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1053).

- **HIGH**: Result stars render historical best stars, not the current attempt’s stars. `buildResultUiState` stores `computeBestStars(userAttempts)` and `ResultContent` displays that value, while the spec requires “this attempt stars value.” See [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:224), [ResultContent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:61), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:148).

**Skeptic**

- **BLOCKER**: `doOnStop` treats rotation like Home/background. It auto-random scores the current question and marks paused on every stop, directly violating AC 35’s “current question + answers preserved” on configuration change. See [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:60), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:150), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1084).

- **HIGH**: Pause starts the next question’s deadline while the app is backgrounded. `autoAnswerOnTimeout` computes `deadlineMs = nowMs + duration`, then `onContinue` only clears `isPaused`; after a long background interval the next question immediately times out instead of showing a new timer. See [RunnerLogic.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:34), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:94), [QuestionProgressHeader.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt:39), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1082).

- **HIGH**: Save failure on abort is not surfaced as a result warning. `triggerAbort` emits `NavigateBack` even for `RunnerState.SaveFailed`, so AC 52a’s result screen warning is only implemented for complete attempts. See [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:197), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1114).

- **HIGH**: Rating submit failure disables retry. The UI sets `submitted = true` before repository result is known and never re-enables after `SaveRatingFailed`, contradicting AC 52b. See [RatingPromptSection.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt:52), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:134), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1115).

- **HIGH**: Process death restores `QuizzesConfig.LessonRunner` and starts a fresh attempt instead of returning to pre-attempt state. The stack serializes `LessonRunner`, and the recreated runner auto-starts from `Loading`, violating AC 36. See [DefaultQuizzesComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:49), [QuizzesConfig.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:35), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:62), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1085).

**Architect**

- **MEDIUM**: ADR-QS-16 scope is exceeded. The ADR limits quizzes-screen’s lesson-runner domain import to `LessonAttemptRepository`, but `DefaultLessonListComponent` imports `Attempt` plus domain logic functions directly. Direction is one-way, but the contract boundary widened beyond the accepted ADR. See [DefaultLessonListComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:16), ADR [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:581).

**Checked OK**

DI chain is present in the requested production order at [AppApplication.kt](/home/Programming/Android/schoolquiz4.0/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:118). `lessonRunnerDomainKoinAdapter` is in data/androidMain, `LessonRunnerRootComponent`/factory live in lesson-runner presentation, no production duplicate factory binding showed up, and I did not find a reverse lesson-runner → quizzes-screen import. I did not run the full Gradle gate; this is a static adversarial review.
hook: Stop
hook: Stop Completed
2026-04-27T23:09:23.321066Z ERROR codex_core::session: failed to record rollout items: thread 019dd12c-06d2-7252-b6c4-ed4a8072c885 not found
tokens used
238,836
**Findings**

**Realist**

- **BLOCKER**: MultipleChoice and Ordering submit drafts as final answers. Checkbox toggles and reorder buttons call `component.onAnswer(...)`, and `onAnswer` immediately scores and advances the pool, so users cannot build “all picked” / final ordering answers required by AC 8-12. See [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:197), [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:218), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:67), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1048).

- **BLOCKER**: FillBlank is explicitly unwired. The screen passes `candidates = emptyList()`, no-op selection handlers, and submits `FillBlankDraft(emptyMap())`, making AC 13-14 impossible through UI. See [LessonRunnerScreen.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt:245), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1053).

- **HIGH**: Result stars render historical best stars, not the current attempt’s stars. `buildResultUiState` stores `computeBestStars(userAttempts)` and `ResultContent` displays that value, while the spec requires “this attempt stars value.” See [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:224), [ResultContent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/ResultContent.kt:61), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:148).

**Skeptic**

- **BLOCKER**: `doOnStop` treats rotation like Home/background. It auto-random scores the current question and marks paused on every stop, directly violating AC 35’s “current question + answers preserved” on configuration change. See [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:60), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:150), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1084).

- **HIGH**: Pause starts the next question’s deadline while the app is backgrounded. `autoAnswerOnTimeout` computes `deadlineMs = nowMs + duration`, then `onContinue` only clears `isPaused`; after a long background interval the next question immediately times out instead of showing a new timer. See [RunnerLogic.kt](/home/Programming/Android/schoolquiz4.0/shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/RunnerLogic.kt:34), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:94), [QuestionProgressHeader.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionProgressHeader.kt:39), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1082).

- **HIGH**: Save failure on abort is not surfaced as a result warning. `triggerAbort` emits `NavigateBack` even for `RunnerState.SaveFailed`, so AC 52a’s result screen warning is only implemented for complete attempts. See [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:197), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1114).

- **HIGH**: Rating submit failure disables retry. The UI sets `submitted = true` before repository result is known and never re-enables after `SaveRatingFailed`, contradicting AC 52b. See [RatingPromptSection.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/RatingPromptSection.kt:52), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:134), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1115).

- **HIGH**: Process death restores `QuizzesConfig.LessonRunner` and starts a fresh attempt instead of returning to pre-attempt state. The stack serializes `LessonRunner`, and the recreated runner auto-starts from `Loading`, violating AC 36. See [DefaultQuizzesComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt:49), [QuizzesConfig.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt:35), [DefaultLessonRunnerRootComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt:62), spec [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/lesson-runner/0-spec.md:1085).

**Architect**

- **MEDIUM**: ADR-QS-16 scope is exceeded. The ADR limits quizzes-screen’s lesson-runner domain import to `LessonAttemptRepository`, but `DefaultLessonListComponent` imports `Attempt` plus domain logic functions directly. Direction is one-way, but the contract boundary widened beyond the accepted ADR. See [DefaultLessonListComponent.kt](/home/Programming/Android/schoolquiz4.0/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt:16), ADR [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:581).

**Checked OK**

DI chain is present in the requested production order at [AppApplication.kt](/home/Programming/Android/schoolquiz4.0/apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt:118). `lessonRunnerDomainKoinAdapter` is in data/androidMain, `LessonRunnerRootComponent`/factory live in lesson-runner presentation, no production duplicate factory binding showed up, and I did not find a reverse lesson-runner → quizzes-screen import. I did not run the full Gradle gate; this is a static adversarial review.
