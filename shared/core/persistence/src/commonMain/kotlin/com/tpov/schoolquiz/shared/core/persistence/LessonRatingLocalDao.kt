package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonRatingLocalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long

    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
    fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>

    /**
     * Ставит оценку в общую очередь (AD-5).
     *
     * `IGNORE`, а не `REPLACE`: ключ идемпотентности один на одно действие (AD-2), а `REPLACE`
     * снёс бы строку вместе с накопленными попытками и выдал ей новый `id`.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueOutboxRow(entity: OutboxEntity): Long

    /** Состояние записи под этим ключом — чтобы отличить «уже отложено» от «уже не уедет». */
    @Query("SELECT state FROM outbox WHERE mutation_id = :mutationId")
    suspend fun outboxRowState(mutationId: String): String?

    /**
     * Пишет оценку и её строку очереди одной транзакцией (AD-23).
     *
     * Порознь возможны обе беды и обе молчаливые: оценка, показанная поставленной и никогда не
     * доехавшая, и строка очереди без локального следа, которую нечем откатить при карантине.
     *
     * Подавленная вставка тоже не принимается на веру: −1 от `IGNORE` значит либо «то же
     * намерение уже отложено», либо «ключ занят записью, которая уже не уедет». Разбирает это
     * [requireOutboxIntentQueued]; во втором случае транзакция падает целиком, и оценка не
     * остаётся показанной поставленной.
     */
    @Transaction
    suspend fun submitWithOutbox(
        rating: LessonRatingSubmittedLocalEntity,
        outboxRow: OutboxEntity?,
    ): Long {
        val rowId = upsert(rating)
        outboxRow?.let {
            val insertResult = enqueueOutboxRow(it)
            if (insertResult == OUTBOX_ROW_IGNORED) {
                requireOutboxIntentQueued(insertResult, it.mutationId, outboxRowState(it.mutationId))
            }
        }
        return rowId
    }

    /** Откат по карантину: оценки, которой сервер не принял, локально быть не должно (AD-28). */
    @Query("DELETE FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
    suspend fun delete(userId: String, lessonId: String)
}
