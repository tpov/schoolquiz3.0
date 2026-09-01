package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEarning
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
import com.tpov.schoolquiz.shared.core.persistence.OUTBOX_ROW_IGNORED
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonAttemptDao : LessonAttemptDao {

    private val store = mutableListOf<LessonAttemptEntity>()
    private val _flow = MutableStateFlow<List<LessonAttemptEntity>>(emptyList())
    var upsertCallCount = 0
        private set

    /** Что лежит в таблице прохождений прямо сейчас — для сравнения «до и после». */
    val attempts: List<LessonAttemptEntity> get() = store.toList()

    override suspend fun upsert(entity: LessonAttemptEntity): Long {
        upsertCallCount++
        val idx = store.indexOfFirst { it.attemptId == entity.attemptId }
        if (idx >= 0) store[idx] = entity else store.add(entity)
        _flow.value = store.toList()
        return store.indexOf(entity).toLong() + 1
    }

    val answers = mutableListOf<QuestionAnswerEntity>()
    val repetitions = mutableListOf<QuestionRepetitionEntity>()

    override suspend fun upsertAnswers(entities: List<QuestionAnswerEntity>) {
        answers += entities
    }

    override suspend fun upsertRepetitions(entities: List<QuestionRepetitionEntity>) {
        repetitions += entities
    }

    val outboxRows = mutableListOf<OutboxEntity>()

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

    override suspend fun deleteAnswersOfAttempt(attemptId: String) {
        answers.removeAll { it.attemptId == attemptId }
    }

    override suspend fun deleteAttempt(attemptId: String) {
        store.removeAll { it.attemptId == attemptId }
        _flow.value = store.toList()
    }

    override fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>> =
        _flow.map { list -> list.filter { it.userId == userId && it.lessonId == lessonId } }

    override fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>> =
        _flow.map { list -> list.filter { it.userId == userId } }

    override fun observeEarningsSince(userId: String, sinceMs: Long): Flow<List<LessonAttemptEarning>> =
        _flow.map { list ->
            list.filter { it.userId == userId && it.completedAt >= sinceMs }
                .sortedBy { it.completedAt }
                .map { LessonAttemptEarning(it.completedAt, it.percentScore, it.isHard) }
        }
}
