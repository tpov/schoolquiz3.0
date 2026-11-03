---
phase: phase-06
name: Quizzes-screen Integration
tag: complex
date: 2026-04-27
---

# Phase 06 — Quizzes-screen Integration

## Goal

Атомарно заменить `LessonPlaceholder` на `LessonRunner` в `quizzes-screen`, расширить `DefaultLessonListComponent` для `bestStars`/`hardUnlocked`, создать `LessonItemCard` и `LessonItemUi`. После фазы тап на урок запускает полноценный gameplay loop.

## Scope

- Атомарная замена per ADR-LR-07:
  - `QuizzesConfig.LessonRunner` добавить, `QuizzesConfig.LessonPlaceholder` удалить
  - `QuizzesChild.LessonRunner` добавить, `LessonPlaceholder` удалить
  - `DefaultQuizzesComponent.createChild` — exhaustive when update
  - `QuizzesScreen.kt` — exhaustive when update
  - DELETE: 4 production + 3 test `LessonPlaceholder*` файла; UPDATE: 2 теста (`QuizzesConfigSerializationTest`, `DefaultLessonListComponentTest`) — full list per `phase-06/backend.md` Delete/Update section
- `DefaultLessonListComponent`: + `lessonAttemptRepository`, + `authRepository`; combine flow для bestStars/hardUnlocked
- `LessonItemUi` (NEW) + `LessonItemCard` (NEW composable)
- `LessonListScreen`: использовать `LessonItemCard` вместо `HierarchyItemCard` для уроков
- `LessonRunnerComponentFactory` binding в `quizzesPresentationModule`
- `DefaultQuizzesComponent`: + 2 новых deps; factory wiring
- Tests: PT-15..PT-17, PT-34..PT-36, CT-22..CT-24

## Role Inputs

- `backend.md` — Yes
- `frontend.md` — Yes
- `tests.md` — Yes

## Layer

`android/feature/quizzes-screen/presentation` (modified), `android/feature/lesson-runner/presentation` (consumed via one-way import per ADR-LR-17). `android/core/navigation` — не затронут (ADR-LR-16).

## Review Tags

`architecture-review` (атомарная sealed class замена; cross-feature import ADR-QS-16), `concurrency-review` (combine Flow bestStars + hardUnlocked в LessonListComponent)

## State Matrix Coverage

Matrix 3 (bestStars/hardUnlocked) — `DefaultLessonListComponent.combine(...)` реализует PT-15..PT-17.

## Domain Contract Coverage

ADR-QS-15 (factory boundary), ADR-QS-16 (cross-feature domain import).

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 7: `LessonListComponent` extension impact | `quizzes-screen/presentation` — backend-dev | `DefaultLessonListComponent.kt:26` — нет `LessonAttemptRepository`; `HierarchyItemUi.kt:3` — нет `bestStars`; `HierarchyItemCard.kt:34` — нет Checkbox slot | ADR-QS-09: designsystem не импортирует feature types; ADR-LR-11: `LessonItemCard` в quizzes-screen/presentation | Создать `LessonItemUi` + `LessonItemCard`; добавить `LessonAttemptRepository`/`AuthRepository` deps; `combine` flow | PT-15..17, CT-22..24; `rg "^import .*lesson_runner" android/core/designsystem` пусто |
| Problem 5: `Difficulty @Serializable` (для QuizzesConfig) | `quizzes-screen/presentation` — backend-dev | `QuizzesConfig.kt:5-6`; `DefaultQuizzesComponent.kt:42-54` | `Difficulty @Serializable` — Phase-01 prerequisite | Добавить `QuizzesConfig.LessonRunner(lessonId, mode, titles)` после Phase-01 done | `QuizzesConfigSerializationTest` round-trip с LessonRunner |

## Files

### New Files

- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonItemUi.kt` (path per existing uistate/ package in quizzes-screen)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt` (path: screen/ alongside LessonListScreen.kt)

### Modified Files

- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt` — add `LessonRunner`, remove `LessonPlaceholder`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/QuizzesChild.kt` — add/remove variants
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt` — createChild exhaustive when + 3 new deps
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/QuizzesScreen.kt` — exhaustive when (path: screen/, NOT ui/)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt` — 2 new deps + combine flow
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonListScreen.kt` — use LessonItemCard (path: screen/, NOT ui/)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt` — 3 new deps
- `android/feature/quizzes-screen/presentation/build.gradle.kts` — add `:android:feature:lesson-runner:presentation` dep (backend-dev owned)

### Deleted Files

**Production (4):**
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/LessonPlaceholderComponent.kt` (interface)
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponent.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreen.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/uistate/LessonPlaceholderUiState.kt`

**Tests (3):**
- `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonPlaceholderComponentTest.kt`
- `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/fake/FakeLessonPlaceholderComponent.kt`
- `android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonPlaceholderScreenTest.kt`

**Updated tests (2 — referenced LessonPlaceholder; replace cases с LessonRunner):**
- `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/QuizzesConfigSerializationTest.kt`
- `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt`

Full canonical list — see `phase-06/backend.md` Delete/Update section.

## Dependencies

- Phase-01 (Difficulty @Serializable, TopParticipant, Provider interfaces)
- Phase-03 (LessonAttemptRepository production impl)
- Phase-04 (LessonRunnerRootComponent interface + LessonRunnerComponentFactory — both in lesson-runner/presentation per ADR-LR-16)
- Phase-05 (LessonRunnerScreen — for QuizzesScreen.LessonRunner branch per ADR-LR-17)

## Criteria for Acceptance

1. `QuizzesConfig.LessonRunner` round-trip serialization (QuizzesConfigSerializationTest)
2. All `when(config)` / `when(child)` blocks exhaustive — compiler check (no `else`)
3. `DefaultLessonListComponent` приимает `LessonAttemptRepository` и `AuthRepository`
4. `LessonItemUi.hardUnlocked == true` только когда ∃ EASY attempt с `allShownAnswersAre9 == true` (PT-16)
5. `LessonItemUi.hardUnlocked == false` even if rawTenths==20 without perfect EASY (PT-17)
6. `LessonItemCard` renders `StarRating(bestStarsRawTenths/10f)` + checkbox if `hardUnlocked` (CT-22, CT-23, CT-24)
7. `rg "^import .*lesson_runner" android/core/designsystem/src -g "*.kt"` — пусто; `rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"` — пусто (ADR-LR-17 reverse-dep gate; filesystem hyphen paths)
8. `isHardChecked` toggleable только если `hardUnlocked=true` (PT-35, PT-36)
9. Tapping Lesson → pushes `QuizzesConfig.LessonRunner(lessonId, mode, titles)` (no crash)
10. **Stateful field reset**: `isHardChecked` Set in `DefaultLessonListComponent` — очищается при component destroy

## Tests Required

- `PT-15_no_attempts_defaults`: given `LessonAttemptRepository` с пустым списком для урока, when `DefaultLessonListComponent.lessonItems` emits, then `LessonItemUi(bestStarsRawTenths=0, hardUnlocked=false)`
- `PT-16_easy_perfect_unlocks_hard`: given один EASY attempt с `codeAnswer.allShownAnswersAre9 == true`, when `lessonItems` computed, then `LessonItemUi.hardUnlocked == true`
- `PT-17_easy_imperfect_no_unlock`: given один EASY attempt с `rawTenths==20` но `allShownAnswersAre9 == false`, when `lessonItems` computed, then `LessonItemUi.hardUnlocked == false`
- `PT-34_bestStars_mapped_correctly`: given attempt с `stars.rawTenths == 15`, when `lessonItems` computed, then `LessonItemUi.bestStarsRawTenths == 15`
- `PT-35_hardNotUnlocked_ignoresToggle`: given `LessonItemUi.hardUnlocked == false`, when `onHardCheckToggled(lessonId)` called, then `isHardChecked` remains false
- `PT-36_hardUnlocked_toggleable`: given `LessonItemUi.hardUnlocked == true`, when `onHardCheckToggled(lessonId)` called twice, then `isHardChecked` toggles true → false
- `CT-22_starRating_rendered`: given `bestStarsRawTenths == 15`, when `LessonItemCard` rendered, then `StarRating(rating=1.5f)` visible
- `CT-23_hardCheckbox_hidden_when_not_unlocked`: given `hardUnlocked == false`, when `LessonItemCard` rendered, then no Checkbox visible in semantics tree
- `CT-24_hardCheckbox_visible_when_unlocked`: given `hardUnlocked == true`, when `LessonItemCard` rendered, then Checkbox visible in semantics tree
- `quizzesConfig_lessonRunner_roundtrip`: given `QuizzesConfig.LessonRunner(lessonId="abc", mode=Difficulty.HARD, titles=["A","B"])`, when JSON serialize then deserialize, then equal to original

## Validation

```bash
./gradlew :android:feature:quizzes-screen:presentation:test --no-configuration-cache
./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# ADR-LR-17: No designsystem importing lesson-runner types:
rg "^import .*lesson_runner" android/core/designsystem/src -g "*.kt"
# Expected: empty
# ADR-LR-17: No reverse dep (lesson-runner must NOT import quizzes-screen):
rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"
# Expected: empty
# ADR-LR-16: Interface lives in lesson-runner/presentation, not core/navigation:
rg "interface LessonRunnerRootComponent" android/feature/lesson-runner/presentation/src -g "*.kt"
# Expected: 1 match
rg "interface LessonRunnerRootComponent" android/core/navigation -g "*.kt"
# Expected: empty
```

## Handoff Notes

После phase-06:
- Phase-07 (composition root) добавляет в `AppApplication.startKoin`: `questionSchemaModule`, `lessonRunnerDataModule`, `lessonRunnerDomainKoinAdapter`, `lessonRunnerPresentationModule`
- Phase-07 добавляет `single<LessonRunnerComponentFactory>` binding в `AppApplication.kt` (composition root per `06-api-contract.md:374`) — это НЕ в `quizzesPresentationModule`
- `quizzesPresentationModule` в phase-06 добавляет `lessonRunnerFactory = get<LessonRunnerComponentFactory>()` — Koin разрешит через composition root в runtime

## Options Considered

Phase отмечена `complex` (затрагивает 3+ модулей: quizzes-screen/presentation, lesson-runner/presentation, shared/core/persistence через repo, core/designsystem через StarRating; реализует новый cross-feature architectural pattern per ChildStack Compose rendering exception ADR-LR-17).

| Критерий | Option A: Direct Compose import (recommended, ADR-LR-17) | Option B: Slot pattern (slotContent lambda) | Option C: Root-level orchestration (DefaultRootComponent owns ChildStack) |
|----------|----------------------------------------------------------|---------------------------------------------|---------------------------------------------------------------------------|
| Строк кода | Минимум — 1 when-ветка в QuizzesScreen | Больше — lambda prop threading | Максимум — restructuring DefaultRootComponent |
| Gradle coupling | quizzes-screen → lesson-runner (one-way) | quizzes-screen → lesson-runner для factory type | Нет feature → feature; но нарушает phase structure |
| Bidirectional risk | Защищён ADR-LR-17 + grep validation | Защищён (нет direct import) | Защищён (root orchestrates both) |
| Precedent | `AppShellScreen.kt:53-56` (established pattern) | Нет прецедента в проекте | Требует рефактор RootComponent |
| Test cost | CT-coverage через QuizzesScreen (existing test setup) | Нужен новый slot test harness | Нужен рефактор RootComponent tests |
| Refactor cost если неверно | Средний (перенести в Option B/C) | Низкий (убрать lambda prop) | Высокий |
| Coupling с external SDK | Нет | Нет | Нет |

**Recommended: Option A** (принят ADR-LR-17, 2026-04-27)

**Rationale:** Прямой Compose import — project-established pattern (`AppShellScreen.kt:53-56`); минимальный coupling; защита от bidirectional через grep rule + ADR; один commit устраняет compile blocker.

**Rejected Option B (slot pattern):** Lambda `slotContent: @Composable (LessonRunnerRootComponent) -> Unit` threading через `LessonListComponent` → `LessonListScreen` → `QuizzesScreen` — нет прецедента в проекте; усложняет API без выгоды при одном consumer.

**Rejected Option C (root orchestration):** Перенос ChildStack в `DefaultRootComponent` требует значительного рефакторинга инфраструктуры, выходящего за scope этой фичи; противоречит phase-04 design.

## Pattern Invariants

- Атомарная замена `LessonPlaceholder→LessonRunner` ОБЯЗАНА быть в одном commit — compile errors guide (no partial state)
- `DefaultLessonListComponent` ОБЯЗАН использовать `combine(lessonRepository.observeByTheme(...), attemptRepository.observeAllByUser(...))` — НЕ flat map или separate flows — per `02-behavior.md DFD 1`; per pattern at `DefaultQuizzesComponent.kt:113`
- `isHardChecked: Boolean` ОБЯЗАН храниться в component как `MutableStateFlow<Set<String>>` (lessonId set) — НЕ в Room (ephemeral UI state per `06-api-contract.md:486` LessonItemUi.isHardChecked field comment)
- `LessonItemCard` ДОЛЖЕН быть в `quizzes-screen/presentation` — НЕ в `android/core/designsystem` (ADR-LR-11)
- `QuizzesConfig.LessonRunner` ДОЛЖНА быть `@Serializable` data class (Decompose StateKeeper compat)
- Exhaustive `when(config)` и `when(child)` — НЕ использовать `else` branch (compile safety)
- `isHardChecked` Set ОБЯЗАН сбрасываться при component destroy (stateful field reset rule)
- `QuizzesScreen.kt` импортирует ТОЛЬКО `@Composable` function `LessonRunnerScreen` и `LessonRunnerRootComponent` interface из `lesson-runner/presentation` — НЕ component classes, НЕ use cases, НЕ repositories (ADR-LR-17 condition 2)
- `rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"` ДОЛЖЕН возвращать пустой результат после фазы (no reverse dep; filesystem hyphen path)
