package com.tpov.schoolquiz.apps.android_next

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
import com.tpov.schoolquiz.android.feature.economy.presentation.di.economyPresentationModule
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.di.profilePresentationModule
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.di.questAuthoringPresentationModule
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
import com.tpov.schoolquiz.apps.android_next.di.authModule
import com.tpov.schoolquiz.apps.android_next.di.syncModule
import com.tpov.schoolquiz.platform.android_services.attribution.InstallReferrerReader
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorker
import com.tpov.schoolquiz.platform.billing.di.billingModule
import com.tpov.schoolquiz.platform.firebase.di.analyticsModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseCatalogModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseLessonCommentModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseLessonModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestionModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseSectionModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseThemeModule
import com.tpov.schoolquiz.platform.firebase.initializeFirebaseSecurity
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.analytics.UserProperty
import com.tpov.schoolquiz.shared.core.catalog.data.di.catalogDataModule
import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
import com.tpov.schoolquiz.shared.core.persistence.di.persistenceModule
import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
import com.tpov.schoolquiz.shared.feature.economy.data.di.economyDataModule
import com.tpov.schoolquiz.shared.feature.economy.domain.di.economyDomainModule
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.di.leaderboardDataModule
import com.tpov.schoolquiz.shared.feature.internet.profile.data.di.profileDataModule
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.di.profileDomainModule
import com.tpov.schoolquiz.shared.feature.lesson.data.di.lessonDataModule
import com.tpov.schoolquiz.shared.feature.lesson.domain.di.lessonDomainModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
import com.tpov.schoolquiz.shared.feature.quest.data.di.questDataModule
import com.tpov.schoolquiz.shared.feature.quest.domain.di.questDomainModule
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.di.questAuthoringDataModule
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.di.questAuthoringDomainModule
import com.tpov.schoolquiz.shared.feature.question.data.di.questionDataModule
import com.tpov.schoolquiz.shared.feature.question.domain.di.questionDomainModule
import com.tpov.schoolquiz.shared.feature.section.data.di.sectionDataModule
import com.tpov.schoolquiz.shared.feature.section.domain.di.sectionDomainModule
import com.tpov.schoolquiz.shared.feature.theme.data.di.themeDataModule
import com.tpov.schoolquiz.shared.feature.theme.domain.di.themeDomainModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.Locale

class AppApplication : Application(), Configuration.Provider {
    /**
     * Application-scoped CoroutineScope for shared hot Flows (auth UID, etc.).
     * Survives the entire process lifetime. Dispatchers.Default — non-blocking work
     * (Firebase auth listener trampolines internally; explicit dispatcher avoids
     * relying on kotlinx-coroutines fallback semantics).
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Тот же самый scope, но доступный экранам.
     *
     * Нужен работе, которая обязана пережить экран: полное перечитывание содержимого длится
     * дольше поворота, а отменённое на середине оставляет курсоры обнулёнными и половину журнала
     * непрочитанной. Второй scope тут не подойдёт — смысл именно в том, что этот живёт столько же,
     * сколько процесс.
     */
    private val appScopeModule = module { single<CoroutineScope> { appScope } }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(GlobalContext.get().get<WorkerFactory>())
                .build()

    override fun onCreate() {
        super.onCreate()
        releaseForcedRussianLocaleOnce()
        initializeFirebaseSecurity(
            app = this,
            useDebugAppCheckProvider = BuildConfig.DEBUG,
            appCheckDebugSecret = BuildConfig.FIREBASE_APP_CHECK_DEBUG_SECRET,
        )
        val auth = FirebaseAuth.getInstance()
        // Ensure every user has a Firebase identity (anonymous) before any feature
        // tries to read authRepository.observeUid(). Without this, a fresh-install
        // user would see "Требуется авторизация" on lesson tap (AC spec assumes
        // authenticated user; no explicit login screen exists in this app version).
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
        // Cold callbackFlow: each collect would register a new AuthStateListener.
        val coldAuthUidFlow =
            callbackFlow<String?> {
                val listener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser?.uid) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
        // Codex Round 4 N2 fix: shareIn(WhileSubscribed) → single AuthStateListener
        // shared across all consumers (UserStatsRepositoryImpl + AuthRepositoryImpl + future
        // MyQuestsViewModel). Replay 1 ensures late subscribers get the current UID immediately.
        val sharedAuthUidFlow =
            coldAuthUidFlow.shareIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                replay = 1,
            )
        startKoin {
            androidContext(this@AppApplication)
            modules(
                appScopeModule,
                persistenceModule,
                firebaseModule,
                analyticsModule,
                billingModule,
                authModule,
                firebaseCatalogModule,
                firebaseQuestModule,
                firebaseSectionModule,
                firebaseThemeModule,
                firebaseLessonModule,
                firebaseLessonCommentModule,
                firebaseQuestionModule,
                appShellDataModule { sharedAuthUidFlow },
                profileDataModule { sharedAuthUidFlow },
                leaderboardDataModule,
                economyDataModule { sharedAuthUidFlow },
                appShellPresentationModule,
                profilePresentationModule,
                economyPresentationModule,
                questPresentationModule,
                questAuthoringPresentationModule,
                quizzesPresentationModule,
                catalogDataModule,
                catalogDomainModule,
                questDataModule,
                questDomainModule,
                questAuthoringDataModule,
                questAuthoringDomainModule,
                sectionDataModule,
                sectionDomainModule,
                themeDataModule,
                themeDomainModule,
                lessonDataModule,
                lessonDomainModule,
                questionDataModule,
                questionDomainModule,
                questionSchemaModule,
                profileDomainModule,
                economyDomainModule,
                lessonRunnerDataModule,
                lessonRunnerDomainKoinAdapter,
                lessonRunnerPresentationModule,
                syncModule,
            )
        }
        startMeasurement()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(
                SyncWorker.PERIODIC_INTERVAL.first,
                SyncWorker.PERIODIC_INTERVAL.second,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
        // Bootstrap one-shot sync: runs ONCE after install (first-ever launch).
        // Subsequent sync is handled by periodic WorkManager schedule + manual Sync Now.
        val prefs = getSharedPreferences("sync_state", MODE_PRIVATE)
        if (!prefs.getBoolean("bootstrap_done", false)) {
            workManager.enqueueUniqueWork(
                SyncWorker.WORK_NAME_BOOTSTRAP,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )
            prefs.edit().putBoolean("bootstrap_done", true).apply()
        }
    }

    /**
     * Turns the funnel on.
     *
     * Until this existed the app could not answer a single question about itself: how many people
     * opened a lesson, how many finished one, how many reached the shop, where an install came
     * from. Everything downstream — deciding whether a Telegram post is cheaper than an ad
     * network, whether retention is the problem — needs these three lines to have run.
     */
    private fun startMeasurement() {
        val analytics = GlobalContext.get().get<AnalyticsTracker>()
        // The resolved UI language, not the device's — this is the segment that tells us whether
        // the Ukrainian build is actually reaching Ukrainian users.
        analytics.setUserProperty(
            UserProperty.UI_LANGUAGE,
            resources.configuration.locales.get(0)?.language ?: Locale.getDefault().language,
        )
        InstallReferrerReader(
            context = this,
            analytics = analytics,
            appVersion = BuildConfig.VERSION_NAME,
        ).readOnce()
    }

    /**
     * Undoes the old hard-coded Russian locale, exactly once per install.
     *
     * The app used to call `setApplicationLocales(forLanguageTags("ru"))` on every launch, which
     * pinned every screen to Russian regardless of the device. That call is gone, but the choice
     * it made was **persisted** by AppCompat, so an existing install would keep opening in Russian
     * forever and a Ukrainian user would never see the Ukrainian build.
     *
     * Clearing it to the empty list hands the decision back to the system, after which Android
     * resolves values-uk, values-ru or the English default from the device language — and the
     * per-app language picker (Android 13+, enabled by locales_config.xml) keeps working, because
     * this runs once and then never touches the setting again.
     */
    private fun releaseForcedRussianLocaleOnce() {
        val prefs = getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FORCED_RU_RELEASED, false)) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        prefs.edit().putBoolean(KEY_FORCED_RU_RELEASED, true).apply()
    }

    private companion object {
        const val LOCALE_PREFS = "locale_state"
        const val KEY_FORCED_RU_RELEASED = "forced_ru_released"
    }
}
