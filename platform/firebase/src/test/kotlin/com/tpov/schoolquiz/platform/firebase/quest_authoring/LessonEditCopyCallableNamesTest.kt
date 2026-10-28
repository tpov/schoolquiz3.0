package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callable редактируемой копии одинаково (4.23, UX-23).
 *
 * Клиент стреляет по строке имени; расхождение в бою выглядит как обычный NOT_FOUND сервера.
 * Реестр читается текстом — тот же приём, что у [LessonProposalQuoteCallableNamesTest].
 *
 * Имя [LessonEditCopyCallableNames] внутреннее; тест живёт в том же модуле и видит его.
 */
class LessonEditCopyCallableNamesTest {
    private val source: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `edit copy callable is registered as a read-only view`() {
        val pattern =
            Regex("""exports\.${LessonEditCopyCallableNames.BUILD_EDIT_COPY} = onCall\(CALLABLE_OPTIONS, async \(request\) => \{""")
        assertTrue(
            "exports.${LessonEditCopyCallableNames.BUILD_EDIT_COPY} обязан быть read-only view на CALLABLE_OPTIONS",
            pattern.containsMatchIn(source),
        )
    }

    @Test
    fun `edit copy callable is registered exactly once and never rides the mutation queue`() {
        val registrations = Regex("""exports\.${LessonEditCopyCallableNames.BUILD_EDIT_COPY}\b""").findAll(source).toList()
        assertEquals("callable копии регистрируется ровно один раз", 1, registrations.size)
    }
}
