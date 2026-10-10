package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestionReviewOutcome
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcomeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FirebaseQuestArenaSubmissionRemoteDataSourceTest {

    private fun assertIllegalState(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // Expected: a malformed projection must fail closed.
        }
    }

    @Test
    fun decoderProjectsOnlyTheServerAllowlist() {
        val outcomes =
            questReviewSubmissionOutcomes(
                mapOf(
                    "viewVersion" to "quest-review-submissions-v1",
                    "entries" to
                        listOf(
                            mapOf(
                                "submissionId" to "submission-1",
                                "draftId" to "draft-1",
                                "status" to "REJECTED",
                                "rejectionReason" to "Correct the format",
                                // A malformed/widened server response still cannot leak this
                                // field through the platform DTO.
                                "draft" to mapOf("questions" to listOf("private")),
                            ),
                        ),
                ),
                QuestSubmissionOutcomeRequest(setOf("submission-1"), emptySet()),
            )

        assertEquals(
            listOf(
                QuestSubmissionOutcome(
                    submissionId = "submission-1",
                    draftId = "draft-1",
                    status = "REJECTED",
                    rejectionReason = "Correct the format",
                ),
            ),
            outcomes,
        )
    }

    @Test
    fun decoderCarriesFailedStagesAndPerQuestionOutcomes_whileAnOldDocumentStaysEmpty() {
        val outcomes =
            questReviewSubmissionOutcomes(
                mapOf(
                    "viewVersion" to "quest-review-submissions-v1",
                    "entries" to
                        listOf(
                            mapOf(
                                "submissionId" to "submission-1",
                                "draftId" to "draft-1",
                                "status" to "UNDER_REVIEW",
                                "rejectionReason" to null,
                                "failedStages" to listOf("TESTING", "TRANSLATION"),
                                "questionOutcomes" to
                                    listOf(
                                        mapOf("questionId" to "q1", "testingScore" to 2.5, "passed" to true),
                                        mapOf("questionId" to "q2", "testingScore" to null, "passed" to false),
                                    ),
                            ),
                            mapOf(
                                "submissionId" to "submission-2",
                                "draftId" to "draft-2",
                                "status" to "UNDER_REVIEW",
                                "rejectionReason" to null,
                                // Старый документ: аддитивных полей нет — это честная пустота.
                            ),
                        ),
                ),
                QuestSubmissionOutcomeRequest(setOf("submission-1", "submission-2"), emptySet()),
            )

        assertEquals(
            listOf(
                QuestSubmissionOutcome(
                    submissionId = "submission-1",
                    draftId = "draft-1",
                    status = "UNDER_REVIEW",
                    rejectionReason = null,
                    failedStages = listOf("TESTING", "TRANSLATION"),
                    questionOutcomes =
                        listOf(
                            QuestionReviewOutcome("q1", 2.5, passed = true),
                            QuestionReviewOutcome("q2", null, passed = false),
                        ),
                ),
                QuestSubmissionOutcome(
                    submissionId = "submission-2",
                    draftId = "draft-2",
                    status = "UNDER_REVIEW",
                    rejectionReason = null,
                ),
            ),
            outcomes,
        )
    }

    @Test
    fun decoderFailsClosedOnMalformedOutcomes() {
        val request = QuestSubmissionOutcomeRequest(setOf("submission-1"), emptySet())

        fun entry(outcomes: Any?) =
            mapOf(
                "viewVersion" to "quest-review-submissions-v1",
                "entries" to
                    listOf(
                        mapOf(
                            "submissionId" to "submission-1",
                            "draftId" to "draft-1",
                            "status" to "UNDER_REVIEW",
                            "rejectionReason" to null,
                            "questionOutcomes" to outcomes,
                        ),
                    ),
            )

        // Оценка не число — перечитать, а не осесть на мусоре.
        assertIllegalState { questReviewSubmissionOutcomes(entry(listOf(mapOf("questionId" to "q1", "testingScore" to "3", "passed" to true))), request) }
        // Флаг порога потерян — то же.
        assertIllegalState { questReviewSubmissionOutcomes(entry(listOf(mapOf("questionId" to "q1", "testingScore" to 2.0))), request) }
        // Стадия не строка — то же.
        assertIllegalState {
            questReviewSubmissionOutcomes(
                mapOf(
                    "viewVersion" to "quest-review-submissions-v1",
                    "entries" to
                        listOf(
                            mapOf(
                                "submissionId" to "submission-1",
                                "draftId" to "draft-1",
                                "status" to "UNDER_REVIEW",
                                "rejectionReason" to null,
                                "failedStages" to listOf(42),
                            ),
                        ),
                ),
                request,
            )
        }
    }

    @Test
    fun decoderFailsClosedForAnUnrequestedOrInvalidOutcome() {
        val request = QuestSubmissionOutcomeRequest(setOf("submission-1"), emptySet())

        assertIllegalState {
            questReviewSubmissionOutcomes(
                mapOf(
                    "viewVersion" to "quest-review-submissions-v1",
                    "entries" to
                        listOf(
                            mapOf(
                                "submissionId" to "submission-other",
                                "draftId" to "draft-other",
                                "status" to "PUBLISHED",
                                "rejectionReason" to null,
                            ),
                        ),
                ),
                request,
            )
        }
        assertIllegalState {
            questReviewSubmissionOutcomes(
                mapOf(
                    "viewVersion" to "quest-review-submissions-v1",
                    "entries" to
                        listOf(
                            mapOf(
                                "submissionId" to "submission-1",
                                "draftId" to "draft-1",
                                "status" to "REJECTED",
                                "rejectionReason" to " ",
                            ),
                        ),
                ),
                request,
            )
        }
    }
}
