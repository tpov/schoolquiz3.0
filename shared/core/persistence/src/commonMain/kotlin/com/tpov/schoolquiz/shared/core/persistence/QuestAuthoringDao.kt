package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestAuthoringDao {

    @Query(
        """
        SELECT
            draft.id AS id,
            draft.catalogId AS catalogId,
            draft.title AS title,
            draft.status AS status,
            COUNT(question.id) AS questionCount,
            draft.updatedAtMs AS updatedAtMs,
            draft.isActive AS isActive
        FROM quest_drafts draft
        LEFT JOIN draft_questions question ON question.draftId = draft.id
        WHERE draft.ownerUid = :ownerUid
        GROUP BY draft.id
        ORDER BY draft.updatedAtMs DESC
        """,
    )
    fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>>

    @Query("SELECT * FROM quest_drafts WHERE id = :draftId")
    fun observeDraftEntity(draftId: String): Flow<QuestDraftEntity?>

    @Query("SELECT * FROM draft_sections WHERE draftId = :draftId ORDER BY `order` ASC")
    fun observeSections(draftId: String): Flow<List<DraftSectionEntity>>

    @Query("SELECT * FROM draft_themes WHERE draftId = :draftId ORDER BY `order` ASC")
    fun observeThemes(draftId: String): Flow<List<DraftThemeEntity>>

    @Query("SELECT * FROM draft_lessons WHERE draftId = :draftId ORDER BY `order` ASC")
    fun observeLessons(draftId: String): Flow<List<DraftLessonEntity>>

    @Query("SELECT * FROM draft_questions WHERE draftId = :draftId ORDER BY `order` ASC")
    fun observeQuestions(draftId: String): Flow<List<DraftQuestionEntity>>

    @Query("SELECT * FROM quest_drafts WHERE id = :draftId")
    suspend fun findDraftById(draftId: String): QuestDraftEntity?

    @Query(
        """
        SELECT * FROM quest_drafts
        WHERE ownerUid = :ownerUid
        AND isActive = 1
        AND status IN (:activeStatuses)
        ORDER BY updatedAtMs DESC
        LIMIT 1
        """,
    )
    suspend fun findActiveDraft(
        ownerUid: String,
        activeStatuses: List<String>,
    ): QuestDraftEntity?

    @Query("SELECT * FROM draft_sections WHERE draftId = :draftId ORDER BY `order` ASC")
    suspend fun findSections(draftId: String): List<DraftSectionEntity>

    @Query("SELECT * FROM draft_themes WHERE draftId = :draftId ORDER BY `order` ASC")
    suspend fun findThemes(draftId: String): List<DraftThemeEntity>

    @Query("SELECT * FROM draft_lessons WHERE draftId = :draftId ORDER BY `order` ASC")
    suspend fun findLessons(draftId: String): List<DraftLessonEntity>

    @Query("SELECT * FROM draft_questions WHERE draftId = :draftId ORDER BY `order` ASC")
    suspend fun findQuestions(draftId: String): List<DraftQuestionEntity>

    @Query("SELECT draftId FROM draft_lessons WHERE id = :lessonId")
    suspend fun findLessonDraftId(lessonId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(entity: QuestDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(entities: List<DraftSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThemes(entities: List<DraftThemeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(entities: List<DraftLessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(entities: List<DraftQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(entity: DraftQuestionEntity)

    @Query("DELETE FROM draft_questions WHERE draftId = :draftId")
    suspend fun deleteQuestions(draftId: String)

    @Query("DELETE FROM draft_lessons WHERE draftId = :draftId")
    suspend fun deleteLessons(draftId: String)

    @Query("DELETE FROM draft_themes WHERE draftId = :draftId")
    suspend fun deleteThemes(draftId: String)

    @Query("DELETE FROM draft_sections WHERE draftId = :draftId")
    suspend fun deleteSections(draftId: String)

    @Query("UPDATE quest_drafts SET isActive = 0 WHERE ownerUid = :ownerUid AND id != :exceptDraftId")
    suspend fun deactivateOtherDrafts(ownerUid: String, exceptDraftId: String)

    @Query(
        """
        UPDATE quest_drafts
        SET updatedAtMs = :updatedAtMs,
            localRevision = localRevision + 1,
            status = 'SAVED'
        WHERE id = :draftId
        """,
    )
    suspend fun touchDraftAfterQuestionChange(draftId: String, updatedAtMs: Long)

    @Query(
        """
        UPDATE quest_drafts
        SET status = :status,
            updatedAtMs = :updatedAtMs
        WHERE id = :draftId
        """,
    )
    suspend fun updateDraftStatus(
        draftId: String,
        status: String,
        updatedAtMs: Long,
    )

    /** Drafts whose submission is still awaiting a verdict, newest first. */
    @Query("SELECT * FROM quest_drafts WHERE status = 'REVIEW_SENT' ORDER BY updatedAtMs DESC LIMIT :limit")
    suspend fun findDraftsAwaitingReview(limit: Int): List<QuestDraftEntity>

    /**
     * Sends a draft back to its author with the reviewer's reason attached. Status returns to
     * DRAFT so it is editable again; the reason stays until the next submission clears it.
     */
    @Query(
        """
        UPDATE quest_drafts
        SET status = :status,
            rejectionReason = :rejectionReason,
            updatedAtMs = :updatedAtMs
        WHERE id = :draftId
        """,
    )
    suspend fun updateDraftStatusWithReason(
        draftId: String,
        status: String,
        rejectionReason: String?,
        updatedAtMs: Long,
    )

    @Transaction
    suspend fun saveDraft(
        draft: QuestDraftEntity,
        sections: List<DraftSectionEntity>,
        themes: List<DraftThemeEntity>,
        lessons: List<DraftLessonEntity>,
        questions: List<DraftQuestionEntity>,
    ) {
        if (draft.isActive) {
            deactivateOtherDrafts(draft.ownerUid, draft.id)
        }
        insertDraft(draft)
        deleteQuestions(draft.id)
        deleteLessons(draft.id)
        deleteThemes(draft.id)
        deleteSections(draft.id)
        if (sections.isNotEmpty()) insertSections(sections)
        if (themes.isNotEmpty()) insertThemes(themes)
        if (lessons.isNotEmpty()) insertLessons(lessons)
        if (questions.isNotEmpty()) insertQuestions(questions)
    }

    @Transaction
    suspend fun upsertQuestion(entity: DraftQuestionEntity) {
        val lessonDraftId = requireNotNull(findLessonDraftId(entity.lessonId)) {
            "Draft lesson ${entity.lessonId} not found"
        }
        require(lessonDraftId == entity.draftId) {
            "Question draft ${entity.draftId} does not match lesson draft $lessonDraftId"
        }
        insertQuestion(entity)
        touchDraftAfterQuestionChange(entity.draftId, entity.updatedAtMs)
    }
}
