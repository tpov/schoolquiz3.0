package com.tpov.schoolquiz.android.feature.app_shell.presentation

import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopHook
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopRegistry
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * JVM tests for [ScrollToTopRegistry] identity-aware unregister (ADR-COMP-06).
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-05/tests.md):
 *  - register_sets_hook_for_tab
 *  - unregister_with_same_reference_removes_hook
 *  - identity_unregister_does_not_remove_different_instance (ADR-COMP-06)
 *  - crossfade_overlap_incoming_hook_survives_outgoing_unregister
 *  - current_returns_null_for_unregistered_tab
 */
class ScrollToTopRegistryTest {

    private val registry = ScrollToTopRegistry()

    private fun fakeHook() = object : ScrollToTopHook {
        override suspend fun scrollToTop() = true
    }

    // GIVEN registry is empty WHEN register called THEN current returns that hook
    @Test
    fun `register sets hook for tab`() {
        val hook = fakeHook()
        registry.register(Tab.LOCAL, hook)
        assertEquals(hook, registry.current(Tab.LOCAL))
    }

    // GIVEN hook registered WHEN unregister called with same reference THEN current is null
    @Test
    fun `unregister with same reference removes hook`() {
        val hook = fakeHook()
        registry.register(Tab.LOCAL, hook)
        registry.unregister(Tab.LOCAL, hook)
        assertNull(registry.current(Tab.LOCAL))
    }

    // GIVEN hook1 registered, hook2 registered after WHEN hook1 unregisters THEN hook2 survives
    // ADR-COMP-06: identity check prevents stale unregister from removing newer hook
    @Test
    fun `identity_unregister does not remove different instance`() {
        val hook1 = fakeHook()
        val hook2 = fakeHook()
        registry.register(Tab.LOCAL, hook1)
        registry.register(Tab.LOCAL, hook2)
        registry.unregister(Tab.LOCAL, hook1)
        assertEquals(hook2, registry.current(Tab.LOCAL))
    }

    // GIVEN outgoing registered, incoming registers during Crossfade overlap
    // WHEN outgoing disposes THEN incoming hook survives (identity !== outgoing)
    @Test
    fun `crossfade_overlap incoming hook survives outgoing unregister`() {
        val outgoing = fakeHook()
        val incoming = fakeHook()
        registry.register(Tab.LOCAL, outgoing)
        registry.register(Tab.LOCAL, incoming)
        registry.unregister(Tab.LOCAL, outgoing)
        assertEquals(incoming, registry.current(Tab.LOCAL))
    }

    // GIVEN no hook registered for tab WHEN current called THEN returns null
    @Test
    fun `current returns null for unregistered tab`() {
        assertNull(registry.current(Tab.INTERNET))
    }
}
