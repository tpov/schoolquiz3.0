# Realist Review Pass-3

## Verdict
PARTIAL

## Verification

### A. 5→6 variants — FIXED
Evidence:
Exact grep for `5 child Components|5 вариантов|5 variants` in `01-architecture.md` returns 0 matches.

Positive lines:
[01-architecture.md:45](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:45) says `6 configs`.
[01-architecture.md:165](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:165) says `6 вариантами`.
[01-architecture.md:227](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:227) says `6 вариантов ChildStack конфигурации`.
[01-architecture.md:275](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:275) says `QuizzesConfig — 6 Вариантов`.

Note: [01-architecture.md:228](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:228) still says `Idle anchor + 5 active components`, but that is semantically correct, not the stale “5 child Components” issue.

### B. Process death Idle — FIXED
Evidence:
DFD 3:
[02-behavior.md:63](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:63) saves `[Idle, QuestList, SectionList, ThemeList]` to Bundle and notes Idle anchor serialization.
[02-behavior.md:66](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:66) restores `[Idle, QuestList, SectionList, ThemeList]` and says Idle restores as first element.

Seq-7:
[02-behavior.md:375](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:375) serializes `[Idle, QuestList(...), SectionList(...), ThemeList(...)]` and notes Idle anchor first.
[02-behavior.md:379](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:379) restores `[Idle, QuestList, SectionList, ThemeList]` and notes `anchor всегда stack[0]`.

### C. Breadcrumb popTo offset — FIXED
Evidence:
[02-behavior.md:315](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:315) Seq-5 uses `navigation.popTo(index = level + 1 = 2)`.
[02-behavior.md:156](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:156) Matrix 1 uses `navigation.popTo(i + 1)`.
[02-behavior.md:185](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:185) Matrix 3 equivalent rule uses `uiLevel i -> navigation.popTo(i + 1)` with example `uiLevel=0 -> popTo(1)`.
[02-behavior.md:138](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:138) High-Level State Machine note explicitly documents the `+1 offset`.

## New issues
[02-behavior.md:322](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:322) says `stack == 1 (root QuestList или SectionList от MyQuests)`. With Idle anchor, `stack == 1` means `[Idle]` only. Root `QuestList` / `SectionList` should be size 2: `[Idle, QuestList]` or `[Idle, SectionList]`. This conflicts with the correct notes at [02-behavior.md:139](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:139) and [01-architecture.md:338](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:338).

## Final verdict
A/B/C are fixed. Overall: PARTIAL due to the new stale back-coordination line at `02-behavior.md:322`.