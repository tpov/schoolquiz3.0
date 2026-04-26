# Skeptic Review Pass-2

## Verdict
PASS

## Per-finding

### Finding #1 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:25) uses `onCatalogDrillDown: (CatalogId, String) -> Unit`; line 26 uses `onQuestDrillDown: (QuestDisplayItem) -> Unit`; line 49 explicitly says not raw `String`. [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:51) §2 and [§7](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:265) both use typed lambdas.
Notes: fixed in both docs.

### Finding #2 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:94) adds stable schema consequences: `@SerialName`, default values, `SerializationException`, and reset to `listOf(QuizzesConfig.Idle)`.
Notes: fixed.

### Finding #3 — FIXED
Evidence: skipped per instruction.
Notes: low/non-blocking, not used as pass-2 gate.

### Finding #4 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:170) adds Variant C: always-created component plus separate visibility flag.
Notes: fixed.

### Finding #5 — FIXED
Evidence: [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:81) says extend `QuestDisplayItem`; line 119 says `QuestListUiState.Loaded.quests` is `List<QuestDisplayItem>` and “not wrapper”.
Notes: exact grep for `QuestDisplayItemWithCatalog` returned empty.

### Finding #6 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:250) adds haptic feedback, `onLongClickLabel` a11y, and Compose UI test requirements.
Notes: fixed.

### Finding #7 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:280) says `expandedQuestId` is local `remember`, not component state. [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:636) has only `quests` in `Loaded`; `expandedQuestId` is explicitly “НЕ здесь”.
Notes: fixed.

### Finding #8 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:307) sets `expandedQuestId = null` first; `try` starts later at line 313.
Notes: fixed.

### Finding #9 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:344) and [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:716) define `HierarchyItemCard` with primitives: `title`, `orderLabel`, `subtitleCount`, callbacks, modifier. No `HierarchyItemUi` parameter.
Notes: `subtitleCount` is restored.

### Finding #10 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:398) adds rollback note for live breadcrumbs migration and saved-bundle versioning/defaults.
Notes: fixed.

### Finding #11 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:412) says `dismissQuizzes()` only calls `navigation.popToFirst()` with no callback. [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:431) constructor has `NO onDismiss`; line 473 body is only `navigation.popToFirst()`.
Notes: `rg '\bonDismiss\s*:'` returned empty.

### Finding #12 — FIXED
Evidence: [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:434) requires `childStack(handleBackButton = false)` plus manual `BackCallback(priority = PRIORITY_OVERLAY)` and subscribe-based enablement. Reconciliation with `DefaultRootComponent.backHandler` is explicit at line 454. [06-api-contract.md](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:436) matches this in the API snippet.
Notes: fixed.

## New issues
None found in the requested pass-2 scope.

## Final verdict
PASS