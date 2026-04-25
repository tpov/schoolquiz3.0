package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildCatalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [DefaultHomeQuestsComponent].
 *
 * Tests are traced to AC#21 (catalog list in home state) and AC#22 (archived excluded).
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 * Plan: docs/features/home-and-my-quests/plan/phase-05/tests.md §2
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeQuestsComponentTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    private fun testCtx(): DefaultComponentContext {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    private fun buildComponent(
        catalogRepo: FakeCatalogRepository = FakeCatalogRepository(),
    ) = DefaultHomeQuestsComponent(
        componentContext = testCtx(),
        observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
        mainContext = testDispatcher,
    )

    // ── AC#21 — catalogs from repository appear in state ─────────────────────

    @Test
    fun `when observeCatalogs emits then state catalogs updated`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "cat1", name = "Catalog 1"),
            buildCatalog(id = "cat2", name = "Catalog 2"),
        ))

        val component = buildComponent(catalogRepo = catalogRepo)

        val catalogs = component.state.value.catalogs
        assertEquals(2, catalogs.size, "both catalogs must appear in state")
        assertTrue(catalogs.any { it.id == CatalogId("cat1") })
        assertTrue(catalogs.any { it.id == CatalogId("cat2") })
    }

    // ── AC#22 — archived catalog not in state ────────────────────────────────

    @Test
    fun `when catalog is archived then it is not in state`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(
            buildCatalog(id = "active", archived = false),
            buildCatalog(id = "archived", archived = true),
        ))

        val component = buildComponent(catalogRepo = catalogRepo)

        val catalogs = component.state.value.catalogs
        assertEquals(1, catalogs.size, "archived catalog must be excluded")
        assertEquals(CatalogId("active"), catalogs.first().id)
    }

    // ── Late catalog emission updates state reactively ────────────────────────

    @Test
    fun `when catalog emitted after construction then state updates`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        val component = buildComponent(catalogRepo = catalogRepo)

        assertTrue(component.state.value.catalogs.isEmpty(), "initial state must be empty")

        catalogRepo.emit(listOf(buildCatalog(id = "cat1")))

        assertEquals(1, component.state.value.catalogs.size, "state must update on late emission")
        assertEquals(CatalogId("cat1"), component.state.value.catalogs.first().id)
    }

    // ── Empty catalog list → not stuck in loading ─────────────────────────────

    @Test
    fun `when catalog list is empty then state is empty and not loading`() = runTest {
        val component = buildComponent(catalogRepo = FakeCatalogRepository())

        val state = component.state.value
        assertTrue(state.catalogs.isEmpty(), "empty store → empty catalog list")
        assertFalse(state.isLoading, "must not be stuck in loading state on empty data")
    }
}
