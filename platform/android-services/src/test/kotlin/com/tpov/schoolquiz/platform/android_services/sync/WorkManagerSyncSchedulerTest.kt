package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerSyncSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    // ── Request shape: what exactly gets handed to WorkManager ────────────────────────────────

    @Test
    fun `daily content frequency enqueues unique periodic work with a one day period`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val request = slot<PeriodicWorkRequest>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), capture(request)) } returns mockk(relaxed = true)

        WorkManagerSyncScheduler(workManager).applyFrequency(SyncFrequency.DAILY)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                any(),
            )
        }
        assertEquals(SyncFrequency.DAILY.intervalMs, request.captured.workSpec.intervalDuration)
        assertEquals(SyncWorker::class.java.name, request.captured.workSpec.workerClassName)
    }

    @Test
    fun `weekly profile frequency enqueues unique periodic work with a seven day period`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val request = slot<PeriodicWorkRequest>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), capture(request)) } returns mockk(relaxed = true)

        WorkManagerSyncScheduler(workManager).applyProfileFrequency(SyncFrequency.WEEKLY)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                ProfileSyncWorker.WORK_NAME_PROFILE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                any(),
            )
        }
        assertEquals(SyncFrequency.WEEKLY.intervalMs, request.captured.workSpec.intervalDuration)
        assertEquals(ProfileSyncWorker::class.java.name, request.captured.workSpec.workerClassName)
    }

    @Test
    fun `manual content frequency cancels the content schedule`() {
        val workManager = mockk<WorkManager>(relaxed = true)

        WorkManagerSyncScheduler(workManager).applyFrequency(SyncFrequency.MANUAL)

        verify(exactly = 1) { workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `manual profile frequency cancels the profile schedule`() {
        val workManager = mockk<WorkManager>(relaxed = true)

        WorkManagerSyncScheduler(workManager).applyProfileFrequency(SyncFrequency.MANUAL)

        verify(exactly = 1) { workManager.cancelUniqueWork(ProfileSyncWorker.WORK_NAME_PROFILE_PERIODIC) }
    }

    // ── End to end through a real (test-initialized) WorkManager ─────────────────────────────

    @Test
    fun `applied schedules exist as unique enqueued work`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val scheduler = WorkManagerSyncScheduler(workManager)

        scheduler.applyFrequency(SyncFrequency.DAILY)
        scheduler.applyProfileFrequency(SyncFrequency.WEEKLY)

        val content = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME_PERIODIC).get()
        assertEquals(1, content.size)
        assertEquals(WorkInfo.State.ENQUEUED, content.single().state)

        val profile = workManager.getWorkInfosForUniqueWork(ProfileSyncWorker.WORK_NAME_PROFILE_PERIODIC).get()
        assertEquals(1, profile.size)
        assertEquals(WorkInfo.State.ENQUEUED, profile.single().state)
    }

    @Test
    fun `manual frequency cancels the previously scheduled content work`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val scheduler = WorkManagerSyncScheduler(workManager)

        scheduler.applyFrequency(SyncFrequency.DAILY)
        scheduler.applyFrequency(SyncFrequency.MANUAL)

        val content = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME_PERIODIC).get()
        assertEquals(1, content.size)
        assertEquals(WorkInfo.State.CANCELLED, content.single().state)
    }
}
