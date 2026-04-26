# Realist Review Pass-2 — quizzes-screen 01+02

## Verdict
REJECT

## Per-finding verification

### Finding #1 — Status: PARTIAL
Evidence:
- `Idle` added as 6th config: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:275), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:281), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:288)
- State machine has `[*] --> Idle` and `popToFirst() + dismissQuizzes()`: [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:106), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:117)
- Old “pop stack empty” wording: not found.

Notes:
- Still inconsistent: `QuizzesConfig` is still described as “5 variants” in two places: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:165), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:227)
- Process-death diagrams still serialize/restore stacks without `Idle`: [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:63), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:374), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:378)

### Finding #2 — Status: FIXED
Evidence:
- Explicit `BackCallback(priority = BackCallback.PRIORITY_OVERLAY)`: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:338)
- `PRIORITY_OVERLAY` vs `PRIORITY_DEFAULT` differentiation documented: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:338)
- REQUIRES verify Essenty constant / fallback `priority = 100`: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:340), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:138)

### Finding #3 — Status: FIXED
Evidence:
- C4 L2 relationships use lambda closures, not `QuizzesNavigator`: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:54), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:56)
- App-shell integration uses stdlib lambda callbacks for quest/presentation and states `QuizzesNavigator` is not imported there: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:125)
- `QuizzesNavigator` is scoped to quizzes-screen/presentation: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:226), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:375)

### Finding #4 — Status: FIXED
Evidence:
- All fake copies are listed, including sync fake and conditional local datasource fake: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:183), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:187), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:190)

### Finding #5 — Status: FIXED
Evidence:
- DFD 2 uses `onQuestClick(quest: QuestDisplayItem)`: [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:41), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:42)
- Seq-2 uses full object signature: [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:237)
- Old `onQuestClick(id, catalogId)` style signature: not found.

### Finding #6 — Status: FIXED
Evidence:
- Rotation row says components are recreated, new `componentJob/scope`, Flow collection restarts: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:334)
- `componentJob.cancel()` explicitly documented: [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:327), [01-architecture.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/01-architecture.md:336)

## New Issues Introduced By Fixes
Breadcrumb indexing was not adjusted for hidden `Idle`. Docs say `Idle = 0` in the ChildStack, but still pass breadcrumb segment index directly to `navigation.popTo(i)`: [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:137), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:155), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:178), [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/02-behavior.md:184). With `Idle` inserted, visible breadcrumb level `0` now maps to hidden `Idle`, not `QuestList`.

## Final Verdict Reason
Most fixes landed, but Finding #1 is not cleanly fixed: the docs now contain both the new `Idle` model and stale no-Idle assumptions. Because that was the blocker finding, and because the Idle fix introduces an off-by-one breadcrumb navigation problem, this pass stays REJECT.