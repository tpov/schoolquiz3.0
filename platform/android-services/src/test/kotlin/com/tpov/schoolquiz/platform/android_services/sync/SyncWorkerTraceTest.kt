package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.sync.SyncRun
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Исход прогона доходит до состояния синхронизации — из обоих воркеров.
 *
 * До этого единственным следом прогона был `Log.w`: отказ каталога, приватных квестов, заданий
 * ревью или профиля («нет сети») на экран не попадал вообще, и молчание синхронизации было
 * неотличимо от её успеха (AD-14).
 */
class SyncWorkerTraceTest {

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)

    private val now = 1_700_000_000_000L
    private val earlier = 1_600_000_000_000L

    /** След в памяти теста, но с той же формой снимка, что видит экран. */
    private class RecordingStatus(initialSuccessAtMs: Long = 0L) : SyncStatusRepository {
        private val state = MutableStateFlow(SyncStatus(lastSuccessAtMs = initialSuccessAtMs))

        val snapshot: SyncStatus get() = state.value

        override fun observeStatus(): Flow<SyncStatus> = state

        override suspend fun recordSuccess(kind: SyncRunKind, atMs: Long) {
            // Каждое расписание пишет в своё поле: общая строка и была тем, из-за чего удачный
            // профильный прогон снимал ветвь упавшего контентного.
            state.value =
                when (kind) {
                    SyncRunKind.CONTENT -> state.value.copy(lastSuccessAtMs = atMs, lastError = null)
                    SyncRunKind.PROFILE ->
                        state.value.copy(profileLastSuccessAtMs = atMs, profileLastError = null)
                    SyncRunKind.RESYNC ->
                        state.value.copy(resyncLastSuccessAtMs = atMs, resyncLastError = null)
                }
        }

        override suspend fun recordFailure(kind: SyncRunKind, error: SyncError, atMs: Long) {
            state.value =
                when (kind) {
                    SyncRunKind.CONTENT -> state.value.copy(lastError = error)
                    SyncRunKind.PROFILE -> state.value.copy(profileLastError = error)
                    SyncRunKind.RESYNC -> state.value.copy(resyncLastError = error)
                }
        }

        override suspend fun recordUnreadableChanges(journal: String, count: Int) {
            state.value = state.value.copy(unreadableChanges = count, counts = OutboxCounts())
        }
    }

    private class Step(private val result: Result<Unit>) : Syncable {
        override suspend fun sync(): Result<Unit> = result
    }

    private fun ok() = Step(Result.success(Unit))

    private fun failing(error: SyncError) = Step(Result.failure(SyncFailure(error)))

    @Test
    fun `given the content run went through then the player sees the success time`() = runTest {
        val status = RecordingStatus()
        val worker = SyncWorker(context, workerParams, listOf(ok(), ok()), SyncRun(status) { now })

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
        assertEquals(now, status.snapshot.lastSuccessAtMs)
        assertNull(status.snapshot.lastError)
    }

    @Test
    fun `given a step of the content run failed then the reason reaches the snapshot`() = runTest {
        // Ровно тот случай, ради которого эпик существует: «нет сети» при чтении каталога.
        val status = RecordingStatus(initialSuccessAtMs = earlier)
        val worker =
            SyncWorker(context, workerParams, listOf(failing(SyncError.NoNetwork), ok()), SyncRun(status) { now })

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry().javaClass, result.javaClass)
        assertEquals(SyncError.NoNetwork, status.snapshot.lastError)
        assertEquals("прогон удался не целиком — успеха нет", earlier, status.snapshot.lastSuccessAtMs)
    }

    @Test
    fun `given the profile run failed then it leaves a trace of its own`() = runTest {
        // Профильный прогон реже, но его отказ — такая же причина молчания синхронизации.
        val status = RecordingStatus(initialSuccessAtMs = earlier)
        val worker =
            ProfileSyncWorker(
                context,
                workerParams,
                listOf(failing(SyncError.Refused("нет прав"))),
                SyncRun(status) { now },
            )

        worker.doWork()

        assertEquals(SyncError.Refused("нет прав"), status.snapshot.profileLastError)
        assertEquals("содержимое тут ни при чём — его ветвь чужая", null, status.snapshot.lastError)
        assertEquals(earlier, status.snapshot.lastSuccessAtMs)
    }

    @Test
    fun `given the profile run went through then its success is its own number`() = runTest {
        val status = RecordingStatus()
        val worker = ProfileSyncWorker(context, workerParams, listOf(ok()), SyncRun(status) { now })

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
        assertEquals(now, status.snapshot.profileLastSuccessAtMs)
        assertEquals("содержимое не приезжало — его времени нет", 0L, status.snapshot.lastSuccessAtMs)
    }

    @Test
    fun `given the content run failed then a profile run that went through does not clear it`() = runTest {
        // Два расписания эпика 9 писали в одну строку следа: профильный прогон, случившийся после
        // упавшего контентного, снимал его ветвь и ставил поверх неё своё время успеха. Игрок
        // видел «синхронизировались только что» над содержимым, которое не приезжало сутки.
        val status = RecordingStatus(initialSuccessAtMs = earlier)
        val content =
            SyncWorker(context, workerParams, listOf(failing(SyncError.NoNetwork)), SyncRun(status) { now })
        val profile = ProfileSyncWorker(context, workerParams, listOf(ok()), SyncRun(status) { now })

        content.doWork()
        profile.doWork()

        assertEquals(SyncError.NoNetwork, status.snapshot.lastError)
        assertEquals("содержимое не приезжало — свежего времени у него нет", earlier, status.snapshot.lastSuccessAtMs)
        assertEquals(now, status.snapshot.profileLastSuccessAtMs)
    }
}
