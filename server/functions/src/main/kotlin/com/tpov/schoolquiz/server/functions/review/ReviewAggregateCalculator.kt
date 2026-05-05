package com.tpov.schoolquiz.server.functions.review

import kotlin.math.abs
import kotlin.math.roundToInt

object ReviewAggregateCalculator {
    fun rebuild(
        task: AdminReviewLessonTask,
        records: List<ReviewRecord>,
        config: ArenaReviewConfig?,
    ): ReviewAggregationResult {
        val accepted = records.filter { it.acceptedByServer }
        val testing = scoreAggregate(accepted, ReviewTaskKind.TESTING)
        val logic = scoreAggregate(accepted, ReviewTaskKind.LOGIC)
        val translatedLanguages = translatedLanguages(task, accepted)
        val requiredLanguages = requiredLanguages(task, config)
        val translationReviews = accepted.filter { it.kind == ReviewTaskKind.TRANSLATION_REVIEW }
        val reviewedSegments = translationReviews.flatMap { it.segmentResults }
        val translationScore =
            reviewedSegments.takeIf { it.isNotEmpty() }
                ?.let { segments -> segments.count { it.accepted } * 100 / segments.size }
        val checks =
            ReviewChecks(
                isTested = testing.score != null,
                testingScore = testing.score,
                isLogicReviewed = logic.score != null,
                logicScore = logic.score,
                isTranslationReviewed = requiredLanguages.all { it in translatedLanguages.keys },
                translationScore = translationScore,
                translatedLanguages = translatedLanguages,
            )
        return ReviewAggregationResult(
            checks = checks,
            reviewerDeltas = testing.reviewerDeltas + logic.reviewerDeltas,
        )
    }

    fun requiredLanguages(
        task: AdminReviewLessonTask,
        config: ArenaReviewConfig?,
    ): Set<String> {
        val configured = config?.requiredLanguages.orEmpty().mapTo(linkedSetOf()) { it.normalizedLanguage() }
            .filterTo(linkedSetOf()) { it.isNotBlank() }
        if (configured.isNotEmpty()) return configured
        return task.sourceLanguages
    }

    private fun scoreAggregate(
        records: List<ReviewRecord>,
        kind: ReviewTaskKind,
    ): ScoreAggregation {
        val scored = records.filter { it.kind == kind && it.score != null }
        if (scored.isEmpty()) return ScoreAggregation(score = null)

        val maxReviewerLevel = scored.maxOf { it.reviewerLevelAtSubmit }
        val activeThreshold = maxReviewerLevel - ACTIVE_LEVEL_WINDOW
        val active = scored.filter { it.reviewerLevelAtSubmit >= activeThreshold }
        val activeScore = active.mapNotNull { it.score }.average()
        val activeStars = activeScore.roundToInt().coerceIn(1, 3)
        val activeIds = active.mapTo(hashSetOf()) { it.id }
        val deltas =
            scored
                .filterNot { it.id in activeIds }
                .mapNotNull { weak ->
                    val weakScore = weak.score ?: return@mapNotNull null
                    val points =
                        when (abs(weakScore - activeStars)) {
                            0 -> 3
                            1 -> 0
                            else -> -3
                        }
                    ReviewerReputationDelta(weak.reviewerUid, points)
                }
        return ScoreAggregation(
            score = activeScore,
            reviewerDeltas = deltas,
        )
    }

    private fun translatedLanguages(
        task: AdminReviewLessonTask,
        records: List<ReviewRecord>,
    ): Map<String, Int> {
        val sourceQuestionCount = task.sourceQuestionCount()
        val result = linkedMapOf<String, Int>()
        task.checks.translatedLanguages.toSortedMap().forEach { (language, level) ->
            val normalized = language.normalizedLanguage()
            if (normalized.isNotBlank()) result[normalized] = level
        }
        records
            .filter { it.kind == ReviewTaskKind.TRANSLATION && it.language != null }
            .groupBy { it.language!!.normalizedLanguage() }
            .forEach { (language, languageRecords) ->
                val completed =
                    languageRecords
                        .filter { it.translatedQuestions.size >= sourceQuestionCount }
                        .maxByOrNull { it.reviewerLevelAtSubmit }
                        ?: return@forEach
                result[language] = completed.reviewerLevelAtSubmit
            }
        return result
    }

    private fun AdminReviewLessonTask.sourceQuestionCount(): Int =
        questions
            .groupBy { it.language.normalizedLanguage() }
            .values
            .maxOfOrNull { it.size }
            ?: questions.size

    private fun String.normalizedLanguage(): String = trim().lowercase()

    private data class ScoreAggregation(
        val score: Double?,
        val reviewerDeltas: List<ReviewerReputationDelta> = emptyList(),
    )

    private const val ACTIVE_LEVEL_WINDOW = 100
}

data class ReviewAggregationResult(
    val checks: ReviewChecks,
    val reviewerDeltas: List<ReviewerReputationDelta> = emptyList(),
)
