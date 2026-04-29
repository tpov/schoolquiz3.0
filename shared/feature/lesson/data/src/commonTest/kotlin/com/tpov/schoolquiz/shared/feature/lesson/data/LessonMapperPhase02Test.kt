package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
import com.tpov.schoolquiz.shared.feature.lesson.data.mapper.LessonDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.mapper.LessonMapper.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM mapper tests for phase-02 new fields on LessonEntity and LessonDto.
 * Source: docs/features/lesson-runner/plan/phase-02/tests.md §Mapper-01..Mapper-04
 *
 * Run with: ./gradlew :shared:feature:lesson:data:jvmTest --no-configuration-cache
 *
 * Spec coverage:
 *   Mapper-01: toDomain maps averageRating, ratingCount, top3 (empty list)
 *   Mapper-02: toDomain passes top3 List<TopParticipant> unchanged
 *   Mapper-03: LessonDto without top3 (null) → LessonEntity.top3 == emptyList(), no NPE
 *   Mapper-04: LessonDto with null ratingCount → LessonEntity.ratingCount == 0
 */
class LessonMapperPhase02Test {

    // Mapper-01: GIVEN LessonEntity with averageRating=2.5, ratingCount=10, top3=emptyList
    //            WHEN toDomain() THEN domain fields mapped correctly
    @Test
    fun `lessonMapper_toDomain_newFieldsMapped`() {
        val entity = makeEntity(averageRating = 2.5f, ratingCount = 10)

        val domain = entity.toDomain()

        assertEquals(2.5f, domain.averageRating, "averageRating must be 2.5f")
        assertEquals(10, domain.ratingCount, "ratingCount must be 10")
        assertTrue(domain.top3.isEmpty(), "top3 must be emptyList for default")
    }

    // Mapper-01b: GIVEN LessonEntity with averageRating=null WHEN toDomain() THEN averageRating is null
    @Test
    fun `lessonMapper_toDomain_averageRating_null`() {
        val entity = makeEntity(averageRating = null, ratingCount = 0)

        val domain = entity.toDomain()

        assertNull(domain.averageRating, "averageRating should be null when entity field is null")
        assertEquals(0, domain.ratingCount)
        assertTrue(domain.top3.isEmpty())
    }

    // Mapper-02: GIVEN LessonEntity with top3=List<TopParticipant> WHEN toDomain() THEN top3 passed through
    @Test
    fun `lessonMapper_toDomain_top3Passed`() {
        val participants = listOf(TopParticipant(nickname = "Bob", avatarUrl = null, percent = 85))
        val entity = makeEntity(top3 = participants)

        val domain = entity.toDomain()

        assertEquals(1, domain.top3.size, "Expected 1 TopParticipant")
        val p = domain.top3.first()
        assertEquals("Bob", p.nickname)
        assertNull(p.avatarUrl)
        assertEquals(85, p.percent)
    }

    // Mapper-03: GIVEN LessonDto without top3 (null) WHEN toEntity() THEN top3 == emptyList(), no NPE
    @Test
    fun `lessonDtoMapper_backwardCompat_missingTop3Field`() {
        val dto = makeDtoWithNullTop3()

        val entity = dto.toEntity()

        assertTrue(entity.top3.isEmpty(), "top3 must be emptyList when DTO field is null")
    }

    // Mapper-04: GIVEN LessonDto with null ratingCount WHEN toEntity() THEN ratingCount == 0
    @Test
    fun `lessonDtoMapper_backwardCompat_missingRatingCount`() {
        val dto = makeDtoWithNullRatingCount()

        val entity = dto.toEntity()

        assertEquals(0, entity.ratingCount, "ratingCount must default to 0 when DTO field is null")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun makeEntity(
        id: String = "l1",
        themeId: String = "th1",
        title: String = "Lesson",
        order: Int = 0,
        version: Long = 1L,
        contentsVersion: Long = 0L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
        averageRating: Float? = null,
        ratingCount: Int = 0,
        top3: List<TopParticipant> = emptyList(),
    ) = LessonEntity(
        id = id,
        themeId = themeId,
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
        averageRating = averageRating,
        ratingCount = ratingCount,
        top3 = top3,
    )

    private fun makeDtoWithNullTop3() = LessonDto(
        id = "l1",
        themeId = "th1",
        title = "Lesson",
        order = 0,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
        archived = false,
        averageRating = null,
        ratingCount = 0,
        top3 = null,
    )

    private fun makeDtoWithNullRatingCount() = LessonDto(
        id = "l1",
        themeId = "th1",
        title = "Lesson",
        order = 0,
        version = 1L,
        contentsVersion = 0L,
        lastModifiedAt = 0L,
        archived = false,
        averageRating = null,
        ratingCount = null,
        top3 = null,
    )
}
