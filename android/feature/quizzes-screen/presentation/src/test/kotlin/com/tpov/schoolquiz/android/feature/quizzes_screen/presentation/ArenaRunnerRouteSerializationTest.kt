package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaFreshStart
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArenaRunnerRouteSerializationTest {
    private val json = Json

    @Test
    fun `restored Arena route keeps only owner and ticket pointer`() {
        val route =
            QuizzesConfig.ArenaRunner(
                ownerUid = "owner",
                ticketId = "ticket",
                fresh = ArenaFreshStart(ArenaSessionStamp("owner", 7), SessionMode.LEARNING),
                initiatingSession = ArenaSessionStamp("owner", 7),
            )

        val encoded = json.encodeToString(QuizzesConfig.serializer(), route)
        val restored = json.decodeFromString(QuizzesConfig.serializer(), encoded) as QuizzesConfig.ArenaRunner

        assertEquals("owner", restored.ownerUid)
        assertEquals("ticket", restored.ticketId)
        assertNull(restored.fresh)
        assertNull(restored.initiatingSession)
    }
}
