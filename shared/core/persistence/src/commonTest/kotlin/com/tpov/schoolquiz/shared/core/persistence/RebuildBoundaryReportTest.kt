package com.tpov.schoolquiz.shared.core.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The report is what the rebuild decision is taken against, so its two failure modes are pinned:
 * calling a dirty database clean, and printing a clean verdict without printing the numbers behind
 * it. Both would make the record unreadable months later, when the only evidence left is the text.
 */
class RebuildBoundaryReportTest {

    @Test
    fun `all four zero is clean and blocks nothing`() {
        val report = RebuildBoundaryReport(0, 0, 0, 0)

        assertTrue(report.isClean)
        assertEquals(emptyList(), report.blockingTables)
    }

    @Test
    fun `a single pending row anywhere blocks the rebuild and names its table`() {
        val cases = listOf(
            RebuildBoundaryReport(1, 0, 0, 0) to
                RebuildBoundaryReport.TABLE_LESSON_RESULT_ATTEMPT_OUTBOX,
            RebuildBoundaryReport(0, 1, 0, 0) to RebuildBoundaryReport.TABLE_QUEST_RATING_OUTBOX,
            RebuildBoundaryReport(0, 0, 1, 0) to
                RebuildBoundaryReport.TABLE_QUEST_ARENA_SUBMISSION_OUTBOX,
            RebuildBoundaryReport(0, 0, 0, 1) to
                RebuildBoundaryReport.TABLE_QUEST_DRAFTS_UNDELIVERED,
        )

        cases.forEach { (report, expectedTable) ->
            assertFalse(report.isClean, "expected blocked: $report")
            assertEquals(listOf(expectedTable), report.blockingTables)
            assertTrue(expectedTable in report.render(), "render must name $expectedTable")
        }
    }

    @Test
    fun `the rendering always prints all four counts, zeroes included`() {
        val rendered = RebuildBoundaryReport(0, 0, 0, 0).render()

        val lines = rendered.lines()
        assertEquals(5, lines.size, "four counts and a verdict, got: $rendered")
        assertEquals("${RebuildBoundaryReport.TABLE_LESSON_RESULT_ATTEMPT_OUTBOX}: 0", lines[0])
        assertEquals("${RebuildBoundaryReport.TABLE_QUEST_RATING_OUTBOX}: 0", lines[1])
        assertEquals("${RebuildBoundaryReport.TABLE_QUEST_ARENA_SUBMISSION_OUTBOX}: 0", lines[2])
        assertEquals("${RebuildBoundaryReport.TABLE_QUEST_DRAFTS_UNDELIVERED}: 0", lines[3])
        assertTrue(lines[4].startsWith("CLEAN"), "verdict line was: ${lines[4]}")
    }

    @Test
    fun `several non-empty tables are all named, in table order`() {
        val report = RebuildBoundaryReport(2, 0, 3, 7)

        assertEquals(
            listOf(
                RebuildBoundaryReport.TABLE_LESSON_RESULT_ATTEMPT_OUTBOX,
                RebuildBoundaryReport.TABLE_QUEST_ARENA_SUBMISSION_OUTBOX,
                RebuildBoundaryReport.TABLE_QUEST_DRAFTS_UNDELIVERED,
            ),
            report.blockingTables,
        )
        assertTrue(report.render().contains("BLOCKED"))
    }
}
