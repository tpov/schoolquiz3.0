package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerContentSyncCallbacks
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaConsentHandles
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunResultServices
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunServices
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultArenaRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.LessonRunnerUseCases
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.TermsConsentVerdict
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyTermsConsentGate
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonConsentHandles
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonCommentRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.GetResultAdviceUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchLessonEditCopyUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.QuoteLessonProposalFeeUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitCopyEditProposalUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitErrorReportProposalUseCase
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val lessonRunnerPresentationModule =
    module {
        single<ArenaRunnerComponentFactory> {
            val services =
                ArenaRunServices(
                    runs = get(),
                    entries = get(),
                    sessions = get(),
                    sessionEvents = get(),
                    wallet = get(),
                    result = ArenaRunResultServices(
                    settlements = get(),
                    ratings = get(),
                    sync = get(),
                    consent = ArenaConsentHandles(
                        satisfied = { get<EconomyTermsConsentGate>().verdict() == TermsConsentVerdict.Sufficient },
                        accept = { get<EconomyTermsConsentGate>().acceptCurrentTerms() },
                    ),
                ),
                // Онлайн-O2 (ретро A1): окно перехода + проверка связи для бокового канала
                // Continue-из-паузы. Оба nullable: сборка без firebase-графа (тесты) продолжает
                // ездить на локальном таймере (CAP-1) — окно best-effort, не гейт.
                windows = getOrNull(),
                online = { get<NetworkMonitor>().isOnline() },
                )
            val clock = get<kotlinx.datetime.Clock>()
            ArenaRunnerComponentFactory { context, request ->
                DefaultArenaRunnerRootComponent(context, request, services, clock)
            }
        }
        factory { parameters ->
            DefaultLessonRunnerRootComponent(
                componentContext = parameters.get<ComponentContext>(),
                lessonId = parameters.get<LessonId>(),
                mode = parameters.get<Difficulty>(),
                sessionMode = parameters.get<SessionMode>(),
                contentSyncCallbacks = parameters.get<LessonRunnerContentSyncCallbacks>(),
                useCases =
                    LessonRunnerUseCases(
                        startAttempt = get<StartLessonAttemptUseCase>()::invoke,
                        completeAttempt = get<CompleteAttemptUseCase>()::invoke,
                        abortAttempt = get<AbortAttemptUseCase>()::invoke,
                        submitRating = get<SubmitLessonRatingUseCase>()::invoke,
                    ),
                lessonRepository = get(),
                attemptRepository = get(),
                attemptSyncMarks = get(),
                attemptSettlements = get(),
                syncScheduler = get(),
                profileRepository = get<ProfileRepository>(),
                commentRepository = get<LessonCommentRepository>(),
                // «Сообщить об ошибке» — предложение автору (4.22): подача в очередь и цитата
                // платы; uid — из авторизации, как у приватного квиза.
                submitErrorReportProposal = get<SubmitErrorReportProposalUseCase>(),
                quoteProposalFee = get<QuoteLessonProposalFeeUseCase>(),
                // «Предложить правку» (4.23): копия чужого урока и подача отмеченных правок.
                fetchLessonEditCopy = get<FetchLessonEditCopyUseCase>(),
                submitCopyEditProposal = get<SubmitCopyEditProposalUseCase>(),
                currentUid = { get<AuthRepository>().currentUid() },
                getResultAdvice = GetResultAdviceUseCase(lessonRepository = get()),
                clock = get(),
                analytics = get<AnalyticsTracker>(),
                consent = LessonConsentHandles(
                    satisfied = { get<EconomyTermsConsentGate>().verdict() == TermsConsentVerdict.Sufficient },
                    accept = { get<EconomyTermsConsentGate>().acceptCurrentTerms() },
                ),
            ) as LessonRunnerRootComponent
        }

        single<LessonRunnerComponentFactory> {
            LessonRunnerComponentFactory { ctx, lessonId, mode, sessionMode, contentSyncCallbacks ->
                get { parametersOf(ctx, lessonId, mode, sessionMode, contentSyncCallbacks) }
            }
        }

        // Приватный квиз (AD-29, история 8.4): дверь → один заход → подача очередью → результат.
        single<com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerComponentFactory> {
            com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerComponentFactory { ctx, request ->
                com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultPrivateQuizRunnerComponent(
                    componentContext = ctx,
                    request = request,
                    startPrivateQuizAttempt = get(),
                    repository = get(),
                    currentUid = { get<com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository>().currentUid().orEmpty() },
                    parser = get(),
                )
            }
        }

        // Экзамен (CAP-21/22): компонент прогона получает транспорт сидинга напрямую.
        single<com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam.ExamRunComponentFactory> {
            com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam.ExamRunComponentFactory
                .default(remote = get())
        }
    }
