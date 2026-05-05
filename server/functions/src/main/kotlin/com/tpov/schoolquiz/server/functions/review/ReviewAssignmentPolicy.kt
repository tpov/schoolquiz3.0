package com.tpov.schoolquiz.server.functions.review

object ReviewAssignmentPolicy {
    fun availableTasks(
        profile: TrustedProfile,
        task: AdminReviewLessonTask,
        config: ArenaReviewConfig? = null,
    ): Set<ReviewTaskKind> {
        val openTasks =
            if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) {
                task.openTasksForDeveloper(config)
            } else {
                task.openTasksFor(profile, config)
            }
        if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) return openTasks

        val result = mutableSetOf<ReviewTaskKind>()
        if (profile.testerLevel >= QUALIFIED_LEVEL && ReviewTaskKind.TESTING in openTasks) {
            result += ReviewTaskKind.TESTING
        }
        if (profile.adminLevel >= QUALIFIED_LEVEL) {
            if (ReviewTaskKind.TESTING in openTasks) result += ReviewTaskKind.TESTING
            if (ReviewTaskKind.LOGIC in openTasks) result += ReviewTaskKind.LOGIC
        }
        if (profile.translatorLevel >= QUALIFIED_LEVEL) {
            val targets = translationTargets(profile, task, config)
            if (targets.newTranslationLanguages.isNotEmpty()) result += ReviewTaskKind.TRANSLATION
            if (targets.reviewLanguages.isNotEmpty()) result += ReviewTaskKind.TRANSLATION_REVIEW
        }
        return result
    }

    fun canSubmit(
        profile: TrustedProfile,
        task: AdminReviewLessonTask,
        kind: ReviewTaskKind,
        config: ArenaReviewConfig? = null,
        existingRecords: List<ReviewRecord> = emptyList(),
    ): Boolean {
        if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) {
            return task.isStageOpenForSubmit(kind, existingRecords, config)
        }

        return when (kind) {
            ReviewTaskKind.TESTING ->
                profile.hasTestingQualification() &&
                    task.isStageOpenForSubmit(ReviewTaskKind.TESTING, existingRecords, config)
            ReviewTaskKind.LOGIC ->
                profile.hasLogicQualification() &&
                    task.isStageOpenForSubmit(ReviewTaskKind.LOGIC, existingRecords, config)
            ReviewTaskKind.TRANSLATION ->
                profile.translatorLevel >= QUALIFIED_LEVEL &&
                    translationTargets(profile, task, config).newTranslationLanguages.isNotEmpty()
            ReviewTaskKind.TRANSLATION_REVIEW ->
                profile.translatorLevel >= QUALIFIED_LEVEL &&
                    translationTargets(profile, task, config).reviewLanguages.isNotEmpty()
        }
    }

    fun translationTargets(
        profile: TrustedProfile,
        task: AdminReviewLessonTask,
        config: ArenaReviewConfig? = null,
    ): TranslationTargets {
        if (!task.checks.isReadyForTranslation || profile.translatorLevel < QUALIFIED_LEVEL) {
            return TranslationTargets(emptySet(), emptySet(), emptySet())
        }

        val known = profile.knownLanguages.map { it.normalizedLanguage() }.filter { it.isNotBlank() }.toSet()
        val sourceLanguages =
            task.sourceLanguages
                .map { it.normalizedLanguage() }
                .filter { it.isNotBlank() }
                .toSet()
        val translated =
            task.checks.translatedLanguages.keys
                .map { it.normalizedLanguage() }
                .filter { it.isNotBlank() }
                .toSet()
        val knownSources = sourceLanguages intersect known
        if (knownSources.isEmpty()) {
            return TranslationTargets(emptySet(), emptySet(), emptySet())
        }

        val requiredLanguages = ReviewAggregateCalculator.requiredLanguages(task, config)
        val newTargets = (requiredLanguages - translated) intersect known
        val reviewThreshold = profile.translatorLevel - TRANSLATION_REVIEW_LEVEL_GAP
        val reviewTargets =
            if (newTargets.isEmpty()) {
                task.checks.translatedLanguages
                    .filterKeys { it.normalizedLanguage() in known }
                    .filterKeys { it.normalizedLanguage() !in sourceLanguages }
                    .filterValues { it <= reviewThreshold }
                    .keys
                    .map { it.normalizedLanguage() }
                    .toSet()
            } else {
                emptySet()
        }
        return TranslationTargets(
            sourceLanguages = knownSources,
            newTranslationLanguages = newTargets,
            reviewLanguages = reviewTargets,
        )
    }

    private fun AdminReviewLessonTask.openTasksFor(
        profile: TrustedProfile,
        config: ArenaReviewConfig?,
    ): Set<ReviewTaskKind> {
        val result = mutableSetOf<ReviewTaskKind>()
        when {
            !checks.hasTestingResult -> result += ReviewTaskKind.TESTING
            !checks.hasLogicResult -> result += ReviewTaskKind.LOGIC
            else -> {
                val targets = translationTargets(profile, this, config)
                if (targets.newTranslationLanguages.isNotEmpty()) result += ReviewTaskKind.TRANSLATION
                if (targets.reviewLanguages.isNotEmpty()) result += ReviewTaskKind.TRANSLATION_REVIEW
            }
        }
        return result
    }

    private fun AdminReviewLessonTask.isStageOpenForSubmit(
        kind: ReviewTaskKind,
        existingRecords: List<ReviewRecord>,
        config: ArenaReviewConfig?,
    ): Boolean =
        when (kind) {
            ReviewTaskKind.TESTING -> !checks.hasLogicResult && !existingRecords.hasAcceptedLogic()
            ReviewTaskKind.LOGIC -> checks.hasTestingResult && !existingRecords.hasAcceptedTranslation()
            ReviewTaskKind.TRANSLATION ->
                checks.isReadyForTranslation &&
                    ReviewAggregateCalculator.requiredLanguages(this, config)
                        .any { it !in checks.translatedLanguages.keys.map { language -> language.normalizedLanguage() } }
            ReviewTaskKind.TRANSLATION_REVIEW -> checks.isReadyForTranslation
        }

    private fun AdminReviewLessonTask.openTasksForDeveloper(config: ArenaReviewConfig?): Set<ReviewTaskKind> =
        when {
            !checks.hasTestingResult -> setOf(ReviewTaskKind.TESTING)
            !checks.hasLogicResult -> setOf(ReviewTaskKind.LOGIC)
            ReviewAggregateCalculator.requiredLanguages(this, config)
                .any { it !in checks.translatedLanguages.keys.map { language -> language.normalizedLanguage() } } ->
                setOf(ReviewTaskKind.TRANSLATION)
            else -> emptySet()
        }

    private fun TrustedProfile.hasTestingQualification(): Boolean =
        testerLevel >= QUALIFIED_LEVEL || adminLevel >= QUALIFIED_LEVEL

    private fun TrustedProfile.hasLogicQualification(): Boolean =
        adminLevel >= QUALIFIED_LEVEL

    private fun List<ReviewRecord>.hasAcceptedLogic(): Boolean =
        any { it.acceptedByServer && it.kind == ReviewTaskKind.LOGIC }

    private fun List<ReviewRecord>.hasAcceptedTranslation(): Boolean =
        any {
            it.acceptedByServer &&
                (it.kind == ReviewTaskKind.TRANSLATION || it.kind == ReviewTaskKind.TRANSLATION_REVIEW)
        }

    private fun String.normalizedLanguage(): String = trim().lowercase()

    private const val QUALIFIED_LEVEL = 100
    private const val DEVELOPER_ALL_ACCESS_LEVEL = 100
    private const val TRANSLATION_REVIEW_LEVEL_GAP = 100
}
