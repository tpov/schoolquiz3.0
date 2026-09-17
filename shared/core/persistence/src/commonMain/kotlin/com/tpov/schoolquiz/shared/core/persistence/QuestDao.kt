package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Query(
        """
        SELECT * FROM quests
        WHERE authorUid = :authorUid
        ORDER BY lastModifiedAt DESC
    """,
    )
    fun observeMyQuests(authorUid: String): Flow<List<QuestEntity>>

    @Query(
        """
        SELECT * FROM quests
        WHERE authorUid = :authorUid AND catalogId = :catalogId
        ORDER BY lastModifiedAt DESC
    """,
    )
    fun observeMyQuestsInCatalog(
        authorUid: String,
        catalogId: String,
    ): Flow<List<QuestEntity>>

    // Delimiter-wrapped exact-element match: prevents "shelf1" matching "shelf10"
    @Query(
        """
        SELECT * FROM quests
        WHERE (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')
        ORDER BY lastModifiedAt DESC
    """,
    )
    fun observeByShelf(shelf: String): Flow<List<QuestEntity>>

    @Query(
        """
        SELECT * FROM quests
        WHERE catalogId = :catalogId
        AND (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')
        ORDER BY lastModifiedAt DESC
    """,
    )
    fun observeByCatalog(
        catalogId: String,
        shelf: String,
    ): Flow<List<QuestEntity>>

    @Query(
        """
        SELECT q.* FROM quests q
        WHERE q.catalogId = :catalogId
        AND q.archived = 1
        AND (CHAR(31) || q.visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')
        AND EXISTS (
            SELECT 1 FROM sections s
            JOIN themes t ON t.sectionId = s.id AND t.archived = 0
            JOIN lessons l ON l.themeId = t.id AND l.archived = 0
            WHERE s.questId = q.id
            AND s.archived = 0
        )
        AND NOT EXISTS (
            SELECT 1 FROM sections s
            JOIN themes t ON t.sectionId = s.id AND t.archived = 0
            JOIN lessons l ON l.themeId = t.id AND l.archived = 0
            WHERE s.questId = q.id
            AND s.archived = 0
            AND NOT EXISTS (
                SELECT 1 FROM questions question
                WHERE question.lessonId = l.id
                AND question.archived = 0
            )
        )
        ORDER BY q.lastModifiedAt DESC
    """,
    )
    fun observeDownloadedArchivedByCatalog(
        catalogId: String,
        shelf: String,
    ): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun findById(id: String): QuestEntity?

    @Query("SELECT COUNT(*) FROM catalogs WHERE id = :id")
    suspend fun catalogCount(id: String): Int

    // Upsert, not INSERT OR REPLACE: SQLite implements REPLACE as delete + insert, and deleting
    // the quest row cascades through sections → themes → lessons → questions. A metadata-only
    // update (title, picture) would wipe the whole downloaded subtree, and the sync list only
    // re-fetches the quest itself, so the content would not come back.
    @Upsert
    suspend fun insertOrReplace(entity: QuestEntity)

    /**
     * Applies a snapshot the server reported as changed.
     *
     * `version` alone is not enough here. It is the conflict token (AD-12) and moves only when the
     * content of the quest changes — a rating does not move it, on purpose, or a stranger's rating
     * would invalidate the author's `expected_version`. But a rating still changes the document the
     * player looks at, and the server records that in `lastModifiedAt`. Gating on `version` alone
     * meant a quest already on the device never learned it had been rated again.
     */
    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: QuestEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.isOlderThan(entity)) {
            insertOrReplace(entity)
        }
    }

    private fun QuestEntity.isOlderThan(incoming: QuestEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)

    @Transaction
    suspend fun upsertFromSyncList(entity: QuestEntity) {
        if (catalogCount(entity.catalogId) == 0) {
            deleteById(entity.id)
            return
        }
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBySyncList(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun deleteById(id: String)

    private fun QuestEntity.shouldBeReplacedBySyncList(incoming: QuestEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
