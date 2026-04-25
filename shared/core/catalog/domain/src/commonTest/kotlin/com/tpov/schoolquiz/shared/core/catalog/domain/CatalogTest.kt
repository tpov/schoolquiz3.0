package com.tpov.schoolquiz.shared.core.catalog.domain

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Domain Test Scenarios for [Catalog] construction, equality, and invariants.
 *
 * Each test maps 1:1 to a scenario from the Feature Domain Contract in
 * docs/features/catalog-foundation/0-spec.md § "Domain Test Scenarios".
 *
 * Also covers new scenarios from docs/features/home-and-my-quests/0-spec.md
 * § "Domain Test Scenarios" (scenarios 6-9 for version / contentsVersion / archived).
 *
 * Covered scenarios:
 *   3-10 : init-time validation (blank id, blank name, picturePath rules)
 *   16-17: data class equality (structural equality + difference on picturePath)
 *   hq-6 : version=0 throws (version must be >= 1)
 *   hq-7 : contentsVersion=-1 throws
 *   hq-8 : valid Catalog(version=1, contentsVersion=0, archived=false) constructs
 *   hq-9 : valid Catalog(archived=true) constructs
 */
class CatalogTest {

    // ── Scenario 3 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 3 Catalog(surveys, Опросы, null) constructs without exception`() {
        val catalog = Catalog(
            id = CatalogId("surveys"),
            name = "Опросы",
            picturePath = null,
        )
        assertEquals(CatalogId("surveys"), catalog.id)
        assertEquals("Опросы", catalog.name)
        assertEquals(null, catalog.picturePath)
    }

    // ── Scenario 4 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 4 Catalog with blank CatalogId value throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId(""),
                name = "Name",
                picturePath = null,
            )
        }
    }

    // ── Scenario 5 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 5 Catalog with blank name throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "",
                picturePath = null,
            )
        }
        assertEquals(true, error.message?.contains("name"))
    }

    // ── Scenario 6 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 6 Catalog with blank picturePath throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = "",
            )
        }
        assertEquals(true, error.message?.contains("picturePath"))
    }

    // ── Scenario 7 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 7 Catalog with https picturePath throws (must be relative path)`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = "https://x",
            )
        }
        assertEquals(true, error.message?.contains("relative"))
    }

    // ── Scenario 7b ───────────────────────────────────────────────────────────
    // Explicit guard for http:// (spec Business Rules §3 also forbids http)
    @Test
    fun `scenario 7b Catalog with http picturePath throws (must be relative path)`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = "http://x",
            )
        }
        assertEquals(true, error.message?.contains("relative"))
    }

    // ── Scenario 8 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 8 Catalog with gs URI picturePath throws (must be relative path)`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = "gs://bucket/path",
            )
        }
        assertEquals(true, error.message?.contains("relative"))
    }

    // ── Scenario 9 ────────────────────────────────────────────────────────────
    @Test
    fun `scenario 9 Catalog with relative picturePath constructs without exception`() {
        val catalog = Catalog(
            id = CatalogId("id"),
            name = "Name",
            picturePath = "catalog-pictures/surveys.jpg",
        )
        assertEquals("catalog-pictures/surveys.jpg", catalog.picturePath)
    }

    // ── Scenario 10 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 10 Catalog with null picturePath constructs without exception`() {
        val catalog = Catalog(
            id = CatalogId("id"),
            name = "Name",
            picturePath = null,
        )
        assertEquals(null, catalog.picturePath)
    }

    // ── Scenario 16 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 16 two Catalogs with identical fields are equal (data class)`() {
        val a = Catalog(CatalogId("surveys"), "Опросы", null)
        val b = Catalog(CatalogId("surveys"), "Опросы", null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ── Scenario 17 ───────────────────────────────────────────────────────────
    @Test
    fun `scenario 17 two Catalogs with different picturePath are not equal`() {
        val a = Catalog(CatalogId("surveys"), "Опросы", null)
        val b = Catalog(CatalogId("surveys"), "Опросы", "catalog-pictures/x.jpg")
        assertNotEquals(a, b)
    }

    // ── CatalogId invariant (shared prerequisite for scenarios 4, 20) ────────
    @Test
    fun `CatalogId with blank value throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { CatalogId("") }
    }

    @Test
    fun `CatalogId with whitespace-only value throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { CatalogId("   ") }
    }

    @Test
    fun `CatalogId with non-blank value constructs without exception`() {
        assertEquals("surveys", CatalogId("surveys").value)
    }

    // ── home-and-my-quests Scenario 6 ─────────────────────────────────────────
    // GIVEN Catalog with version=0 WHEN construct THEN throws (version must be >= 1)
    @Test
    fun `hq scenario 6 Catalog with version 0 throws IllegalArgumentException`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = null,
                version = 0,
                contentsVersion = 0,
                archived = false,
            )
        }
        assertEquals(true, error.message?.contains("version"))
    }

    // ── home-and-my-quests Scenario 7 ─────────────────────────────────────────
    // GIVEN Catalog with contentsVersion=-1 WHEN construct THEN throws
    @Test
    fun `hq scenario 7 Catalog with negative contentsVersion throws IllegalArgumentException`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = null,
                version = 1,
                contentsVersion = -1,
                archived = false,
            )
        }
        assertEquals(true, error.message?.contains("contentsVersion"))
    }

    // ── home-and-my-quests Scenario 8 ─────────────────────────────────────────
    // GIVEN valid Catalog(version=1, contentsVersion=0, archived=false) WHEN construct THEN no exception
    @Test
    fun `hq scenario 8 Catalog with version 1 and contentsVersion 0 constructs without exception`() {
        val catalog = Catalog(
            id = CatalogId("surveys"),
            name = "Опросы",
            picturePath = null,
            version = 1,
            contentsVersion = 0,
            archived = false,
        )
        assertEquals(1L, catalog.version)
        assertEquals(0L, catalog.contentsVersion)
        assertEquals(false, catalog.archived)
    }

    // ── home-and-my-quests Scenario 9 ─────────────────────────────────────────
    // GIVEN Catalog with archived=true WHEN construct THEN no exception (archived can be true)
    @Test
    fun `hq scenario 9 Catalog with archived true constructs without exception`() {
        val catalog = Catalog(
            id = CatalogId("old"),
            name = "Old Catalog",
            picturePath = null,
            version = 1,
            contentsVersion = 0,
            archived = true,
        )
        assertEquals(true, catalog.archived)
    }

    // ── Backward-compatibility: existing tests still pass with new default fields ──
    @Test
    fun `Catalog default fields version=1 contentsVersion=0 archived=false lastModifiedAt=0 are applied`() {
        val catalog = Catalog(
            id = CatalogId("surveys"),
            name = "Опросы",
            picturePath = null,
        )
        assertEquals(1L, catalog.version)
        assertEquals(0L, catalog.contentsVersion)
        assertEquals(false, catalog.archived)
        assertEquals(0L, catalog.lastModifiedAt)
    }

    // ── Catalog.lastModifiedAt invariant (home-and-my-quests spec) ────────────
    @Test
    fun `Catalog with negative lastModifiedAt throws IllegalArgumentException`() {
        val error = assertFailsWith<IllegalArgumentException> {
            Catalog(
                id = CatalogId("id"),
                name = "Name",
                picturePath = null,
                lastModifiedAt = -1L,
            )
        }
        assertEquals(true, error.message?.contains("lastModifiedAt"))
    }

    @Test
    fun `Catalog with lastModifiedAt zero constructs`() {
        val catalog = Catalog(
            id = CatalogId("id"),
            name = "Name",
            picturePath = null,
            lastModifiedAt = 0L,
        )
        assertEquals(0L, catalog.lastModifiedAt)
    }

    @Test
    fun `Catalog with positive lastModifiedAt constructs`() {
        val catalog = Catalog(
            id = CatalogId("surveys"),
            name = "Опросы",
            picturePath = null,
            lastModifiedAt = 1714000000000L,
        )
        assertEquals(1714000000000L, catalog.lastModifiedAt)
    }
}
