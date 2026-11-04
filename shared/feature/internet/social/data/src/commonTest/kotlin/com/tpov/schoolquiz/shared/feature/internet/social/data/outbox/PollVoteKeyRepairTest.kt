package com.tpov.schoolquiz.shared.feature.internet.social.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxKeyRepair
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Правило опросной половины ремонта ключей (ретро эпика 8, находка 4): старая форма ключа
 * `…-q<idx>` получает ось участника, новая форма — не трогается, операция — ровно голосовая.
 */
class PollVoteKeyRepairTest {

    /** Записывающая заглушка механики: фиксирует операцию и прогоняет правило по обеим формам. */
    private class RecordingRepair : OutboxKeyRepair {
        var lastOperation: String? = null
        val ruleAnswers = mutableListOf<String?>()
        var result: Int = 0

        override suspend fun rewritePendingKeys(
            operation: String,
            repair: (ownerUid: String, mutationId: String) -> String?,
        ): Int {
            lastOperation = operation
            ruleAnswers += repair("u1", OLD_KEY)
            ruleAnswers += repair("u1", NEW_KEY)
            return result
        }
    }

    @Test
    fun queuedVotesAreRepairedUnderTheVoteOperation() = runTest {
        val repair = RecordingRepair()
        repair.result = 3
        val rewritten = PollVoteKeyRepair(repair).repairQueuedVotes()

        assertEquals(3, rewritten, "наружу идёт счёт механики")
        assertEquals(OutboxOperations.SUBMIT_POLL_VOTE, repair.lastOperation,
            "правило применяется к операции голоса, а не к очереди целиком")
        assertEquals(
            listOf("polls-SUBMIT_VOTE-series-p1-r2-q0-u-u1", null),
            repair.ruleAnswers,
            "старая форма получает ось участника, новая — null (как есть)",
        )
    }

    @Test
    fun theOldKeyGainsTheParticipantAxisAndTheNewOneStays() {
        assertEquals(NEW_KEY, PollVoteKeyRepair.migratedKey("u1", OLD_KEY))
        // kotlin.test.assertNull(actual, message): строка-сообщение первой позицией
        // превращалась в «actual» и валила тест при верном правиле.
        assertNull(PollVoteKeyRepair.migratedKey("u1", NEW_KEY), "ряд уже новой формы — менять нечего")
        // Точный хвост: чужая ось ("-u-u1") не признаётся своей для "u1-u" — правило дописывает
        // своего участника, а не молчит.
        assertEquals(NEW_KEY + "-u-u1-u", PollVoteKeyRepair.migratedKey("u1-u", NEW_KEY))
    }

    private companion object {
        const val OLD_KEY = "polls-SUBMIT_VOTE-series-p1-r2-q0"
        const val NEW_KEY = "polls-SUBMIT_VOTE-series-p1-r2-q0-u-u1"
    }
}
