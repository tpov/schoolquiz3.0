package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRun
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// NOTE: Requires backend-dev to add to platform/android-services/build.gradle.kts:
//   testImplementation(libs.junit4)
//   testImplementation(libs.kotlinx.coroutines.test)
//   testImplementation(libs.mockk)
// libs.mockk must also be added to gradle/libs.versions.toml (see platform/firebase precedent).
// mockk is needed to mock Context + WorkerParameters without Android runtime.

private class FakeSyncable(private val result: Result<Unit> = Result.success(Unit)) : Syncable {
    var syncCalls: Int = 0
    override suspend fun sync(): Result<Unit> {
        syncCalls++
        return result
    }
}

/** След прогона здесь не проверяется — он свой у [SyncWorkerTraceTest]; тут только порядок шагов. */
private object SilentStatus : SyncStatusRepository {
    override fun observeStatus(): Flow<SyncStatus> = flowOf(SyncStatus())

    override suspend fun recordSuccess(kind: SyncRunKind, atMs: Long) = Unit

    override suspend fun recordFailure(kind: SyncRunKind, error: SyncError, atMs: Long) = Unit

    override suspend fun recordUnreadableChanges(journal: String, count: Int) = Unit
}

class SyncWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val run = SyncRun(SilentStatus) { 0L }

    // Spec scenario 1:
    // GIVEN List<Syncable> with 2 successful fakes
    // WHEN doWork()
    // THEN Result.success() + each syncCalls == 1
    @Test
    fun `when all syncables succeed then doWork returns success and each called once`() = runTest {
        val fake1 = FakeSyncable(Result.success(Unit))
        val fake2 = FakeSyncable(Result.success(Unit))
        val worker = SyncWorker(context, workerParams, listOf(fake1, fake2), run)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
        assertEquals(1, fake1.syncCalls)
        assertEquals(1, fake2.syncCalls)
    }

    /**
     * A failing step no longer cancels the ones behind it.
     *
     * This replaces the original fail-fast rule. The steps do not depend on each other, and the
     * catalog sync sits last — so under fail-fast a refused profile call left the app with no
     * quests, which the owner read as "sync is broken" rather than "the profile did not refresh".
     */
    @Test
    fun `when first syncable fails then doWork returns retry and the rest still run`() = runTest {
        val fake1 = FakeSyncable(Result.failure(java.io.IOException("network error")))
        val fake2 = FakeSyncable(Result.success(Unit))
        val worker = SyncWorker(context, workerParams, listOf(fake1, fake2), run)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry().javaClass, result.javaClass)
        assertEquals(1, fake1.syncCalls)
        assertEquals(1, fake2.syncCalls)
    }

    /** Retry is still asked for when a step fails somewhere in the middle. */
    @Test
    fun `when a later syncable fails then doWork still returns retry`() = runTest {
        val fake1 = FakeSyncable(Result.success(Unit))
        val fake2 = FakeSyncable(Result.failure(java.io.IOException("network error")))
        val fake3 = FakeSyncable(Result.success(Unit))
        val worker = SyncWorker(context, workerParams, listOf(fake1, fake2, fake3), run)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry().javaClass, result.javaClass)
        assertEquals(1, fake3.syncCalls)
    }

    // Spec scenario 3:
    // GIVEN empty List<Syncable>
    // WHEN doWork()
    // THEN Result.success() (no syncables = nothing to fail)
    @Test
    fun `when syncables list is empty then doWork returns success`() = runTest {
        val worker = SyncWorker(context, workerParams, emptyList(), run)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
    }
}
