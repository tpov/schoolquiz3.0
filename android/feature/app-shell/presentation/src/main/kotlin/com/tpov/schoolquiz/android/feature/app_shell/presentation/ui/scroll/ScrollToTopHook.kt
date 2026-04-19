package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll

/**
 * Interface for screens that can scroll to top.
 * Implemented per-screen by scrollable content.
 * Called by AppShellScreen on RetapOutcome.NO_OP.
 * Spec: 0-spec.md:82. ADR-COMP-06.
 */
interface ScrollToTopHook {
    /** @return true if scroll happened, false if already at top */
    suspend fun scrollToTop(): Boolean
}
