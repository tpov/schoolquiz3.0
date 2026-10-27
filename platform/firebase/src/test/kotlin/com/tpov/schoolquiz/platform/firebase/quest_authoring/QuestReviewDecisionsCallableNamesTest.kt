package com.tpov.schoolquiz.platform.firebase.quest_authoring

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Клиент и сервер называют callables «Решений» одинаково (история 4.17, UX-22).
 *
 * Чтение страницы (`fetchReviewDecisions`) и отметка (`markReviewDecision`) стреляют по строке
 * имени; расхождение в бою выглядит как обычный NOT_FOUND. Оба файла читаются текстом — тот же
 * приём, что у [QuestReviewOutcomeCallableNamesTest]. Роль проверяет сервер: оба обработчика
 * обязаны звать requireVerifier до любого чтения.
 */
class QuestReviewDecisionsCallableNamesTest {
    private val indexSource: String by lazy {
        val file = File("../../functions/index.js")
        assertTrue("нет functions/index.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    private val clientSource: String by lazy {
        val file = File(
            "src/main/kotlin/com/tpov/schoolquiz/platform/firebase/quest_authoring/" +
                "FirebaseReviewDecisionsRemoteDataSource.kt",
        )
        assertTrue("нет клиентского источника: ${file.absolutePath}", file.exists())
        file.readText()
    }

    private val moduleSource: String by lazy {
        val file = File("../../functions/review-decisions.js")
        assertTrue("нет functions/review-decisions.js: ${file.absolutePath}", file.exists())
        file.readText()
    }

    private val parserSource: String by lazy {
        val file = File(
            "../../shared/feature/quest-authoring/data/src/commonMain/kotlin/com/tpov/schoolquiz/" +
                "shared/feature/quest_authoring/data/remote/ReviewDecisionsWireParser.kt",
        )
        assertTrue("нет парсера «Решений»: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `fetch callable is registered once with the exact client name and the verifier gate`() {
        val name = "fetchReviewDecisions"
        val registrations = Regex("exports\\.$name = onCall\\(CALLABLE_OPTIONS").findAll(indexSource).toList()
        assertEquals(1, registrations.size)
        val body = indexSource.substringAfter("exports.$name = onCall(").substringBefore("\n});")
        assertTrue(
            "чтение «Решений» проверяет роль до данных (requireVerifier)",
            body.contains("requireVerifier(uid)"),
        )
        assertEquals(1, Regex("\"$name\"").findAll(clientSource).toList().size)
    }

    @Test
    fun `mark callable is registered once with the exact client name and the verifier gate`() {
        val name = "markReviewDecision"
        val registrations = Regex("exports\\.$name = onCall\\(CALLABLE_OPTIONS").findAll(indexSource).toList()
        assertEquals(1, registrations.size)
        val body = indexSource.substringAfter("exports.$name = onCall(").substringBefore("\n});")
        assertTrue(
            "отметка «Решений» проверяет роль до записи (requireVerifier)",
            body.contains("requireVerifier(uid)"),
        )
        assertEquals(1, Regex("\"$name\"").findAll(clientSource).toList().size)
    }

    @Test
    fun `client and server pin the same decisions view version`() {
        assertTrue(moduleSource.contains("quest-review-decisions-v1"))
        assertTrue(parserSource.contains("quest-review-decisions-v1"))
    }
}
