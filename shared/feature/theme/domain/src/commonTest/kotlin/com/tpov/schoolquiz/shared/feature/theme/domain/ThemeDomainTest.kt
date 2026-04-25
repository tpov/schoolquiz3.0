package com.tpov.schoolquiz.shared.feature.theme.domain

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.fake.FakeThemeRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.use_case.SyncThemesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Domain tests for Theme value objects, invariants, and SyncThemesUseCase.
 *
 * Covers:
 *   scenario 5 (ThemeId invariants)
 *   scenario 18 (Theme.order < 0 throws)
 *   State Matrix rows for theme sync
 *   scenario 42 (FakeThemeRepository: theme updated + cursor advance)
 */
class ThemeDomainTest {

    // ── ThemeId invariants ────────────────────────────────────────────────────
    @Test
    fun `ThemeId blank throws`() {
        assertFailsWith<IllegalArgumentException> { ThemeId("") }
    }

    @Test
    fun `ThemeId valid constructs`() {
        assertEquals("t1", ThemeId("t1").value)
    }

    // ── Theme invariants (scenario 18) ────────────────────────────────────────
    @Test
    fun `Theme with order -1 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            makeTheme(order = -1)
        }
        assertEquals(true, error.message?.contains("order"))
    }

    @Test
    fun `Theme with blank title throws`() {
        assertFailsWith<IllegalArgumentException> { makeTheme(title = "") }
    }

    @Test
    fun `Theme with version 0 throws`() {
        assertFailsWith<IllegalArgumentException> { makeTheme(version = 0L) }
    }

    @Test
    fun `Theme with negative contentsVersion throws`() {
        assertFailsWith<IllegalArgumentException> { makeTheme(contentsVersion = -1L) }
    }

    @Test
    fun `Theme with negative lastModifiedAt throws`() {
        assertFailsWith<IllegalArgumentException> { makeTheme(lastModifiedAt = -1L) }
    }

    @Test
    fun `Theme order 0 constructs`() {
        val t = makeTheme(order = 0)
        assertEquals(0, t.order)
    }

    @Test
    fun `Theme archived false by default`() {
        val t = makeTheme()
        assertEquals(false, t.archived)
    }

    @Test
    fun `Theme archived true constructs`() {
        val t = makeTheme(archived = true)
        assertEquals(true, t.archived)
    }

    // ── SyncThemesUseCase ─────────────────────────────────────────────────────
    @Test
    fun `SyncThemesUseCase inserts new theme for known sectionId`() = runTest {
        val fake = FakeThemeRepository()
        val t1 = makeTheme(id = "t1", sectionId = "s1", lastModifiedAt = 1000L)
        fake.simulateRemoteThemes(listOf(t1))

        val result = SyncThemesUseCase(fake).invoke(setOf(SectionId("s1")), cursor = 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fake.snapshot().size)
    }

    @Test
    fun `SyncThemesUseCase ignores themes for unknown sectionIds`() = runTest {
        val fake = FakeThemeRepository()
        val t1 = makeTheme(id = "t1", sectionId = "s99", lastModifiedAt = 1000L)
        fake.simulateRemoteThemes(listOf(t1))

        SyncThemesUseCase(fake).invoke(setOf(SectionId("s1")), cursor = 0L)

        assertTrue(fake.snapshot().isEmpty())
    }

    @Test
    fun `SyncThemesUseCase upserts when incoming version is higher`() = runTest {
        val existing = makeTheme(id = "t1", version = 1L, title = "Old", lastModifiedAt = 500L)
        val fake = FakeThemeRepository(initial = listOf(existing))
        fake.simulateRemoteThemes(listOf(existing.copy(title = "New", version = 2L, lastModifiedAt = 1000L)))

        SyncThemesUseCase(fake).invoke(setOf(SectionId("s1")), cursor = 0L)

        assertEquals("New", fake.snapshot().first().title)
    }

    @Test
    fun `SyncThemesUseCase returns failure on network error`() = runTest {
        val fake = FakeThemeRepository()
        fake.setNextRefreshFailure(RuntimeException("fail"))

        val result = SyncThemesUseCase(fake).invoke(setOf(SectionId("s1")), cursor = 0L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `observeBySection returns themes sorted by order`() = runTest {
        val t2 = makeTheme(id = "t2", sectionId = "s1", order = 2)
        val t0 = makeTheme(id = "t0", sectionId = "s1", order = 0)
        val t1 = makeTheme(id = "t1", sectionId = "s1", order = 1)
        val fake = FakeThemeRepository(initial = listOf(t2, t0, t1))

        val result = fake.observeBySection(SectionId("s1")).first()

        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }

    // ── Scenario 42 : FakeThemeRepository update + cursor advance ─────────────
    @Test
    fun `scenario 42 FakeThemeRepository with existing theme server returns theme lastMod 2000 cursor 1000 THEN theme updated cursor becomes 2000`() = runTest {
        val existing = makeTheme(id = "t1", sectionId = "s1", version = 1L, title = "Old Theme", lastModifiedAt = 500L)
        val fake = FakeThemeRepository(initial = listOf(existing))

        val updated = existing.copy(title = "Updated Theme", version = 2L, lastModifiedAt = 2000L)
        fake.simulateRemoteThemes(listOf(updated))

        val result = SyncThemesUseCase(fake).invoke(setOf(SectionId("s1")), cursor = 1000L)

        assertTrue(result.isSuccess)
        assertEquals("Updated Theme", fake.snapshot().first().title)
        assertEquals(2000L, fake.lastCursor)
    }

    // ── Scenario 42b : theme with lastModifiedAt <= cursor is skipped ─────────
    @Test
    fun `scenario 42b theme with lastModifiedAt at cursor boundary is skipped`() = runTest {
        val existing = makeTheme(id = "t1", sectionId = "s1", version = 1L, title = "Existing", lastModifiedAt = 500L)
        val fake = FakeThemeRepository(initial = listOf(existing))

        // Server returns theme with lastModifiedAt = 1000 but cursor = 1000 → skip
        val incoming = existing.copy(title = "Should Be Skipped", version = 2L, lastModifiedAt = 1000L)
        fake.simulateRemoteThemes(listOf(incoming))

        SyncThemesUseCase(fake).invoke(setOf(SectionId("s1")), cursor = 1000L)

        assertEquals("Existing", fake.snapshot().first().title, "Theme at cursor boundary should be skipped")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeTheme(
        id: String = "t1",
        sectionId: String = "s1",
        title: String = "Theme Title",
        order: Int = 0,
        version: Long = 1L,
        contentsVersion: Long = 0L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
    ) = Theme(
        id = ThemeId(id),
        sectionId = SectionId(sectionId),
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
    )
}
