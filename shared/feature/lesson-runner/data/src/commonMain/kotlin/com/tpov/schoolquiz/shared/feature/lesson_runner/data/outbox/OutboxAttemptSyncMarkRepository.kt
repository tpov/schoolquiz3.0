package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.AttemptSyncMarkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Первый потребитель ответа очереди по `entity_ref`.
 *
 * Ссылку строит тот же [LessonResultEntityRef], которым писатель фичи помечает строку очереди:
 * спрашивается ровно то, что было записано, и ядро эту строку целиком сравнивает, а не разбирает
 * (AD-7).
 *
 * Поток пересобирается на смене аккаунта через `flatMapLatest` от потока `uid`, а не по разово
 * снятому снимку: иначе после выхода экран продолжал бы показывать пометку прежнего владельца.
 * Гость получает [AttemptSyncMark.NONE] — пустой поток здесь запрещён (инвариант 8).
 */
class OutboxAttemptSyncMarkRepository(
    private val outbox: OutboxStore,
    private val currentUidFlow: () -> Flow<String?>,
) : AttemptSyncMarkRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAttemptMark(attemptId: AttemptId): Flow<AttemptSyncMark> {
        val entityRef = LessonResultEntityRef.attempt(attemptId.value)
        return currentUidFlow()
            .flatMapLatest { uid ->
                if (uid == null) {
                    flowOf(AttemptSyncMark.NONE)
                } else {
                    outbox.observeEntity(uid, entityRef).map { it.toMark() }
                }
            }
            .distinctUntilChanged()
    }
}

/**
 * Состояние записи — словами экрана.
 *
 * [OutboxEntitySync.None] это [AttemptSyncMark.NONE]: очередь ответила, и ответ значит «ничего не
 * ждёт», а не «неизвестно».
 *
 * [OutboxState.SYNCHRONOUS] тоже [AttemptSyncMark.NONE]: такая строка хранит только ключ уже
 * ушедшего действия, чтобы после смерти процесса не предложить его второй раз (AD-2), — ждать
 * игроку нечего.
 */
private fun OutboxEntitySync.toMark(): AttemptSyncMark =
    when (this) {
        OutboxEntitySync.None -> AttemptSyncMark.NONE
        is OutboxEntitySync.Queued ->
            when (state) {
                OutboxState.QUARANTINED -> AttemptSyncMark.QUARANTINED
                OutboxState.WAITING,
                OutboxState.WAITING_PRECONDITION,
                // Прохождение не версионируется, и конфликта по нему не бывает; но если он всё же
                // придёт, на сервере результата нет — это по-прежнему «не отправлено».
                OutboxState.CONFLICT,
                -> AttemptSyncMark.NOT_SENT
                OutboxState.SYNCHRONOUS -> AttemptSyncMark.NONE
            }
    }
