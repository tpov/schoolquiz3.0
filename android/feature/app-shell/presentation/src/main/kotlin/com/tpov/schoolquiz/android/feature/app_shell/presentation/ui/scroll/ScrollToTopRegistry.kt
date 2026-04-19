package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll

import androidx.compose.runtime.staticCompositionLocalOf
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab

/**
 * Registry mapping Tab → active ScrollToTopHook.
 *
 * Identity-aware unregister: prevents Crossfade overlap from accidentally
 * removing newly registered hook when outgoing screen disposes.
 *
 * Main thread invariant: register/unregister called only from Compose (Main thread).
 * ADR-COMP-06.
 */
class ScrollToTopRegistry {
    private val hooks = mutableMapOf<Tab, ScrollToTopHook>()

    fun register(
        tab: Tab,
        hook: ScrollToTopHook,
    ) {
        hooks[tab] = hook
    }

    /** Identity check (===): only removes entry if stored instance IS the same reference. */
    fun unregister(
        tab: Tab,
        hook: ScrollToTopHook,
    ) {
        if (hooks[tab] === hook) hooks.remove(tab)
    }

    fun current(tab: Tab): ScrollToTopHook? = hooks[tab]
}

val LocalScrollToTopRegistry =
    staticCompositionLocalOf<ScrollToTopRegistry> {
        error("ScrollToTopRegistry not provided — wrap in CompositionLocalProvider")
    }
