package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.FirebaseUserStatsDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseArenaTicketRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.arena.FirebaseArenaWalletRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseEconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseEconomyRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebaseGiftBoxRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.economy.FirebasePurchaseVerifier
import com.tpov.schoolquiz.platform.firebase.lesson_result.FirebaseLessonResultRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.network.FirebaseMutationTransport
import com.tpov.schoolquiz.platform.firebase.network.FirebaseOutboxSessionProvider
import com.tpov.schoolquiz.platform.firebase.network.namingFailures
import com.tpov.schoolquiz.platform.firebase.nickname.FirebaseLogoRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.nickname.FirebaseNicknameRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.profile.FirebaseProfileRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseQuestPrivateRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.quest_authoring.FirebaseReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseCatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.sync.FirebaseLessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.tournament.FirebaseTournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.platform.firebase.verification.FirebaseVerificationRemoteDataSource
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.PurchaseVerifier
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.VerificationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaWalletRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
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
        // Единственный адрес очереди (AD-6): новая операция появляется обработчиком на сервере,
        // а не новым путём на клиенте.
        single<MutationTransport> {
            FirebaseMutationTransport(functions = get(), networkMonitor = get())
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
        single<QuestPrivateRemoteDataSource> {
            FirebaseQuestPrivateRemoteDataSource(
                firestore = get(),
                auth = FirebaseAuth.getInstance(),
                policy = get(),
            ).namingFailures()
        }
        single<ReviewAssignmentRemoteDataSource> {
            FirebaseReviewAssignmentRemoteDataSource(functions = get(), policy = get()).namingFailures()
        }
        single<TournamentLeaderboardRemoteDataSource> {
            FirebaseTournamentLeaderboardRemoteDataSource(functions = get())
        }
    }
