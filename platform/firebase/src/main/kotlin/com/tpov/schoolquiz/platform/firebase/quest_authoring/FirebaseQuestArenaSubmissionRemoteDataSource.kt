package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestionReviewOutcome
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcomeRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.MAX_QUEST_REVIEW_OUTCOME_TARGETS
import kotlinx.coroutines.tasks.await

/**
 * Только чтение вердиктов.
 *
 * Прямая запись заявки в `quest_review_requests` снята: отложенная мутация уходит единственным
 * приёмником (AD-6), потому что ключ идемпотентности проверяется там, где выполняется код, — а
 * правило Firestore кода не выполняет и вторую доставку той же заявки отличить не может.
 */
class FirebaseQuestArenaSubmissionRemoteDataSource(
    private val functions: FirebaseFunctions,
) : QuestArenaSubmissionRemoteDataSource {
    override suspend fun fetchOutcomes(request: QuestSubmissionOutcomeRequest): List<QuestSubmissionOutcome> {
        if (request.submissionIds.isEmpty() && request.legacyDraftIds.isEmpty()) return emptyList()
        val data =
            functions
                .getHttpsCallable(LIST_MY_QUEST_REVIEW_SUBMISSIONS)
                .withAppTimeout()
                .call(
                    mapOf(
                        SUBMISSION_IDS to request.submissionIds.sorted(),
                        LEGACY_DRAFT_IDS to request.legacyDraftIds.sorted(),
                    ),
                )
                .await()
                .data
        return questReviewSubmissionOutcomes(data, request)
    }
}

/** Strictly decodes the narrow server view; malformed or widened data must retry, never settle a draft. */
internal fun questReviewSubmissionOutcomes(
    data: Any?,
    request: QuestSubmissionOutcomeRequest,
): List<QuestSubmissionOutcome> {
    val root = data as? Map<*, *> ?: throw IllegalStateException("Review outcome response is not an object")
    check(root.string(VIEW_VERSION) == QUEST_REVIEW_SUBMISSIONS_VIEW_VERSION) {
        "Review outcome response has an unsupported view version"
    }
    val rawEntries = root[ENTRIES] as? List<*> ?: throw IllegalStateException("Review outcome entries are missing")
    check(rawEntries.size <= MAX_QUEST_REVIEW_OUTCOME_TARGETS) { "Review outcome response exceeds its bound" }

    val seenSubmissionIds = mutableSetOf<String>()
    val seenLegacyDraftIds = mutableSetOf<String>()
    return rawEntries.map { rawEntry ->
        val entry = rawEntry as? Map<*, *> ?: throw IllegalStateException("Review outcome entry is not an object")
        val submissionId = entry.requiredString(SUBMISSION_ID)
        val draftId = entry.requiredString(DRAFT_ID)
        val status = entry.requiredString(STATUS)
        check(status in REVIEW_SUBMISSION_STATUSES) { "Review outcome has an unsupported status" }
        val reason = entry[REJECTION_REASON]?.let { it as? String ?: throw IllegalStateException("Invalid rejection reason") }
        check(if (status == QuestSubmissionOutcome.STATUS_REJECTED) !reason.isNullOrBlank() else reason == null) {
            "Review outcome carries an invalid author-visible reason"
        }
        check(seenSubmissionIds.add(submissionId)) { "Review outcome response repeats a submission" }
        if (submissionId in request.submissionIds) {
            // The shared sync verifies the exact draft/submission pairing against Room before it
            // mutates. The adapter only confirms the server did not widen the named request.
        } else {
            check(draftId in request.legacyDraftIds) { "Review outcome was not requested" }
            check(seenLegacyDraftIds.add(draftId)) { "Legacy draft received more than one outcome" }
        }
        QuestSubmissionOutcome(
            submissionId = submissionId,
            draftId = draftId,
            status = status,
            rejectionReason = reason,
            failedStages = entry.failedStages(),
            questionOutcomes = entry.questionOutcomes(),
        )
    }
}

/**
 * Стадии и по-вопросные исходы — аддитивные поля вью: старый документ отвечает их отсутствием,
 * и это честная пустота. Битое поле — не пустота, а повод перечитать: оседать на мусоре синхронизация
 * не должна.
 */
private const val MAX_FAILED_STAGES = 8
private const val MAX_QUESTION_OUTCOMES = 64

private fun Map<*, *>.failedStages(): List<String> {
    val raw = this[FAILED_STAGES] ?: return emptyList()
    val stages = raw as? List<*> ?: throw IllegalStateException("Review outcome failed stages are not a list")
    check(stages.size <= MAX_FAILED_STAGES) { "Review outcome names too many failed stages" }
    return stages.map { stage ->
        stage as? String ?: throw IllegalStateException("Review outcome failed stage is not a string")
    }
}

private fun Map<*, *>.questionOutcomes(): List<QuestionReviewOutcome> {
    val raw = this[QUESTION_OUTCOMES] ?: return emptyList()
    val outcomes = raw as? List<*> ?: throw IllegalStateException("Review outcome list is not a list")
    check(outcomes.size <= MAX_QUESTION_OUTCOMES) { "Review outcome exceeds its question bound" }
    return outcomes.map { item ->
        val outcome = item as? Map<*, *> ?: throw IllegalStateException("Review outcome entry is not an object")
        val questionId = outcome.requiredString(QUESTION_ID)
        val testingScore = when (val score = outcome[TESTING_SCORE]) {
            null -> null
            is Number -> score.toDouble()
            else -> throw IllegalStateException("Review outcome testing score is not a number")
        }
        val passed = outcome[PASSED] as? Boolean ?: throw IllegalStateException("Review outcome passed flag is missing")
        QuestionReviewOutcome(questionId = questionId, testingScore = testingScore, passed = passed)
    }
}

private fun Map<*, *>.string(key: String): String? = this[key] as? String

private fun Map<*, *>.requiredString(key: String): String =
    string(key)?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Review outcome field $key is missing")

private const val LIST_MY_QUEST_REVIEW_SUBMISSIONS = "listMyQuestReviewSubmissions"
private const val QUEST_REVIEW_SUBMISSIONS_VIEW_VERSION = "quest-review-submissions-v1"
private const val SUBMISSION_IDS = "submissionIds"
private const val LEGACY_DRAFT_IDS = "legacyDraftIds"
private const val VIEW_VERSION = "viewVersion"
private const val ENTRIES = "entries"
private const val SUBMISSION_ID = "submissionId"
private const val DRAFT_ID = "draftId"
private const val STATUS = "status"
private const val REJECTION_REASON = "rejectionReason"
private const val FAILED_STAGES = "failedStages"
private const val QUESTION_OUTCOMES = "questionOutcomes"
private const val QUESTION_ID = "questionId"
private const val TESTING_SCORE = "testingScore"
private const val PASSED = "passed"
private val REVIEW_SUBMISSION_STATUSES = setOf("PROCESSING", "UNDER_REVIEW", "PUBLISHED", "REJECTED")
