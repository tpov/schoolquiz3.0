package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountDataKind
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountSwitchLoss
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Предупреждение называет виды данных и числа по состояниям — не одну сумму (история 6.4).
 *
 * «6 действий» ничего не говорит про конфликт, ждущий решения игрока, и про историю ответов,
 * расписание повторов и неопубликованные черновики, которых сервер не хранит. Согласие, полученное
 * на одну цифру, не покрывает того, что удаляется.
 */
class AccountSwitchWarningTest {

    private fun loss(
        waiting: Int = 0,
        waitingPrecondition: Int = 0,
        conflicted: Int = 0,
        quarantined: Int = 0,
        synchronous: Int = 0,
        isKnown: Boolean = true,
        namedData: List<AccountDataKind> = AccountDataKind.entries,
    ) = AccountSwitchLoss(
        waiting = waiting,
        waitingPrecondition = waitingPrecondition,
        conflicted = conflicted,
        quarantined = quarantined,
        synchronous = synchronous,
        isKnown = isKnown,
        namedData = namedData,
    )

    @Test
    fun `given numbers by state then each non-zero gets its own line, in a fixed order`() {
        val warning =
            loss(waiting = 3, waitingPrecondition = 1, conflicted = 0, quarantined = 2, synchronous = 1).toWarning()

        assertEquals(
            listOf(
                AccountSwitchCount(AccountSwitchCountKind.WAITING, 3),
                AccountSwitchCount(AccountSwitchCountKind.WAITING_PRECONDITION, 1),
                AccountSwitchCount(AccountSwitchCountKind.QUARANTINED, 2),
                AccountSwitchCount(AccountSwitchCountKind.SYNCHRONOUS, 1),
            ),
            warning.counts,
        )
        assertTrue(warning.counts.none { it.value == 7 }, "суммарного числа на экране нет — только состояния")
    }

    @Test
    fun `given a zero then it is not shown at all`() {
        val warning = loss(conflicted = 1).toWarning()

        assertEquals(listOf(AccountSwitchCount(AccountSwitchCountKind.CONFLICTED, 1)), warning.counts)
    }

    @Test
    fun `given data without a server twin then the warning names every kind of it`() {
        val warning = loss(waiting = 1).toWarning()

        assertEquals(
            listOf(
                AccountDataKind.ANSWER_HISTORY,
                AccountDataKind.REPETITION_SCHEDULE,
                AccountDataKind.UNPUBLISHED_QUEST_DRAFTS,
            ),
            warning.namedData,
        )
    }

    @Test
    fun `given the queue is empty but data would be lost then the question is still whole`() {
        // Вопрос вызывают не только числа: черновики и история ответов исчезают и при пустой
        // очереди. Показывать тогда нечего числами — но назвать данные обязательно, иначе диалог
        // окажется пустым, а согласие получено ни на что.
        val warning = loss(namedData = listOf(AccountDataKind.UNPUBLISHED_QUEST_DRAFTS)).toWarning()

        assertTrue(warning.counts.isEmpty(), "нулей на экране нет")
        assertEquals(listOf(AccountDataKind.UNPUBLISHED_QUEST_DRAFTS), warning.namedData)
        assertTrue(warning.isKnown, "числа известны — их просто нет")
    }

    @Test
    fun `given the count is unknown then the names are still there`() {
        val warning = loss(isKnown = false).toWarning()

        assertTrue(warning.counts.isEmpty())
        assertEquals(AccountDataKind.entries, warning.namedData)
        assertTrue(!warning.isKnown)
    }
}
