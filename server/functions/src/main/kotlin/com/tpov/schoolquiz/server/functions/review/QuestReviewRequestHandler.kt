package com.tpov.schoolquiz.server.functions.review

class QuestReviewRequestHandler(
    private val store: QuestReviewRequestStore,
    private val processor: QuestArenaSubmissionProcessor,
) {
    suspend fun processPending(limit: Int = DEFAULT_LIMIT): Result<List<ReviewSubmissionResult>> =
        runCatching {
            store.readPendingSubmissions(limit).map { request ->
                processor.process(request).getOrThrow()
            }
        }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}
