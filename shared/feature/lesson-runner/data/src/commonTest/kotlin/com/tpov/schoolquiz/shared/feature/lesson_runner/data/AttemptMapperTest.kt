package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mapper unit tests for LessonAttemptMapper (Attempt ↔ LessonAttemptEntity).
 * Source: docs/features/lesson-runner/plan/phase-03/tests.md §Map-01..Map-04
 */
class AttemptMapperTest {

    private fun makeAttempt(
        id: String = "uuid-123",
        mode: Difficulty = Difficulty.EASY,
        codeAnswer: String = "9",
        percentScore: Int = 100,
        completedAt: Long = 1000L,
        lessonVersion: Long = 2L,
    ) = Attempt(
        id = AttemptId(id),
        userId = "u1",
        lessonId = LessonId("l1"),
        lessonVersion = lessonVersion,
        mode = mode,
        codeAnswer = CodeAnswer(codeAnswer),
        percentScore = PercentScore(percentScore),
        completedAt = completedAt,
    )

    // Map-01: GIVEN Attempt(mode=EASY) WHEN toEntity() THEN entity.isHard == 0
    @Test
    fun attemptMapper_toEntity_isHardEasy() {
        val entity = makeAttempt(mode = Difficulty.EASY).toEntity()

        assertEquals(0, entity.isHard, "EASY mode must map to isHard=0")
    }

    // Map-02: GIVEN Attempt(mode=HARD) WHEN toEntity() THEN entity.isHard == 1
    @Test
    fun attemptMapper_toEntity_isHardHard() {
        val entity = makeAttempt(mode = Difficulty.HARD).toEntity()

        assertEquals(1, entity.isHard, "HARD mode must map to isHard=1")
    }

    // Map-03: GIVEN Attempt(id=AttemptId("uuid-123")) WHEN toEntity() THEN entity.attemptId == "uuid-123"
    @Test
    fun attemptMapper_toEntity_attemptIdValue() {
        val entity = makeAttempt(id = "uuid-123").toEntity()

        assertEquals("uuid-123", entity.attemptId, "entity.attemptId must equal AttemptId.value (ADR-LR-12)")
    }

    // Map-04: GIVEN entity(isHard=1, attemptId="abc", codeAnswer="19", percentScore=85)
    //         WHEN toDomain()
    //         THEN mode=HARD, id.value="abc", codeAnswer.raw="19", percentScore.raw=85
    @Test
    fun attemptMapper_entityToDomain_roundtrip() {
        val entity = LessonAttemptEntity(
            attemptId = "abc",
            userId = "u1",
            lessonId = "l1",
            lessonVersion = 3L,
            isHard = 1,
            codeAnswer = "19",
            percentScore = 85,
            completedAt = 2000L,
        )

        val domain = entity.toDomain()

        assertEquals(Difficulty.HARD, domain.mode, "isHard=1 must map to Difficulty.HARD")
        assertEquals("abc", domain.id.value, "id.value must equal entity.attemptId")
        assertEquals("19", domain.codeAnswer.raw, "codeAnswer.raw must equal entity.codeAnswer")
        assertEquals(85, domain.percentScore.raw, "percentScore.raw must equal entity.percentScore")
    }
}
