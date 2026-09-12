package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ArenaReviewSubmissionTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcomeRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.coroutines.CancellationException

/**
 * The second half of the authoring loop: a review verdict reaches the one local submission that
 * is still waiting for it.
 *
 * Auth is the boundary for reading: a shared device must never query owner A's private review
 * requests under owner B's Firebase token. The local active submission ID is the boundary for
 * writing: an old terminal result cannot reopen or publish a later resubmission of the same
 * draft.
 */
class QuestArenaOutcomeSync(
    private val local: QuestAuthoringLocalDataSource,
    private val remote: QuestArenaSubmissionRemoteDataSource,
    private val timestampProvider: QuestAuthoringTimestampProvider,
    private val currentUidProvider: suspend () -> String?,
) : Syncable {
    override suspend fun sync(): Result<Unit> =
        try {
            val ownerUid = currentUidProvider()?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
            val targets = local.findAwaitingReviewTargets(ownerUid)
            if (targets.isEmpty()) return Result.success(Unit)

            val exactBySubmissionId =
                targets.mapNotNull { target ->
                    target.activeSubmissionId?.let { submissionId -> submissionId to target }
                }.toMap()
            check(exactBySubmissionId.size == targets.count { it.activeSubmissionId != null }) {
                "Awaiting drafts must not share an active review submission"
            }
            val legacyDraftIds = targets.filter { it.activeSubmissionId == null }.mapTo(linkedSetOf()) { it.draftId }
            val outcomes =
                remote.fetchOutcomes(
                    QuestSubmissionOutcomeRequest(
                        submissionIds = exactBySubmissionId.keys,
                        legacyDraftIds = legacyDraftIds,
                    ),
                )
            validateOutcomes(outcomes, exactBySubmissionId, legacyDraftIds)

            outcomes.forEach { outcome ->
                val exactTarget = exactBySubmissionId[outcome.submissionId]
                if (exactTarget != null) {
                    settle(ownerUid, exactTarget, outcome)
                } else {
                    // A legacy lookup is allowed only for an old row with no current submission
                    // marker. Claim an in-progress server submission so the next run becomes an
                    // exact named read; a terminal result is settled by the same conditional DB
                    // predicate and a newer resubmission blocks it.
                    if (!outcome.isRejected && !outcome.isPublished) {
                        local.claimLegacyReviewSubmission(ownerUid, outcome.draftId, outcome.submissionId)
                    }
                    settle(
                        ownerUid = ownerUid,
                        target = ArenaReviewSubmissionTarget(outcome.draftId, activeSubmissionId = null),
                        outcome = outcome,
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun settle(
        ownerUid: String,
        target: ArenaReviewSubmissionTarget,
        outcome: QuestSubmissionOutcome,
    ) {
        // Both terminal states leave the review-waiting state. The data source conditionally
        // checks owner, draft, status and active submission before it mutates anything.
        when {
            outcome.isRejected ->
                local.applyRejection(
                    ownerUid = ownerUid,
                    draftId = target.draftId,
                    submissionId = outcome.submissionId,
                    reason = outcome.rejectionReason,
                    updatedAtMs = timestampProvider.nowMs(),
                )

            outcome.isPublished ->
                local.applyPublication(
                    ownerUid = ownerUid,
                    draftId = target.draftId,
                    submissionId = outcome.submissionId,
                    updatedAtMs = timestampProvider.nowMs(),
                )
        }
    }

    private fun validateOutcomes(
        outcomes: List<QuestSubmissionOutcome>,
        exactBySubmissionId: Map<String, ArenaReviewSubmissionTarget>,
        legacyDraftIds: Set<String>,
    ) {
        val seenSubmissions = mutableSetOf<String>()
        val seenLegacyDrafts = mutableSetOf<String>()
        outcomes.forEach { outcome ->
            check(outcome.submissionId.isNotBlank() && outcome.draftId.isNotBlank()) {
                "Review outcome carries a blank identifier"
            }
            check(outcome.status in REVIEW_SUBMISSION_STATUSES) {
                "Review outcome carries an unsupported status"
            }
            check(
                if (outcome.isRejected) !outcome.rejectionReason.isNullOrBlank() else outcome.rejectionReason == null,
            ) { "Review outcome carries an invalid author-visible rejection reason" }
            check(seenSubmissions.add(outcome.submissionId)) { "Review response repeats a submission" }

            val exactTarget = exactBySubmissionId[outcome.submissionId]
            if (exactTarget != null) {
                check(exactTarget.draftId == outcome.draftId) {
                    "Review response changes the draft bound to a submission"
                }
            } else {
                check(outcome.draftId in legacyDraftIds) {
                    "Review response names an outcome this device did not request"
                }
                check(seenLegacyDrafts.add(outcome.draftId)) {
                    "Review response returns more than one legacy outcome for a draft"
                }
            }
        }
    }

    private companion object {
        val REVIEW_SUBMISSION_STATUSES = setOf("PROCESSING", "UNDER_REVIEW", "PUBLISHED", "REJECTED")
    }
}
