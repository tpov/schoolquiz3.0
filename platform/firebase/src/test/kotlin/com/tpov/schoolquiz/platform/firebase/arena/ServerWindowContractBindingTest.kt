package com.tpov.schoolquiz.platform.firebase.arena

import com.tpov.schoolquiz.platform.firebase.network.SETTLEMENT_REUSE_REASON
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaOnlineO2Adapter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client's window-state gate and the settle-replay reason are cross-language literals:
 * the adapter rejects any window whose `state` is not one of its own constants, and the
 * transport maps a server refusal by exact reason string. Both halves must stay spelled the
 * same, and a coordinated rename inside `functions/` would otherwise ship every online HARD
 * continue into RELOAD_REQUIRED with every client test green (the exact literal-drift class
 * the epic-3 retro L9 re-pin history was about). Same binding pattern as the arena ticket
 * fixture pins: the test reads the actual server source.
 */
class ServerWindowContractBindingTest {
    private val repo =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "functions/lesson-question-window.js").isFile }

    @Test
    fun windowStateLiteralsRemainBoundToTheActualServerSource() {
        val source = File(repo, "functions/lesson-question-window.js").readText()
        for ((constant, literal) in mapOf(
            "WINDOW_OPEN" to ArenaOnlineO2Adapter.WINDOW_STATE_OPEN,
            "WINDOW_DELIVERED" to ArenaOnlineO2Adapter.WINDOW_STATE_DELIVERED,
            "WINDOW_EXPIRED" to ArenaOnlineO2Adapter.WINDOW_STATE_EXPIRED,
        )) {
            val declared = requireNotNull(
                Regex("\\b$constant\\s*=\\s*\"([^\"]+)\"").find(source),
            ) { "server source no longer declares $constant" }
            assertEquals(
                "$constant drifted from ArenaOnlineO2Adapter's window-state gate",
                literal,
                declared.groupValues[1],
            )
        }
    }

    @Test
    fun settlementReuseReasonRemainsBoundToItsServerProducers() {
        val expected = SETTLEMENT_REUSE_REASON
        for (name in listOf("arena-entry-runtime.js", "arena-tickets.js")) {
            val source = File(repo, "functions/$name").readText()
            assertTrue(
                "$name no longer emits the $expected literal the transport maps by",
                source.contains("\"$expected\"") || source.contains("'$expected'"),
            )
        }
    }
}
