---
phase: phase-06
role: test-dev
---

# Phase 06 — Tests

## Pattern Invariants

- Walking Skeleton fakes из `shared/feature/lesson-runner/domain/src/commonTest/kotlin/.../fake/` — используются для `LessonAttemptRepository` faking (per `testing.md` Fakes convention: use existing fakes first)
- `QuizzesConfigSerializationTest` — добавить LessonRunner round-trip; убрать LessonPlaceholder (если был) — test file в `android/feature/quizzes-screen/presentation/src/test/`
- `test-dev` не модифицирует production code — per `testing.md`: "test-dev adds tests but does not modify production code"
- Bidirectional check ОБЯЗАН быть в validation: `rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"` — must be empty (per `clean-architecture.md` review check)

## Test Locations

| Test | Location |
|------|----------|
| PT-15..17, PT-34..36 | `android/feature/quizzes-screen/presentation/src/test/` |
| CT-22..24 | `android/feature/quizzes-screen/presentation/src/androidTest/` |
| QuizzesConfig serialization | `android/feature/quizzes-screen/presentation/src/test/` |

---

## Scenario Group A — LessonItemUi State (PT-15..PT-17, PT-34..PT-36)

### PT-15 `lessonItemUi_noAttempts_zeroStars_hardUnlocked_false`

- **Given:** `FakeLessonAttemptRepository` with empty list for this lesson
- **When:** `DefaultLessonListComponent` combines flows
- **Then:** `lessonItems[0].bestStarsRawTenths == 0`; `lessonItems[0].hardUnlocked == false`
- **AC:** AC-21

### PT-16 `lessonItemUi_easyPerfectAttempt_hardUnlocked_true`

- **Given:** one EASY attempt with `codeAnswer.allShownAnswersAre9 == true` (all '9's)
- **When:** combine flow
- **Then:** `lessonItems[0].hardUnlocked == true`
- **AC:** AC-22

### PT-17 `lessonItemUi_easyImperfect_hardUnlocked_false`

- **Given:** EASY attempt with `codeAnswer` NOT allShownAnswersAre9 (e.g. "199" → first shown not 9)
- **When:** combine flow
- **Then:** `lessonItems[0].hardUnlocked == false` (even if rawTenths==20 via high percent)
- **AC:** AC-23

### PT-34 `lessonItemUi_bestStars_rawTenths_15`

- **Given:** EASY attempt with `percentScore=75` → `Stars(rawTenths=15)`
- **When:** combine flow
- **Then:** `lessonItems[0].bestStarsRawTenths == 15`
- **AC:** AC-47

### PT-35 `lessonItemUi_hardUnlocked_false_isHardChecked_ignores_toggle`

- **Given:** `hardUnlocked == false` for lesson; `onHardCheckToggled(lessonId)` called
- **When:** toggle
- **Then:** `lessonItems[0].isHardChecked` remains `false` (component ignores toggle when not unlocked)
- **AC:** AC-48

### PT-36 `lessonItemUi_hardUnlocked_true_isHardChecked_toggleable`

- **Given:** `hardUnlocked == true`; `isHardChecked` starts `false`
- **When:** `onHardCheckToggled(lessonId)`
- **Then:** `lessonItems[0].isHardChecked == true`
- **AC:** AC-49

---

## Scenario Group B — Serialization

### Ser-01 `quizzesConfig_lessonRunner_serialization_roundtrip`

- **Given:** `QuizzesConfig.LessonRunner(lessonId="l1", mode=Difficulty.HARD, titles=listOf("Cat","Quest","Lesson"))`
- **When:** `Json.encodeToString(QuizzesConfig.serializer(), config)` → `Json.decodeFromString`
- **Then:** decoded `== original`; `mode == Difficulty.HARD`

### Ser-02 `quizzesConfig_lessonRunner_easy_roundtrip`

- **Given:** `QuizzesConfig.LessonRunner(mode=Difficulty.EASY, ...)`
- **When:** round-trip
- **Then:** `mode == Difficulty.EASY`

---

## Scenario Group C — Compose UI (CT-22..CT-24)

### CT-22 `lessonItemCard_bestStarsRawTenths_15_starRating_1_5`

- **Given:** `LessonItemUi(bestStarsRawTenths=15, ...)`
- **When:** `composeTestRule.setContent { LessonItemCard(item=item, onClick={}, onHardCheckChanged={}) }`
- **Then:** `StarRating` renders with rating ≈ 1.5 (visual check or semantics)

### CT-23 `lessonItemCard_hardUnlocked_false_noCheckbox`

- **Given:** `LessonItemUi(hardUnlocked=false, ...)`
- **When:** render `LessonItemCard`
- **Then:** `Checkbox` not present (`onNodeWithTag("hard_checkbox").assertDoesNotExist()` or similar)

### CT-24 `lessonItemCard_hardUnlocked_true_checkboxVisible`

- **Given:** `LessonItemUi(hardUnlocked=true, isHardChecked=false, ...)`
- **When:** render
- **Then:** Checkbox visible and unchecked

---

## Validation Commands

```bash
./gradlew :android:feature:quizzes-screen:presentation:test --no-configuration-cache
./gradlew :android:feature:quizzes-screen:presentation:connectedAndroidTest --no-configuration-cache
./gradlew detekt ktlintCheck --no-configuration-cache
# Designsystem clean (no lesson-runner imports):
rg "^import .*lesson_runner" android/core/designsystem/src -g "*.kt"
# Bidirectional check (filesystem path uses hyphen, Kotlin package uses underscore):
rg "^import .*quizzes_screen" android/feature/lesson-runner -g "*.kt"
```
