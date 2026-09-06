package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Source-contract guard required for names shared with the actual CommonJS receipt writer. */
class AttemptSettlementWireContractTest {
    @Test
    fun typedClientRecognizesEveryActualServerReceiptStatusAndEnvelope() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "functions/attempt-receipt.js").isFile }
        assertNotNull(root, "Run in the integrated repository containing the actual receipt writer")
        val source = File(root, "functions/attempt-receipt.js").readText()
        val statusObject = Regex("""const STATUS = Object\.freeze\(\{([\s\S]*?)\}\);""").find(source)
        assertNotNull(statusObject)
        val names = Regex("\"([^\"]+)\"").findAll(statusObject.groupValues[1]).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("SETTLED", "REJECTED", "UNSCORABLE", "LEGACY_RECORDED"), names)
        assertTrue(ServerAttemptSettlementState.entries.map { it.name }.containsAll(names))
        assertTrue(source.contains("schemaVersion: 1"))
        assertTrue(source.contains("completed: settled && event.completed === true"))
        listOf("receiptId:", "attemptId:", "uid:", "lessonId:", "lessonVersion:", "settledAtMs:",
            "codeAnswer:", "percentScore:", "lifePointsSpent:", "plasmaPointsSpent:", "reward:")
            .forEach { assertTrue(source.contains(it), "Receipt field $it changed") }
        val handler = File(root, "functions/index.js").readText()
        assertTrue(handler.contains("return {replayed: true, result: gate.result}"))
        assertTrue(handler.contains("return {replayed: false, result: result === undefined ? null : result}"))
    }
}
