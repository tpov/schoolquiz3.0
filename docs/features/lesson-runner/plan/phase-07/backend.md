---
phase: phase-07
role: backend-dev
---

# Phase 07 — Backend Tasks

## Pattern Invariants

- Module registration order в `startKoin`: data → domain adapter → presentation (each module's deps must be registered first)
- `questionSchemaModule` добавляется ДО `lessonRunnerDomainKoinAdapter` (parser dependency)
- Walking Skeleton `lessonRunnerDomainModule` НЕ добавляется (оно для tests only); `lessonRunnerDomainKoinAdapter` заменяет его
- Scaffold файл `AppApplication.kt` — только `backend-dev`

---

## Modify `AppApplication.kt` — register modules + factory binding

- **Файл:** `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`
- **Тип:** class (existing — modify)
- **Сигнатура:** add 4 modules + `LessonRunnerComponentFactory` binding to `startKoin { modules(...) }`
- **Вход:** existing `startKoin` block; add in correct order
- **Поведение / Выход:**
  - Найти `startKoin { modules(persistenceModule, ...) }` block
  - Добавить в правильном порядке:
    1. `questionSchemaModule` (из Phase-01 — parser impl)
    2. `lessonRunnerDataModule` (из Phase-03 — repos + providers)
    3. `lessonRunnerDomainKoinAdapter` (из Phase-03 — use case factories)
    4. `lessonRunnerPresentationModule` (из Phase-04 — component factory)
  - Добавить `single<LessonRunnerComponentFactory>` binding в `startKoin` (НЕ в отдельный module): `single<LessonRunnerComponentFactory> { LessonRunnerComponentFactory { ctx, lessonId, mode -> getKoin().get(parametersOf(ctx, lessonId, mode)) } }` — per `06-api-contract.md:374` (composition root binding)
  - Проверить что `persistenceModule` идёт перед `lessonRunnerDataModule` (DAO deps)
  - Imports для всех 4 новых modules + `LessonRunnerComponentFactory`
- **Edge cases:**
  - Если `lessonRunnerDomainModule` (Walking Skeleton) уже случайно добавлен — удалить; его заменяет `lessonRunnerDomainKoinAdapter`
  - `Clock` binding: если `StartLessonAttemptUseCase` требует `Clock` instance через Koin — добавить `single<Clock> { Clock.System }` в `lessonRunnerDataModule` или `lessonRunnerDomainKoinAdapter` (Phase-03)
  - Проверить что все DAO bindings в `persistenceModule` — `lessonAttemptDao`, `lessonRatingLocalDao`
  - `LessonRunnerComponentFactory` binding ОБЯЗАН быть в composition root (НЕ в `quizzesPresentationModule`) — per ADR-LR-16 и `06-api-contract.md:374`
- **Depends on:** все Phase-01..06 modules created
- **Canonical reference:** `2-grounding.md §Problem 4 Fix Shape`, `06-api-contract.md:374`
- **Rationale:** Composition root — единственное место регистрации всех production Koin modules; `LessonRunnerComponentFactory` binding здесь предотвращает `NoBeanDefinitionFoundException` при nav push (NEW BLOCKER B resolution)
