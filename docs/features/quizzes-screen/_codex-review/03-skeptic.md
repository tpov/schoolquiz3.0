# Skeptic Review — 03-decisions.md

## Verdict
CONTESTED

## Findings (per ADR)

### ADR-QS-01 — [SEVERITY: medium]
**Claim**: cross-module wiring через lambda callbacks, без `QuizzesNavigator` в `quest/presentation`.  
**Issue**: решение правильное по направлению, но ADR необоснованно уходит в raw `String` IDs. Для сохранения Invariant 3 не нужно терять type-safety: `CatalogId`/`QuestId` уже shared/domain типы, не cross-feature import.  
**Evidence**: [03-decisions.md:25](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:25), [03-decisions.md:49](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:49), [06-api-contract.md:83](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:83).  
**Suggested**: зафиксировать typed lambdas: `(CatalogId, String) -> Unit`, `(QuestId, List<String>) -> Unit`; оставить `String` только внутри serialized `QuizzesConfig`.

### ADR-QS-02 — [SEVERITY: medium]
**Claim**: `@Serializable QuizzesConfig` решает process death.  
**Issue**: решение обосновано, но trade-off по Bundle size слишком оптимистичен и consequences не покрывают schema evolution. Stack может хранить cumulative titles на каждом уровне, а не просто “≤ 5 items max”; при будущих изменениях config variants restore может стать хрупким.  
**Evidence**: [03-decisions.md:91](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:91), [06-api-contract.md:54](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:54), [0-spec.md:504](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:504).  
**Suggested**: добавить consequence: stable serialized schema, `@SerialName`, defaults for future fields, restore-failure fallback to `Idle`.

### ADR-QS-03 — [SEVERITY: low]
**Claim**: isolated inner `ChildStack`, не расширять app-shell `NavStack` FSM.  
**Issue**: это сильное архитектурное решение, но alternative set неполный: не рассмотрен вариант “feature-specific stack scoped inside Local tab/content”, отличающийся и от domain FSM, и от root-level overlay.  
**Evidence**: [03-decisions.md:101](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:101), [03-decisions.md:123](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:123).  
**Suggested**: добавить вариант C/D и явно сказать, почему root-owned overlay лучше tab-scoped component.

### ADR-QS-04 — [SEVERITY: medium]
**Claim**: `Idle` anchor означает `QuizzesComponent` живёт весь lifetime root.  
**Issue**: ADR заявляет high-level tradeoff, но не рассматривает third option, которую реально надо закрыть: always-created component + separate visibility flag/state. Сейчас выбор `Idle` выглядит как единственная альтернатива lazy/null component.  
**Evidence**: [03-decisions.md:149](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:149), [03-decisions.md:157](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:157).  
**Suggested**: добавить visibility-flag alternative и последствия неверного выбора: двойной source of truth, restore mismatch, overlay shown/hidden inconsistently.

### ADR-QS-05 — [SEVERITY: high]
**Claim**: расширить core `QuestDisplayItem` полем `catalogId`.  
**Issue**: прямое противоречие с API contract. ADR говорит менять core designsystem model, а `06-api-contract.md` вводит `QuestDisplayItemWithCatalog` в quizzes-screen и прямо говорит “не меняет core designsystem model”. Это blocker для implementation ownership.  
**Evidence**: [03-decisions.md:186](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:186), [06-api-contract.md:307](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:307), [QuestDisplayItem.kt:14](/home/Programming/Android/schoolquiz4.0/android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/QuestDisplayItem.kt:14).  
**Suggested**: выбрать один SSoT. С учётом grounding/User Q4 лучше обновить `QuestDisplayItem` required `catalogId: CatalogId` и привести `06-api-contract.md` к ADR.

### ADR-QS-06 — [SEVERITY: low]
**Claim**: добавить `onLongClick` в `QuestCard`, не wrapper.  
**Issue**: решение обосновано как public design-system API change, но это скорее component API ADR, не большая архитектура. Trade-off по accessibility/semantics `combinedClickable` не назван.  
**Evidence**: [QuestCard.kt:41](/home/Programming/Android/schoolquiz4.0/android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt:41), [03-decisions.md:230](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:230).  
**Suggested**: добавить consequence: проверить a11y semantics, haptic behavior, Compose UI test for click + long-click coexistence.

### ADR-QS-07 — [SEVERITY: high]
**Claim**: `expandedQuestId` локальный `remember` state, не ViewModel/component state.  
**Issue**: противоречит API contract: `QuestListUiState.Loaded` содержит `expandedQuestId`. Это два разных source of truth для menu state.  
**Evidence**: [03-decisions.md:260](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:260), [03-decisions.md:268](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:268), [06-api-contract.md:300](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:300).  
**Suggested**: оставить menu expansion UI-only в composable и удалить из `QuestListUiState`, либо поднять state в component и добавить events/actions. Сейчас mixed design.

### ADR-QS-08 — [SEVERITY: medium]
**Claim**: share через `Intent.createChooser` из Compose.  
**Issue**: решение нормально для Android UI layer, но ADR не закрывает testability и обязательное закрытие меню после share/error. Spec требует menu closes always; snippet этого не фиксирует.  
**Evidence**: [03-decisions.md:277](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:277), [0-spec.md:489](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:489), [0-spec.md:491](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:491).  
**Suggested**: добавить `finally { expandedQuestId = null }` pattern или explicit ordering; рассмотреть tiny `ShareDispatcher` helper for tests.

### ADR-QS-09 — [SEVERITY: blocker]
**Claim**: один `HierarchyItemCard` для Section/Theme/Lesson.  
**Issue**: signature violates module direction: `HierarchyItemCard` живёт в core designsystem, но принимает `HierarchyItemUi`, который ADR кладёт в `quizzes-screen/presentation`. Core не должен импортировать feature presentation. Плюс из signature потерян `subtitleCount`, хотя spec/grounding держат его как delegated decision.  
**Evidence**: [03-decisions.md:315](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:315), [03-decisions.md:336](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:336), [0-spec.md:47](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:47), [2-grounding.md:482](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/2-grounding.md:482).  
**Suggested**: либо component принимает primitive params (`title`, `orderLabel`, `subtitleCount`), либо `HierarchyItemUi` живёт в `android/core/designsystem/model`.

### ADR-QS-10 — [SEVERITY: low]
**Claim**: breadcrumb titles frozen in `QuizzesConfig.titles`.  
**Issue**: ADR хороший и соответствует user decision. Недостаток: consequences не говорят, что при wrong decision через 6 месяцев придётся менять serialized configs и добавить parent observer chain.  
**Evidence**: [03-decisions.md:345](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:345), [0-spec.md:162](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/0-spec.md:162).  
**Suggested**: добавить rollback/migration note: live breadcrumbs require storing IDs per level, not only titles.

### ADR-QS-11 — [SEVERITY: medium]
**Claim**: `Idle` anchor solves non-empty `ChildStack`.  
**Issue**: coherent with ADR-QS-04, but duplicates it and still omits visibility-flag alternative. Также `dismissQuizzes() -> popToFirst() + callback` conflicts with contract routing, where `Idle` itself hides UI.  
**Evidence**: [03-decisions.md:367](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:367), [03-decisions.md:372](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:372), [06-api-contract.md:179](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:179).  
**Suggested**: clarify whether overlay visibility is derived solely from `active is Idle` or also controlled by `onDismiss`. Avoid dual mechanism.

### ADR-QS-12 — [SEVERITY: blocker]
**Claim**: explicit `BackCallback(priority = PRIORITY_OVERLAY)` guarantees overlay back priority.  
**Issue**: ADR leaves a critical API fact as “REQUIRES verify”, while consequences already assume `childStack(handleBackButton = true) + explicit priority`. The API contract only passes `handleBackButton = true` and has priority as a comment, not as executable contract. Also “root handler → dismissQuizzes()” is inconsistent with current root handler, which calls `onDestination(Destination.Back)`.  
**Evidence**: [03-decisions.md:392](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:392), [03-decisions.md:403](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/03-decisions.md:403), [06-api-contract.md:117](/home/Programming/Android/schoolquiz4.0/docs/features/quizzes-screen/06-api-contract.md:117), [DefaultRootComponent.kt:139](/home/Programming/Android/schoolquiz4.0/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt:139).  
**Suggested**: verify Decompose/Essenty API before accepting. If `childStack` cannot set priority, implement/register manual `BackCallback(priority=...)` in `DefaultQuizzesComponent` and disable default `handleBackButton`, or document registration-order dependency honestly.

## Strong points

- **ADR-QS-03**: strongest architectural boundary decision. It ties the choice to real blast radius in `shared/feature/app-shell/domain` and `TabConfig` serialization constraints.
- **ADR-QS-10**: cleanly grounded in User Decision #14 and honest about stale breadcrumb titles after rename.
- **ADR-QS-01**: directionally strong for preserving Invariant 3; it just needs typed callback signatures instead of raw strings.

## Final verdict reason
CONTESTED, not REJECT: the core architecture is plausible, but `03-decisions.md` is not yet a reliable implementation source. The main blockers are SSoT contradictions with `06-api-contract.md` (`QuestDisplayItem`, menu state, `HierarchyItemCard`) and an unverified back-priority decision that may not be expressible through the documented contract.