package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseArenaTicketRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseArenaWalletRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseQuestionWindowRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseEconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseEconomyReceiptsDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseEconomyRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseGiftBoxRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseGiftClaimRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseOwnHistoryRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebasePurchaseVerifier
import com.tpov.schoolquiz.platform.firebase.exam.FirebaseExamCertificateRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.exam.FirebaseExamSittingRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.lesson_result.FirebaseLessonResultRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.lesson_runner.FirebasePrivateQuizRunRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.network.EconomyCapturedTermsJson
import com.tpov.schoolquiz.platform.firebase.network.FirebaseMutationTransport
import com.tpov.schoolquiz.platform.firebase.network.FirebaseOutboxSessionProvider
import com.tpov.schoolquiz.platform.firebase.network.namingFailures
import com.tpov.schoolquiz.platform.firebase.nickname.FirebaseLogoRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.nickname.FirebaseNicknameRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.profile.FirebaseProfileRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseInboxRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseLessonLifecycleRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestPrivateRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseReviewDecisionsRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseReviewFeeQuoteRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.social.FirebasePollsRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseCatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseLessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.tournament.FirebaseTournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.tournament.FirebaseTournamentRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.verification.FirebaseVerificationRemoteDataSource
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyReceiptsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftClaimRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.OwnHistoryRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.VerificationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.social.data.remote.PollsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaWalletRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizRunRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.InboxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonLifecycleRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewFeeQuoteRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentEventRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentMeetingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentOperationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.tournament.domain.remote.TournamentSessionReadRemoteDataSource
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val firebaseModule =
    module {
        single<FirebaseFirestore> { FirebaseFirestore.getInstance() }
        single<FirebaseFunctions> { FirebaseFunctions.getInstance() }
        single { FirebaseOutboxSessionProvider(FirebaseAuth.getInstance()) }
        single<OutboxSessionProvider> { get<FirebaseOutboxSessionProvider>() }
        single<ArenaSessionObserver> { get<FirebaseOutboxSessionProvider>() }
        single<ArenaWalletRemoteDataSource> {
            FirebaseArenaWalletRemoteDataSource(
                firestore = get(),
                sessions = get(),
                network = get(),
                gate = get(),
                dispatcher = Dispatchers.IO,
                clock = get(),
            )
        }
        single {
            FirebaseArenaTicketRemoteDataSource(
                gate = get(),
                functions = get(),
                sessions = get(),
                networkMonitor = get(),
                dispatcher = Dispatchers.IO,
            )
        }
        single<ArenaTicketRemoteDataSource> { get<FirebaseArenaTicketRemoteDataSource>() }
        single<ArenaTicketStatusRemoteDataSource> { get<FirebaseArenaTicketRemoteDataSource>() }
        single {
            FirebaseArenaLessonCatalogRemoteDataSource(
                functions = get(),
                sessions = get(),
                networkMonitor = get(),
                dispatcher = Dispatchers.IO,
            )
        }
        single<ArenaLessonCatalogRemoteDataSource> { get<FirebaseArenaLessonCatalogRemoteDataSource>() }
        // Онлайн-O2 (ретро A1): окно перехода поднимается лениво — разрешение не трогает ни SDK,
        // ни сессию, ни сеть; вызовы окна начинаются только с явного Continue-из-паузы в хосте.
        // Конкретный тип зарегистрирован рядом с интерфейсом — как у арены: проводные тесты
        // ссылаются на бетон.
        single {
            FirebaseQuestionWindowRemoteDataSource(
                functions = get(),
                sessions = get(),
                networkMonitor = get(),
                dispatcher = Dispatchers.IO,
            )
        }
        single<QuestionWindowRemoteDataSource> { get<FirebaseQuestionWindowRemoteDataSource>() }
        // Соревнование (история 5.9): прямые вызовы через аренный транспортный образец; мутации
        // сессии едут из очереди, поэтому здесь только чтения, подготовка, операции и запись
        // скачивания. Строгие разборы и фасад привязывает tournamentDataModule.
        single {
            FirebaseTournamentRemoteDataSource(
                functions = get(),
                sessions = get(),
                networkMonitor = get(),
                dispatcher = Dispatchers.IO,
                eventParser = get(),
                meetingParser = get(),
                sessionParser = get(),
            )
        }
        single<TournamentEventRemoteDataSource> { get<FirebaseTournamentRemoteDataSource>() }
        single<TournamentMeetingRemoteDataSource> { get<FirebaseTournamentRemoteDataSource>() }
        single<TournamentSessionReadRemoteDataSource> { get<FirebaseTournamentRemoteDataSource>() }
        single<TournamentOperationRemoteDataSource> { get<FirebaseTournamentRemoteDataSource>() }
        // Экзамен (CAP-21/22): шесть вызовов сидинга — списание делает сервер, повторы
        // решает режимный адаптер по типу отказа. Конкретный тип зарегистрирован рядом с
        // интерфейсом — как у арены: проводные тесты ссылаются на бетон.
        single {
            FirebaseExamSittingRemoteDataSource(
                functions = get(),
                sessions = get(),
                networkMonitor = get(),
                dispatcher = Dispatchers.IO,
            )
        }
        single<ExamSittingRemoteDataSource> { get<FirebaseExamSittingRemoteDataSource>() }
        // Сертификаты (CAP-22): профиль читает свою проекцию; верификация работает вне входа.
        single {
            FirebaseExamCertificateRemoteDataSource(
                functions = get(),
                dispatcher = Dispatchers.IO,
            )
        }
        single<ExamCertificateRemoteDataSource> { get<FirebaseExamCertificateRemoteDataSource>() }
        // Единственный адрес очереди (AD-6): новая операция появляется обработчиком на сервере,
        // а не новым путём на клиенте. Захваченное согласие платных операций едет в конверте
        // (CAP-16): сервер сверяет его с текущей таблицей, и отказ по согласию паркует запись,
        // а не карантинит.
        single<MutationTransport> {
            FirebaseMutationTransport(
                functions = get(),
                networkMonitor = get(),
                capturedTerms = EconomyCapturedTermsJson(gate = get()),
            )
        }
        single<UserStatsDataSource> {
            FirebaseUserStatsDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
            ).namingFailures()
        }
        single<ProfileRemoteDataSource> {
            FirebaseProfileRemoteDataSource(functions = get()).namingFailures()
        }
        single<NicknameRemoteDataSource> {
            FirebaseNicknameRemoteDataSource(functions = get()).namingFailures()
        }
        single<LogoRemoteDataSource> { FirebaseLogoRemoteDataSource(functions = get()).namingFailures() }
        single<VerificationRemoteDataSource> {
            FirebaseVerificationRemoteDataSource(
                functions = get(),
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
            )
        }
        single<EconomyConstantsRemoteDataSource> { FirebaseEconomyConstantsRemoteDataSource(get()).namingFailures() }
        single<EconomyRemoteDataSource> {
            FirebaseEconomyRemoteDataSource(functions = get(), networkMonitor = get())
        }
        single<GiftBoxRemoteDataSource> {
            FirebaseGiftBoxRemoteDataSource()
        }
        // Заявки подарков (7.4): прямой вызываемый с тем же разбором конверта, что у квитанций
        // открытия; тело едет ровно то, что лежало в намерении.
        single<GiftClaimRemoteDataSource> {
            FirebaseGiftClaimRemoteDataSource(functions = get(), networkMonitor = get())
        }
        single<EconomyReceiptsRemoteDataSource> {
            FirebaseEconomyReceiptsDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
            )
        }
        // Денежный вызов: прямой и синхронный, не через приёмник отложенных мутаций (ADM-5).
        single<PurchaseVerifier> {
            FirebasePurchaseVerifier(functions = get(), networkMonitor = get())
        }
        single<LessonResultRemoteDataSource> {
            FirebaseLessonResultRemoteDataSource(functions = get())
        }
        single<CatalogSyncChangeRemoteDataSource> {
            FirebaseCatalogSyncChangeRemoteDataSource(firestore = get(), policy = get()).namingFailures()
        }
        single<LessonContentSyncChangeRemoteDataSource> {
            FirebaseLessonContentSyncChangeRemoteDataSource(firestore = get(), policy = get()).namingFailures()
        }
        single<QuestArenaSubmissionRemoteDataSource> {
            FirebaseQuestArenaSubmissionRemoteDataSource(functions = get()).namingFailures()
        }
        single<LessonLifecycleRemoteDataSource> {
            FirebaseLessonLifecycleRemoteDataSource(functions = get())
        }
        single<ReviewFeeQuoteRemoteDataSource> {
            FirebaseReviewFeeQuoteRemoteDataSource(functions = get())
        }

        // «Решения» админов (4.17, UX-22): чтение страницы вердиктов и отметки ✓/✗ — прямые
        // callable за requireVerifier; отказ роли транспорт переводит в свой тип исключения.
        single<ReviewDecisionsRemoteDataSource> {
            FirebaseReviewDecisionsRemoteDataSource(functions = get())
        }

        single<PrivateQuizRunRemoteDataSource> {
            FirebasePrivateQuizRunRemoteDataSource(functions = get())
        }

        single<OwnHistoryRemoteDataSource> {
            FirebaseOwnHistoryRemoteDataSource(functions = get())
        }
        single<InboxRemoteDataSource> {
            FirebaseInboxRemoteDataSource(functions = get())
        }
        single<PollsRemoteDataSource> {
            FirebasePollsRemoteDataSource(functions = get())
        }
        single<QuestPrivateRemoteDataSource> {
            FirebaseQuestPrivateRemoteDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
                policy = get(),
            ).namingFailures()
        }
        single<TournamentLeaderboardRemoteDataSource> {
            FirebaseTournamentLeaderboardRemoteDataSource(functions = get())
        }
    }
