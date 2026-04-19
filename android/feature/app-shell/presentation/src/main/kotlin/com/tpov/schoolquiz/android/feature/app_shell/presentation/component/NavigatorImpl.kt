package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.RootComponent

/**
 * Delegates Navigator.goTo() → RootComponent.onDestination().
 * Created inside DefaultRootComponent.init{} — not a separate Koin binding.
 */
class NavigatorImpl(
    private val rootComponent: RootComponent,
) : Navigator {
    override fun goTo(destination: Destination) {
        rootComponent.onDestination(destination)
    }
}
