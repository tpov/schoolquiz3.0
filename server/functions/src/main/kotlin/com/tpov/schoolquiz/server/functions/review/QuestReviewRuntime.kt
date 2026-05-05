package com.tpov.schoolquiz.server.functions.review

import com.google.cloud.firestore.Firestore

class QuestReviewRuntime private constructor(
    private val requestHandler: QuestReviewRequestHandler,
    private val assignmentChangesHandler: FetchReviewAssignmentChangesHandler,
    private val assignmentHandler: FetchReviewAssignmentsHandler,
    private val submitReviewActionHandler: SubmitReviewActionHandler,
) {
    suspend fun processPendingArenaRequests(limit: Int = DEFAULT_PENDING_LIMIT): Result<List<ReviewSubmissionResult>> =
        requestHandler.processPending(limit)

    suspend fun fetchReviewAssignmentChanges(
        authUid: String?,
        cursorMs: Long,
    ): Map<String, Any?> =
        assignmentChangesHandler.handle(authUid, cursorMs)

    suspend fun fetchReviewAssignments(
        authUid: String?,
        ids: Set<String>,
    ): Map<String, Any?> =
        assignmentHandler.handle(authUid, ids)

    suspend fun submitReviewAction(
        authUid: String?,
        action: SubmitReviewAction,
    ): Map<String, Any?> =
        submitReviewActionHandler.handle(authUid, action)

    companion object {
        fun fromFirestore(
            firestore: Firestore,
            clock: ReviewClock = SystemReviewClock,
        ): QuestReviewRuntime {
            val store = FirestoreQuestReviewStore(firestore)
            return fromStores(
                requestStore = store,
                assignmentStore = store,
                clock = clock,
            )
        }

        fun fromStores(
            requestStore: QuestReviewRequestStore,
            assignmentStore: ReviewActionStore,
            clock: ReviewClock = SystemReviewClock,
        ): QuestReviewRuntime =
            QuestReviewRuntime(
                requestHandler =
                    QuestReviewRequestHandler(
                        store = requestStore,
                        processor = QuestArenaSubmissionProcessor(requestStore, clock),
                    ),
                assignmentHandler =
                    FetchReviewAssignmentsHandler(
                        service = ReviewAssignmentService(assignmentStore),
                    ),
                assignmentChangesHandler =
                    FetchReviewAssignmentChangesHandler(
                        service = ReviewAssignmentService(assignmentStore),
                    ),
                submitReviewActionHandler =
                    SubmitReviewActionHandler(
                        processor = SubmitReviewActionProcessor(assignmentStore, clock),
                    ),
            )

        private const val DEFAULT_PENDING_LIMIT = 20
    }
}

object SystemReviewClock : ReviewClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
