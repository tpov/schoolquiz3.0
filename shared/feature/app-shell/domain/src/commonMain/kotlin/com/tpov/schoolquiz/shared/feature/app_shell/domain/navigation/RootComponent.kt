package com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation

import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for the root navigation component.
 *
 * Pure Kotlin / coroutines — no Decompose types in this interface.
 * Flow<AppShellState> is kotlinx.coroutines, allowed in domain per domain-models.md.
 *
 * Implementation: DefaultRootComponent in android/feature/app-shell/presentation
 * Spec NFR #1, ADR-0011 (split interface/impl).
 */
interface RootComponent {
    val appShellState: Flow<AppShellState>
    val events: Flow<RootEvent>

    /**
     * Что игрок может узнать про синхронизацию (AD-14).
     *
     * Соседний поток, а не поле [AppShellState]: состояние оболочки описывает навигацию и
     * пересобирается на каждом переходе, а снимок синхронизации приходит из своего репозитория и
     * с навигацией не связан. Экраны читают снимок отсюда и получают его параметром — сами они
     * ни к репозиторию, ни к Koin не ходят.
     */
    val syncStatus: Flow<SyncStatus>

    fun onDestination(destination: Destination)

    fun onActiveTabRetap(tab: Tab): RetapOutcome

    fun onDeepLink(deepLink: DeepLink)

    /** Called when user taps the version label. [nowMillis] is epoch milliseconds. */
    fun onVersionTap(nowMillis: Long)

    /** Called when user taps the SyncNow footer action. */
    fun onSyncNow()
}
