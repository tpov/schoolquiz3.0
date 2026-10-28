package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonLifecycleWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Строгий декодер view-конвертов TZ06B: проекция читается только под подтверждённой редакцией
 * view, и каждое поле домена получает ровно то, что прислал сервер.
 */
class LessonLifecycleWireParserTest {
    private val reviewEntry =
        mapOf(
            "lessonId" to "lesson-under-review",
            "currentContentVersion" to 3L,
            "published" to true,
            "reviewApproved" to false,
            "rating" to
                mapOf(
                    "sum" to 0L,
                    "count" to 0L,
                    "average" to null,
                    "version" to 0L,
                    "updatedAtMs" to null,
                ),
            "state" to "REVIEW",
            "placement" to null,
        )

    @Test
    fun `lifecycle page decodes review and placed entries`() {
        val placed =
            mapOf(
                "lessonId" to "lesson-placed",
                "currentContentVersion" to 7L,
                "published" to true,
                "reviewApproved" to true,
                "rating" to
                    mapOf(
                        "sum" to 4L,
                        "count" to 2L,
                        "average" to 2.0,
                        "version" to 2L,
                        "updatedAtMs" to 1_000L,
                    ),
                "state" to "PLACED",
                "placement" to
                    mapOf(
                        "shelf" to "arena",
                        "previousShelf" to "review",
                        "contentVersion" to 6L,
                        "currentVersion" to false,
                        "placementVersion" to 2L,
                        "updatedAtMs" to 2_000L,
                    ),
            )
        val page =
            LessonLifecycleWireParser.lifecyclePage(
                mapOf(
                    "viewVersion" to LessonLifecycleWireParser.VIEW_VERSION,
                    "entries" to listOf(reviewEntry, placed),
                    "nextLessonId" to null,
                ),
            )

        assertEquals(2, page.entries.size)
        assertNull(page.nextLessonId)
        val review = page.entries.first()
        assertEquals(LessonLifecycleState.REVIEW, review.state)
        assertNull(review.placement)
        assertNull(review.rating.average)
        val placedEntry = page.entries.last()
        assertEquals(LessonLifecycleState.PLACED, placedEntry.state)
        assertEquals(LessonShelf.ARENA, placedEntry.placement?.shelf)
        assertEquals(false, placedEntry.placement?.currentVersion)
        assertEquals(2.0, placedEntry.rating.average)
    }

    @Test
    fun `lifecycle page refuses a foreign view version`() {
        val error =
            assertFailsWith<IllegalStateException> {
                LessonLifecycleWireParser.lifecyclePage(
                    mapOf(
                        "viewVersion" to "arena-lifecycle-views-v2",
                        "entries" to listOf(reviewEntry),
                        "nextLessonId" to null,
                    ),
                )
            }
        assertTrue(error.message!!.contains("view version"))
    }

    @Test
    fun `history page decodes statuses and permits a missing reason code`() {
        val page =
            LessonLifecycleWireParser.historyPage(
                mapOf(
                    "viewVersion" to LessonLifecycleWireParser.VIEW_VERSION,
                    "entries" to
                        listOf(
                            mapOf(
                                "fromShelf" to "review",
                                "targetShelf" to "arena",
                                "status" to "APPLIED",
                                "reasonCode" to null,
                                "decidedAtMs" to 5_000L,
                            ),
                            mapOf(
                                "fromShelf" to "review",
                                "targetShelf" to "arena",
                                "status" to "REJECTED",
                                "reasonCode" to "REVIEW_APPROVAL_REQUIRED",
                                "decidedAtMs" to 6_000L,
                            ),
                        ),
                    "nextCursor" to "opaque-cursor",
                ),
            )

        assertEquals(2, page.entries.size)
        assertTrue(page.hasNext)
        assertEquals("opaque-cursor", page.nextCursor)
        assertEquals(LessonHistoryStatus.APPLIED, page.entries.first().status)
        assertNull(page.entries.first().reasonCode)
        assertEquals("REVIEW_APPROVAL_REQUIRED", page.entries.last().reasonCode)
    }

    @Test
    fun `royalty page decodes summary and ledger rows`() {
        val page =
            LessonLifecycleWireParser.royaltyPage(
                mapOf(
                    "viewVersion" to LessonLifecycleWireParser.VIEW_VERSION,
                    "summary" to mapOf("totalCreditedNolics" to 31L, "updatedAtMs" to 9_000L),
                    "entries" to
                        listOf(
                            mapOf("creditedNolics" to 4L, "creditedAtMs" to 8_000L),
                        ),
                    "nextCursor" to null,
                ),
            )

        assertEquals(31L, page.summary.totalCreditedNolics)
        assertEquals(1, page.entries.size)
        assertEquals(4L, page.entries.first().creditedNolics)
        assertEquals(false, page.hasNext)
    }

    @Test
    fun `transition receipt decodes applied decision`() {
        val command = LessonTransitionCommand("lesson-1", LessonShelf.ARENA, "admit after review")
        val decision =
            LessonLifecycleWireParser.transitionDecision(
                mapOf(
                    "schemaVersion" to 1L,
                    "mutationId" to "mutation-1",
                    "decidedAtMs" to 7_000L,
                    "status" to "APPLIED",
                    "result" to
                        mapOf(
                            "lessonId" to "lesson-1",
                            "fromShelf" to "review",
                            "targetShelf" to "arena",
                            "awarded" to null,
                            "alreadyAwarded" to false,
                        ),
                ),
                command,
            )

        assertTrue(decision is LessonTransitionDecision.Applied)
        assertEquals(LessonShelf.REVIEW, (decision as LessonTransitionDecision.Applied).fromShelf)
        assertEquals(LessonShelf.ARENA, decision.targetShelf)
    }

    @Test
    fun `transition receipt keeps a rejection as an honest decision`() {
        val command = LessonTransitionCommand("lesson-1", LessonShelf.TOURNAMENT, "promote")
        val decision =
            LessonLifecycleWireParser.transitionDecision(
                mapOf(
                    "schemaVersion" to 1L,
                    "mutationId" to "mutation-2",
                    "decidedAtMs" to 8_000L,
                    "status" to "REJECTED",
                    "result" to mapOf("code" to "REVIEW_APPROVAL_REQUIRED"),
                ),
                command,
            )

        assertTrue(decision is LessonTransitionDecision.Rejected)
        assertEquals("REVIEW_APPROVAL_REQUIRED", (decision as LessonTransitionDecision.Rejected).code)
        assertEquals("promote", decision.reason)
    }

    @Test
    fun `transition receipt refuses a receipt naming another target shelf`() {
        val command = LessonTransitionCommand("lesson-1", LessonShelf.HOME, "promote")
        assertFailsWith<IllegalStateException> {
            LessonLifecycleWireParser.transitionDecision(
                mapOf(
                    "schemaVersion" to 1L,
                    "mutationId" to "mutation-3",
                    "decidedAtMs" to 9_000L,
                    "status" to "APPLIED",
                    "result" to
                        mapOf(
                            "lessonId" to "lesson-1",
                            "fromShelf" to "review",
                            "targetShelf" to "arena",
                        ),
                ),
                command,
            )
        }
    }

    @Test
    fun `transition receipt accepts the schema version in every integer wire form`() {
        // Живой прогон 4.3: Android callable SDK отдал schemaVersion не Long, и прежний
        // `as? Long` отбрасывал честную квитанцию как «unsupported schema version».
        val command = LessonTransitionCommand("lesson-1", LessonShelf.ARENA, "admit")
        for (wireForm in listOf(1, 1.0)) {
            val decision =
                LessonLifecycleWireParser.transitionDecision(
                    mapOf(
                        "schemaVersion" to wireForm,
                        "mutationId" to "mutation-4",
                        "decidedAtMs" to 11_000,
                        "status" to "APPLIED",
                        "result" to
                            mapOf(
                                "lessonId" to "lesson-1",
                                "fromShelf" to "review",
                                "targetShelf" to "arena",
                            ),
                    ),
                    command,
                )
            assertTrue(decision is LessonTransitionDecision.Applied)
        }
        assertFailsWith<IllegalStateException> {
            LessonLifecycleWireParser.transitionDecision(
                mapOf(
                    "schemaVersion" to 2L,
                    "mutationId" to "mutation-5",
                    "decidedAtMs" to 12_000L,
                    "status" to "APPLIED",
                    "result" to
                        mapOf(
                            "lessonId" to "lesson-1",
                            "fromShelf" to "review",
                            "targetShelf" to "arena",
                        ),
                ),
                command,
            )
        }
    }

    @Test
    fun `integer fields decode the non-Long wire forms the callable SDK sends`() {
        // Ретро 2026-10-25, L1: requiredLong/optionalLong исчерпывающе матчили is Long/is Int,
        // хотя живой прогон 4.3 уже доказал, что SDK отдаёт числа и другими Number-формами, —
        // чтение падало после того, как сервер применил переход.
        val page =
            LessonLifecycleWireParser.lifecyclePage(
                mapOf(
                    "viewVersion" to LessonLifecycleWireParser.VIEW_VERSION,
                    "entries" to
                        listOf(
                            mapOf(
                                "lessonId" to "lesson-int-forms",
                                "currentContentVersion" to 3,
                                "published" to true,
                                "reviewApproved" to false,
                                "rating" to
                                    mapOf(
                                        "sum" to 4,
                                        "count" to 2,
                                        "average" to 2.0,
                                        "version" to 2,
                                        "updatedAtMs" to null,
                                    ),
                                "state" to "PLACED",
                                "placement" to
                                    mapOf(
                                        "shelf" to "arena",
                                        "previousShelf" to "review",
                                        "contentVersion" to 6,
                                        "currentVersion" to true,
                                        "placementVersion" to 2,
                                        "updatedAtMs" to 2_000,
                                    ),
                            ),
                        ),
                    "nextLessonId" to null,
                ),
            )

        val entry = page.entries.single()
        assertEquals(3L, entry.currentContentVersion)
        assertEquals(4L, entry.rating.sum)
        assertEquals(2L, entry.rating.version)
        assertNull(entry.rating.updatedAtMs)
        assertEquals(2L, entry.placement?.placementVersion)
        assertEquals(2_000L, entry.placement?.updatedAtMs)

        // Строка в целом поле по-прежнему отказ: коэрсия — только про числовые формы.
        val stringForm =
            assertFailsWith<IllegalStateException> {
                LessonLifecycleWireParser.lifecyclePage(
                    mapOf(
                        "viewVersion" to LessonLifecycleWireParser.VIEW_VERSION,
                        "entries" to
                            listOf(
                                mapOf(
                                    "lessonId" to "lesson-string-form",
                                    "currentContentVersion" to "3",
                                    "published" to true,
                                    "reviewApproved" to false,
                                    "rating" to
                                        mapOf(
                                            "sum" to 0L,
                                            "count" to 0L,
                                            "average" to null,
                                            "version" to 0L,
                                            "updatedAtMs" to null,
                                        ),
                                    "state" to "REVIEW",
                                    "placement" to null,
                                ),
                            ),
                        "nextLessonId" to null,
                    ),
                )
            }
        assertTrue(stringForm.message!!.contains("not an integer"))
    }
}
