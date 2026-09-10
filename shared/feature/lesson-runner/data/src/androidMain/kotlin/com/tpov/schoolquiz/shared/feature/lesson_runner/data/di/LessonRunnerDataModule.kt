package com.tpov.schoolquiz.shared.feature.lesson_runner.data.di

import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultQuarantineRollback
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.OutboxAttemptSyncMarkRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.OutboxServerAttemptSettlementRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.RoomLessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.AttemptSyncMarkRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.datetime.Clock
import org.koin.dsl.module

val lessonRunnerDataModule =
    module {
        includes(arenaDataModule)
        single<AttemptIdProvider> { DefaultAttemptIdProvider() }
        single<RandomSeedProvider> { DefaultRandomSeedProvider() }
        single<RatingIdProvider> { DefaultRatingIdProvider() }
        single<Clock> { Clock.System }
        single<LessonResultOutboxWriter> {
            RoomLessonResultOutboxWriter(
                lessonDao = get(),
                themeDao = get(),
                sectionDao = get(),
                questDao = get(),
                clock = get(),
            )
        }
        // Откат локальной половины при карантине — решение владеющей фичи (AD-28). Ядру он отдаётся
        // в composition root, где собирается маршрутизатор карантина.
        single<LessonResultQuarantineRollback> {
            LessonResultQuarantineRollback(attemptDao = get(), ratingDao = get(), arenaRatings = get())
        }
        // Пометка «не отправлено» на прохождении: спрашивается у ядра по `entity_ref`, поток
        // пересобирается на смене аккаунта от того же источника `uid`, что и остальные user-scoped
        // репозитории.
        single<AttemptSyncMarkRepository> {
            OutboxAttemptSyncMarkRepository(
                outbox = get<OutboxStore>(),
                currentUidFlow = { get<AuthRepository>().observeUid() },
            )
        }
        single<LessonAttemptRepository> {
            LessonAttemptRepositoryImpl(
                attemptDao = get(),
                outboxWriter = get(),
                repetitionDao = get(),
            )
        }
        single<ServerAttemptSettlementRepository> {
            OutboxServerAttemptSettlementRepository(
                receipts = get<MutationReceiptStore>(),
                currentUidFlow = { get<AuthRepository>().observeUid() },
            )
        }
        single<LessonRatingRepository> {
            LessonRatingRepositoryImpl(
                ratingLocalDao = get(),
                outboxWriter = get(),
            )
        }
    }
