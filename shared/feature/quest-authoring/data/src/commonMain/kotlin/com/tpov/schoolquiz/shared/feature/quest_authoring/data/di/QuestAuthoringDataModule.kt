package com.tpov.schoolquiz.shared.feature.quest_authoring.data.di

import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.InboxRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.LessonProposalRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.LessonLifecycleRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestReviewOutcomeRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewDecisionsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewFeeQuoteRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaQuarantineRollback
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.provider.DefaultQuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.InboxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonProposalQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonLifecycleRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewFeeQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.InboxRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestReviewOutcomeRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewFeeQuoteRepository
import org.koin.dsl.module

val questAuthoringDataModule =
    module {
        single<QuestAuthoringLocalDataSource> { QuestAuthoringLocalDataSourceImpl(get()) }
        single<QuestAuthoringRepository> { QuestAuthoringRepositoryImpl(get(), get(), get()) }
        // Проекции TZ06B и кураторский переход — прямой callable без локальной половины (AD-25);
        // транспортBinding (Firebase) подключается в platform-модуле.
        single<LessonLifecycleRepository> {
            LessonLifecycleRepositoryImpl(
                remote = get<LessonLifecycleRemoteDataSource>(),
            )
        }
        // Общий inbox (AD-20): чтение — view-транспорт (Firebase), подача отчёта — общая очередь.
        single<InboxRepository> {
            InboxRepositoryImpl(remote = get<InboxRemoteDataSource>(), outbox = get<OutboxStore>())
        }
        // Цитата платы подачи (4.11): только чтение, локальной половины нет — тариф и priorCycle
        // живут на сервере, кэш цитаты только врал бы.
        single<ReviewFeeQuoteRepository> {
            ReviewFeeQuoteRepositoryImpl(remote = get<ReviewFeeQuoteRemoteDataSource>())
        }
        // Предложения автору (4.22, AD-33): подача отчёта — общая очередь, цитата — читающий
        // callable; транспортBinding цитаты подключается в platform-модуле.
        single<LessonProposalRepository> {
            LessonProposalRepositoryImpl(outbox = get<OutboxStore>(), quoteRemote = get<LessonProposalQuoteRemoteDataSource>())
        }
        // Исход модельной проверки (4.16): только чтение и доведение статуса; правда о вердиктах
        // живёт на сервере, кэш только врал бы между свипами воркера.
        single<QuestReviewOutcomeRepository> {
            QuestReviewOutcomeRepositoryImpl(remote = get<QuestArenaSubmissionRemoteDataSource>())
        }
        // «Решения» админов (4.17, UX-22): прямые callable — чтение страницы вердиктов и отметки
        // ✓/✗; правда о вердиктах и отметках живёт на сервере, локального кэша нет.
        single<ReviewDecisionsRepository> {
            ReviewDecisionsRepositoryImpl(remote = get<ReviewDecisionsRemoteDataSource>())
        }
        // Откат локальной половины при карантине — решение владеющей фичи (AD-28).
        single { QuestArenaQuarantineRollback(get(), get()) }
        single<QuestAuthoringIdProvider> { DefaultQuestAuthoringIdProvider() }
        single<QuestAuthoringTimestampProvider> { DefaultQuestAuthoringTimestampProvider() }
    }
