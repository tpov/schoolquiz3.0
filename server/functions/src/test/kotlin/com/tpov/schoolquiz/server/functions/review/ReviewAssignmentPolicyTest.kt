package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewAssignmentPolicyTest {
    @Test
    fun testerOnlyReceivesUntestedTasks() {
        val profile = TrustedProfile(uid = "tester", testerLevel = 100)

        val tasks = ReviewAssignmentPolicy.availableTasks(profile, task())

        assertEquals(setOf(ReviewTaskKind.TESTING), tasks)
    }

    @Test
    fun adminReceivesLogicAfterTestingIsDone() {
        val profile = TrustedProfile(uid = "admin", adminLevel = 100)

        val tasks =
            ReviewAssignmentPolicy.availableTasks(
                profile,
                task(checks = ReviewChecks(isTested = true, testingScore = 2.5)),
            )

        assertEquals(setOf(ReviewTaskKind.LOGIC), tasks)
    }

    @Test
    fun translatorDoesNotReceiveTasksBeforeTestingAndLogic() {
        val profile =
            TrustedProfile(
                uid = "translator",
                translatorLevel = 120,
                knownLanguages = setOf("ru", "en"),
            )

        val tasks = ReviewAssignmentPolicy.availableTasks(profile, task())

        assertTrue(tasks.isEmpty())
    }

    @Test
    fun translatorReceivesMissingKnownLanguageAfterTestingAndLogic() {
        val profile =
            TrustedProfile(
                uid = "translator",
                translatorLevel = 200,
                knownLanguages = setOf("ru", "en"),
            )

        val targets =
            ReviewAssignmentPolicy.translationTargets(
                profile,
                task(
                    checks =
                        ReviewChecks(
                            isTested = true,
                            testingScore = 2.0,
                            isLogicReviewed = true,
                            logicScore = 2.0,
                            translatedLanguages = mapOf("ru" to 25),
                        ),
                ),
                config(),
            )

        assertEquals(setOf("ru"), targets.sourceLanguages)
        assertEquals(setOf("en"), targets.newTranslationLanguages)
        assertEquals(
            setOf(ReviewTaskKind.TRANSLATION),
            ReviewAssignmentPolicy.availableTasks(profile, taskFor(targets), config()),
        )
    }

    @Test
    fun translatorCanReviewLanguageThatIsOneHundredLevelsBelow() {
        val profile =
            TrustedProfile(
                uid = "translator",
                translatorLevel = 125,
                knownLanguages = setOf("ru", "en"),
            )
        val reviewTask =
            task(
                checks =
                    ReviewChecks(
                        isTested = true,
                        testingScore = 2.0,
                        isLogicReviewed = true,
                        logicScore = 2.0,
                        translatedLanguages = mapOf("ru" to 25, "en" to 25),
                    ),
            )

        val tasks = ReviewAssignmentPolicy.availableTasks(profile, reviewTask, config())

        assertEquals(setOf(ReviewTaskKind.TRANSLATION_REVIEW), tasks)
    }

    @Test
    fun translatorCanReviewHighLanguageLevelOnlyWhenOneHundredLevelsAbove() {
        val profile =
            TrustedProfile(
                uid = "translator",
                translatorLevel = 250,
                knownLanguages = setOf("ru", "en"),
            )
        val almostQualified =
            profile.copy(
                uid = "translator-low",
                translatorLevel = 249,
            )
        val reviewTask =
            task(
                checks =
                    ReviewChecks(
                        isTested = true,
                        testingScore = 2.0,
                        isLogicReviewed = true,
                        logicScore = 2.0,
                        translatedLanguages = mapOf("ru" to 150, "en" to 150),
                    ),
            )

        assertEquals(
            setOf(ReviewTaskKind.TRANSLATION_REVIEW),
            ReviewAssignmentPolicy.availableTasks(profile, reviewTask, config()),
        )
        assertTrue(ReviewAssignmentPolicy.availableTasks(almostQualified, reviewTask, config()).isEmpty())
    }

    @Test
    fun translatorBelowQualificationLevelReceivesNothing() {
        val profile =
            TrustedProfile(
                uid = "translator",
                translatorLevel = 90,
                knownLanguages = setOf("ru", "en"),
            )

        val tasks =
            ReviewAssignmentPolicy.availableTasks(
                profile,
                task(checks = ReviewChecks(isTested = true, isLogicReviewed = true)),
                config(),
            )

        assertTrue(tasks.isEmpty())
    }

    @Test
    fun developerAboveOneHundredReceivesOpenStageEvenWithoutTranslatorLanguages() {
        val profile = TrustedProfile(uid = "developer", developerLevel = 101)

        val tasks =
            ReviewAssignmentPolicy.availableTasks(
                profile,
                task(checks = ReviewChecks(isTested = true, isLogicReviewed = true)),
                config(),
            )

        assertEquals(setOf(ReviewTaskKind.TRANSLATION), tasks)
    }

    private fun taskFor(targets: TranslationTargets): AdminReviewLessonTask =
        task(
            checks =
                ReviewChecks(
                    isTested = true,
                    testingScore = 2.0,
                    isLogicReviewed = true,
                    logicScore = 2.0,
                    translatedLanguages = targets.sourceLanguages.associateWith { 25 },
                ),
        )

    private fun config(): ArenaReviewConfig =
        ArenaReviewConfig(requiredLanguages = setOf("ru", "en"), updatedAtMs = 1L)

    private fun task(checks: ReviewChecks = ReviewChecks()): AdminReviewLessonTask =
        AdminReviewLessonTask(
            id = "task-1",
            submissionId = "submission-1",
            ownerUid = "owner-1",
            catalogId = "catalog-1",
            draftId = "draft-1",
            questId = "quest-1",
            lessonId = "lesson-1",
            title = "Lesson",
            createdAtMs = 1L,
            changedAtMs = 1L,
            checks = checks,
            questions =
                listOf(
                    ArenaQuestionDto(
                        id = "question-1",
                        draftId = "draft-1",
                        lessonId = "lesson-1",
                        type = "SINGLE_CHOICE",
                        language = "ru",
                        languageLevel = 7,
                        difficulty = "EASY",
                        order = 0,
                        text = "Question?",
                        imagePath = null,
                        payload = """{"type":"single_choice"}""",
                        updatedAtMs = 1L,
                    ),
                ),
        )
}
