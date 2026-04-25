package com.tpov.schoolquiz.platform.firebase.catalog

import io.mockk.every
import io.mockk.mockk
import com.google.firebase.firestore.DocumentSnapshot
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CF-22: DocumentSnapshot.toCatalogDto() — blank name → null.
 *
 * Note: requires testImplementation(libs.mockk) in platform/firebase/build.gradle.kts.
 * Please ask backend-dev to add this dependency (scaffold ownership per CLAUDE.md).
 */
class FirestoreCatalogDtoMapperTest {

    // CF-22: given DocumentSnapshot with blank name, when toCatalogDto(), then returns null
    @Test
    fun `when document has blank name then toCatalogDto returns null`() {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true) {
            every { getString("name") } returns "   "
            every { id } returns "some-id"
        }

        val dto = snapshot.toCatalogDto()

        assertNull(dto)
    }

    @Test
    fun `when document has null name then toCatalogDto returns null`() {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true) {
            every { getString("name") } returns null
            every { id } returns "some-id"
        }

        val dto = snapshot.toCatalogDto()

        assertNull(dto)
    }

    @Test
    fun `when document has valid name then toCatalogDto returns dto with correct id and name`() {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true) {
            every { getString("name") } returns "Опросы"
            every { getString("picturePath") } returns null
            every { id } returns "surveys"
        }

        val dto = snapshot.toCatalogDto()

        assertEquals("surveys", dto?.id)
        assertEquals("Опросы", dto?.name)
    }

    @Test
    fun `when document has null picturePath field then toCatalogDto preserves null picturePath`() {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true) {
            every { getString("name") } returns "Курсы"
            every { getString("picturePath") } returns null
            every { id } returns "courses"
        }

        val dto = snapshot.toCatalogDto()

        assertNull(dto?.picturePath)
    }
}
