package com.tpov.schoolquiz.shared.feature.quest_authoring.domain

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxItem
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitLessonErrorReportCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.InboxRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ListInboxItemsUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.SubmitLessonErrorReportUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Доменная половина общего inbox (AD-20): чтение гоняет страницу сквозь репозиторий, подача —
 * кладёт одно намерение в очередь с фиксированным идентификатором (AD-2) и обрезает текст до
 * серверной границы заранее.
 */
class InboxUseCaseTest {
    private class FakeInboxRepository : InboxRepository {
        var listed: Int = 0
        var lastPageSize: Int = 0
        var lastAnchor: InboxAnchor? = null
        var queued: MutableList<LessonErrorReportSubmission> = mutableListOf()

        override suspend fun listInboxItems(
            pageSize: Int,
            after: InboxAnchor?,
        ): Result<InboxPage> {
            listed += 1
            lastPageSize = pageSize
            lastAnchor = after
            return Result.success(
                InboxPage(
                    entries =
                        listOf(
                            InboxItem.PrivateErrorReport("r1", "les-9", "text", 5_000L),
                            InboxItem.LessonComment("c1", "les-9", "public", 4_000L, "Player"),
                        ),
                    nextAnchor = InboxAnchor(4_000L, "c1"),
                ),
            )
        }

        override suspend fun queueErrorReport(submission: LessonErrorReportSubmission): Result<Unit> {
            queued += submission
            return Result.success(Unit)
        }
    }

    private class FakeIdProvider : QuestAuthoringIdProvider {
        override fun nextId(prefix: String): String = "$prefix-77"
    }

    private class FakeTimestampProvider : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = 1_760_000_000_000
    }

    @Test
    fun `list passes the page bounds through and returns the page as is`() = runTest {
        val repository = FakeInboxRepository()
        val page = ListInboxItemsUseCase(repository)(pageSize = 20, after = InboxAnchor(9_000L, "x")).getOrThrow()

        assertEquals(2, page.entries.size)
        assertEquals(20, repository.lastPageSize)
        assertEquals(InboxAnchor(9_000L, "x"), repository.lastAnchor)
        assertTrue(page.entries[0] is InboxItem.PrivateErrorReport)
        assertTrue(page.entries[1] is InboxItem.LessonComment)
    }

    @Test
    fun `pageSize outside the server bounds is refused before any read`() = runTest {
        val repository = FakeInboxRepository()

        assertTrue(ListInboxItemsUseCase(repository)(pageSize = 0).isFailure)
        assertTrue(ListInboxItemsUseCase(repository)(pageSize = 21).isFailure)
        assertEquals(0, repository.listed)
    }

    @Test
    fun `submission trims the text and carries the fixed intent identity`() = runTest {
        val repository = FakeInboxRepository()
        val useCase =
            SubmitLessonErrorReportUseCase(repository, FakeIdProvider(), FakeTimestampProvider())

        val result = useCase(
            "author-uid",
            SubmitLessonErrorReportCommand("les-9", "  broken answer key  "),
        )

        assertTrue(result.isSuccess)
        val submission = repository.queued.single()
        assertEquals("lesson-error-77", submission.id)
        assertEquals(1_760_000_000_000, submission.requestedAtMs)
        assertEquals("broken answer key", submission.command.text)
    }

    @Test
    fun `blank or oversized text is refused before anything is queued`() = runTest {
        val repository = FakeInboxRepository()
        val useCase =
            SubmitLessonErrorReportUseCase(repository, FakeIdProvider(), FakeTimestampProvider())

        assertTrue(useCase("author-uid", SubmitLessonErrorReportCommand("les-9", "   ")).isFailure)
        assertTrue(useCase("author-uid", SubmitLessonErrorReportCommand("les-9", "x".repeat(1001))).isFailure)
        assertTrue(useCase("author-uid", SubmitLessonErrorReportCommand("", "text")).isFailure)
        assertEquals(0, repository.queued.size)
    }
}
