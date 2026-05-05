package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto

class SubmitReviewActionProcessor(
    private val store: ReviewActionStore,
    private val clock: ReviewClock,
) {
    suspend fun submit(
        authUid: String,
        action: SubmitReviewAction,
    ): Result<ReviewActionResult> =
        runCatching {
            val profile = requireNotNull(store.readTrustedProfile(authUid)) {
                "Trusted profile $authUid not found"
            }
            val task = requireNotNull(store.readAdminReviewLessonTask(action.assignmentId)) {
                "Review assignment ${action.assignmentId} not found"
            }
            require(task.lessonId == action.lessonId) {
                "Action lessonId ${action.lessonId} does not match assignment lessonId ${task.lessonId}"
            }

            val config = store.readArenaReviewConfig()
            val existingRecords = store.readReviewRecords(action.lessonId)
            require(ReviewAssignmentPolicy.canSubmit(profile, task, action.kind, config, existingRecords)) {
                "Reviewer ${profile.uid} is not allowed to submit ${action.kind} for ${action.assignmentId}"
            }

            val now = clock.nowMs()
            val record = action.toRecord(profile, task, config, existingRecords, now)
            val taskWithTranslatedQuestions =
                if (record.kind == ReviewTaskKind.TRANSLATION) {
                    task.copy(questions = mergeQuestions(task.questions, record.translatedQuestions))
                } else {
                    task
                }
            val aggregation =
                ReviewAggregateCalculator.rebuild(
                    task = taskWithTranslatedQuestions,
                    records = existingRecords + record,
                    config = config,
                )
            val updatedTask =
                taskWithTranslatedQuestions.copy(
                    checks = aggregation.checks,
                    changedAtMs = now,
                )
            val previousAggregation =
                ReviewAggregateCalculator.rebuild(
                    task = task,
                    records = existingRecords,
                    config = config,
                )
            val reviewerDeltas =
                newReviewerDeltas(previousAggregation.reviewerDeltas, aggregation.reviewerDeltas) +
                    translationReviewerDeltas(record, existingRecords)

            store.writeReviewRecord(record)
            store.writeAdminReviewLessonTasks(listOf(updatedTask))
            reviewerDeltas.forEach { delta ->
                if (delta.points != 0) store.addReviewerReputation(delta.reviewerUid, delta.points)
            }

            ReviewActionResult(
                record = record,
                aggregate = aggregation.checks,
                reviewerDeltas = reviewerDeltas,
            )
        }

    private fun newReviewerDeltas(
        previous: List<ReviewerReputationDelta>,
        current: List<ReviewerReputationDelta>,
    ): List<ReviewerReputationDelta> {
        val remainingPrevious = previous.groupingBy { it }.eachCount().toMutableMap()
        return current.filter { delta ->
            val count = remainingPrevious.getOrDefault(delta, 0)
            if (count > 0) {
                if (count == 1) {
                    remainingPrevious -= delta
                } else {
                    remainingPrevious[delta] = count - 1
                }
                false
            } else {
                true
            }
        }
    }

    private fun SubmitReviewAction.toRecord(
        profile: TrustedProfile,
        task: AdminReviewLessonTask,
        config: ArenaReviewConfig?,
        existingRecords: List<ReviewRecord>,
        now: Long,
    ): ReviewRecord {
        val reviewerLevel = profile.levelFor(kind)
        val language = language?.normalizedLanguage()
        val reviewId = listOf(assignmentId, kind.name.lowercase(), now.toString(), profile.uid)
            .joinToString(separator = "_")
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")

        return when (kind) {
            ReviewTaskKind.TESTING,
            ReviewTaskKind.LOGIC -> {
                require(score != null) { "$kind requires score" }
                ReviewRecord(
                    id = reviewId,
                    lessonId = lessonId,
                    kind = kind,
                    reviewerUid = profile.uid,
                    reviewerLevelAtSubmit = reviewerLevel,
                    score = score,
                    createdAtMs = now,
                    acceptedByServer = true,
                )
            }
            ReviewTaskKind.TRANSLATION -> {
                require(language != null) { "TRANSLATION requires language" }
                val targets = ReviewAssignmentPolicy.translationTargets(profile, task, config)
                require(language in targets.newTranslationLanguages) {
                    "Language $language is not open for translation"
                }
                require(translatedQuestions.isNotEmpty()) { "TRANSLATION requires translatedQuestions" }
                val normalizedQuestions =
                    translatedQuestions.map { question ->
                        question.copy(
                            id = translatedQuestionId(question.id, language),
                            language = language,
                            languageLevel = reviewerLevel,
                        )
                    }
                ReviewRecord(
                    id = reviewId,
                    lessonId = lessonId,
                    kind = kind,
                    reviewerUid = profile.uid,
                    reviewerLevelAtSubmit = reviewerLevel,
                    language = language,
                    createdAtMs = now,
                    acceptedByServer = true,
                    translatedQuestions = normalizedQuestions,
                )
            }
            ReviewTaskKind.TRANSLATION_REVIEW -> {
                require(language != null) { "TRANSLATION_REVIEW requires language" }
                require(segmentResults.isNotEmpty()) { "TRANSLATION_REVIEW requires segmentResults" }
                val targets = ReviewAssignmentPolicy.translationTargets(profile, task, config)
                require(language in targets.reviewLanguages) {
                    "Language $language is not open for translation review"
                }
                val targetRecord =
                    targetReviewId?.let { id -> existingRecords.firstOrNull { it.id == id } }
                        ?: existingRecords
                            .filter { it.kind == ReviewTaskKind.TRANSLATION && it.language == language }
                            .maxByOrNull { it.createdAtMs }
                requireNotNull(targetRecord) { "Translation record for $language not found" }
                require(profile.translatorLevel >= targetRecord.reviewerLevelAtSubmit + TRANSLATION_REVIEW_LEVEL_GAP) {
                    "Reviewer level must be at least $TRANSLATION_REVIEW_LEVEL_GAP above translation level"
                }
                ReviewRecord(
                    id = reviewId,
                    lessonId = lessonId,
                    kind = kind,
                    reviewerUid = profile.uid,
                    reviewerLevelAtSubmit = reviewerLevel,
                    language = language,
                    targetReviewId = targetRecord.id,
                    createdAtMs = now,
                    acceptedByServer = true,
                    segmentResults = segmentResults,
                )
            }
        }
    }

    private fun translationReviewerDeltas(
        record: ReviewRecord,
        existingRecords: List<ReviewRecord>,
    ): List<ReviewerReputationDelta> {
        if (record.kind != ReviewTaskKind.TRANSLATION_REVIEW) return emptyList()
        val targetRecord = existingRecords.firstOrNull { it.id == record.targetReviewId } ?: return emptyList()
        return record.segmentResults.map { result ->
            ReviewerReputationDelta(
                reviewerUid = targetRecord.reviewerUid,
                points = if (result.accepted) ACCEPTED_TRANSLATION_SEGMENT_POINTS else REJECTED_TRANSLATION_SEGMENT_POINTS,
            )
        }
    }

    private fun mergeQuestions(
        current: List<ArenaQuestionDto>,
        translated: List<ArenaQuestionDto>,
    ): List<ArenaQuestionDto> {
        val translatedIds = translated.mapTo(hashSetOf()) { it.id }
        return (current.filterNot { it.id in translatedIds } + translated)
            .sortedWith(compareBy<ArenaQuestionDto> { it.lessonId }.thenBy { it.language }.thenBy { it.order })
    }

    private fun translatedQuestionId(
        sourceQuestionId: String,
        language: String,
    ): String {
        val suffix = "__$language"
        return if (sourceQuestionId.endsWith(suffix)) sourceQuestionId else "$sourceQuestionId$suffix"
    }

    private fun TrustedProfile.levelFor(kind: ReviewTaskKind): Int =
        when (kind) {
            ReviewTaskKind.TESTING -> maxOf(testerLevel, adminLevel, developerLevel)
            ReviewTaskKind.LOGIC -> maxOf(adminLevel, developerLevel)
            ReviewTaskKind.TRANSLATION,
            ReviewTaskKind.TRANSLATION_REVIEW -> maxOf(translatorLevel, developerLevel)
        }

    private fun String.normalizedLanguage(): String = trim().lowercase()

    private companion object {
        const val TRANSLATION_REVIEW_LEVEL_GAP = 100
        const val ACCEPTED_TRANSLATION_SEGMENT_POINTS = 5
        const val REJECTED_TRANSLATION_SEGMENT_POINTS = -1
    }
}

interface ReviewActionStore : ReviewAssignmentStore {
    suspend fun readAdminReviewLessonTask(assignmentId: String): AdminReviewLessonTask?

    suspend fun readReviewRecords(lessonId: String): List<ReviewRecord>

    suspend fun writeReviewRecord(record: ReviewRecord)

    suspend fun writeAdminReviewLessonTasks(tasks: List<AdminReviewLessonTask>)

    suspend fun addReviewerReputation(
        uid: String,
        points: Int,
    )
}
