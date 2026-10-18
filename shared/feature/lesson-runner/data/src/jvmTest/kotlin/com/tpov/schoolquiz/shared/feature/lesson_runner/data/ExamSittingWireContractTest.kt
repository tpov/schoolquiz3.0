package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Source-contract guard экзаменационного провода (идиом [AttemptSettlementWireContractTest]).
 *
 * Пинит ОДИН и тот же список ключей с двух сторон живых исходников: что JS-писатель
 * (`functions/exam-sitting.js`, `functions/index.js`) реально излучает, то строгий парсер
 * (`ExamSittingWireParser`) и транспорт (`FirebaseExamSittingRemoteDataSource`) требуют — и
 * наоборот. Дрейф форм (класс ошибки «клиент слал строку, сервер ждал объект») ловится здесь,
 * а не на устройстве.
 */
class ExamSittingWireContractTest {

    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "functions/exam-sitting.js").isFile }
            ?: error("Run in the integrated repository containing the actual exam writer")

    @Test
    fun sittingProjectionKeysAreEmittedAndRequiredOnBothSides() {
        val root = repoRoot()
        val js = File(root, "functions/exam-sitting.js").readText()
        val parser = File(
            root,
            "shared/feature/lesson-runner/data/src/commonMain/kotlin/" +
                "com/tpov/schoolquiz/shared/feature/lesson_runner/data/remote/ExamSittingWireParser.kt",
        ).readText()

        val projectionKeys = listOf(
            "schemaVersion", "sittingId", "mode", "assessmentId", "state",
            "currentIndex", "totalQuestions", "rules", "serverNowMs",
        )
        val projectionBody = js.substringAfter("function publicSittingProjection")
            .substringBefore("\nfunction ")
        projectionKeys.forEach { key ->
            assertTrue(projectionBody.contains("$key:"), "publicSittingProjection emit `$key`")
            assertTrue(parser.contains("\"$key\""), "parser require `$key` at the projection root")
        }
        // Редакция вопроса несёт свой serverNowMs рядом с корневым: оба обязаны существовать.
        val questionBody = js.substringAfter("function publicQuestionProjection")
            .substringBefore("\nfunction ")
        listOf("index", "questionId", "revision", "shownDifficulty", "answerSeconds",
            "publicPayload", "ack", "serverNowMs").forEach { key ->
            assertTrue(questionBody.contains("$key:"), "publicQuestionProjection emit `$key`")
        }
    }

    @Test
    fun receiptKeysArePublicExactlyAsTheParserDemands() {
        val root = repoRoot()
        val js = File(root, "functions/exam-sitting.js").readText()
        val parser = File(
            root,
            "shared/feature/lesson-runner/data/src/commonMain/kotlin/" +
                "com/tpov/schoolquiz/shared/feature/lesson_runner/data/remote/ExamSittingWireParser.kt",
        ).readText()

        val receiptKeys = listOf("receiptId", "sittingId", "index", "revision", "receivedAtMs")
        val receiptBody = js.substringAfter("function publicReceipt").substringBefore("\n}")
        receiptKeys.forEach { key ->
            assertTrue(receiptBody.contains("$key:"), "publicReceipt emit `$key`")
        }
        // uid и fingerprint остаются приватным свидетельством документа и НЕ уезжают в ответе.
        assertTrue(!receiptBody.contains("uid"), "publicReceipt must not leak uid")
        assertTrue(!receiptBody.contains("fingerprint"), "publicReceipt must not leak fingerprint")
        // Парсер требует ровно пять полей одной строкой exact(...).
        assertTrue(
            parser.contains("\"receiptId\", \"sittingId\", \"index\", \"revision\", \"receivedAtMs\""),
            "parser demands exactly the public receipt keys",
        )
    }

    @Test
    fun requestFieldsTheClientSendsAreTheFieldsTheServerValidates() {
        val root = repoRoot()
        val js = File(root, "functions/exam-sitting.js").readText()
        val transport = File(
            root,
            "platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/exam/" +
                "FirebaseExamSittingRemoteDataSource.kt",
        ).readText()

        // Идентичность операции: и клиент кладёт, и сервер читает.
        listOf("operationId", "accountEpoch", "shownTerms", "acceptedTermsRevision").forEach { key ->
            assertTrue(transport.contains("put(\"$key\""), "client sends `$key`")
            assertTrue(js.contains(key), "server consumes `$key`")
        }
        // Сидинговые поля.
        listOf("sittingId", "questionIndex", "revision", "answer").forEach { key ->
            assertTrue(transport.contains("put(\"$key\""), "client sends `$key`")
            assertTrue(js.contains(key), "server consumes `$key`")
        }
        // Ответ уезжает ОБЪЕКТОМ (декодированным), а не строкой: сервер валидирует typeof object.
        assertTrue(transport.contains("put(\"answer\", decodedAnswerObject(answerPayload))"),
            "client sends the decoded answer object")
        assertTrue(js.contains("typeof body.answer !== \"object\""),
            "server validates the answer as an object")
        // Ревизия и тема/курс.
        assertTrue(js.contains("themeId") && js.contains("courseId"),
            "server consumes themeId/courseId as the assessment id")
        assertTrue(transport.contains("put(\"themeId\"") && transport.contains("put(\"courseId\""),
            "client sends themeId/courseId")
    }

    @Test
    fun certificateVerifyShapeIsOneFourKeyFormOnBothSides() {
        val root = repoRoot()
        val indexJs = File(root, "functions/index.js").readText()
        val certificateTransport = File(
            root,
            "platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/exam/" +
                "FirebaseExamCertificateRemoteDataSource.kt",
        ).readText()

        val verifyKeys = listOf("found", "status", "issuanceId", "snapshot")
        // Обе без-снимковые ветви сервера несут snapshot: null — иначе строгий exact() рвёт ответ.
        val pendingBranch = indexJs.contains("status: VERIFY_STATUS.PENDING, issuanceId, snapshot: null")
        val notFoundBranch = indexJs.contains("status: VERIFY_STATUS.NOT_FOUND, issuanceId, snapshot: null")
        assertTrue(pendingBranch && notFoundBranch, "verify pending/not_found carry snapshot: null")
        assertTrue(
            certificateTransport.contains("exact(\"found\", \"status\", \"issuanceId\", \"snapshot\")"),
            "client demands exactly the four verify keys",
        )
        verifyKeys.forEach { key ->
            assertTrue(indexJs.contains(key), "server emits `$key`")
        }
    }
}
