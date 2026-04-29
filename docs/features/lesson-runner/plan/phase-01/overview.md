---
phase: phase-01
name: Foundation & Core Types
tag: complex
date: 2026-04-27
---

# Phase 01 — Foundation & Core Types

## Goal

Разрешить все structural BLOCKERS до integration work: создать `shared/core/leaderboard/` для `TopParticipant`, добавить `@Serializable` на `Difficulty` и `QuestionContent`, создать `KotlinxSerializationQuestionContentParser`, переименовать `AttemptId.raw→.value` / `RatingId.raw→.value`, расширить `Lesson` domain моделью тремя новыми полями. После фазы все core types и cross-feature contracts находятся на месте; фаза-02 (persistence) и фаза-03 (data) могут стартовать параллельно.

## Scope

- Новый Gradle-модуль `shared/core/leaderboard/` с `TopParticipant`
- `shared/core/question-schema/`: `@Serializable` на `QuestionContent` + subclasses + `Difficulty`; `KotlinxSerializationQuestionContentParser`; новый Koin `questionSchemaModule`
- `shared/feature/lesson-runner/domain/`: переименование `AttemptId.raw→value`, `RatingId.raw→value`; обновление import `TopParticipant` (из core/leaderboard вместо local)
- `shared/feature/lesson/domain/`: `Lesson` + три новых поля (`averageRating`, `ratingCount`, `top3`); `lesson/domain/build.gradle.kts` + `:shared:core:leaderboard` dep
- Обновление Walking Skeleton domain tests после rename (compile gate)

## Role Inputs

- `backend.md` — Yes
- `frontend.md` — No (нет UI-изменений)
- `tests.md` — Yes

## Layer

`shared/core/leaderboard` (new core), `shared/core/question-schema` (core modified), `shared/feature/lesson-runner/domain` (domain modified — rename only), `shared/feature/lesson/domain` (domain modified)

## Review Tags

`architecture-review` (новый cross-core module, bidirectional coupling resolution)

## State Matrix Coverage

Matrix rows: не покрывает gameplay matrices (1-8) — фаза закладывает core types для них. Domain contract types готовы после этой фазы.

## Domain Contract Coverage

Фаза реализует структурные prerequisites для домена:
- `TopParticipant` — canonical type per `06-api-contract.md:112` (§LR-5a)
- `Lesson.top3`, `Lesson.averageRating`, `Lesson.ratingCount` — per `06-api-contract.md:129` (§LR-5)
- `Difficulty @Serializable` — per ADR-LR-06
- `QuestionContent @Serializable` + parser — per ADR-LR-08
- `AttemptId.value`, `RatingId.value` — per ADR-LR-12

Walking Skeleton domain (`shared/feature/lesson-runner/domain/`) **НЕ переписывается**. Разрешены только: (а) переименование `raw→value` в `AttemptId.kt` и `RatingId.kt`; (б) обновление import `TopParticipant` с local path на `shared/core/leaderboard`; (в) удаление локального `TopParticipant.kt` из domain; (г) создание трёх provider interfaces в новом `domain/provider/` package (`AttemptIdProvider`, `RandomSeedProvider`, `RatingIdProvider` — extension, не modification existing Walking Skeleton types). Всё остальное domain (RunnerState, RunnerLogic, use cases, fakes) — NOT MODIFY.

**BLOCKER 4 resolution**: Provider interfaces (`AttemptIdProvider`, `RandomSeedProvider`, `RatingIdProvider`) создаются в domain в phase-01 (не в phase-03), потому что Walking Skeleton use case конструкторы уже используют `() -> AttemptId` lambda types. `DefaultAttemptIdProvider : AttemptIdProvider` (phase-03) должен реализовывать interface, которое к тому моменту существует в domain. Если переносить создание interface в phase-03 — use cases в Walking Skeleton (`StartLessonAttemptUseCase`, `CompleteAttemptUseCase`, `AbortAttemptUseCase`, `SubmitLessonRatingUseCase`) потребуют изменения сигнатур, что нарушает "NOT MODIFY" Walking Skeleton rule. Interfaces в `provider/` — это добавление, не изменение.

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 1: Production `QuestionContentParser` отсутствует | `shared/core/question-schema` — backend-dev | `StartLessonAttemptUseCase.kt:30` — `parser: QuestionContentParser`; `LessonRunnerDomainModule.kt:25` — `get<QuestionContentParser>()` | `kotlinx.serialization` уже в `build.gradle.kts:13`; domain не импортирует serialization напрямую (AC #54) | Создать `KotlinxSerializationQuestionContentParser` в `shared/core/question-schema/commonMain`; добавить `@Serializable` на `QuestionContent`; зарегистрировать в `questionSchemaModule` | Round-trip JVM test (IT-09f proxy); `./gradlew :shared:core:question-schema:jvmTest` |
| Problem 2: `Lesson.top3` создаёт bidirectional coupling | `shared/feature/lesson/domain` + `shared/feature/lesson-runner/domain` — backend-dev | `Lesson.kt:15`; `TopParticipant.kt:3` в lesson-runner/domain | bidirectional import blocker per Invariant 3; `rg "^import .*lesson_runner" shared/feature/lesson/domain/` должно быть пустым | Создать `shared/core/leaderboard/TopParticipant.kt`; обновить imports в lesson-runner/domain; добавить dep в lesson/domain/build.gradle.kts | `rg "^import .*lesson_runner" shared/feature/lesson/domain/` — пусто; `./gradlew :shared:feature:lesson:domain:jvmTest` зелёный |
| Problem 5: `Difficulty` not `@Serializable` | `shared/core/question-schema` — backend-dev | `Difficulty.kt`; `QuizzesConfig.kt:5-6` | enum backward-compatible change | Добавить `@Serializable` на `Difficulty` enum | `QuizzesConfigSerializationTest` round-trip с `LessonRunner` вариантом |
| Problem 6: Cross-feature ADRs отсутствовали | Resolved в design phase (ADRs LR-01..LR-07 + QS-15/16 созданы в 03-decisions.md) | `03-decisions.md` — created | Architect-reviewer grep: no undocumented cross-feature imports | ADRs зафиксированы | `rg "^import .*shared\.feature\." shared/core android/core` — пусто |
| Problem 10: `value` vs `raw` naming | `shared/feature/lesson-runner/domain` — backend-dev | `AttemptId.kt:4`, `RatingId.kt:4`; ~10 Walking Skeleton files | compile-guided rename; только AttemptId + RatingId (не CodeAnswer.raw) | Переименовать `AttemptId.raw→value`, `RatingId.raw→value`; compile-gate | `./gradlew :shared:feature:lesson-runner:domain:jvmTest` зелёный |

## Files

### New Files

- `shared/core/leaderboard/build.gradle.kts`
- `shared/core/leaderboard/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/leaderboard/TopParticipant.kt`
- `shared/core/question-schema/src/commonMain/kotlin/.../KotlinxSerializationQuestionContentParser.kt`
- `shared/core/question-schema/src/androidMain/kotlin/.../di/QuestionSchemaModule.kt`
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../provider/AttemptIdProvider.kt` (BLOCKER 4 fix)
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../provider/RandomSeedProvider.kt` (BLOCKER 4 fix)
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../provider/RatingIdProvider.kt` (BLOCKER 4 fix)

### Modified Files

- `shared/core/question-schema/src/commonMain/kotlin/.../QuestionContent.kt` — добавить `@Serializable`, `@SerialName` на subclasses
- `shared/core/question-schema/src/commonMain/kotlin/.../Difficulty.kt` — добавить `@Serializable`
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../model/AttemptId.kt` — rename `raw→value`
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../model/RatingId.kt` — rename `raw→value`
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../model/TopParticipant.kt` — DELETE (переехал в core/leaderboard)
- `shared/feature/lesson-runner/domain/build.gradle.kts` — add `:shared:core:leaderboard` dep, remove local TopParticipant
- `shared/feature/lesson/domain/src/commonMain/kotlin/.../model/Lesson.kt` — add 3 fields
- `shared/feature/lesson/domain/build.gradle.kts` — add `:shared:core:leaderboard` dep
- Walking Skeleton use cases + tests: обновить `.raw` → `.value` и import `TopParticipant` (~10 файлов compile-guided)
- `settings.gradle.kts` — include `:shared:core:leaderboard` (backend-dev owned)

### Deleted Files

- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/.../model/TopParticipant.kt`

## Dependencies

- Phase-01 не зависит ни от какой другой фазы — это foundation
- Phase-02 (persistence) и Phase-03 (data) зависят от phase-01 (нужны `TopParticipant`, `Lesson` с новыми полями, `QuestionContent @Serializable`)

## Criteria for Acceptance

1. `shared/core/leaderboard/` собирается; `TopParticipant @Serializable` доступен из `lesson:domain` и `lesson-runner:domain` без bidirectional coupling
2. `rg "^import .*lesson_runner" shared/feature/lesson/domain/` — пусто
3. `Difficulty @Serializable` — `QuizzesConfigSerializationTest` не ломается (round-trip с LessonRunner config)
4. `KotlinxSerializationQuestionContentParser` парсит все 4 subtype JSON (round-trip тест)
5. `AttemptId.value` и `RatingId.value` — `./gradlew :shared:feature:lesson-runner:domain:jvmTest` зелёный (89 тестов)
6. `Lesson` компилируется с новыми полями; `./gradlew :shared:feature:lesson:domain:jvmTest` зелёный
7. `lesson-runner:domain` не импортирует `kotlinx.serialization` напрямую (AC #54)

## Tests Required

- `parser_roundtrip_singleChoice`: given JSON `{"type":"SingleChoice", "options":[...]}`, when `KotlinxSerializationQuestionContentParser.parse()` called, then `Result.success(QuestionContent.SingleChoice)` with correct fields
- `parser_roundtrip_multipleChoice`: given JSON `{"type":"MultipleChoice", ...}`, when `parse()` called, then `Result.success(QuestionContent.MultipleChoice)`
- `parser_roundtrip_ordering`: given JSON `{"type":"Ordering", ...}`, when `parse()` called, then `Result.success(QuestionContent.Ordering)`
- `parser_roundtrip_fillBlank`: given JSON `{"type":"FillBlank", ...}`, when `parse()` called, then `Result.success(QuestionContent.FillBlank)`
- `parser_unknownType_returnsFailure`: given JSON `{"type":"Unknown","dummy":"x"}`, when `parse()` called, then `Result.isFailure == true`
- `parser_emptyString_returnsFailure`: given empty string `""`, when `parse()` called, then `Result.isFailure == true`
- `attemptId_rename_value`: given `AttemptId("abc")`, when `.value` accessed, then returns `"abc"` (compile check: `.raw` doesn't exist)
- `ratingId_rename_value`: given `RatingId("def")`, when `.value` accessed, then returns `"def"`
- `difficulty_serializable_hard`: given `Difficulty.HARD`, when `Json.encodeToString(Difficulty.serializer(), Difficulty.HARD)`, then encoded string contains `"HARD"`
- `difficulty_serializable_roundtrip`: given `Difficulty.EASY`, when encode then decode, then `Difficulty.EASY`
- `topParticipant_noImportFromLessonRunnerInLessonDomain`: given lesson/domain module, when grep `^import .*lesson_runner`, then empty output (structural grep check)
- `lesson_newFields_defaults`: given `Lesson(...)` created without rating fields, when `averageRating`, `ratingCount`, `top3` accessed, then `null`, `0`, `emptyList()` respectively
- `attemptIdProvider_interface_exists`: given `AttemptIdProvider` domain interface, when lambda adaptation `get<AttemptIdProvider>()::next`, then type matches `() -> AttemptId`
- `ratingIdProvider_interface_exists`: given `RatingIdProvider` domain interface, when lambda adaptation `get<RatingIdProvider>()::provide`, then type matches `(String, LessonId) -> RatingId`

## Validation

```bash
./gradlew :shared:core:leaderboard:jvmTest --no-configuration-cache
./gradlew :shared:core:question-schema:jvmTest --no-configuration-cache
./gradlew :shared:feature:lesson-runner:domain:jvmTest --no-configuration-cache
./gradlew :shared:feature:lesson:domain:jvmTest --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# Bidirectional coupling check:
rg "^import .*lesson_runner" shared/feature/lesson/domain/ -g "*.kt"
# Expected: empty
# Domain purity check:
rg "^import (kotlinx\.serialization)" shared/feature/lesson-runner/domain/src/commonMain -g "*.kt"
# Expected: empty
```

## Handoff Notes

После phase-01:
- Phase-02 (persistence) может использовать `TopParticipant` для `TopParticipantListConverter` и `Lesson` с новыми полями для mapper chain
- Phase-03 (data) может создавать `lessonRunnerDataModule` с `LessonAttemptRepositoryImpl`
- Phase-06 (quizzes-screen) может использовать `Difficulty @Serializable` для `QuizzesConfig.LessonRunner`
- 7 Walking Skeleton fakes остаются без изменений — Phase-03/04/05 tests используют их напрямую

## Pattern Invariants

- `TopParticipant` ДОЛЖЕН быть в `shared/core/leaderboard/` — НЕ в `shared/feature/lesson-runner/domain/`
- `@Serializable @SerialName` на `QuestionContent` subclasses ДОЛЖНЫ использовать discriminator field `"type"` (без `@JsonClassDiscriminator` кастомного) — per ADR-LR-08: default kotlinx.serialization discriminator behaviour
- `shared/feature/lesson-runner/domain` ДОЛЖЕН импортировать `TopParticipant` из `shared/core/leaderboard`, НЕ из local package
- `AttemptId` и `RatingId` ДОЛЖНЫ использовать `.value` — `CodeAnswer.raw` НЕ переименовывается (не ID-поле по смыслу)
- Новый Koin module `questionSchemaModule` ДОЛЖЕН жить в `shared/core/question-schema/src/androidMain/` — НЕ в lesson-runner data (per ADR-LR-08)

## Options Considered

| Критерий | Option A: `TopParticipant` в `shared/core/leaderboard/` (recommended) | Option B: `Lesson.top3: List<String>` (JSON-encoded) | Option C: Отдельный `TopParticipantsRepository` в lesson-runner:domain |
|----------|------------------------------------------------------------------------|------------------------------------------------------|------------------------------------------------------------------------|
| Bidirectional coupling | Устранён ✓ | Устранён (domain не зависит от TopParticipant) | Устранён |
| Type safety | Strong typed | Lost — string в domain | Strong typed |
| Coupling с JSON в domain | Нет | Есть (нарушает domain-models.md) | Нет |
| Blast radius | Только добавление core module | Широкий (presentation parse JSON) | Отдельная repository + sync |
| Reusability | Высокая (leaderboard screen и др.) | Низкая | Низкая |
| Стоимость reversal если неверно | Низкая | Высокая | Высокая |

**Recommended: Option A** (resolved в ADR-LR-05)

**Rationale:** `TopParticipant` — value type без поведения; перемещение в core — единственный путь убрать bidirectional coupling не жертвуя type safety и не нарушая domain-models.md.

**Rejected Option B:** domain протекает JSON-контракт в presentation слой — нарушение `domain-models.md:35`.

**Rejected Option C:** требует dual sync infrastructure; разрывает server data model (spec §34: top3 в Lesson document на сервере).
