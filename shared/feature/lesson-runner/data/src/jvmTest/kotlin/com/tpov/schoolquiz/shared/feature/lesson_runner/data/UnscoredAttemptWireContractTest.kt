package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Source-contract guard, not a substitute for the behavioural raw writer and server tests. */
class UnscoredAttemptWireContractTest {
    @Test fun actualNonTicketIntakeStillSupportsOnlyHardRawServedWithoutClaims() {
        val sourceFile = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "functions/attempt-intake.js") }.firstOrNull { it.isFile }
        assertNotNull(sourceFile, "Run against the integrated actual server source")
        val source = sourceFile.readText()
        val raw = source.substringAfter("function readServerScoredAttempt(").substringBefore("function readSubmittedAttempt(")
        assertTrue(raw.contains("difficulty !== \"HARD\""))
        assertTrue(raw.contains("data.served === undefined || data.served === null"))
        assertTrue(raw.contains("REJECTION.CHARGE_CLAIMS_NOT_SCORED_HERE()"))
        assertTrue(raw.contains("scoringAuthority: SCORING_AUTHORITY.SERVER"))
        assertTrue(raw.contains("codeAnswer: null") && raw.contains("percentScore: null"))
        assertTrue(source.contains("data.percentScore !== undefined && data.percentScore !== null"))
        assertTrue(source.contains("data.codeAnswer !== undefined && data.codeAnswer !== null"))
    }
}
