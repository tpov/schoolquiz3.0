package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
import com.tpov.schoolquiz.shared.core.persistence.OUTBOX_ROW_IGNORED
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonRatingLocalDao : LessonRatingLocalDao {

    private val submitted = mutableSetOf<Pair<String, String>>()
    private val _trigger = MutableStateFlow(0)

    /** Что лежит в таблице оценок прямо сейчас — для сравнения «до и после». */
    val rows: Set<Pair<String, String>> get() = submitted.toSet()

    val outboxRows = mutableListOf<OutboxEntity>()

    override suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long {
        submitted.add(entity.userId to entity.lessonId)
        _trigger.value++
        return _trigger.value.toLong()
    }

    override suspend fun enqueueOutboxRow(entity: OutboxEntity): Long {
        // Как и в Room: тот же ключ второй записи не создаёт (AD-2).
        if (outboxRows.any { it.mutationId == entity.mutationId }) return OUTBOX_ROW_IGNORED
        outboxRows += entity
        return outboxRows.size.toLong()
    }

    override suspend fun outboxRowState(mutationId: String): String? =
        outboxRows.firstOrNull { it.mutationId == mutationId }?.state

    /** Ставит запись в карантин, как это делает движок: помечает и оставляет лежать. */
    fun quarantine(mutationId: String) {
        val idx = outboxRows.indexOfFirst { it.mutationId == mutationId }
        if (idx >= 0) outboxRows[idx] = outboxRows[idx].copy(state = OutboxState.QUARANTINED.name)
    }

    override suspend fun delete(userId: String, lessonId: String) {
        submitted.remove(userId to lessonId)
        _trigger.value++
    }

    override fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean> =
        _trigger.map { submitted.contains(userId to lessonId) }
}
