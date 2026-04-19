package com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination

/**
 * Single navigation entry-point for all feature-presentation modules.
 *
 * KMP-pure: no Android, no Decompose in this interface.
 * Feature modules depend only on Navigator + Destination — never on RootComponent directly.
 *
 * Spec FR #16, NFR #3. See ADR-COMP-04, ADR-0011.
 * Implementation: NavigatorImpl in android/feature/app-shell/presentation
 */
interface Navigator {
    fun goTo(destination: Destination)
}
