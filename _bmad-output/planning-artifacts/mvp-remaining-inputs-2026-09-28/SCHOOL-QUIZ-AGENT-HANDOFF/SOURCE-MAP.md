# Карта исходников и материалов

Все ссылки ниже проверены на существование при передаче. Это точки входа, не полный список разрешённых изменений. Локальные исходники — тонкое зеркало; для пути в Git на сервере замените префикс `/Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/` на `/srv/schoolquiz-mvp-20260906/integration/`. Не меняйте оригинальные репозитории.

Новые Entry/RunRead-файлы пока находятся только в draft: их точные списки/preimage/postimage приведены в STATE-MANIFEST.json и оригинальных manifests. Не искать их в HEAD как уже подключённые.

## Контекст и обязательные правила

- [AGENTS.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/AGENTS.md>)
- [PROJECT-CONTEXT.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/.claude/PROJECT-CONTEXT.md>)
- [testing.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/.claude/rules/testing.md>)
- [0004-sync-contract.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/docs/architecture/0004-sync-contract.md>)
- [0005-quest-lifecycle.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/docs/architecture/0005-quest-lifecycle.md>)
- [2026-09-07-arena-financial-journal-retention.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/docs/adr/2026-09-07-arena-financial-journal-retention.md>)

## Арена: сервер, каталог, права и цена (ТЗ 00, 02, 06)

- [index.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/index.js>)
- [arena-policy.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/arena-policy.js>)
- [arena-service.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/arena-service.js>)
- [arena-tickets.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/arena-tickets.js>)
- [arena-entry-runtime.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/arena-entry-runtime.js>)
- [arena-lesson-catalog.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/arena-lesson-catalog.js>)
- [arena-ticket-recovery.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/arena-ticket-recovery.js>)
- [currency-ledger.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/currency-ledger.js>)

## Арена: журнал, checkpoint, сессия и результат (ТЗ 01, 04, 05)

- [ArenaTicketJournal.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/arena/ArenaTicketJournal.kt>)
- [ArenaTicketDao.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ArenaTicketDao.kt>)
- [ArenaCheckpointValidator.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/ArenaCheckpointValidator.kt>)
- [ArenaRunnerCheckpointCodec.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/arena/ArenaRunnerCheckpointCodec.kt>)
- [ArenaRunnerCheckpoint.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/ArenaRunnerCheckpoint.kt>)
- [ArenaRunnerCheckpointMapping.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/logic/ArenaRunnerCheckpointMapping.kt>)
- [ArenaCancelledSettlementResolver.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/arena/ArenaCancelledSettlementResolver.kt>)
- [ArenaSessionProvider.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/ArenaSessionProvider.kt>)
- [OutboxArenaSessionProvider.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/OutboxArenaSessionProvider.kt>)
- [ArenaDataModule.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/androidMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/di/ArenaDataModule.kt>)
- [AttemptSettlementReceiptParser.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/outbox/AttemptSettlementReceiptParser.kt>)
- [OutboxServerAttemptSettlementRepository.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/outbox/OutboxServerAttemptSettlementRepository.kt>)
- [ArenaTicketWireParser.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/remote/ArenaTicketWireParser.kt>)
- [AppDatabase.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt>)

## Каталог и runner: presentation (ТЗ 00, 03, 05)

- [InternetTabComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/tab/InternetTabComponent.kt>)
- [ArenaLessonCatalogComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/ArenaLessonCatalogComponent.kt>)
- [DefaultArenaLessonCatalogComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultArenaLessonCatalogComponent.kt>)
- [ArenaLessonCatalogScreen.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ArenaLessonCatalogScreen.kt>)
- [DefaultQuizzesComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt>)
- [QuizzesConfig.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/config/QuizzesConfig.kt>)
- [QuizzesPresentationModule.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/di/QuizzesPresentationModule.kt>)
- [DefaultLessonRunnerRootComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/DefaultLessonRunnerRootComponent.kt>)
- [RunnerStateHolder.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/RunnerStateHolder.kt>)
- [LessonRunnerRootComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/LessonRunnerRootComponent.kt>)
- [RunnerUiState.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/state/RunnerUiState.kt>)
- [LessonRunnerScreen.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/LessonRunnerScreen.kt>)

## Профиль, баланс, магазин, Billing (ТЗ 02, 09, 10)

- [FirebaseProfileRemoteDataSource.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/profile/FirebaseProfileRemoteDataSource.kt>)
- [UserProfile.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/internet/profile/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/internet/profile/domain/model/UserProfile.kt>)
- [RawUserStats.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/stats/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/stats/RawUserStats.kt>)
- [UserStatsEntity.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/UserStatsEntity.kt>)
- [ProfileRepositoryImpl.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/internet/profile/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/internet/profile/data/ProfileRepositoryImpl.kt>)
- [UserStatsRepositoryImpl.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/app-shell/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/data/UserStatsRepositoryImpl.kt>)
- [GetShopCatalogUseCase.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/economy/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/domain/use_case/GetShopCatalogUseCase.kt>)
- [DefaultShopComponent.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/economy/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/economy/presentation/component/DefaultShopComponent.kt>)
- [PlayBillingRepository.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/platform/billing/src/main/java/com/tpov/schoolquiz/platform/billing/PlayBillingRepository.kt>)
- [BillingRepository.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/economy/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/domain/repository/BillingRepository.kt>)
- [GiftBoxRepositoryImpl.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/economy/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/data/GiftBoxRepositoryImpl.kt>)
- [GiftBoxIntentStore.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/economy/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/data/GiftBoxIntentStore.kt>)

## Премиум, коробки и покупки: сервер (ТЗ 09, 10)

- [box-service.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/box-service.js>)
- [gift-boxes.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/gift-boxes.js>)
- [premium-grants.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/premium-grants.js>)
- [premium-economy.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/premium-economy.js>)
- [purchase-revocations.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/purchase-revocations.js>)
- [purchase-verification.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/purchase-verification.js>)
- [purchase-notifications.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/purchase-notifications.js>)
- [purchase-settlement.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/purchase-settlement.js>)
- [purchase-clawback.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/purchase-clawback.js>)
- [play-developer-api.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/play-developer-api.js>)
- [package.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/package.json>)
- [package-lock.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/package-lock.json>)

## Турниры (ТЗ 07, 08)

- [course-tournament-controller.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/course-tournament-controller.js>)
- [tournament-source-bank.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-source-bank.js>)
- [tournament-bank.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-bank.js>)
- [tournament-policy.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-policy.js>)
- [tournament-service.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-service.js>)
- [tournament-session.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-session.js>)
- [tournament-session-results.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-session-results.js>)
- [tournament-ranking.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-ranking.js>)
- [tournament-qualification.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-qualification.js>)
- [tournament-qualification-freeze.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-qualification-freeze.js>)
- [tournament-qualification-scan.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-qualification-scan.js>)
- [tournament-worker.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/functions/tournament-worker.js>)
- [TournamentEventScreen.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/TournamentEventScreen.kt>)

## Проверки и тестовые контракты

- [ArenaTicketJournalPersistenceTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/ArenaTicketJournalPersistenceTest.kt>)
- [ArenaTicketJournalTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/arena/ArenaTicketJournalTest.kt>)
- [ArenaJournalFakes.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/arena/ArenaJournalFakes.kt>)
- [ArenaRunnerCheckpointCodecTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/feature/lesson-runner/data/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/arena/ArenaRunnerCheckpointCodecTest.kt>)
- [ArenaLessonCatalogScreenTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/quizzes-screen/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/ArenaLessonCatalogScreenTest.kt>)
- [AppShellScreenTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/app-shell/presentation/src/androidTest/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/AppShellScreenTest.kt>)
- [DefaultRootComponentTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/DefaultRootComponentTest.kt>)
- [AppDatabaseMigrationsTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseMigrationsTest.kt>)
- [MigrationCoverageTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/shared/core/persistence/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/MigrationCoverageTest.kt>)
- [FirebaseArenaTicketRemoteDataSourceTest.kt](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/platform/firebase/src/test/kotlin/com/tpov/schoolquiz/platform/firebase/arena/FirebaseArenaTicketRemoteDataSourceTest.kt>)
- [mvp-arena-catalog-emulator-test.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/scripts/mvp-arena-catalog-emulator-test.js>)
- [mvp-arena-emulator-test.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/scripts/mvp-arena-emulator-test.js>)
- [mvp-qualification-firestore-test.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/scripts/mvp-qualification-firestore-test.js>)
- [tournament-emulator-e2e.js](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/scripts/tournament-emulator-e2e.js>)
- [mvp-arena-catalog-http.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/docs/testing/mvp-arena-catalog-http.md>)
- [mvp-qualification-firestore.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/docs/testing/mvp-qualification-firestore.md>)
- [03-open-synced-home.yaml](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/integration-edit/.maestro/mvp/03-open-synced-home.yaml>)

## Рабочие исследования (предложения, не реализованный код)

- [arena-entry-ui-proposal.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/arena-entry-ui-proposal.md>)
- [arena-run-write-proposal.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/arena-run-write-proposal.md>)
- [arena-runner-integration-proposal.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/arena-runner-integration-proposal.md>)
- [JOURNAL-GATE-NOTES.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/JOURNAL-GATE-NOTES.md>)

## Проверенные шаблоны запуска (читать; старые логи не перезаписывать)

- [run-arena-ui-session-r3.sh](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/run-arena-ui-session-r3.sh>)
- [run-arena-navigation-r1.sh](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/run-arena-navigation-r1.sh>)
- [run-arena-ui-device-r1.sh](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/run-arena-ui-device-r1.sh>)
- [run-arena-ui-oracle-r2.sh](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/run-arena-ui-oracle-r2.sh>)
- [run-journal-migrations-r1.sh](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/run-journal-migrations-r1.sh>)
- [run-maestro-journal-r3.sh](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/run-maestro-journal-r3.sh>)
- [summarize-jvm-reports.py](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/summarize-jvm-reports.py>)

## История исследования/реализации

- [SCHOOL-QUIZ-MERGE-RESEARCH.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/outputs/SCHOOL-QUIZ-MERGE-RESEARCH.md>)
- [SCHOOL-QUIZ-LOGIC-REVIEW.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/outputs/SCHOOL-QUIZ-LOGIC-REVIEW.md>)
- [SCHOOL-QUIZ-IMPLEMENTATION.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/outputs/SCHOOL-QUIZ-IMPLEMENTATION.md>)

## Пакеты заготовок — статус обязателен

### arena-session-boundary

Статус: `IMPORTED_UNCOMMITTED_WITH_ROOT_TEST_OVERRIDE`. Manifest SHA-256: `6ddfd7b4a2c004f754c1ab5c5751221c9eeb9de63a7dece840ce96370e95a40e`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-session-boundary/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-session-boundary/README.md>)

### arena-catalog-ui

Статус: `IMPORTED_UNCOMMITTED`. Manifest SHA-256: `c6cefdb9979d40a60e0cbb05f3fcb072bfb07ed56340c057d2b3721ea707e649`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-catalog-ui/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-catalog-ui/README.md>)

### arena-internet-default-fix

Статус: `IMPORTED_UNCOMMITTED_DEVICE_RETEST_PENDING`. Manifest SHA-256: `a9361cbb64d7996fdb9b202cd2823313207863e2582c3468eb1cdf234dd08f8f`.

- [manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-internet-default-fix/manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-internet-default-fix/README.md>)

### arena-entry-facade

Статус: `REVIEWED_NOT_IMPORTED_NOT_COMPILED`. Manifest SHA-256: `543ba9918dddf0a387744e7323a3b767b29443e3f3e0825ee1aafa9582ad7c9d`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-entry-facade/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-entry-facade/README.md>)

### arena-run-read

Статус: `REVISION_2_REVIEWED_NOT_IMPORTED_NOT_COMPILED`. Manifest SHA-256: `d671d16ab5b6dd1372f608b284add793b1e3d7d398cf6b25accde1eeaecac0aa`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-run-read/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-run-read/README.md>)

### arena-entry-koin-wiring

Статус: `REVIEWED_NOT_IMPORTED_NOT_COMPILED`. Manifest SHA-256: `646cfc396e737590d24b61c94c746917d1fc4dbc42aae35a483fd9440de56055`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-entry-koin-wiring/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-entry-koin-wiring/README.md>)

### arena-checkpoint-write-guards

Статус: `DRAFT_NOT_ROOT_REVIEWED_NOT_IMPORTED_NOT_COMPILED`. Manifest SHA-256: `6aba81946c9e955a5199612a114e3887faa442a7cc2e37b092b1bf879871542f`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-checkpoint-write-guards/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-checkpoint-write-guards/README.md>)

### arena-catalog-ui-smoke-r2

Статус: `NOT_IMPORTED_ORACLE_PROBE_FAILED_FLOW_NOT_RUN`. Manifest SHA-256: `e7ebb3c6d3338b702dc938b6d63b97cd495524c8a3d3cabdbad23a8abca9ba49`.

- [manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-catalog-ui-smoke-r2/manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-catalog-ui-smoke-r2/README.md>)

### arena-fixed-user-protocol-diagnostic

Статус: `DRAFT_NOT_ROOT_CODE_REVIEWED_NOT_RUN_ON_SERVER`. Manifest SHA-256: `70123e7970d339fd2068e6007b10bd3ddc2e27c8b4dafafecccc517e52051b55`.

- [manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-fixed-user-protocol-diagnostic/manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/arena-fixed-user-protocol-diagnostic/README.md>)

### qualification-compact-spike

Статус: `PROTOTYPE_ONLY_NOT_PRODUCTION_EQUIVALENT`. Manifest SHA-256: `2c8b174df91cdd64280c96eac2910ecb88e0294fe4ab1b0df37ff0440f48fc98`.

- [handoff-manifest.json](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/qualification-compact-spike/handoff-manifest.json>)
- [README.md](</Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/implementation-drafts/qualification-compact-spike/README.md>)
