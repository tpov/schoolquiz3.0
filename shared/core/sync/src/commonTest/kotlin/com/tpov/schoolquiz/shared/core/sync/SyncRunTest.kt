package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.sync.fake.EmptyOutboxStore
import com.tpov.schoolquiz.shared.core.sync.fake.FakeSyncRunTraceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Исход прогона доходит до экрана — и успех записывается только за прогон, прошедший целиком.
 *
 * Проверяется не запись в порт, а то, что видит подписчик снимка: между прогоном и экраном стоит
 * [DefaultSyncStatusRepository], и «записали, но не показали» — та же немота, ради устранения
 * которой состояние выводится наружу (AD-14).
 */
class SyncRunTest {
    private val now = 1_700_000_000_000L
    private val earlier = 1_600_000_000_000L
    private val uid = "uid-1"

    private val trace = FakeSyncRunTraceStore()

    private val status =
        DefaultSyncStatusRepository(
            store = EmptyOutboxStore(),
            trace = trace,
            currentUidFlow = flowOf(uid),
            currentUidProvider = { uid },
        )

    private val run = SyncRun(status = status, clock = { now })

    private class Step(private val result: Result<Unit>) : Syncable {
        var calls: Int = 0

        override suspend fun sync(): Result<Unit> {
            calls++
            return result
        }
    }

    private fun ok() = Step(Result.success(Unit))

    private fun failing(error: SyncError) = Step(Result.failure(SyncFailure(error)))

    // ── Прогон прошёл целиком ─────────────────────────────────────────────────

    @Test
    fun `given every step went through then the run leaves the success time`() =
        runTest {
            val steps = listOf(ok(), ok())

            val passed = run.run(SyncRunKind.CONTENT, steps).isSuccess

            assertTrue(passed)
            val snapshot = status.observeStatus().first()
            assertEquals(now, snapshot.lastSuccessAtMs, "время берётся из инжектированного источника")
            assertNull(snapshot.lastError, "успех снимает прошлую ошибку — она уже починилась")
        }

    // ── Прогон упал ───────────────────────────────────────────────────────────

    @Test
    fun `given one step failed then the branch reaches the snapshot and the rest still ran`() =
        runTest {
            // До этого исход прогона не выходил наружу вообще: «нет сети» при чтении каталога,
            // приватных квестов или профиля оставалось в `Log.w`, и на экране был вечный успех.
            trace.set(uid, SyncRunKind.CONTENT, SyncRunTrace(lastSuccessAtMs = earlier))
            val broken = failing(SyncError.NoNetwork)
            val after = ok()

            val passed = run.run(SyncRunKind.CONTENT, listOf(broken, after)).isSuccess

            assertFalse(passed)
            val snapshot = status.observeStatus().first()
            assertEquals(SyncError.NoNetwork, snapshot.lastError, "ветвь, а не текст исключения (AD-15)")
            assertEquals(earlier, snapshot.lastSuccessAtMs, "время последнего успеха осталось прежним")
            assertEquals(1, after.calls, "неудача одного шага не отменяет остальные")
        }

    @Test
    fun `given a step that succeeded after the failed one then it does not erase the branch`() =
        runTest {
            // Тот самый ложный успех: пока успех объявлял сам шаг, удавшаяся очередь стирала ветвь
            // упавшего каталога — и игрок видел свежее время при устаревшем содержимом.
            trace.set(uid, SyncRunKind.CONTENT, SyncRunTrace(lastSuccessAtMs = earlier))

            run.run(SyncRunKind.CONTENT, listOf(failing(SyncError.Refused("отказ")), ok(), ok()))

            val snapshot = status.observeStatus().first()
            assertEquals(SyncError.Refused("отказ"), snapshot.lastError)
            assertEquals(earlier, snapshot.lastSuccessAtMs, "прогон удался не целиком — успеха нет")
        }

    @Test
    fun `given the first failure is the network then the later branch does not replace it`() =
        runTest {
            // Причина — та, из-за которой всё посыпалось. Последняя неудача в такой цепочке названа
            // шагом, который к причине отношения не имеет.
            run.run(SyncRunKind.CONTENT, listOf(failing(SyncError.NoNetwork), failing(SyncError.Refused("отказ"))))

            assertEquals(SyncError.NoNetwork, status.observeStatus().first().lastError)
        }

    @Test
    fun `given a nameless failure before a named one then the snapshot names the cause`() =
        runTest {
            // Порядок шагов задаёт композиционный корень, и перед каталогом стоят шаги, которые ещё
            // не пропущены через разбор ошибок. Пока писалась просто первая неудача, у гостя — у кого
            // шаги аккаунта ничего не делают — «нет сети» от каталога пряталось за «неизвестно».
            run.run(
                SyncRunKind.CONTENT,
                listOf(
                    Step(Result.failure(IllegalStateException("порт без разбора ошибок"))),
                    failing(SyncError.NoNetwork),
                ),
            )

            assertEquals(SyncError.NoNetwork, status.observeStatus().first().lastError)
        }

    @Test
    fun `given a step failed without a branch then it is unknown, not the message`() =
        runTest {
            run.run(SyncRunKind.CONTENT, listOf(Step(Result.failure(IllegalStateException("database is gone")))))

            val error = status.observeStatus().first().lastError
            assertTrue(error is SyncError.Unknown, "ветвь по умолчанию — «неизвестно», а не разбор текста")
        }

    // ── Очередь внутри прогона ────────────────────────────────────────────────

    @Test
    fun `given the queue delivered nothing but the rest passed then the run is not a success`() =
        runTest {
            // Проверка через настоящий [OutboxSyncable]: движок намеренно не выпускает наверх неудачу
            // отправки (AD-22), поэтому «шаг вернулся» здесь особенно легко принять за успех прогона.
            trace.set(uid, SyncRunKind.CONTENT, SyncRunTrace(lastSuccessAtMs = earlier))
            val queue =
                OutboxSyncable(
                    engine =
                        OutboxEngine(
                            store = StoreWithOneRecord(uid, now),
                            receipts = FakeMutationReceiptStore(),
                            sessions = { OutboxSession(uid, 0) },
                            transport = MutationTransport { Result.failure(SyncFailure(SyncError.NoNetwork)) },
                            clock = { now },
                            onQuarantined = NoLocalEffect(),
                        ),
                    currentUidProvider = { uid },
                )

            val passed = run.run(SyncRunKind.CONTENT, listOf(queue, ok())).isSuccess

            assertFalse(passed)
            val snapshot = status.observeStatus().first()
            assertEquals(SyncError.NoNetwork, snapshot.lastError)
            assertEquals(earlier, snapshot.lastSuccessAtMs, "очередь не довезла — прогон не удался")
        }

    // ── Два расписания — два следа ────────────────────────────────────────────

    @Test
    fun `given the content run failed then a profile run that went through leaves its branch alone`() =
        runTest {
            // Эпик 9 развёл контентную и профильную каденции намеренно, а след у них оставался один:
            // профильный прогон дописывал свой успех в ту же строку и снимал ветвь контентного. На
            // экране это выглядело как «синхронизировались минуту назад» над содержимым, которое не
            // приезжало сутки, — ровно то молчание, ради устранения которого состояние выводится
            // наружу (AD-14).
            trace.set(uid, SyncRunKind.CONTENT, SyncRunTrace(lastSuccessAtMs = earlier))

            run.run(SyncRunKind.CONTENT, listOf(failing(SyncError.NoNetwork)))
            run.run(SyncRunKind.PROFILE, listOf(ok()))

            val snapshot = status.observeStatus().first()
            assertEquals(SyncError.NoNetwork, snapshot.lastError, "контентная ветвь не чинится чужим успехом")
            assertEquals(earlier, snapshot.lastSuccessAtMs, "содержимое не приезжало — время прежнее")
            assertEquals(now, snapshot.profileLastSuccessAtMs, "а профиль приехал, и это его собственное число")
        }

    @Test
    fun `given the profile run failed then the content run that went through keeps its own success`() =
        runTest {
            run.run(SyncRunKind.PROFILE, listOf(failing(SyncError.Refused("нет прав"))))
            run.run(SyncRunKind.CONTENT, listOf(ok()))

            val snapshot = status.observeStatus().first()
            assertEquals(now, snapshot.lastSuccessAtMs)
            assertNull(snapshot.lastError, "своя ветвь снята своим же успехом")
            assertEquals(SyncError.Refused("нет прав"), snapshot.profileLastError, "чужая — осталась")
            assertEquals(0L, snapshot.profileLastSuccessAtMs)
        }

    // ── Фейк очереди с одной записью: остальное берётся из общего пустого хранилища ──

    private class StoreWithOneRecord(private val ownerUid: String, private val createdAtMs: Long) :
        EmptyOutboxStore() {
        override suspend fun dueRecords(
            ownerUid: String,
            nowMs: Long,
            limit: Int,
        ): List<OutboxRecord> =
            if (ownerUid != this.ownerUid) {
                emptyList()
            } else {
                listOf(
                    OutboxRecord(
                        id = 1L,
                        mutationId = "m-1",
                        ownerUid = ownerUid,
                        operation = "OP",
                        payload = "{}",
                        state = OutboxState.WAITING,
                        createdAtMs = createdAtMs,
                    ),
                )
            }
    }
}
