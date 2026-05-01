package com.tpov.schoolquiz.apps.android_next

import android.app.Application
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
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.di.questAuthoringPresentationModule
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
import com.tpov.schoolquiz.apps.android_next.di.syncModule
import com.tpov.schoolquiz.platform.android_services.sync.SyncWorker
import com.tpov.schoolquiz.platform.firebase.di.firebaseCatalogModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseLessonModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseQuestionModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseSectionModule
import com.tpov.schoolquiz.platform.firebase.di.firebaseThemeModule
import com.tpov.schoolquiz.platform.firebase.initializeFirebaseSecurity
import com.tpov.schoolquiz.shared.core.catalog.data.di.catalogDataModule
import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
import com.tpov.schoolquiz.shared.core.persistence.di.persistenceModule
import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
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

class AppApplication : Application(), Configuration.Provider {
    /**
     * Application-scoped CoroutineScope for shared hot Flows (auth UID, etc.).
     * Survives the entire process lifetime. Dispatchers.Default — non-blocking work
     * (Firebase auth listener trampolines internally; explicit dispatcher avoids
     * relying on kotlinx-coroutines fallback semantics).
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(GlobalContext.get().get<WorkerFactory>())
                .build()

    override fun onCreate() {
        super.onCreate()
        initializeFirebaseSecurity(this)
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
                persistenceModule,
                firebaseModule,
                firebaseCatalogModule,
                firebaseQuestModule,
                firebaseSectionModule,
                firebaseThemeModule,
                firebaseLessonModule,
                firebaseQuestionModule,
                appShellDataModule { sharedAuthUidFlow },
                appShellPresentationModule,
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
                lessonRunnerDataModule,
                lessonRunnerDomainKoinAdapter,
                lessonRunnerPresentationModule,
                syncModule,
            )
        }
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
}
