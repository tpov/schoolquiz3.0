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

---

## ADR-LR-06: Difficulty @Serializable — QuizzesConfig.LessonRunner сериализация (BLOCKER #5)

**Status**: ACCEPTED — Grounding Problem 5

### Context

`QuizzesConfig` — `@Serializable sealed class`, сохраняется в Decompose StateKeeper при process death (ADR-QS-02 quizzes-screen). Новый вариант `QuizzesConfig.LessonRunner(mode: Difficulty)` требует, чтобы `Difficulty` был сериализуемым.

**Verified** `[shared/core/question-schema/src/commonMain/.../Difficulty.kt]`:
```kotlin
enum class Difficulty { EASY, HARD }
// БЕЗ @Serializable
```

При добавлении `mode: Difficulty` в `QuizzesConfig.LessonRunner` без `@Serializable` на `Difficulty` → `kotlinx.serialization` runtime exception при попытке сохранить ChildStack state.

### Decision

**Добавить `@Serializable` на `Difficulty`**:
```kotlin
@Serializable
enum class Difficulty { EASY, HARD }
```

1-line change в `shared/core/question-schema/src/commonMain/`. `Difficulty` — чистый enum без поведения, `@Serializable` не влияет на существующих потребителей (добавление аннотации backward compatible). `shared/core/question-schema` — shared core module, serialization annotations разрешены (исключение из `domain-models.md:35` применяется только к `shared/feature/*/domain/`).

Альтернативно, `QuizzesConfig.LessonRunner` может хранить `modeName: String` и конвертировать при создании компонента. Но это теряет type safety в config.

### Alternatives Considered

**Вариант B — `QuizzesConfig.LessonRunner(modeName: String)` вместо `mode: Difficulty`**:
- (-) Type safety теряется в config; опечатки в строке заметны только в runtime при конвертации
- (-) Нарушает консистентность с другими config полями которые используют типизированные значения где возможно
- (-) `DefaultQuizzesComponent.createChild` должен делать `Difficulty.valueOf(config.modeName)` с try/catch

### Consequences

- `Difficulty.kt` в `shared/core/question-schema` получает `@Serializable` annotation
- `QuizzesConfig.LessonRunner` может использовать `mode: Difficulty` напрямую
- `QuizzesConfigSerializationTest` расширяется с `LessonRunner` round-trip test
- Существующие consumers `Difficulty` (lesson-runner domain, question-schema, quiz-creation) — без изменений

### Risk if wrong (6 months out)

Если `kotlinx.serialization` меняет механизм сериализации enum (маловероятно), или `Difficulty` получает constructor parameter — `@Serializable` может потребовать custom serializer. Runtime failure при process-death restore — незаметный баг. Стоимость обнаружения: высокая. Митигация: `QuizzesConfigSerializationTest` round-trip test.

---

## ADR-LR-07: LessonPlaceholder replacement strategy — full atomic replacement

**Status**: ACCEPTED — Grounding Problem 6 (design-phase decision)

### Context

Spec `§37` и `0-spec.md §1`: заменить `QuizzesConfig.LessonPlaceholder` push на `QuizzesConfig.LessonRunner` в `DefaultLessonListComponent.kt:55`. `LessonPlaceholder` после замены становится мёртвым кодом.

Существующие компоненты:
- `LessonPlaceholderComponent` interface (`android/feature/quizzes-screen/presentation`)
- `DefaultLessonPlaceholderComponent`
- `LessonPlaceholderScreen`
- `LessonPlaceholderUiState`
- `FakeLessonPlaceholderComponent` (test-only)
- `LessonPlaceholderScreenTest`

Все затрагиваются при полной замене. `DefaultQuizzesComponent.createChild` — exhaustive `when` → compile-error-safe migration.

### Decision

**Полная атомарная замена** `LessonPlaceholder` → `LessonRunner`:

1. Удалить `QuizzesConfig.LessonPlaceholder` из sealed class → compile error в:
   - `DefaultQuizzesComponent.createChild` (`:117-138`) — replace case
   - `QuizzesChild.LessonPlaceholder` sealed variant — remove
   - `QuizzesScreen.kt` exhaustive `when` — remove branch
   - `DefaultLessonListComponent.onLessonClick` (`:55`) — update push call
2. Добавить `QuizzesConfig.LessonRunner` + `QuizzesChild.LessonRunner`
3. Удалить мёртвый код: `DefaultLessonPlaceholderComponent`, `LessonPlaceholderScreen`, `LessonPlaceholderUiState`, `FakeLessonPlaceholderComponent`, `LessonPlaceholderScreenTest`

Атомарная замена безопасна: нет сериализованных `LessonPlaceholder` config в production (feature новая; существующие пользователи не имеют saved state с этим config). Сериализованная обратная совместимость не нужна.

### Alternatives Considered

**Вариант B — Coexistence (LessonPlaceholder как fallback)**:
- (-) LessonPlaceholder в normal flow не используется → dead code в production codebase
- (-) Exhaustive `when` с мёртвой веткой → maintainability проблема
- (-) `QuizzesConfig.LessonPlaceholder` остаётся в serialized schema — future config schema changes сложнее
- (-) Нет реального usecase для fallback: если lesson-runner недоступен, тап на урок просто не должен работать

### Consequences

- `QuizzesConfig` sealed class: удаляется `LessonPlaceholder`, добавляется `LessonRunner(lessonId: String, mode: Difficulty, titles: List<String>)`
- `QuizzesChild` sealed interface: аналогично
- `DefaultQuizzesComponent.createChild`: update exhaustive when
- `DefaultLessonListComponent.onLessonClick`: `pushNew(QuizzesConfig.LessonRunner(...))`
- Удалённые файлы: `DefaultLessonPlaceholderComponent.kt`, `LessonPlaceholderScreen.kt`, `LessonPlaceholderUiState.kt`, `FakeLessonPlaceholderComponent.kt`, `LessonPlaceholderScreenTest.kt`
- Canonical `QuizzesConfig.LessonRunner` definition: `06-api-contract.md §LR-1`
- Обновление `docs/features/quizzes-screen/03-decisions.md`: добавить ADR-QS-15 (push consumer side) и ADR-QS-16 (LessonAttemptRepository import)

### Risk if wrong (6 months out)

Если lesson-runner реализация задерживается или отменяется — нет fallback: тап на урок не работает. Reversal = восстановить `LessonPlaceholder*` файлы из git history + откатить ADR-LR-07. Стоимость reversal: средняя, но git history доступен. Атомарная замена правильная ставка для новой фичи без production saved state.

### Producer/Consumer boundary — SUPERSEDED by ADR-LR-16

> ⚠️ Этот раздел описывал первоначальный подход через `android/core/navigation/`. Codex CLI plan-round-1 выявил, что этот подход создаёт circular Gradle dependency: `core/navigation → lesson-runner/presentation` (для RunnerUiState/RunnerEvent типов) + `lesson-runner/presentation → core/navigation` (для interface implementation). Cycle = build failure. Актуальное решение — **ADR-LR-16**: interface и factory живут в `lesson-runner/presentation`, прямой импорт из `quizzes-screen/presentation`.

---

## ADR-LR-16: LessonRunnerRootComponent interface location — lesson-runner/presentation

**Status**: ACCEPTED — устраняет circular Gradle dependency (Codex CLI plan-round-1 blocker, 2026-04-27)

### Context

`plan/phase-04/backend.md:18-44` содержал задачу поместить `LessonRunnerRootComponent` interface в `android/core/navigation/`. При анализе типов выявлена circular Gradle dependency:

```
core/navigation → lesson-runner/presentation   (RunnerUiState, RunnerEvent типы для interface)
lesson-runner/presentation → core/navigation   (interface implementation)
```

Bidirectional module dependency = Gradle build failure. Дополнительно, `clean-architecture.md` запрещает `core → feature/*` imports.

`LessonRunnerComponentFactory` fun interface с тем же планом в `core/navigation` имела идентичную проблему: return type `LessonRunnerRootComponent` обязывает `core/navigation → lesson-runner/presentation`.

### Decision

**Оба артефакта живут в `android/feature/lesson-runner/presentation/`**:

| Артефакт | File path |
|----------|-----------|
| `LessonRunnerRootComponent` interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt` |
| `LessonRunnerComponentFactory` fun interface | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerComponentFactory.kt` |

`quizzes-screen/presentation` импортирует оба напрямую. Направление строго одностороннее: `quizzes-screen/presentation → lesson-runner/presentation`. Reverse direction запрещён.

`android/core/navigation/` не получает новых файлов в рамках этой фичи.

Canonical signatures: `06-api-contract.md §LR-9` (interface) и `§LR-9a` (factory).

### Alternatives Considered

**Вариант A — core/navigation (изначальный план, ADR-LR-07 original section)**:
- (-) Создаёт cycle: `core/navigation → lesson-runner/presentation` + `lesson-runner/presentation → core/navigation`. Gradle build failure.
- (-) Нарушает `clean-architecture.md`: core не зависит от product features.
- Отклонён.

**Вариант B — Marker interface `LessonRunnerRoot` в core/navigation без presentation refs**:
- (+) `core/navigation` остаётся без dep на `lesson-runner/presentation`
- (-) `quizzes-screen/presentation` получает untyped marker — не может обращаться к `uiState`/`events` без downcast
- (-) Downcast в `QuizzesChild.LessonRunner` нарушает type safety; runtime ClassCastException при рефакторинге
- (-) `LessonRunnerScreen` composable всё равно ожидает typed `LessonRunnerRootComponent` — presenter слой не упрощается
- Отклонён.

**Вариант C — RunnerUiState/RunnerEvent в shared/core**:
- (-) Presentation state types (feature-specific sealed interface) в `shared/core` нарушают layer semantics
- (-) core не должен содержать feature-specific presentation state
- Отклонён.

### Consequences

- `quizzes-screen/presentation` Gradle module добавляет `:android:feature:lesson-runner:presentation` в deps (backend-dev scope, `build.gradle.kts`).
- `DefaultQuizzesComponent.createChild` для `QuizzesConfig.LessonRunner` вызывает `factory.create(ctx, lessonId, mode)` → возвращает typed `LessonRunnerRootComponent`.
- Koin: `LessonRunnerComponentFactory` single binding в `apps/android-next/AppApplication.kt` — canonical: `06-api-contract.md §LR-9a`.
- `android/core/navigation/` не затронут в этой фиче.
- Validation grep после phase-04 implementation: `rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src` — ожидается 1 match.

### Risk if wrong (6 months out)

Если появится второй consumer `lesson-runner/presentation` вне `quizzes-screen` — паттерн прямого импорта повторяется с новым ADR (per `clean-architecture.md` ChildStack Compose rendering exception — требует ADR). Если второй consumer в `android/core/` — оба артефакта переезжают в `core` и dep direction разворачивается. Стоимость reversal: средняя (2 файла + Gradle deps update + import changes в consumers). Текущее решение оптимально для одного consumer.

<!-- HL_SECTION_END -->

---

<!-- CMP_SECTION_START: ADR-LR-08..15 (architect-component writes here) -->

## ADR-LR-08 — KotlinxSerializationQuestionContentParser location

**Status**: Accepted  
**Context**: `StartLessonAttemptUseCase` зависит от `QuestionContentParser`. Production impl `KotlinxSerializationQuestionContentParser` не существует (Grounding Problem 1). Два кандидата: (A) `shared/core/question-schema/src/commonMain/` или (B) `shared/feature/lesson-runner/data/`.

**Decision**: **Option A** — `shared/core/question-schema/src/commonMain/…/KotlinxSerializationQuestionContentParser`

**Rationale**: Парсер — shared core infrastructure, не feature-specific data. `kotlinx.serialization` уже является зависимостью `shared/core/question-schema/build.gradle.kts:13`. Размещение в core: (1) позволяет другим фичам переиспользовать без транзитивной зависимости на lesson-runner/data; (2) следует паттерну "интерфейс и реализация в одном модуле" для утилитных core компонентов.

**Implementation notes**:
- `QuestionContent` sealed interface требует `@Serializable` annotation. Discriminator — default `"type"` ключ (без `@JsonClassDiscriminator`); per ADR-0003 source of truth (no custom discriminator annotation in Question hierarchy).
- Subclasses получают `@SerialName` matching simple class names: `"SingleChoice"`, `"MultipleChoice"`, `"Ordering"`, `"FillBlank"` — per ADR-0003 default kotlinx.serialization behaviour.
- Binding: `single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }` в новом `questionSchemaModule`.
- AC #54 (domain purity): `lesson-runner/domain` НЕ импортирует `kotlinx.serialization` напрямую — только через `QuestionContentParser` interface из question-schema.

**Alternatives Considered**:
- **Option B** (`lesson-runner/data`) — создаёт ненужную зависимость `data → question-schema` для любого consumer, которому нужен парсер. Отклонено.

### Risk if wrong (6 months out)

Если parser нужно переместить в `lesson-runner/data` (например специфическая логика только для этой фичи) — перемещение требует обновить Koin module (`questionSchemaModule` → `lessonRunnerDataModule`) и все tests. Умеренная стоимость. Если `Question` schema добавит 5-й тип — parser нужно обновить в `shared/core/question-schema/` с exhaustive `when`; compile-time catch гарантирован.

### Migration Plan (Phase-01 task)

Production не имеет реальных payload данных в старом формате. Существующие legacy fixtures — две позиции:
1. `scripts/seed-hierarchy.js:61` — переписать SingleChoice payload на новый JSON schema (per ADR-0003 Amendments A-D)
2. `shared/feature/question/domain/src/commonTest/.../QuestionDomainTest.kt:226` — обновить test fixture под новый `QuestionContent` sealed hierarchy

Никакого migration script для production DB не требуется.

---

## ADR-LR-09 — Koin lambda binding strategy для providers

**Status**: Accepted  
**Context**: `LessonRunnerDomainModule` использует `get()` для `() -> AttemptId`, `() -> Long` (randomSeedProvider), `(String, LessonId) -> RatingId`. Koin binding на function types имеет type erasure риск на JVM (все `Function1<*, *>` конфликтуют при resolution). Три варианта:
- A: `single<() -> AttemptId> { { AttemptId(UUID.randomUUID().toString()) } }` — type erasure риск
- B: параметр module-функции (pattern из `appShellDataModule`) — ограничивает re-use
- C: wrapper interfaces + адаптация в Koin factory

**Decision**: **Option C** — wrapper interfaces в `shared/feature/lesson-runner/domain/src/commonMain/…/provider/` (C1 fix: domain — единственный потребитель; interfaces принадлежат слою который их использует):

Canonical interface definitions — `06-api-contract.md §LR-13a`.

Default implementations (`DefaultAttemptIdProvider`, `DefaultRandomSeedProvider`, `DefaultRatingIdProvider`) — `shared/feature/lesson-runner/data/src/androidMain/…/provider/`.

Koin adapter (`lessonRunnerDomainKoinAdapter`) — `shared/feature/lesson-runner/data/src/androidMain/…/di/` (C1 fix: adapter в data, не в domain/androidMain):

```kotlin
// lessonRunnerDataModule (data/androidMain)
single<AttemptIdProvider> { DefaultAttemptIdProvider() }   // UUID.randomUUID()
single<RandomSeedProvider> { DefaultRandomSeedProvider() } // System.currentTimeMillis()
single<RatingIdProvider> { DefaultRatingIdProvider() }      // sha256("$userId:$lessonId")

// lessonRunnerDomainKoinAdapter (data/androidMain) — adapts interfaces to function types
factory<CompleteAttemptUseCase> {
    CompleteAttemptUseCase(…, attemptIdProvider = get<AttemptIdProvider>()::next)
}
```

**Rationale**: Нет type erasure, легко тестируется (fake реализации), explicit named types в Koin graph. Адаптация к function type происходит один раз в Koin factory — не затрагивает Walking Skeleton domain code (Invariant 6). Interfaces в domain (не data) следуют Dependency Inversion: domain определяет контракт, data предоставляет реализацию.

**Alternatives Considered**:
- **Option A** — type erasure: все `() -> X` получают erasure `Function0<*>`, первый registered "wins". Koin не поддерживает qualified binding по erasure без явного `named()`. Хрупко. Отклонено.
- **Option B** — module как функция с параметрами — работает для одного consumer, неудобен при нескольких фичах. Отклонено.

### Risk if wrong (6 months out)

Если другая фича нуждается в аналогичных providers — шаблон воспроизводится (не переиспользуется). Нет общего `CoreProvidersModule`. Если `AttemptIdProvider` нужен нескольким фичам — переехать в `shared/core/`. Стоимость: низкая (1 Gradle deps change + import update). Если domain use case изменит сигнатуру provider function — compile error при вызове Koin factory (catch at build time).

---

## ADR-LR-10 — AppDatabase migration strategy v3 → v4

**Status**: Accepted  
**Context**: Lesson-runner добавляет 2 новые таблицы + 3 новые колонки в `lessons`. AppDatabase v3 использует `fallbackToDestructiveMigration(dropAllTables = true)`. Без explicit Migration(3,4) upgrade уничтожит все user data (Grounding Problem 3).

**Decision**: **Option A** — написать настоящую `Migration(3, 4)` + `addTypeConverter`.

**Rationale**: (1) `room-database.md`: "Provide migration paths — don't rely on destructive migration in production". (2) User data в Room (catalogs, quests, sections, themes, lessons, questions, userStats) создаётся через дорогостоящий cascade sync. Уничтожать при каждом schema bump неприемлемо. (3) Новые таблицы через `CREATE TABLE IF NOT EXISTS` + колонки через `ALTER TABLE` — стандартные SQLite операции; существующие строки в `lessons` получат корректные DEFAULT значения.

**TypeConverters**: `DifficultyConverter` и `TopParticipantListConverter` оба `@ProvidedTypeConverter` + `.addTypeConverter()` в Room builder (паттерн из `StringSetConverter` / `PersistenceModule.kt:24`).

**Risk Mitigations** (C14: удалён claim "нулевой риск"):
- `fallbackToDestructiveMigration` **должен быть удалён** из prod build до v4 release; debug build может оставить (MT-06 test).
- `ALTER TABLE … ADD COLUMN` — irreversible в SQLite; downgrade path = отдельная Migration(4,3) если нужна; для v4 upgrade downgrade не планируется.
- `TopParticipantListConverter.fromDb` защищён try/catch → malformed JSON → emptyList() вместо crash.

**Alternatives Considered**:
- **Option B** (продолжать с destructive migration) — уничтожает sync-загруженный контент при каждом schema bump. Неприемлемо для production. Допустимо только до первого production release при явном документировании в phase plan.

### Risk if wrong (6 months out)

Если Migration(3,4) содержит ошибку в SQL — пользователи с v3→v4 upgrade получат crash (`IllegalStateException: Migration didn't properly handle`). Митигация: MT-01..MT-05 тесты перехватывают до release. Если в prod уже есть `fallbackToDestructiveMigration` (текущий AppDatabase v3) — нужно проверить что его убрали перед release v4 (MT-06).

---

## ADR-LR-11 — Компонент для lesson card с StarRating + HARD checkbox

**Status**: Accepted  
**Context**: Spec AC #47-49 требует на карточке урока: `StarRating` + conditional `Checkbox`. `HierarchyItemCard` (`android/core/designsystem/.../HierarchyItemCard.kt:34`) не имеет Checkbox slot. Три варианта:
- A: добавить `trailing: @Composable () -> Unit` в `HierarchyItemCard` — затронет все 5 drill-down screens
- B: новый `LessonItemCard` в `quizzes-screen/presentation` — изолированное изменение
- C: добавить `bestStars: Float?, hardUnlocked: Boolean, isHardChecked: Boolean` напрямую в `HierarchyItemCard`

**Decision**: **Option B** — `LessonItemCard` в `android/feature/quizzes-screen/presentation`.

**Rationale**: (1) Lesson card — единственный уровень drill-down с gameplay state. Section/Theme/Quest cards — чистые navigation items без game state. (2) Designsystem не должен знать о gameplay-специфичных концепциях (hardUnlocked, Stars). (3) Изолирует изменение от Section/Theme/Quest screens.

**LessonItemCard location**: `android/feature/quizzes-screen/presentation/src/main/…/components/LessonItemCard.kt`  
**LessonItemUi** (новый): `LessonItemUi(id, title, orderLabel: String? = null, subtitleCount: String? = null, bestStarsRawTenths: Int = 0, hardUnlocked: Boolean = false, isHardChecked: Boolean = false)`
(Canonical SSoT: `06-api-contract.md §LR-12`. `orderLabel` nullable per `HierarchyItemUi.kt:6`. `bestStarsRawTenths` per `Stars(rawTenths: Int)` domain type.)

**Alternatives Considered**:
- **Option A** (trailing slot) — generic, но затрагивает 5 screens для добавления empty trailing. "Don't add features beyond what the task requires". Отклонено.
- **Option C** (gameplay params в HierarchyItemCard) — нарушает single-responsibility designsystem компонента. Отклонено.

### Risk if wrong (6 months out)

Если `LessonItemCard` нужен в другом feature module — придётся либо переносить в `android/core/designsystem` (затрагивает gameplay-concept), либо дублировать. Пока lesson card — единственный потребитель, изоляция в `quizzes-screen/presentation` правильная. Стоимость reversal: средняя (перемещение + обновление imports).

---

## ADR-LR-12 — Унификация поля `value` vs `raw` в value classes

**Status**: Accepted  
**Context**: Проект использует `value` как имя поля для value classes (`LessonId.value`, `QuestionId.value`, etc.). Walking Skeleton создал `AttemptId.raw` и `RatingId.raw` — inconsistency (Grounding Problem 10, `AttemptId.kt:4`, `RatingId.kt:4`).

**Decision**: Переименовать `AttemptId.raw → AttemptId.value` и `RatingId.raw → RatingId.value`.

**Rationale**: Consistency с project-wide convention. Domain code ещё не интегрирован — низкая стоимость rename. Spec opечатка `sourceId.raw` (line 100) устраняется de facto.

**Impact**: ~10 файлов Walking Skeleton (domain models + use cases + tests). Compile-guided refactor.

**Compile gate** (C13): `./gradlew :shared:feature:lesson-runner:domain:jvmTest` должен быть зелёным после rename. Если тесты ломаются — rename не завершён (не "почти готово").

**Test churn specifics** (C13): файлы, которые потребуют изменений:
- `AttemptId.kt` — rename field
- `RatingId.kt` — rename field
- `Attempt.kt` — использует `AttemptId.raw` в toString/logging если есть
- `CompleteAttemptUseCase.kt`, `AbortAttemptUseCase.kt` — `AttemptId(raw = ...)` → `AttemptId(value = ...)`
- `SubmitLessonRatingUseCase.kt` — `RatingId(raw = ...)` → `RatingId(value = ...)`
- All domain tests using `AttemptId("x").raw` → `.value`
- `08-storage-model.md §LR-3` ссылается на `Attempt.id.value` — consistent после rename ✓

**Note**: `RunnerLogic.kt:29` использует `state.codeAnswer.raw` — поле `CodeAnswer.raw` НЕ переименовывается (это не ID-поле, это семантически "raw string content"). Переименовываются только identity value classes `AttemptId` и `RatingId`.

**Spec typo** (C13): `0-spec.md:100` пишет `sourceId.raw` — это spec-опечатка; после rename фактически будет `sourceId.value`. Spec не обновляется (minor typo), implementation следует renamed field.

**Alternatives Considered**:
- Оставить `raw` — накапливает naming debt; contributors путаются. Отклонено.

### Risk if wrong (6 months out)

Если `value` конфликтует с будущей языковой фичей Kotlin (маловероятно) — потребуется повторный rename. Spec опечатка `sourceId.raw` (line 100) останется в документе но не в коде; не блокер, фиксируется как known typo. Если Walking Skeleton тесты не проходят после rename — release блокирован (compile gate гарантирует).

---

## ADR-LR-13 — `lastModifiedAt` в Attempt для Firestore sync

**Status**: Accepted  
**Context**: Spec §32 требует `lastModifiedAt: serverTimestamp` в Firestore document `lesson_attempts`. Domain `Attempt` имеет `completedAt: Long` (client clock), но не `lastModifiedAt`. Cascade sync (out of scope) использует cursor на `lastModifiedAt`.

**Decision**: **Option B** — sync writer добавляет `FieldValue.serverTimestamp()` как отдельное Firestore-специфичное поле при записи в Firestore.

**Rationale**: Server-side timestamp — единственный надёжный источник для cursor sync. Device clock skew создаёт sync gaps. Разделение: `completedAt` (когда пользователь завершил попытку) vs `lastModifiedAt` (когда Firestore document создан на сервере). Паттерн аналогичен существующему `CascadingSyncOrchestrator` использованию `FieldValue.serverTimestamp()`.

**Note**: Это фиксирует контракт для out-of-scope cascade sync implementation. Наша фича хранит только `completedAt` в Room `Attempt`.

**Alternatives Considered**:
- **Option A** (`lastModifiedAt = completedAt` клиентски) — device clock skew создаёт sync gaps. Отклонено для production надёжности.

### Risk if wrong (6 months out)

Если cascade sync реализация (out of scope) использует другой cursor field — `lastModifiedAt` контракт нужно пересогласовать с server team. Стоимость: сервер-side change + sync test update. Контракт зафиксирован в `06-api-contract.md §LR-3` для server team review.

---

## ADR-LR-14 — Формализация ADR-0003 Amendments A-D

**Status**: Accepted  
**Context**: Spec `0-spec.md §1171-1192` описывает 4 обязательных поправки к `docs/architecture/0003-question-schema.md`. Без формальной фиксации — явное противоречие с architectural lock.

**Decision**: Применить все 4 amendments к `docs/architecture/0003-question-schema.md` в рамках design phase.

**Amendment A** (к `0003-question-schema.md:122-125`):
> **Amendment 2026-04-26 (lesson-runner)**: Прохождение EASY НЕ прерывается на ошибке или таймауте — продолжается до конца pool. Звёзды считаются по итоговому `percentScore`. Прерывание, упомянутое в первоначальном тексте, — отменено.

**Amendment B** (к `0003-question-schema.md:128`):
> **Amendment 2026-04-26 (lesson-runner)**: На EASY и HARD — после ответа сразу переход к следующему вопросу, без раскрытия правильного ответа. Feedback — отдельная фича.

**Amendment C** (к `0003-question-schema.md:30,102`):
> **Amendment 2026-04-26 (lesson-runner)**: `timeLimitSec` **может остаться в payload** для backward compatibility, но **runtime игнорирует его** в пользу формулы `seconds = max(5, round(charsCount × k))`. EASY k≈0.18, HARD k≈0.12.

**Amendment D** (к `0003-question-schema.md:118`):
> **Amendment 2026-04-26 (lesson-runner)**: Reference `shared/feature/quiz/domain` заменяется на `shared/feature/lesson-runner/domain` — фактический module для runtime gameplay.

**Alternatives Considered**: Оставить ADR-0003 без изменений — явное противоречие с реализацией; blocker для architect-reviewer. Отклонено.

### Risk if wrong (6 months out)

Если Amendment C (игнорирование `timeLimitSec`) потребует reversal (например новые question types хотят custom timer) — потребуется Amendment E к ADR-0003 + обновление `RunnerLogic.computeTimer`. Стоимость: низкая (1 function change + tests). Amendment B (отмена обучающего feedback) вынесена в отдельную фичу — риск feature scope creep при реализации.

---

## ADR-LR-15 — Lesson rating fields naming и типы

**Status**: Accepted (user resolution 2026-04-26 — Option B approved; spec amendment applied в `0-spec.md §34`)
**Context**: Spec `0-spec.md §34` использует `ratingCount: Int?` (nullable). Quest precedent: `Quest.averageRating: Float?`, `Quest.averageRatingCount: Int` (non-nullable). Inconsistency требует resolve.

**Decision**: Align с Quest pattern:
- `Lesson.averageRating: Float?` — nullable (absent until first server aggregate)
- `Lesson.ratingCount: Int = 0` — **non-nullable**, default 0
- `Lesson.top3: List<TopParticipant> = emptyList()` — non-nullable, default empty

**Rationale**: (1) Consistency с `Quest.averageRatingCount: Int` (`shared/feature/quest/domain/.../model/Quest.kt:62,69`). (2) `ratingCount` всегда имеет смысловой default 0. Nullable Int требовал бы везде null-check без смысловой ценности. (3) `top3` empty list — natural default (нет участников). (4) Firebase backward compat: missing field defaults via DTO `@SerialName` + Kotlin defaults.

**LessonEntity column**: `ratingCount INTEGER NOT NULL DEFAULT 0` — `@ColumnInfo(defaultValue = "0")`.

**Alternatives Considered**:
- `ratingCount: Int?` — создаёт null-check noise в presentation. Spec nullable — опечатка vs user intent "нет оценок = 0". Отклонено.

### Risk if wrong (6 months out)

Если Cloud Function записывает `ratingCount: null` в Firestore (например баг CF) — DTO mapper должен иметь `?: 0` default для `ratingCount`; без этого `LessonEntity.ratingCount NOT NULL` вызовет crash при десериализации. Митигация: DTO mapper добавляет `ratingCount = dto.ratingCount ?: 0` (defensive default). Если `averageRating: Float?` становится non-nullable позже — breaking change только в DTO/presentation layer, domain модель остаётся совместимой.

---

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
  ```

### Risk if wrong (6 months out)

Очень низкий. Паттерн установлен как project precedent (`AppShellScreen.kt:53-56`) и широко используется в Decompose ecosystem. Если `LessonRunnerScreen` переедет или переименуется — compile error при сборке, не runtime regression. Если bidirectional coupling проникнет — grep check в architect-reviewer выявит немедленно. Если slot pattern станет необходимым позже — стоимость migration низкая (1 composable wrapper + registration).

<!-- CMP_SECTION_END -->

---

## ADR-LR-18 — DifficultyConverter удалён: mapper-based conversion вместо TypeConverter

**Status**: ACCEPTED — Phase-02 runtime finding (2026-04-27)

### Context

Phase-02 добавил `DifficultyConverter` (`@ProvidedTypeConverter`) для конвертации `Difficulty ↔ String` в Room. После реализации выяснилось, что ни одна Entity не хранит поле типа `Difficulty` напрямую — `LessonAttemptEntity.isHard: Int` (spec-mandated primitive). Room KMP 2.7+ `validateTypeConverters()` отклоняет любой `@ProvidedTypeConverter`, тип которого не используется ни одной Entity, выбрасывая `IllegalArgumentException: Unexpected type converter` при открытии БД.

Verified: `LessonAttemptEntity.isHard` — `@ColumnInfo(name="is_hard") val isHard: Int` (spec AC#52 mandates Int for safer migration validation). Converter на `Difficulty` не имеет Entity-binding.

### Decision

**Удалить `DifficultyConverter.kt` полностью.** Конвертация `Difficulty ↔ Int` выполняется в mapper слое:

```kotlin
// LessonAttemptMapper (data layer)
isHard = if (domain.mode == Difficulty.HARD) 1 else 0
// fromEntity:
mode = if (entity.isHard == 1) Difficulty.HARD else Difficulty.EASY
```

`DifficultyConverter` не регистрируется ни в `@TypeConverters`, ни в Room builder. `TypeConvertersPhase02Test.kt` MT-05 тесты удалены.

Plan invariant `overview.md §Pattern Invariants` строка про `DifficultyConverter` — superseded этим ADR.

### Alternatives Considered

- **Оставить DifficultyConverter + изменить Entity на `Difficulty` тип** — нарушает spec AC#52 ("isHard: Int для safer migration validation"). Отклонено: spec mandates Int.
- **Оставить converter, зарегистрировать без Entity use** — Room KMP 2.7+ выбрасывает `IllegalArgumentException` при открытии БД. Технически невозможно.

### Consequences

- `DifficultyConverter.kt` удалён
- `AppDatabase.@TypeConverters` содержит только `StringSetConverter::class, TopParticipantListConverter::class`
- `PersistenceModule.kt` Room builder содержит только `.addTypeConverter(StringSetConverter())` и `.addTypeConverter(TopParticipantListConverter())`
- `TypeConvertersPhase02Test` MT-05 тесты (`difficultyConverter EASY/HARD roundtrip`) удалены
- Mapper `LessonAttemptMapper` обрабатывает `Difficulty ↔ Int` через условный маппинг

### Risk if wrong (6 months out)

Если `Difficulty` когда-либо нужно хранить как строку в Entity — добавить новый `@ProvidedTypeConverter` с Entity полем. Стоимость: низкая (1 converter file + 1 Entity field change). Текущий mapper-based подход — стандартный паттерн для примитивных представлений domain enum.

## ADR-LR-19 — Phase-04: RunnerUiState.Result flat projection (security)

Status: Accepted
Date: 2026-04-28

### Decision

`RunnerUiState.Result` использует flat-поля (`percentScore`, `mode`, `completedAt`, `hardUnlocked`, `bestStarsRawTenths`) вместо `attempt: Attempt` aggregate из `06-api-contract.md:408`.

### Rationale

Phase-04 security review (task #23) обнаружил что `attempt: Attempt` в публичном `StateFlow<RunnerUiState>` содержит PII: `userId`, `codeAnswer`, `attemptId`. Flat projection — security best practice (minimal exposure): UI получает только поля, необходимые для рендеринга, без доступа к идентификаторам пользователя и raw code answers.

Дополнительно: `hardUnlocked` и `bestStarsRawTenths` отсутствуют в domain `Attempt` — flat-подход единственно возможный без изменения domain модели. Phase-04 code-review (task #22) принял реализацию. Тесты PT-01..PT-41 зелёные.

### Supersedes

- `06-api-contract.md:408` — spec `data class Result(val attempt: Attempt, ...)` (добавить note там)
- `plan/phase-05/frontend.md` Pattern Invariant строки про `state.attempt.mode` и `state.attempt.percentScore`

### Consequences

- `RunnerUiState.Result` имеет flat-поля; Phase-05 UI обращается к `state.mode`, `state.percentScore.raw` напрямую
- Phase-06 и последующие фазы используют flat-поля `RunnerUiState.Result` — не `attempt: Attempt`
- `Attempt` domain aggregate остаётся неизменным; в `StateFlow` не передаётся

### Alternatives Considered

- **Вернуть `attempt: Attempt`** — невозможно без раскрытия PII полей (userId, codeAnswer) в публичном StateFlow. Отклонено: противоречит security finding #23.
- **Добавить separate `hardUnlocked: Boolean` + `bestStarsRawTenths: Int` рядом с `attempt`** — создаёт hybrid сигнатуру, сложнее чем full flat. Отклонено: flat проще и safer.

## ADR-LR-20 — LessonRunnerComponentFactory binding location: lessonRunnerPresentationModule (Phase-04 accepted deviation)

Status: Accepted
Date: 2026-04-28

### Decision

`single<LessonRunnerComponentFactory>` binding живёт в `lessonRunnerPresentationModule` (`android/feature/lesson-runner/presentation/src/main/.../di/LessonRunnerPresentationModule.kt:37`), а **не** inline в `AppApplication.kt startKoin` как предписывалось `06-api-contract.md:374`.

### Rationale

Phase-04 HIGH finding (task #21): binding добавлен в `lessonRunnerPresentationModule` в рамках fix loop. Binding корректен: при регистрации `lessonRunnerPresentationModule` в composition root factory автоматически доступна через Koin graph. Поведение идентично spec — `NoBeanDefinitionFoundException` при навигации на LessonRunner не возникает.

Phase-07 backend-dev корректно **не дублировал** binding в `AppApplication.kt` (`di-patterns.md`: "No duplicate `single<T>` for the same production type").

### Consequences

- `06-api-contract.md:374` spec binding — документально отклонён в пользу module-encapsulated подхода
- `AppApplication.kt` регистрирует `lessonRunnerPresentationModule` → factory доступна транзитивно
- Будущие разработчики **не должны** добавлять `single<LessonRunnerComponentFactory>` в `AppApplication.kt` — это создаст Koin duplicate binding exception

### Supersedes

- `06-api-contract.md:374-381` — inline binding в composition root (заменяется этим ADR)
