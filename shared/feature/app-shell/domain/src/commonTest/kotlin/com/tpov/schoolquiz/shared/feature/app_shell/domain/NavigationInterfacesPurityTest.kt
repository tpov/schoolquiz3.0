package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.RootComponent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Compile-time purity verification: Navigator and RootComponent are pure domain interfaces —
 * no Decompose types, no Android framework, only Kotlin + coroutines.
 *
 * If Decompose types leaked into either interface, anonymous implementations here would
 * fail to compile (domain commonTest has no Decompose dependency).
 *
 * Covers: overview.md lines 175-180, ADR-COMP-04, ADR-0011.
 */
class NavigationInterfacesPurityTest {

    @Test
    fun `Navigator can be implemented without Decompose dependency`() {
        val nav: Navigator = object : Navigator {
            override fun goTo(destination: Destination) = Unit
        }
        assertNotNull(nav)
    }

    @Test
    fun `RootComponent appShellState is pure coroutines Flow without Decompose`() {
        val rc: RootComponent = object : RootComponent {
            override val appShellState: Flow<AppShellState> = emptyFlow()
            override val events: Flow<RootEvent> = emptyFlow()
            override fun onDestination(destination: Destination) = Unit
            override fun onActiveTabRetap(tab: Tab): RetapOutcome = RetapOutcome.NO_OP
            override fun onDeepLink(deepLink: DeepLink) = Unit
            override fun onVersionTap(nowMillis: Long) = Unit
            override fun onSyncNow() = Unit
        }
        assertNotNull(rc)
        assertNotNull(rc.appShellState)
    }
}
