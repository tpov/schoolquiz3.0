package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonResultAttemptOutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonResultSyncOutboxDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.QuestRatingOutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import kotlinx.datetime.Clock

interface LessonResultOutboxWriter {
    suspend fun enqueueAttempt(attempt: Attempt)

    suspend fun enqueueRating(rating: LessonRating)

    /**
     * Builds the queue row without writing it, so the caller can store it in the same transaction
     * as the attempt itself. Enqueueing separately used to leave a saved attempt that never
     * reached the server while the UI reported a save failure.
     *
     * Returns null when there is nothing to queue (no-op writer).
     */
    suspend fun buildAttemptRow(attempt: Attempt): LessonResultAttemptOutboxEntity? = null

    companion object {
        val NoOp: LessonResultOutboxWriter =
            object : LessonResultOutboxWriter {
                override suspend fun enqueueAttempt(attempt: Attempt) = Unit

                override suspend fun enqueueRating(rating: LessonRating) = Unit
            }
    }
}

class RoomLessonResultOutboxWriter(
    private val outboxDao: LessonResultSyncOutboxDao,
    private val lessonDao: LessonDao,
    private val themeDao: ThemeDao,
    private val sectionDao: SectionDao,
    private val questDao: QuestDao,
    private val clock: Clock,
) : LessonResultOutboxWriter {

    override suspend fun enqueueAttempt(attempt: Attempt) {
        outboxDao.upsertAttempt(buildAttemptRow(attempt))
    }

    override suspend fun buildAttemptRow(attempt: Attempt): LessonResultAttemptOutboxEntity {
        val context = resolveContentContext(attempt.lessonId.value)
        return LessonResultAttemptOutboxEntity(
            attemptId = attempt.id.value,
            userId = attempt.userId,
            scope = context.scope,
            ownerUid = context.ownerUid,
            catalogId = context.catalogId,
            questId = context.questId,
            sectionId = context.sectionId,
            themeId = context.themeId,
            lessonId = context.lessonId,
            lessonVersion = attempt.lessonVersion,
            sourceShelf = context.sourceShelf,
            difficulty = attempt.mode.name,
            codeAnswer = attempt.codeAnswer.raw,
            percentScore = attempt.percentScore.raw,
            completedAtMs = attempt.completedAt,
            createdAtMs = clock.now().toEpochMilliseconds(),
        )
    }

    override suspend fun enqueueRating(rating: LessonRating) {
        val context = resolveContentContext(rating.lessonId.value)
        outboxDao.upsertRating(
            QuestRatingOutboxEntity(
                ratingId = rating.id.value,
                userId = rating.userId,
                scope = context.scope,
                ownerUid = context.ownerUid,
                catalogId = context.catalogId,
                questId = context.questId,
                sectionId = context.sectionId,
                themeId = context.themeId,
                lessonId = context.lessonId,
                lessonVersion = rating.lessonVersion,
                sourceShelf = context.sourceShelf,
                rating = rating.rating,
                ratedAtMs = rating.ratedAt,
                createdAtMs = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    private suspend fun resolveContentContext(lessonId: String): LessonContentContext {
        val lesson = checkNotNull(lessonDao.findById(lessonId)) { "Lesson $lessonId not found" }
        val theme = checkNotNull(themeDao.findById(lesson.themeId)) { "Theme ${lesson.themeId} not found" }
        val section = checkNotNull(sectionDao.findById(theme.sectionId)) { "Section ${theme.sectionId} not found" }
        val quest = checkNotNull(questDao.findById(section.questId)) { "Quest ${section.questId} not found" }
        val scope = if (quest.visibleOn.isEmpty()) PRIVATE_SCOPE else PUBLIC_SCOPE
        return LessonContentContext(
            scope = scope,
            ownerUid = if (scope == PRIVATE_SCOPE) quest.authorUid else null,
            catalogId = quest.catalogId,
            questId = quest.id,
            sectionId = section.id,
            themeId = theme.id,
            lessonId = lesson.id,
            sourceShelf = quest.visibleOn.sourceShelf(),
        )
    }

    private fun Set<String>.sourceShelf(): String =
        when {
            ARENA_SHELF in this -> ARENA_SHELF
            HOME_SHELF in this -> HOME_SHELF
            ARCHIVE_SHELF in this -> ARCHIVE_SHELF
            isEmpty() -> PRIVATE_SCOPE
            else -> first()
        }
}

private data class LessonContentContext(
    val scope: String,
    val ownerUid: String?,
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
    val lessonId: String,
    val sourceShelf: String,
)

private const val PUBLIC_SCOPE = "public"
private const val PRIVATE_SCOPE = "private"
private const val ARENA_SHELF = "arena"
private const val HOME_SHELF = "home"
private const val ARCHIVE_SHELF = "archive"
