package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callables исхода проверки одинаково (история 4.16).
 *
 * Чтение исходов (`listMyQuestReviewSubmissions`) и доведение статуса (`advanceQuestReviewOutcome`)
 * стреляют по строке имени; расхождение в бою выглядит как обычный NOT_FOUND. Оба файла читаются
 * текстом — тот же приём, что у [ReviewFeeQuoteCallableNamesTest].
 */
class QuestReviewOutcomeCallableNamesTest {
    private val indexSource: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    private val clientSource: String by lazy {
        val file = File("src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest_authoring/" +
            "FirebaseQuestArenaSubmissionRemoteDataSource.kt")
        assertTrue("нет клиентского источника: ${file.absolutePath}", file.exists())
        file.readText()
    }

    private val viewSource: String by lazy {
        val file = File("../../functions/arena-lifecycle-views.js")
        assertTrue("нет functions/arena-lifecycle-views.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `outcomes view is registered once with the exact client name`() {
        val name = "listMyQuestReviewSubmissions"
        assertEquals(1, Regex("exports\\.$name\\b").findAll(indexSource).toList().size)
        assertEquals(1, Regex("\"$name\"").findAll(clientSource).toList().size)
    }

    @Test
    fun `advance callable is registered once with the exact client name and an owner check`() {
        val name = "advanceQuestReviewOutcome"
        val registrations = Regex("exports\\.$name = onCall\\(CALLABLE_OPTIONS").findAll(indexSource).toList()
        assertEquals(1, registrations.size)
        assertTrue(
            "доведение проверяет владельца заявки до публикации",
            indexSource.contains("snapshot.data().ownerUid !== uid"),
        )
        assertEquals(1, Regex("\"$name\"").findAll(clientSource).toList().size)
    }

    @Test
    fun `client pins the view version the server names`() {
        assertTrue(viewSource.contains("quest-review-submissions-v2"))
        assertTrue(clientSource.contains("quest-review-submissions-v2"))
    }
}
