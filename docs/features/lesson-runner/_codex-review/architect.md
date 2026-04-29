# Architect Review — lesson-runner design

## Verdict
REJECT

## Boundary violations
- [BLOCKER] Domain is made to depend on data-layer provider interfaces. `03-decisions.md:331`: “wrapper interfaces в `shared/feature/lesson-runner/data/…/provider/`”, then `06-api-contract.md:430`: “File: `shared/feature/lesson-runner/domain/src/androidMain/.../LessonRunnerDomainKoinAdapter.kt`” and `06-api-contract.md:443`: “`get<RandomSeedProvider>().next()`”. Rule violated: `.claude/rules/clean-architecture.md:35` — “`domain/ | data.* ... | NO`”.
- [HIGH] DI boundary is unstable: 01 says register existing domain module, 06 says register adapter. `01-architecture.md:713-715`: “`lessonRunnerDataModule, lessonRunnerDomainModule, lessonRunnerPresentationModule`”; `06-api-contract.md:501-503`: “`lessonRunnerDataModule, lessonRunnerDomainKoinAdapter, lessonRunnerPresentationModule`”. Rule impacted: `.claude/rules/di-patterns.md:17` app composition root must include every production module.

## SSoT violations
- [BLOCKER] Full data-class signature duplicated outside 06. `03-decisions.md:152-155`: “`TopParticipant` … `data class TopParticipant(nickname: String, avatarUrl: String?, percent: Int)`”. Violates `.claude/commands/feature-design.md:198-200`: `03-decisions.md` signatures = “НЕТ”; `06-api-contract.md` = “CANONICAL signatures”.
- [BLOCKER] Full provider interface signatures duplicated outside 06. `03-decisions.md:333-337`: “`interface AttemptIdProvider { fun next(): AttemptId }` … `interface RatingIdProvider { fun provide(...) }`”. Same SSoT rule.
- [HIGH] `LessonRunnerRootComponent` public API disagrees across docs. `01-architecture.md:386-402` says `uiState: Value`, `events: Flow`, plus callbacks like `onSingleChoiceAnswer`, `onPauseDialogResume`, `onRatingSelected`; canonical `06-api-contract.md:271-283` says `StateFlow`, `ReceiveChannel`, and `onAnswer`, `onContinue`, `onExit`, `onSubmitRating`. This is exactly the drift 06 is supposed to prevent.
- [HIGH] Provider API is internally inconsistent. `03-decisions.md:337`: “`RatingIdProvider { fun provide(...) }`”; `06-api-contract.md:465`: “`ratingIdProvider = { get<RatingIdProvider>().next() }`”. Canonical contract would not compile as written.

## AC coverage gaps
- No numeric AC #1-65 is completely unmapped in `04-testing.md:217-283`.  
- [HIGH] The map is not reliable as an implementation gate because DT IDs are asserted but not defined in 04. Example: `04-testing.md:240-243` maps AC-24..27 to `DT-37`, `DT-39a`, `DT-40`, `DT-39b`, while `02-behavior.md:559-562` maps the same timer matrix differently. Coverage exists on paper, but traceability is shaky.

## State Matrix gaps
- [HIGH] Matrix 3 loses the critical “bestStars can reach 20 without unlock” edge. Spec says `0-spec.md:978`: “near-perfect rounding может дать 20” with `hardUnlocked=false`; design says `02-behavior.md:523`: “rawTenths=20 без allShown9 невозможно”. That weakens the canonical SSoT for HARD checkbox safety.
- [HIGH] Matrix 2 extension omits spec cells. `02-behavior.md:511-516` covers EASY 0/50/100 and HARD 0/80/100, but spec Matrix 2 also has EASY 75 and HARD 50 (`0-spec.md:961-968`). `02-behavior` was required to extend all cells with code locations + test IDs.
- [MEDIUM] Matrix 1 introduces an invalid edge. `02-behavior.md:503`: “1-element list → always '9'”; spec question constraints require Ordering items `2..8` (`0-spec.md:44`). This should not be a design/test target.

## Test strategy holes
- [HIGH] Fake blueprints duplicate existing/canonical fakes instead of referencing them. `04-testing.md:34-56` defines `FakeLessonAttemptRepository` and `FakeLessonRatingRepository`; rule says `.claude/rules/testing.md:36-40`: “Use existing fakes first” and “Do not create duplicate fakes”.
- [HIGH] Fake use case strategy is not substitutable against concrete constructor dependencies. `04-testing.md:58`: “`class FakeStartLessonAttemptUseCase`”; but production design injects concrete use cases (`01-architecture.md:408-411`). Without interfaces or function injection, these fakes cannot be passed to the component.
- [HIGH] Instrumented test locations are likely wrong for existing KMP persistence convention. `04-testing.md:18` and `04-testing.md:185` use `shared/core/persistence/src/androidTest/`; the existing module uses `src/androidInstrumentedTest`. This risks tests never running under the canonical gate.
- [MEDIUM] Walking Skeleton is correctly referenced, not rewritten: `04-testing.md:24`: “DT-01..DT-89 | Domain JVM (Walking Skeleton, existing)”. That part passes.

## Migration / DI / lifecycle issues
- [HIGH] Migration impact scan is explicitly unfinished. `08-storage-model.md:151`: “DAO queries на `lessons` проверены | ⚠️ REQUIRES”. Rule violated: `.claude/rules/room-database.md:58`: “check impact on ALL existing DAO queries”.
- [HIGH] Migration test only proves `lessons` survives, not all existing user/content tables. `04-testing.md:193`: “Existing lessons data preserved”; rule says `.claude/rules/room-database.md:69`: “Test migration preserves existing data.”
- [HIGH] Fallback strategy remains ambiguous. `08-storage-model.md:140`: “Debug build может оставить как последний fallback.” Production removal is not a tested acceptance item, and migration-failure behavior is undocumented. Rule: `.claude/rules/room-database.md:59` — “don't rely on destructive migration in production”.
- [HIGH] Koin wiring test is too narrow. `04-testing.md:179`: “Koin loadModules: lessonRunnerDataModule + Presentation + DomainAdapter registered”. It does not explicitly extend `KoinModuleWiringTest`, nor cover quizzes-screen module changes, parser binding location drift, or Room converters.
- [MEDIUM] `Channel<RunnerEvent>` lifecycle is bounded but tradeoffs are underdocumented. `07-events.md:37-45` uses `Channel.BUFFERED` + `trySend`; it does not discuss single-consumer semantics, event loss after close, or why this beats `SharedFlow`/state-backed errors.
- [MEDIUM] Lifecycle cleanup is mostly sound: `07-events.md:41` only closes the event channel on destroy. I did not find business cleanup in `onDestroy`.
- [HIGH] HARD `FLAG_SECURE` rotation is asserted but not test-covered. `02-behavior.md:277`: “Нет риска … при rotation”; `04-testing.md:146-148` only covers set/clear/EASY absent, and AC-35 maps to generic `IT-02` rotation (`04-testing.md:251`), not window flag retention.

## Recommendations
- Treat 06/07/08 as the only canonical shape docs; remove type bodies from 03 and align 01 diagrams to names only.
- Resolve the provider boundary before planning: provider interfaces cannot live in data if domain Koin code consumes them.
- Rework 04 so AC/state-matrix rows trace to stable, named tests that actually exist or are explicitly new.
- Make migration safety a real gate: all existing tables preserved, destructive fallback absent in prod, converters registered, and DAO impact scan closed.