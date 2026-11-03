package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callable «Предложений» одинаково (история 4.25, UX-22).
 *
 * Реестр читается текстом — тот же приём, что у [ReviewFeeQuoteCallableNamesTest].
 */
class QuestProposalsCallableNamesTest {
    private val source: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `proposals page callable is registered as a read-only view`() {
        val pattern =
            Regex("""exports\.${QuestProposalsCallableNames.FETCH} = onCall\(CALLABLE_OPTIONS, async \(request\) => \{""")
        assertTrue(
            "exports.${QuestProposalsCallableNames.FETCH} обязан быть read-only view на CALLABLE_OPTIONS",
            pattern.containsMatchIn(source),
        )
    }

    @Test
    fun `decision callable is registered exactly once under the server name`() {
        val registrations = Regex("""exports\.${QuestProposalsCallableNames.DECIDE}\b""").findAll(source).toList()
        assertEquals(
            "решение автора регистрируется ровно один раз (приёмник 4.21)",
            1,
            registrations.size,
        )
    }
}
