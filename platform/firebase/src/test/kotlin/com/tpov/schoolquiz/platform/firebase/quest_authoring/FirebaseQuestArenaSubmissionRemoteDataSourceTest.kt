package com.tpov.schoolquiz.platform.firebase.quest_authoring

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
