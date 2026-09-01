package com.tpov.schoolquiz.apps.android_next

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.defaultComponentContext
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
import com.tpov.schoolquiz.platform.android_services.sync.SyncPreferences
import com.tpov.schoolquiz.shared.core.sync.ForceResync
import kotlinx.coroutines.CoroutineScope
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf

private const val DESIGN_PREFS_NAME = "schoolquiz_design"
private const val DESIGN_STYLE_KEY = "selected_design_style"

class MainActivity : AppCompatActivity() {
    private lateinit var rootComponent: DefaultRootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootComponent = get { parametersOf(defaultComponentContext()) }

        // The stored cadences are reconciled on every launch: this (re)arms the periodic workers
        // after a force-stop or an app update, and fires the one-shot for "every launch".
        val syncScheduler = get<SyncScheduler>()
        // Живёт столько же, сколько процесс: см. AppApplication.appScopeModule.
        val appScope = get<CoroutineScope>()
        val syncPreferences = SyncPreferences(this)
        val storedFrequency = syncPreferences.read()
        val storedProfileFrequency = syncPreferences.readProfile()
        syncScheduler.applyFrequency(storedFrequency)
        syncScheduler.applyProfileFrequency(storedProfileFrequency)
        // Каденции две и они независимы: «при запуске» для профиля обязано поднимать профильный
        // воркер, а не полный контентный список.
        if (storedFrequency == SyncFrequency.ON_LAUNCH) syncScheduler.enqueueManualSync()
        if (storedProfileFrequency == SyncFrequency.ON_LAUNCH) syncScheduler.enqueueManualProfileSync()

        setContent {
            val preferences = remember { getSharedPreferences(DESIGN_PREFS_NAME, MODE_PRIVATE) }
            var selectedDesignStyle by remember {
                mutableStateOf(
                    preferences.getString(DESIGN_STYLE_KEY, null).toSchoolQuizDesignStyle(),
                )
            }
            // Состояние синхронизации — единственное, из чего игрок узнаёт, что действия ждут
            // отправки или что одно уже не уедет (AD-14).
            val syncStatus by get<SyncStatusRepository>().observeStatus()
                .collectAsStateWithLifecycle(initialValue = SyncStatus())
            var syncFrequency by remember { mutableStateOf(storedFrequency) }
            var profileSyncFrequency by remember { mutableStateOf(storedProfileFrequency) }

            SchoolQuizTheme(designStyle = selectedDesignStyle) {
                NoirTheme {
                    AppShellScreen(
                        rootComponent = rootComponent,
                        appVersionName = BuildConfig.VERSION_NAME,
                        appVersionCode = BuildConfig.VERSION_CODE,
                        isDebugBuild = BuildConfig.DEBUG,
                        syncFrequency = syncFrequency,
                        onSyncFrequencySelected = { frequency ->
                            syncFrequency = frequency
                            syncPreferences.write(frequency)
                            syncScheduler.applyFrequency(frequency)
                            if (frequency == SyncFrequency.ON_LAUNCH) {
                                syncScheduler.enqueueManualSync()
                            }
                        },
                        syncStatus = syncStatus,
                        onForceResync = {
                            // Долгая операция: перечитывается всё содержимое. Живёт в области
                            // самой Activity, чтобы уход с экрана её отменял.
                            // Не lifecycleScope: перечитывание всего содержимого длится дольше
                            // поворота экрана, а отменённое на середине оно оставляет курсоры
                            // обнулёнными и половину журнала непрочитанной.
                            appScope.launch { get<ForceResync>().run() }
                        },
                        profileSyncFrequency = profileSyncFrequency,
                        onProfileSyncFrequencySelected = { frequency ->
                            profileSyncFrequency = frequency
                            syncPreferences.writeProfile(frequency)
                            syncScheduler.applyProfileFrequency(frequency)
                            if (frequency == SyncFrequency.ON_LAUNCH) {
                                syncScheduler.enqueueManualProfileSync()
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.dataString ?: return
        if (!uri.startsWith("schoolquiz://")) return
        rootComponent.onDeepLink(DeepLink(uri))
    }
}

private fun String?.toSchoolQuizDesignStyle(): SchoolQuizDesignStyle =
    when (this) {
        "MainLegacy" -> SchoolQuizDesignStyle.Main
        else ->
            this
                ?.let { value -> runCatching { SchoolQuizDesignStyle.valueOf(value) }.getOrNull() }
                ?: SchoolQuizDesignStyle.Main
    }
