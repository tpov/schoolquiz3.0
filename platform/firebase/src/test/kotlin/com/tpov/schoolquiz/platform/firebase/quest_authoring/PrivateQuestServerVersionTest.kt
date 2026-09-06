package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Серверная версия приватного квеста читается как серверная — или не читается вовсе (AD-12).
 *
 * Этим тестом заперт запасной путь, из-за которого вся сверка версий была бесполезной: при
 * отсутствии серверного поля источник данных подставлял `localRevision`, число самого клиента, и
 * приёмник сравнивал клиента с клиентом. Ложное расхождение получалось у автора, который просто
 * работал, а настоящее — не обнаруживалось.
 *
 * [DocumentSnapshot] — финальный класс SDK, подменить его фейком нечем; mockk здесь по тому же
 * прецеденту, что и в `LegacyContentsVersionFieldTest`.
 */
class PrivateQuestServerVersionTest {

    private fun snapshotOf(data: Map<String, Any?>): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { snapshot.get(any<String>()) } answers { data[firstArg<String>()] }
        return snapshot
    }

    @Test
    fun `given the server wrote a version then it is read as is`() {
        assertEquals(7L, snapshotOf(mapOf("version" to 7L)).serverAssignedVersion())
    }

    @Test
    fun `given no server version then the local revision is not passed off as one`() {
        val snapshot = snapshotOf(mapOf("localRevision" to 11L))

        assertNull(
            "локальный счётчик правок не является серверной версией",
            snapshot.serverAssignedVersion(),
        )
    }

    @Test
    fun `given an empty document then there is no version to compare with`() {
        assertNull(snapshotOf(emptyMap()).serverAssignedVersion())
    }

    @Test
    fun `given a version below the first one then it is not a version`() {
        // Ноль у сервера означает «документа не было», а не «нулевая редакция»: сверять с ним
        // нечего, и хранить его как известную версию нельзя.
        assertNull(snapshotOf(mapOf("version" to 0L)).serverAssignedVersion())
        assertNull(snapshotOf(mapOf("version" to -3L)).serverAssignedVersion())
    }

    @Test
    fun `given garbage in the version field then there is no version`() {
        assertNull(snapshotOf(mapOf("version" to "не число")).serverAssignedVersion())
    }
}
