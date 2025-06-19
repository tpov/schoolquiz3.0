package com.tpov.schoolquiz.presentation

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.tpov.common.ExceptionInteractor
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.LockServerResult
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.usecase.SyncInteractor
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.services.ProfileInteractor
import com.tpov.setting.data.PreferencesManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [Config.OLDEST_SDK]) // Configure Robolectric as needed
class SyncWorkerTest {

    @Mock
    lateinit var mockWorkerParams: WorkerParameters
    @Mock
    lateinit var mockSyncInteractor: SyncInteractor
    @Mock
    lateinit var mockProfileUseCase: ProfileUseCase
    @Mock
    lateinit var mockExceptionInteractor: ExceptionInteractor
    @Mock
    lateinit var mockProfileInteractor: ProfileInteractor
    @Mock
    lateinit var mockViewModelFactory: ViewModelProvider.Factory // Though not directly used in doWork
    @Mock
    lateinit var mockPreferencesManager: PreferencesManager // For syncSettings

    private lateinit var context: Context
    private lateinit var syncWorker: SyncWorker

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()

        // Mock PreferencesManager creation within syncSettings if it's instantiated there
        // For now, assuming it could be injected or easily mocked if SyncWorker was refactored.
        // If syncSettings() directly news PreferencesManager(context), this test will use a real one
        // unless we use a PowerMockito-like approach or refactor SyncWorker.
        // Given PreferencesManager is in com.tpov.setting.data, it's a separate module.
        // For this test, we'll assume syncSettings can be tested by outcome or direct prefs access.
        // A better way would be to inject PreferencesManager into SyncWorker.
        // For simplicity here, we'll focus on mocking the main interactors.

        syncWorker = SyncWorker(
            context,
            mockWorkerParams,
            mockSyncInteractor,
            mockProfileUseCase,
            mockExceptionInteractor,
            mockProfileInteractor,
            mockViewModelFactory
        )

        // Mock for syncSettings() part if it directly instantiates PreferencesManager:
        // This is a bit of a hack. Ideally, PreferencesManager is injected.
        // If syncSettings is complex, it should be its own class and unit tested.
        // For now, we assume syncSettings will run, and we can mock get/save on mockPreferencesManager if it were injectable.
        // Since it's not, syncSettings() will run with a real PreferencesManager.
        // We can mock getProfileFlow to control data used by syncSettings.
        whenever(mockProfileUseCase.getProfileFlow()) doReturn flowOf(null) // Default for syncSettings
    }

    @Test
    fun `doWork - successful full sync, returns success`() = runBlocking {
        // Arrange
        // Profile sync success
        whenever(mockProfileUseCase.syncProfile()) doReturn Unit
        // Settings sync will run with real PrefsManager due to no injection point in SyncWorker for it.
        // We assume it completes without error for this happy path.
        // Quiz data sync success for all events
        EventQuiz.entries.forEach { event ->
            whenever(mockSyncInteractor.lockStructureData(event)) doReturn LockServerResult.Success
            whenever(mockSyncInteractor.syncQuizes(eq(event), any())) doReturn SyncStructureResult.Success(com.tpov.common.domain.model.SyncStage.COMPLETE, com.tpov.common.domain.model.SyncState(event))
            whenever(mockSyncInteractor.unlockStructureData(event)) doReturn LockServerResult.Success
        }

        // Act
        val result = syncWorker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(result.outputData.getBoolean(SyncWorker.KEY_SYNC_SUCCESS, false))
        verify(mockProfileUseCase).syncProfile()
        EventQuiz.entries.forEach { event ->
            verify(mockSyncInteractor).lockStructureData(event)
            verify(mockSyncInteractor).syncQuizes(eq(event), any())
            verify(mockSyncInteractor).unlockStructureData(event)
        }
        // Cannot easily verify showNotification directly without refactoring or more complex Robolectric setup for notifications.
    }

    @Test
    fun `doWork - profileUseCase syncProfile throws exception, returns failure`() = runBlocking {
        // Arrange
        val exception = RuntimeException("Profile sync failed")
        whenever(mockProfileUseCase.syncProfile()) doThrow exception

        // Act
        val result = syncWorker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Failure)
        verify(mockProfileUseCase).syncProfile()
        verifyNoInteractions(mockSyncInteractor) // Should fail before quiz sync
    }

    @Test
    fun `doWork - syncInteractor syncQuizes returns Error, returns failure`() = runBlocking {
        // Arrange
        whenever(mockProfileUseCase.syncProfile()) doReturn Unit // Profile sync is fine
        val errorEvent = EventQuiz.entries.first()
        whenever(mockSyncInteractor.lockStructureData(errorEvent)) doReturn LockServerResult.Success
        whenever(mockSyncInteractor.syncQuizes(eq(errorEvent), any())) doReturn SyncStructureResult.Error(com.tpov.common.domain.model.SyncStage.STRUCTURE_FETCH, "Quiz sync error")

        // Mock other events to succeed to ensure it stops on first error
        EventQuiz.entries.filter { it != errorEvent }.forEach { event ->
             whenever(mockSyncInteractor.lockStructureData(event)) doReturn LockServerResult.Success
             whenever(mockSyncInteractor.syncQuizes(eq(event), any())) doReturn SyncStructureResult.Success(com.tpov.common.domain.model.SyncStage.COMPLETE, com.tpov.common.domain.model.SyncState(event))
             whenever(mockSyncInteractor.unlockStructureData(event)) doReturn LockServerResult.Success
        }

        // Act
        val result = syncWorker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Failure)
        verify(mockSyncInteractor).lockStructureData(errorEvent)
        verify(mockSyncInteractor).syncQuizes(eq(errorEvent), any())
        // Depending on internal try-catch in syncQuizData, unlock/rollback might be called.
        // The current SyncWorker.syncQuizData returns immediately on SyncStructureResult.Error from syncQuizes.
        verify(mockSyncInteractor, times(0)).unlockStructureData(errorEvent)
        verify(mockSyncInteractor).rollbackStructureData(errorEvent) // This is called after error
    }

    @Test
    fun `doWork - lockStructureData returns Error, returns failure`() = runBlocking {
        // Arrange
        whenever(mockProfileUseCase.syncProfile()) doReturn Unit
        val errorEvent = EventQuiz.entries.first()
        whenever(mockSyncInteractor.lockStructureData(errorEvent)) doReturn LockServerResult.Error("Lock failed")

        // Act
        val result = syncWorker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Failure)
        verify(mockSyncInteractor).lockStructureData(errorEvent)
        verify(mockSyncInteractor, times(0)).syncQuizes(any(), any()) // Should not proceed to sync
    }

    @Test
    fun `doWork - unlockStructureData returns Error, calls rollback and returns failure`() = runBlocking {
        // Arrange
        whenever(mockProfileUseCase.syncProfile()) doReturn Unit
        val errorEvent = EventQuiz.entries.first()
        whenever(mockSyncInteractor.lockStructureData(errorEvent)) doReturn LockServerResult.Success
        whenever(mockSyncInteractor.syncQuizes(eq(errorEvent), any())) doReturn SyncStructureResult.Success(com.tpov.common.domain.model.SyncStage.COMPLETE, com.tpov.common.domain.model.SyncState(errorEvent))
        whenever(mockSyncInteractor.unlockStructureData(errorEvent)) doReturn LockServerResult.Error("Unlock failed")

        // Act
        val result = syncWorker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Failure)
        verify(mockSyncInteractor).unlockStructureData(errorEvent)
        verify(mockSyncInteractor).rollbackStructureData(errorEvent)
    }
}
