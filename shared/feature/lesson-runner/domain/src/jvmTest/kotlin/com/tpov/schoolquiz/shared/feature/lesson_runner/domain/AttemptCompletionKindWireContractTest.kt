package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Client/server wire names must change together; mandated by the repository testing contract. */
class AttemptCompletionKindWireContractTest {
    @Test
    fun completionKindNamesMatchTheActualServerAllowList() {
        val server = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "functions/index.js") }
            .firstOrNull { it.isFile }
        assertNotNull(server, "Run from the integrated repository containing functions/index.js")
        val allowList = Regex("""\[("COMPLETED"\s*,\s*"ABORTED")\]\.includes\(item\.completionKind\)""")
            .find(server.readText())
        assertNotNull(allowList, "Server completionKind allow-list moved or changed: update both sides")
        val names = Regex("\"([^\"]+)\"").findAll(allowList.groupValues[1])
            .map { it.groupValues[1] }.toSet()
        assertEquals(AttemptCompletionKind.entries.map { it.name }.toSet(), names)
    }
}
