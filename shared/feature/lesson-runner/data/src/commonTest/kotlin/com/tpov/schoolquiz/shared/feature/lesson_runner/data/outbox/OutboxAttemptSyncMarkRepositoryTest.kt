package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeEntityOutboxStore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ответ очереди про одно прохождение — тот самый, которым экран результата помечает карточку.
 *
 * Спрашивается по ссылке, которую составил писатель этой же фичи: ядро её не разбирает, а
 * сравнивает целиком (AD-7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxAttemptSyncMarkRepositoryTest {

    private val attemptId = AttemptId("attempt-42")
    private val entityRef = LessonResultEntityRef.attempt(attemptId.value)

    @Test
    fun `запись ждёт отправки - пометка «не отправлено»`() = runTest {
        val store = FakeEntityOutboxStore()
        store.seed(ownerUid = "A", entityRef = entityRef, state = OutboxState.WAITING)
        val repository = OutboxAttemptSyncMarkRepository(store) { MutableStateFlow<String?>("A") }

        assertEquals(AttemptSyncMark.NOT_SENT, repository.observeAttemptMark(attemptId).first())
    }

    @Test
    fun `запись в карантине - пометка отличает карантин от ожидания`() = runTest {
        val store = FakeEntityOutboxStore()
        store.seed(ownerUid = "A", entityRef = entityRef, state = OutboxState.QUARANTINED)
        val repository = OutboxAttemptSyncMarkRepository(store) { MutableStateFlow<String?>("A") }

        assertEquals(AttemptSyncMark.QUARANTINED, repository.observeAttemptMark(attemptId).first())
    }

    @Test
    fun `по ссылке нет записей - явное «ждать нечего», а не пустой поток`() = runTest {
        val repository =
            OutboxAttemptSyncMarkRepository(FakeEntityOutboxStore()) { MutableStateFlow<String?>("A") }

        assertEquals(AttemptSyncMark.NONE, repository.observeAttemptMark(attemptId).first())
    }

    @Test
    fun `запись чужого владельца не видна`() = runTest {
        val store = FakeEntityOutboxStore()
        store.seed(ownerUid = "A", entityRef = entityRef, state = OutboxState.WAITING)
        val repository = OutboxAttemptSyncMarkRepository(store) { MutableStateFlow<String?>("B") }

        assertEquals(AttemptSyncMark.NONE, repository.observeAttemptMark(attemptId).first())
    }

    @Test
    fun `гость получает «ждать нечего», а не пустой поток`() = runTest {
        val store = FakeEntityOutboxStore()
        store.seed(ownerUid = "A", entityRef = entityRef, state = OutboxState.QUARANTINED)
        val repository = OutboxAttemptSyncMarkRepository(store) { MutableStateFlow<String?>(null) }

        // Именно эмиссия: пустой поток здесь запрещён (инвариант 8), и `first()` на нём завис бы.
        assertEquals(AttemptSyncMark.NONE, repository.observeAttemptMark(attemptId).first())
    }

    @Test
    fun `смена аккаунта пересобирает тот же поток без пересоздания подписки`() = runTest {
        val store = FakeEntityOutboxStore()
        store.seed(ownerUid = "B", entityRef = entityRef, state = OutboxState.WAITING)
        val uid = MutableStateFlow<String?>("A")
        val repository = OutboxAttemptSyncMarkRepository(store) { uid }

        val marks = mutableListOf<AttemptSyncMark>()
        val collecting =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeAttemptMark(attemptId).toList(marks)
            }
        uid.value = "B"
        collecting.cancel()

        assertEquals(listOf(AttemptSyncMark.NONE, AttemptSyncMark.NOT_SENT), marks)
    }

    @Test
    fun `движок увёл запись в карантин - тот же поток отдаёт новую пометку`() = runTest {
        val store = FakeEntityOutboxStore()
        val record = store.seed(ownerUid = "A", entityRef = entityRef, state = OutboxState.WAITING)
        val repository = OutboxAttemptSyncMarkRepository(store) { MutableStateFlow<String?>("A") }

        val marks = mutableListOf<AttemptSyncMark>()
        val collecting =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeAttemptMark(attemptId).toList(marks)
            }
        store.moveTo(record.id, OutboxState.QUARANTINED)
        collecting.cancel()

        assertEquals(listOf(AttemptSyncMark.NOT_SENT, AttemptSyncMark.QUARANTINED), marks)
    }
}
