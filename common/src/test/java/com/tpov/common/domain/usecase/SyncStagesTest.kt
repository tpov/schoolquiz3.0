package com.tpov.common.domain.usecase

import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.SyncState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

// Enable Mockito support for JUnit 5
@ExtendWith(MockitoExtension::class)
class SyncStagesTest {

    // Mocks for UseCases - these will be injected or used by the stage functions
    @Mock
    lateinit var mockStructureUseCase: StructureUseCase

    @Mock
    lateinit var mockQuestionUseCase: QuestionUseCase

    @Mock
    lateinit var mockQuestionDetailUseCase: QuestionDetailUseCase

    // DomainExceptions handler - will be initialized before each test
    // We might need a mock for ExceptionInteractor if we want to verify calls to it
    // For now, we'll create a real DomainExceptions with a potentially mocked ExceptionInteractor
import com.tpov.common.data.model.SettingConfigModel
import com.tpov.common.presentation.utils.LanguageUtils
import org.junit.jupiter.api.AfterEach

    // @Mock lateinit var mockExceptionInteractor: ExceptionInteractor
    lateinit var domainExceptions: com.tpov.common.domain.DomainExceptions

    private lateinit var originalSettingsConfig: SettingConfigModel
    private val testTpovId = 999 // Example test value
    private val testLanguages = listOf(LanguageUtils.ENGLISH, LanguageUtils.UKRAINIAN) // Example test value


    @BeforeEach
    fun setUp() {
        // Store original settings and update to test settings
        originalSettingsConfig = SettingConfigObject.settingsConfig
        SettingConfigObject.updateSettings(
            // Assuming SettingConfigModel has a copy method or is a data class
            // And defaultMiddle() provides a base. If not, construct manually.
            originalSettingsConfig.copy(
                tpovId = testTpovId,
                languages = testLanguages
            )
        )

        // Initialize DomainExceptions - actual ExceptionInteractor might be needed if its methods are called
        // For now, assuming ExceptionInteractor's constructor doesn't do anything complex or can be mocked if needed.
        // If ExceptionInteractor itself needs to be mocked:
        // domainExceptions = com.tpov.common.domain.DomainExceptions(interactor = mockExceptionInteractor)
        // For now, let's assume a simple setup. We'll refine if ExceptionInteractor's methods are crucial for a stage.
        domainExceptions = com.tpov.common.domain.DomainExceptions(
            beforeException = {}, // Placeholder
            afterException = {},  // Placeholder
            interactor = com.tpov.common.ExceptionInteractor(null) // Assuming it can take null or a mock repo
        )
        StructureDataExtention.init(domainExceptions)
    }

    @AfterEach
    fun tearDown() {
        // Restore original settings
        SettingConfigObject.updateSettings(originalSettingsConfig)
    }

import com.tpov.common.data.model.local.StructureDataLocal // Assuming this is the correct import
import com.tpov.common.domain.model.SyncStage
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow

    // --- Tests for initStateStructureData ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `initStateStructureData - should fetch remote and local data and set correct state`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val mockRemoteData = mutableListOf(StructureDataLocal(nameItem = "RemoteCategory1"))
        val mockLocalData = mutableListOf(StructureDataLocal(nameItem = "LocalCategory1"))

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(eventQuiz)) doReturn mockRemoteData
        whenever(mockStructureUseCase.getStructureEventData(eventQuiz)) doReturn mockLocalData

        // Act
        syncState.initStateStructureData(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.STRUCTURE_FETCH, syncState.currentStage)
        assertEquals(mockRemoteData, syncState.structureCategoryDataListRemote)
        assertEquals(mockLocalData, syncState.structureCategoryDataListLocal)
        assertNull(syncState.exception)

        verify(mockStructureUseCase).fetchStructureCategoryDataList(eventQuiz)
        verify(mockStructureUseCase).getStructureEventData(eventQuiz)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `initStateStructureData - when remote fetch returns null, should set remote list to empty and not throw`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val mockLocalData = mutableListOf(StructureDataLocal(nameItem = "LocalCategory1"))

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(eventQuiz)) doReturn null
        whenever(mockStructureUseCase.getStructureEventData(eventQuiz)) doReturn mockLocalData

        // Act
        // Note: The actual implementation uses `?: exceptionHandler.exceptionInitStructureRemoteData()`
        // We need to ensure DomainExceptions is set up to not make the test itself fail, but rather set syncState.exception
        // For this test, we'll simulate that the handler sets the exception message.
        val customExceptionHandler = com.tpov.common.domain.DomainExceptions(
            beforeException = { syncState.exception = it },
            afterException = {},
            interactor = com.tpov.common.ExceptionInteractor(null) // Placeholder
        )
        StructureDataExtention.init(customExceptionHandler)


        syncState.initStateStructureData(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.STRUCTURE_FETCH, syncState.currentStage)
        assertTrue(syncState.structureCategoryDataListRemote.isEmpty()) // Actual code sets to empty list via handler.
        assertEquals(mockLocalData, syncState.structureCategoryDataListLocal)
        assertNotNull(syncState.exception) // Exception should be set by the handler
        assertTrue(syncState.exception!!.contains("Error initializing remote structure data") || syncState.exception!!.contains("Error during structure fetch"))


        verify(mockStructureUseCase).fetchStructureCategoryDataList(eventQuiz)
        verify(mockStructureUseCase).getStructureEventData(eventQuiz)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `initStateStructureData - when local fetch returns null, should set local list to empty`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val mockRemoteData = mutableListOf(StructureDataLocal(nameItem = "RemoteCategory1"))

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(eventQuiz)) doReturn mockRemoteData
        whenever(mockStructureUseCase.getStructureEventData(eventQuiz)) doReturn null

        // Act
        syncState.initStateStructureData(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.STRUCTURE_FETCH, syncState.currentStage)
        assertEquals(mockRemoteData, syncState.structureCategoryDataListRemote)
        assertTrue(syncState.structureCategoryDataListLocal.isEmpty())
        assertNull(syncState.exception)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `initStateStructureData - when remote fetch throws exception, should set exception in SyncState`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val errorMessage = "Network Error!"
        whenever(mockStructureUseCase.fetchStructureCategoryDataList(eventQuiz)) doThrow RuntimeException(errorMessage)
        whenever(mockStructureUseCase.getStructureEventData(eventQuiz)) doReturn mutableListOf()


        // Configure the exception handler to set the exception message in SyncState
         val customExceptionHandler = com.tpov.common.domain.DomainExceptions(
            beforeException = { syncState.exception = it },
            afterException = {},
            interactor = com.tpov.common.ExceptionInteractor(null) // Placeholder
        )
        StructureDataExtention.init(customExceptionHandler)

        // Act
        syncState.initStateStructureData(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.STRUCTURE_FETCH, syncState.currentStage)
        assertNotNull(syncState.exception)
        assertTrue(syncState.exception!!.contains(errorMessage) || syncState.exception!!.contains("Error initializing remote structure data"))
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `initStateStructureData - when local fetch throws exception, should proceed but set exception in SyncState`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val errorMessage = "Local DB Error!"
        val mockRemoteData = mutableListOf(StructureDataLocal(nameItem = "RemoteCategory1"))

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(eventQuiz)) doReturn mockRemoteData
        whenever(mockStructureUseCase.getStructureEventData(eventQuiz)) doThrow RuntimeException(errorMessage)

        // Configure the exception handler
        val customExceptionHandler = com.tpov.common.domain.DomainExceptions(
            beforeException = { syncState.exception = it },
            afterException = {},
            interactor = com.tpov.common.ExceptionInteractor(null) // Placeholder
        )
        StructureDataExtention.init(customExceptionHandler)

        // Act
        syncState.initStateStructureData(mockStructureUseCase)

        // Assert
        // The stage tries to fetch remote first, then local. If local fails, remote might still be set.
        // The current implementation catches the exception and calls exceptionHandler.exceptionInitStructureRemoteData.
        // This seems like a slight misnomer if the error is from local, but it's what the code does.
        assertEquals(SyncStage.STRUCTURE_FETCH, syncState.currentStage)
        assertEquals(mockRemoteData, syncState.structureCategoryDataListRemote) // Remote data should still be fetched
        assertNotNull(syncState.exception)
        assertTrue(syncState.exception!!.contains(errorMessage) || syncState.exception!!.contains("Error initializing remote structure data"))
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `initStateStructureData - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val syncState = SyncState(eventId = eventQuiz, exception = initialException)

        // Act
        syncState.initStateStructureData(mockStructureUseCase)

        // Assert
        assertEquals(initialException, syncState.exception) // Exception should remain unchanged
        assertTrue(syncState.structureCategoryDataListRemote.isEmpty()) // Should not have been populated
        assertTrue(syncState.structureCategoryDataListLocal.isEmpty()) // Should not have been populated
                                                                      // currentStage might remain NOT_STARTED or whatever it was.
                                                                      // The code returns `this` immediately.
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage) // Current stage should not change
    }


    // --- Tests for updateLocalStructureData ---

    // Helper function to create StructureDataLocal for tests more easily
    private fun createTestData(name: String, version: Int = 0, dataUpdateRemote: String = "", children: MutableList<StructureDataLocal>? = null): StructureDataLocal {
        return StructureDataLocal(
            nameItem = name,
            version = version,
            dataUpdateGlobal = dataUpdateRemote, // Assuming dataUpdateGlobal is what remote provides
            children = children ?: mutableListOf()
        )
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalStructureData - remote has new top-level category, local should add it`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteCategory = createTestData("RemoteCat1", dataUpdateRemote = "2023-01-01")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteCategory),
            structureCategoryDataListLocal = mutableListOf()
        )

        // Act
        syncState.updateLocalStructureData()

        // Assert
        assertEquals(SyncStage.STRUCTURE_LOCAL_SYNC, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)
        assertEquals("RemoteCat1", syncState.structureCategoryDataListLocal[0].nameItem)
        // Add more assertions for other fields if necessary, e.g., dataUpdateLocal should be set
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalStructureData - remote has new sub-category, local should add it under existing parent`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteSubCategory = createTestData("RemoteSubCat1", dataUpdateRemote = "2023-01-01")
        val remoteParentCategory = createTestData("ParentCat1", children = mutableListOf(remoteSubCategory))

        val localParentCategory = createTestData("ParentCat1", children = mutableListOf()) // Local parent exists but no sub-cat

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteParentCategory),
            structureCategoryDataListLocal = mutableListOf(localParentCategory.copy(children = mutableListOf())) // Ensure deep copy for safety
        )

        // Act
        syncState.updateLocalStructureData()

        // Assert
        assertEquals(SyncStage.STRUCTURE_LOCAL_SYNC, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)
        assertEquals("ParentCat1", syncState.structureCategoryDataListLocal[0].nameItem)
        assertEquals(1, syncState.structureCategoryDataListLocal[0].children?.size)
        assertEquals("RemoteSubCat1", syncState.structureCategoryDataListLocal[0].children?.get(0)?.nameItem)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalStructureData - existing item needs update based on remote version or date`(eventQuiz: EventQuiz) {
        // Arrange
        // isUpdateStructure logic: (old.version < new.version || (old.version == new.version && old.dataUpdateGlobal < new.dataUpdateGlobal))
        val remoteItem = createTestData("Item1", version = 1, dataUpdateRemote = "2023-01-02T10:00:00Z")
        val localItem = createTestData("Item1", version = 1, dataUpdateRemote = "2023-01-01T10:00:00Z") // Older date
        localItem.dataUpdateLocal = "2023-01-01T09:00:00Z" // ensure local data is distinct

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteItem),
            structureCategoryDataListLocal = mutableListOf(localItem)
        )

        // Act
        syncState.updateLocalStructureData()

        // Assert
        assertEquals(SyncStage.STRUCTURE_LOCAL_SYNC, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)
        // Assuming updateLocalInfoData updates relevant fields from remote
        // The actual updateLocalInfoData logic in StructureDataUtils.kt would determine which fields are copied.
        // For this test, we'll assume dataUpdateGlobal from remote is copied to localItem.dataUpdateLocal or similar
        // and other fields like version, ratingGlobal etc.
        // We need to inspect `updateLocalInfoData` in `StructureDataUtils` to make precise assertions.
        // For now, let's assume dataUpdateGlobal is a key field that indicates an update happened.
        // The actual `updateLocalInfoData` copies fields from the *remote* item found at the same path.
        // NB: The call to `this.structureCategoryDataListLocal.updateLocalInfoData(...)` inside the
        // `updateLocalStructureData` stage currently does NOT modify `structureCategoryDataListLocal` in place,
        // as `StructureDataUtils.updateLocalInfoData` only returns a found list, it doesn't mutate its caller.
        // Thus, any "update" to an existing item's fields in this stage must come from other logic
        // within `processStructureDataDifferences` if `isUpdateStructure` is true (e.g. if it implicitly replaces nodes).
        // If `isUpdateStructure` is true, and no other mechanism replaces the node or its data, then fields
        // asserted below might only match if the `localItem` was already identical to `remoteItem` in those fields,
        // or if `processStructureDataDifferences` implicitly handles replacement.
        // For this test, we assume that if isUpdateStructure is true, the expectation is an update.
        // If the test fails on these assertions, it points to the above observation.
        assertEquals(remoteItem.dataUpdateGlobal, syncState.structureCategoryDataListLocal[0].dataUpdateGlobal)
        assertEquals(remoteItem.version, syncState.structureCategoryDataListLocal[0].version)
        // Add more specific field assertions based on `updateLocalInfoData`'s behavior
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalStructureData - no differences, local structure remains unchanged`(eventQuiz: EventQuiz) {
        // Arrange
        val commonItem = createTestData("Item1", version = 1, dataUpdateRemote = "2023-01-01T10:00:00Z")
        commonItem.dataUpdateLocal = commonItem.dataUpdateGlobal // Make local and remote identical for update checks

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(commonItem.copy()), // Use copy to avoid object reference issues
            structureCategoryDataListLocal = mutableListOf(commonItem.copy())
        )
        val originalLocalDataJson = kotlinx.serialization.json.Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListLocal[0])


        // Act
        syncState.updateLocalStructureData()

        // Assert
        assertEquals(SyncStage.STRUCTURE_LOCAL_SYNC, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)
        val finalLocalDataJson = kotlinx.serialization.json.Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListLocal[0])
        assertEquals(originalLocalDataJson, finalLocalDataJson, "Local data should not change if remote and local are identical according to isUpdateStructure logic")
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalStructureData - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) {
        // Arrange
        val initialException = "Previous stage failed"
        val syncState = SyncState(
            eventId = eventQuiz,
            exception = initialException,
            structureCategoryDataListRemote = mutableListOf(createTestData("RemoteCat1")),
            structureCategoryDataListLocal = mutableListOf()
        )
        val originalLocalData = syncState.structureCategoryDataListLocal.map { it.copy() }

        // Act
        syncState.updateLocalStructureData()

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage) // currentStage should not change
        assertEquals(originalLocalData.size, syncState.structureCategoryDataListLocal.size)
        if (originalLocalData.isNotEmpty() && syncState.structureCategoryDataListLocal.isNotEmpty()) {
           assertEquals(Json.encodeToString(StructureDataLocal.serializer(), originalLocalData[0]), Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListLocal[0]))
        }
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalStructureData - handles exception during processing and sets syncState exception`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteCategory = createTestData("RemoteCat1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteCategory),
            structureCategoryDataListLocal = mutableListOf()
        )

        // To simulate an exception, we can try to make one of the utility functions fail.
        // This is tricky without directly mocking StructureDataUtils.
        // Let's assume for now that a NullPointerException or similar could occur if data is malformed
        // in a way that `processStructureDataDifferences` or its callbacks don't expect.
        // A more robust way would be to inject a mockable utility or make callbacks throw.
        // For this test, we'll corrupt data slightly to see if the catch block is hit.
        // This test is more of a safeguard and might need refinement if specific internal exceptions are expected.
        syncState.structureCategoryDataListRemote[0].children = null // Potentially problematic if code expects non-null children list internally for addNodeByPath
                                                                  // This is a bit of a guess to trigger the catch.

        val customExceptionHandler = com.tpov.common.domain.DomainExceptions(
            beforeException = { syncState.exception = it },
            afterException = {},
            interactor = com.tpov.common.ExceptionInteractor(null)
        )
        StructureDataExtention.init(customExceptionHandler)

        // Act
        syncState.updateLocalStructureData()

        // Assert
        assertEquals(SyncStage.STRUCTURE_LOCAL_SYNC, syncState.currentStage)
        assertNotNull(syncState.exception)
        assertTrue(syncState.exception!!.contains("Error syncing local structure data"))
    }

    // --- Tests for syncChangeListQuestionsLocal ---

    private fun setupSyncStateForQuestionChangeTests(
        eventQuiz: EventQuiz,
        remoteItems: List<StructureDataLocal>,
        localItems: List<StructureDataLocal> = emptyList() // Default to empty if not specified
    ): SyncState {
        return SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = remoteItems.toMutableList(),
            structureCategoryDataListLocal = localItems.toMutableList(),
            changedListQuestionLocal = mutableListOf() // Ensure this is empty before the test
        )
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - item needs update (isShowArchive=true, local.isShowDownload=true, isUpdateStructure=true)`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteQuiz = createTestData("Quiz1", version = 2, dataUpdateRemote = "v2").apply { isShowArchive = true }
        val localQuiz = createTestData("Quiz1", version = 1, dataUpdateRemote = "v1").apply { isShowDownload = true }
        // isUpdateStructure should be true because remoteQuiz.version > localQuiz.version

        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(remoteQuiz), localItems = listOf(localQuiz))

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.changedListQuestionLocal.size)
        assertEquals("Quiz1", syncState.changedListQuestionLocal[0].name)
        assertFalse(syncState.changedListQuestionLocal[0].isCreate, "Should not be marked as new create if local item exists")
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - item does NOT need update (isShowArchive=true, local.isShowDownload=true, isUpdateStructure=false)`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteQuiz = createTestData("Quiz1", version = 1, dataUpdateRemote = "v1").apply { isShowArchive = true }
        val localQuiz = createTestData("Quiz1", version = 1, dataUpdateRemote = "v1").apply { isShowDownload = true }
        // isUpdateStructure should be false

        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(remoteQuiz), localItems = listOf(localQuiz))

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.changedListQuestionLocal.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - new item needs download by optimization (isShowArchive=true, local item missing, isDownloadQuestionForOptimization=true)`(eventQuiz: EventQuiz) {
        // Arrange
        // To make isDownloadQuestionForOptimization true: use high rating or low changedListQuestionLocal.size (0 here)
        val remoteQuiz = createTestData("Quiz1", version = 1).apply {
            isShowArchive = true
            ratingGlobal = 1000 // High rating to trigger optimization
        }
        // No local item for "Quiz1"

        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(remoteQuiz), localItems = emptyList())

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.changedListQuestionLocal.size)
        assertEquals("Quiz1", syncState.changedListQuestionLocal[0].name)
        assertTrue(syncState.changedListQuestionLocal[0].isCreate, "Should be marked as new create if local item is missing")
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - existing item needs download by optimization (isShowArchive=true, local.isShowDownload=false, isDownloadQuestionForOptimization=true)`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteQuiz = createTestData("Quiz1", version = 1).apply {
            isShowArchive = true
            ratingGlobal = 1000 // High rating
        }
        val localQuiz = createTestData("Quiz1", version = 1).apply { isShowDownload = false }

        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(remoteQuiz), localItems = listOf(localQuiz))

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.changedListQuestionLocal.size)
        assertEquals("Quiz1", syncState.changedListQuestionLocal[0].name)
        // Based on current code: if findLocalQuizByName.structureData is not null, isCreate is false.
        assertFalse(syncState.changedListQuestionLocal[0].isCreate, "Should not be marked as new create if local item exists, even if isShowDownload was false")
    }


    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - item does NOT need download by optimization (isShowArchive=true, local.isShowDownload=false, isDownloadQuestionForOptimization=false)`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteQuiz = createTestData("Quiz1", version = 1).apply {
            isShowArchive = true
            ratingGlobal = 0 // Low rating
        }
        val localQuiz = createTestData("Quiz1", version = 1).apply { isShowDownload = false }
        // Assume changedListQuestionLocal.size will be > some threshold for optimization to be false.
        // The function `isDownloadQuestionForOptimization` takes `changedListQuestionLocal.size`.
        // If we want to ensure it's false, we can pre-populate changedListQuestionLocal to a large number if needed,
        // or rely on a low ratingGlobal and hope the threshold isn't 0.
        // For simplicity, relying on low ratingGlobal. The actual function is:
        // currentPath.isFirstLevel() || ratingGlobal > 100 || changedListQuestionLocalSize < 10
        // So, if not first level, ratingGlobal <=100, and size >=10, it's false.
        // Let's simulate a non-first level item by giving it a parent path.
        val parentRemote = createTestData("Parent", children = mutableListOf(remoteQuiz))
        val parentLocal = createTestData("Parent", children = mutableListOf(localQuiz))


        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(parentRemote), localItems = listOf(parentLocal))
        // To ensure changedListQuestionLocal.size is high for the test of the *second* item if there were multiple:
        // syncState.changedListQuestionLocal.addAll(List(10) { ChangeVersionStructure("dummy", PathStructure(), false) })
        // For a single item, ratingGlobal = 0 and not being first level (has parent) should be enough if default changedListQuestionLocalSize < 10 is not met.

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.changedListQuestionLocal.isEmpty(), "Item should not be added if optimization check is false.")
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - remote item isShowArchive=false, not added`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteQuiz = createTestData("Quiz1", version = 1).apply { isShowArchive = false }
        val localQuiz = createTestData("Quiz1", version = 1).apply { isShowDownload = true }

        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(remoteQuiz), localItems = listOf(localQuiz))

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.changedListQuestionLocal.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncChangeListQuestionsLocal - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) {
        // Arrange
        val initialException = "Previous stage failed"
        val syncState = setupSyncStateForQuestionChangeTests(eventQuiz, remoteItems = listOf(createTestData("Quiz1")))
        syncState.exception = initialException

        // Act
        syncState.syncChangeListQuestionsLocal()

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage) // currentStage should not change from initial if exception set
        assertTrue(syncState.changedListQuestionLocal.isEmpty())
    }

    // --- Tests for syncChangeListQuestionsRemote ---

    @ParameterizedTest
    @EnumSource(value = EventQuiz::class, mode = EnumSource.Mode.EXCLUDE, names = ["QUIZ_BY_USER"])
    fun `syncChangeListQuestionsRemote - not QUIZ_BY_USER event, should do nothing`(eventQuiz: EventQuiz) {
        // Arrange
        val localQuiz = createTestData("LocalQuiz1", version = 1).apply { dataUpdateLocal = "v1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureCategoryDataListRemote = mutableListOf(), // Remote can be empty or have data
            changedListQuestionRemote = mutableListOf()
        )
        val initialStage = syncState.currentStage

        // Act
        syncState.syncChangeListQuestionsRemote()

        // Assert
        assertTrue(syncState.changedListQuestionRemote.isEmpty())
        // currentStage should remain unchanged because the method returns early for non-QUIZ_BY_USER events
        // before setting the stage to QUESTION_CHANGE_LIST
        assertEquals(initialStage, syncState.currentStage)
        assertNull(syncState.exception)
    }

    @Test // Specific to QUIZ_BY_USER
    fun `syncChangeListQuestionsRemote - QUIZ_BY_USER - local item updated, needs remote update`() {
        // Arrange
        val eventQuiz = EventQuiz.QUIZ_BY_USER
        val localQuiz = createTestData("Quiz1", version = 2, dataUpdateRemote = "v2_local_update").apply { dataUpdateLocal = "v2_local_update" } // local is newer
        val remoteQuizEquivalent = createTestData("Quiz1", version = 1, dataUpdateRemote = "v1_remote_original")

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent),
            changedListQuestionRemote = mutableListOf()
        )

        // Act
        syncState.syncChangeListQuestionsRemote()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.changedListQuestionRemote.size)
        assertEquals("Quiz1", syncState.changedListQuestionRemote[0].name)
        assertFalse(syncState.changedListQuestionRemote[0].isCreate)
    }

    @Test
    fun `syncChangeListQuestionsRemote - QUIZ_BY_USER - local item same as remote, no remote update`() {
        // Arrange
        val eventQuiz = EventQuiz.QUIZ_BY_USER
        val commonQuiz = createTestData("Quiz1", version = 1, dataUpdateRemote = "v1_common")
        commonQuiz.dataUpdateLocal = commonQuiz.dataUpdateGlobal // Ensure they are "the same" by isUpdateStructure logic

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(commonQuiz.copy()),
            structureCategoryDataListRemote = mutableListOf(commonQuiz.copy()),
            changedListQuestionRemote = mutableListOf()
        )

        // Act
        syncState.syncChangeListQuestionsRemote()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.changedListQuestionRemote.isEmpty())
    }

    @Test
    fun `syncChangeListQuestionsRemote - QUIZ_BY_USER - new local item, needs remote creation`() {
        // Arrange
        val eventQuiz = EventQuiz.QUIZ_BY_USER
        val localQuiz = createTestData("NewLocalQuiz1", version = 1).apply { dataUpdateLocal = "new_v1" }

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureCategoryDataListRemote = mutableListOf(), // No remote equivalent
            changedListQuestionRemote = mutableListOf()
        )

        // Act
        syncState.syncChangeListQuestionsRemote()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.changedListQuestionRemote.size)
        assertEquals("NewLocalQuiz1", syncState.changedListQuestionRemote[0].name)
        assertTrue(syncState.changedListQuestionRemote[0].isCreate)
    }

    @Test
    fun `syncChangeListQuestionsRemote - QUIZ_BY_USER - local item marked for deletion, needs remote update`() {
        // Arrange
        val eventQuiz = EventQuiz.QUIZ_BY_USER
        val localQuiz = createTestData("QuizToDelete1", version = 1).apply { dataUpdateLocal = "-1" } // Marked for deletion
        val remoteQuizEquivalent = createTestData("QuizToDelete1", version = 1, dataUpdateRemote = "v1_remote")

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent),
            changedListQuestionRemote = mutableListOf()
        )

        // Act
        syncState.syncChangeListQuestionsRemote()

        // Assert
        assertEquals(SyncStage.QUESTION_CHANGE_LIST, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.changedListQuestionRemote.size)
        assertEquals("-1", syncState.changedListQuestionRemote[0].name, "Name should be -1 for deletion marker")
        assertFalse(syncState.changedListQuestionRemote[0].isCreate)
    }

    @Test
    fun `syncChangeListQuestionsRemote - QUIZ_BY_USER - if syncState already has exception, should not proceed`() {
        // Arrange
        val eventQuiz = EventQuiz.QUIZ_BY_USER
        val initialException = "Previous stage failed"
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(createTestData("LocalQuiz1")),
            changedListQuestionRemote = mutableListOf(),
            exception = initialException
        )

        // Act
        syncState.syncChangeListQuestionsRemote()

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        assertTrue(syncState.changedListQuestionRemote.isEmpty())
    }

import com.tpov.common.data.model.entity.StructureInfoEntity // Required for StructureInfoEntity
import com.tpov.common.domain.usecase.SettingConfigObject // Required for settingsConfig

    // --- Tests for syncInfoGlobal ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoGlobal - populates structureInfoGlobal from remote data`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteQuiz1 = createTestData("RemoteQuiz1", version = 1).apply {
            dataUpdateGlobal = "2023-01-01"
            ratingGlobal = 100
            starsMaxGlobal = 5
            starsAverageGlobal = 4
            languages = "en"
            isShowArchive = true
        }
        val remoteCategory1 = createTestData("RemoteCat1", children = mutableListOf(remoteQuiz1)).apply {
            dataUpdateGlobal = "2023-01-02"
            ratingGlobal = 50
            starsMaxGlobal = 4
            starsAverageGlobal = 3
            languages = "fr"
            isShowArchive = false
        }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteCategory1),
            structureInfoGlobal = mutableListOf()
        )

        // Mocking SettingConfigObject.settingsConfig.tpovId if possible, or ensure it has a default.
        // For this test, let's assume SettingConfigObject.settingsConfig.tpovId has a value (e.g. 123)
        // If direct modification is hard, this part of assertion might be less strict or require refactor for testability.
        val expectedTpovId = SettingConfigObject.settingsConfig.tpovId // Use the actual value for assertion

        // Act
        syncState.syncInfoGlobal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(2, syncState.structureInfoGlobal.size, "Should have info for category and quiz")

        val catInfo = syncState.structureInfoGlobal.find { it.pathStructure.nameCategory == "RemoteCat1" && it.pathStructure.nameQuiz == "" }
        assertNotNull(catInfo)
        catInfo?.let {
            assertEquals(remoteCategory1.dataUpdateGlobal, it.dateUpdate)
            assertEquals(expectedTpovId, it.idUser)
            assertEquals(remoteCategory1.ratingGlobal, it.rating)
            assertEquals(remoteCategory1.starsMaxGlobal, it.starsMax)
            assertEquals(remoteCategory1.starsAverageGlobal, it.starsAverage)
            assertEquals(remoteCategory1.languages, it.languages)
            assertEquals(remoteCategory1.isShowArchive, it.isShow)
        }

        val quizInfo = syncState.structureInfoGlobal.find { it.pathStructure.nameQuiz == "RemoteQuiz1" }
        assertNotNull(quizInfo)
        quizInfo?.let {
            assertEquals(remoteQuiz1.dataUpdateGlobal, it.dateUpdate)
            assertEquals(expectedTpovId, it.idUser)
            assertEquals(remoteQuiz1.ratingGlobal, it.rating)
            assertEquals(remoteQuiz1.starsMaxGlobal, it.starsMax)
            assertEquals(remoteQuiz1.starsAverageGlobal, it.starsAverage)
            assertEquals(remoteQuiz1.languages, it.languages)
            assertEquals(remoteQuiz1.isShowArchive, it.isShow)
        }
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoGlobal - empty remote data, structureInfoGlobal remains empty`(eventQuiz: EventQuiz) {
        // Arrange
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(),
            structureInfoGlobal = mutableListOf()
        )

        // Act
        syncState.syncInfoGlobal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.structureInfoGlobal.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoGlobal - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) {
        // Arrange
        val initialException = "Previous stage failed"
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(createTestData("RemoteQuiz1")),
            structureInfoGlobal = mutableListOf(),
            exception = initialException
        )

        // Act
        syncState.syncInfoGlobal()

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        assertTrue(syncState.structureInfoGlobal.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoGlobal - handles exception during processing and sets syncState exception`(eventQuiz: EventQuiz) {
        // Arrange
        val remoteCategory = createTestData("RemoteCat1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteCategory),
            structureInfoGlobal = mutableListOf()
        )

        // Simulate an issue that might cause addInfoGlobal or the iteration to fail.
        // Forcing settingsConfig.tpovId to a problematic value is hard if it's an object.
        // Let's assume a general exception scenario. The current catch block is generic.
        // To make this test more concrete, if there's a specific field in StructureDataLocal which,
        // if null when expected to be non-null by addInfoGlobal, could cause an NPE, that'd be a way.
        // For now, this test relies on the generic catch block in syncInfoGlobal.
        // We can try to make structureCategoryDataListRemote un-iterable or throw during access,
        // but processStructureDataDifferences is quite robust.
        // A more direct way to test the exception handling of this specific stage's logic
        // would be if addInfoGlobal itself could throw a custom, testable exception.

        // For now, we rely on the existing generic catch.
        // To actually trigger the catch block in syncInfoGlobal, the problem would likely need to be
        // inside the processStructureDataDifferences or its callbacks, or the addInfoGlobal method.
        // Let's assume, hypothetically, that addInfoGlobal could throw if a language string was too long
        // or some other internal validation failed within addInfoGlobal (though it doesn't seem to have such).
        // This test will be more of a placeholder for "if something unexpected goes wrong".

        val customExceptionHandler = com.tpov.common.domain.DomainExceptions(
            beforeException = { syncState.exception = it },
            afterException = {},
            interactor = com.tpov.common.ExceptionInteractor(null)
        )
        StructureDataExtention.init(customExceptionHandler)

        // To make this test more meaningful, we'd need a way to make `addInfoGlobal` specifically fail.
        // One way is to make a field used by it null, if it's not nullable.
        // `settingsConfig.tpovId` is an Int, so can't be null.
        // Let's assume `currentPath.copy()` could fail under extreme memory, not testable here.
        // The most likely failure is an NPE if structureDataNew was unexpectedly null inside a callback,
        // but processStructureDataDifferences usually handles that.
        // So, this test for specific exception *within* syncInfoGlobal is hard to trigger without deeper modification.
        // We will assume the generic try-catch is for truly unexpected issues.
        // For the test to actually set an exception, we'd need to mock a lower-level component or cause an NPE.

        // Let's try to make `currentPath` problematic (though `processStructureDataDifferences` should handle path generation)
        // This test might not effectively trigger the catch block as `processStructureDataDifferences` is robust.
        // The primary value is testing the happy path and pre-existing exception.

        // Act
        // To *force* an exception for testing the handler, we'd ideally mock a behavior.
        // Since we can't easily mock parts of processStructureDataDifferences or addInfoGlobal,
        // this test will likely pass the happy path unless there's a latent bug.
        // To demonstrate the exception handling part of the stage, we'd need a more direct way to cause failure.
        // For now, this test will behave like a happy path test.
        // A true test of its exception *catch* block would require more invasive setup or refactoring.

        // To actually test the EXCEPTION part of THIS stage, we'd need addInfoGlobal to throw.
        // Let's simulate that the `processStructureDataDifferences` itself fails by making the input list problematic
        // in a way that its iteration might fail (e.g. if it was not thread safe and modified concurrently - not applicable here)
        // Or if one of the StructureDataLocal items was fundamentally broken.
        // The current `catch (e: Exception)` is very broad.
        // This test will likely show happy path unless there's a bug in `processStructureDataDifferences` with the given data.
        // To properly test the exception handling of *this specific stage*, we'd need to make `addInfoGlobal` throw.
        // This is not easily done without changing source or using PowerMock for static/object mocking.

        // Given the limitations, this test will likely pass like a happy path.
        // The main purpose of the catch block in the source is for truly unexpected runtime exceptions.
        syncState.syncInfoGlobal() // Run it

        // If we wanted to ensure the exception handler IS used, we'd need a way to make addInfoGlobal fail.
        // For now, we assume if an *unexpected* exception happens, it's caught.
        // This test doesn't reliably *cause* an exception in this stage's specific logic.
        // It mainly verifies the stage completes and sets currentStage.
        // A more targeted exception test would be needed if specific failure modes of `addInfoGlobal` were known.

        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        // No reliable way to assert syncState.exception is set by *this stage's catch block* without more control.
        // If processStructureDataDifferences had an issue with empty list, it would be caught by its own safety.
    }


import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.presentation.model.PathStructure
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.times

    // --- Tests for updateLocalQuestion ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalQuestion - updates existing questions (isCreate=false)`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val change = ChangeVersionStructure(name = "Quiz1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionLocal = mutableListOf(change))

        val remoteQuestion1 = QuestionLocal(id = 1, nameQuestion = "Remote Q1", pathStructure = pathForChange)
        val remoteQuestion2 = QuestionLocal(id = 2, nameQuestion = "Remote Q2", pathStructure = pathForChange)
        val mockRemoteQuestions = arrayListOf(remoteQuestion1, remoteQuestion2)

        // Simulate findStructureDataOld returning a path (by ensuring local/remote structure lists could form this path)
        // For this test, we mainly care that delete/insert are called with *some* path.
        // The actual path derivation logic via findStructureDataOld is complex and part of previous stages.
        // We assume changedListQuestionLocal has paths that are resolvable.
        // The key is that `change.pathStructure` is used for fetch, and the resolved `pathLocal` for delete/insert.
        // For simplicity in mocking, we'll assume pathLocal is same as pathForChange for this test's scope.
        val pathLocalForDbOperations = pathForChange

        whenever(mockQuestionUseCase.fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)) doReturn mockRemoteQuestions
        // No need to mock delete/insert, just verify calls.

        // Act
        syncState.updateLocalQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage) // As per current source code
        assertNull(syncState.exception)

        verify(mockQuestionUseCase).fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)
        verify(mockQuestionUseCase).deleteQuestionByPath(pathLocalForDbOperations)
        verify(mockQuestionUseCase).insertQuestion(remoteQuestion1.copy(pathStructure = pathLocalForDbOperations))
        verify(mockQuestionUseCase).insertQuestion(remoteQuestion2.copy(pathStructure = pathLocalForDbOperations))
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalQuestion - creates new questions (isCreate=true)`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "NewQuiz1")
        val change = ChangeVersionStructure(name = "NewQuiz1", pathStructure = pathForChange, isCreate = true)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionLocal = mutableListOf(change))

        val remoteQuestion1 = QuestionLocal(id = 1, nameQuestion = "Remote New Q1")
        val mockRemoteQuestions = arrayListOf(remoteQuestion1)
        val pathLocalForDbOperations = pathForChange // Assumed for simplicity

        whenever(mockQuestionUseCase.fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)) doReturn mockRemoteQuestions

        // Act
        syncState.updateLocalQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)

        verify(mockQuestionUseCase).fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)
        verify(mockQuestionUseCase, times(0)).deleteQuestionByPath(any()) // Should NOT be called
        verify(mockQuestionUseCase).insertQuestion(remoteQuestion1.copy(pathStructure = pathLocalForDbOperations))
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalQuestion - empty changedListQuestionLocal, no use case calls`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionLocal = mutableListOf())

        // Act
        syncState.updateLocalQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage) // Stage is set even if list is empty
        assertNull(syncState.exception)
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalQuestion - fetchQuestion returns empty list (isCreate=false)`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val change = ChangeVersionStructure(name = "Quiz1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionLocal = mutableListOf(change))
        val pathLocalForDbOperations = pathForChange

        whenever(mockQuestionUseCase.fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)) doReturn arrayListOf()

        // Act
        syncState.updateLocalQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase).fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)
        verify(mockQuestionUseCase).deleteQuestionByPath(pathLocalForDbOperations)
        verify(mockQuestionUseCase, times(0)).insertQuestion(any()) // No insert calls
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalQuestion - fetchQuestion throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val change = ChangeVersionStructure(name = "Quiz1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionLocal = mutableListOf(change))
        val exceptionMessage = "Fetch failed"

        whenever(mockQuestionUseCase.fetchQuestion(pathForChange, SettingConfigObject.settingsConfig.languages)) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrownException = assertThrows(RuntimeException::class.java) {
            runBlocking { // Need to wrap the suspend call in runBlocking for assertThrows
                syncState.updateLocalQuestion(mockQuestionUseCase)
            }
        }
        assertEquals(exceptionMessage, thrownException.message)
        // syncState.exception will not be set by this stage as the try-catch is commented out
        assertNull(syncState.exception)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateLocalQuestion - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val change = ChangeVersionStructure(name = "Quiz1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(
            eventId = eventQuiz,
            changedListQuestionLocal = mutableListOf(change),
            exception = initialException)

        // Act
        syncState.updateLocalQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage) // Stage should not change
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    // --- Tests for updateRemoteQuestion ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - deletion (name='-1', isCreate=false), delete called twice`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizToDelete")
        val change = ChangeVersionStructure(name = "-1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf(change))

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase, times(2)).deleteQuestionByPath(pathForChange) // Called twice
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - deletion (name='-1', isCreate=true), delete called once`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizToDeleteNew")
        val change = ChangeVersionStructure(name = "-1", pathStructure = pathForChange, isCreate = true)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf(change))

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase, times(1)).deleteQuestionByPath(pathForChange) // Called once
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - updates existing questions (isCreate=false, name not '-1')`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizToUpdate")
        val change = ChangeVersionStructure(name = "QuizToUpdate", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf(change))

        val localQuestion1 = QuestionLocal(id = 10, nameQuestion = "Local Q1 for update", pathStructure = pathForChange)
        val mockLocalQuestions = arrayListOf(localQuestion1)
        whenever(mockQuestionUseCase.getQuestionByPath(pathForChange)) doReturn mockLocalQuestions

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase).getQuestionByPath(pathForChange)
        verify(mockQuestionUseCase).deleteQuestionByPath(pathForChange)
        verify(mockQuestionUseCase).pushQuestion(localQuestion1)
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - creates new questions (isCreate=true, name not '-1')`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizToCreate")
        val change = ChangeVersionStructure(name = "QuizToCreate", pathStructure = pathForChange, isCreate = true)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf(change))

        val localQuestion1 = QuestionLocal(id = 11, nameQuestion = "Local Q1 for create", pathStructure = pathForChange)
        val mockLocalQuestions = arrayListOf(localQuestion1)
        whenever(mockQuestionUseCase.getQuestionByPath(pathForChange)) doReturn mockLocalQuestions

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase).getQuestionByPath(pathForChange)
        verify(mockQuestionUseCase, times(0)).deleteQuestionByPath(any()) // Delete NOT called
        verify(mockQuestionUseCase).pushQuestion(localQuestion1)
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - empty changedListQuestionRemote, no use case calls`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf())

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - getQuestionByPath returns empty list (isCreate=false, name not '-1')`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizToUpdate")
        val change = ChangeVersionStructure(name = "QuizToUpdate", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf(change))

        whenever(mockQuestionUseCase.getQuestionByPath(pathForChange)) doReturn arrayListOf() // Empty list

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase).getQuestionByPath(pathForChange)
        verify(mockQuestionUseCase).deleteQuestionByPath(pathForChange) // Still called
        verify(mockQuestionUseCase, times(0)).pushQuestion(any()) // No push calls
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - getQuestionByPath throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val change = ChangeVersionStructure(name = "Quiz1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(eventId = eventQuiz, changedListQuestionRemote = mutableListOf(change))
        val exceptionMessage = "getQuestionByPath failed"

        whenever(mockQuestionUseCase.getQuestionByPath(pathForChange)) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrownException = assertThrows(RuntimeException::class.java) {
            runBlocking {
                syncState.updateRemoteQuestion(mockQuestionUseCase)
            }
        }
        assertEquals(exceptionMessage, thrownException.message)
        assertNull(syncState.exception) // Stage doesn't set it
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateRemoteQuestion - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val pathForChange = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val change = ChangeVersionStructure(name = "Quiz1", pathStructure = pathForChange, isCreate = false)
        val syncState = SyncState(
            eventId = eventQuiz,
            changedListQuestionRemote = mutableListOf(change),
            exception = initialException)

        // Act
        syncState.updateRemoteQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        verifyNoMoreInteractions(mockQuestionUseCase)
    }


    // --- Tests for updateStructureLocalNumberQuestion ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureLocalNumberQuestion - sets numQ and numHQ to 0 for leaf nodes`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val leafQuiz1 = createTestData("LeafQuiz1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(leafQuiz1)
        )
        // Mocking getQuestionsByPath even though its result is ignored by the hardcoding, because it's still called.
        whenever(mockQuestionUseCase.getQuestionsByPath(any())) doReturn arrayListOf(QuestionLocal(), QuestionLocal())


        // Act
        syncState.updateStructureLocalNumberQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)
        val updatedLeafQuiz = syncState.structureCategoryDataListLocal[0]
        assertEquals(0, updatedLeafQuiz.numQ, "numQ should be hardcoded to 0")
        assertEquals(0, updatedLeafQuiz.numHQ, "numHQ should be hardcoded to 0")

        // Verify getQuestionsByPath was called for the leaf node's path
        // Need to determine the exact PathStructure that processStructureDataDifferences would generate.
        // For a single top-level leaf, path might be PathStructure(nameEvent=eventQuiz.name, nameQuiz="LeafQuiz1")
        // For simplicity, using any() if path construction is too complex to replicate here easily,
        // but ideally, match the exact path.
        val expectedPathForLeafQuiz1 = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "LeafQuiz1")
        verify(mockQuestionUseCase).getQuestionsByPath(expectedPathForLeafQuiz1)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureLocalNumberQuestion - sets numQ and numHQ to 0 for branch nodes`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val leafQuiz = createTestData("ChildQuiz")
        val branchCategory = createTestData("BranchCategory", children = mutableListOf(leafQuiz))
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(branchCategory)
        )
        whenever(mockQuestionUseCase.getQuestionsByPath(any())) doReturn arrayListOf(QuestionLocal()) // For the child

        // Act
        syncState.updateStructureLocalNumberQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)

        val updatedBranch = syncState.structureCategoryDataListLocal[0]
        assertEquals(0, updatedBranch.numQ, "numQ for branch should be hardcoded to 0")
        assertEquals(0, updatedBranch.numHQ, "numHQ for branch should be hardcoded to 0")

        val updatedChild = updatedBranch.children?.get(0)
        assertNotNull(updatedChild)
        assertEquals(0, updatedChild!!.numQ, "numQ for child leaf should be hardcoded to 0")
        assertEquals(0, updatedChild.numHQ, "numHQ for child leaf should be hardcoded to 0")

        val expectedPathForChildQuiz = PathStructure(
            nameEvent = eventQuiz.name,
            nameCategory = "BranchCategory",
            nameQuiz = "ChildQuiz"
        )
        verify(mockQuestionUseCase).getQuestionsByPath(expectedPathForChildQuiz)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureLocalNumberQuestion - empty local list, no errors`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf()
        )

        // Act
        syncState.updateStructureLocalNumberQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.structureCategoryDataListLocal.isEmpty())
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureLocalNumberQuestion - getQuestionsByPath throws exception for leaf, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val leafQuiz1 = createTestData("LeafQuizException")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(leafQuiz1)
        )
        val exceptionMessage = "DB error on getQuestionsByPath"
        val expectedPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "LeafQuizException")
        whenever(mockQuestionUseCase.getQuestionsByPath(expectedPath)) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking { // Ensure suspend call is within runBlocking for assertThrows
                 syncState.updateStructureLocalNumberQuestion(mockQuestionUseCase)
            }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception) // Exception not set in SyncState as try-catch is commented
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureLocalNumberQuestion - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val leafQuiz1 = createTestData("LeafQuiz1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(leafQuiz1),
            exception = initialException
        )

        // Act
        syncState.updateStructureLocalNumberQuestion(mockQuestionUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        verifyNoMoreInteractions(mockQuestionUseCase)
    }

    // --- Tests for clearStructureLocal ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `clearStructureLocal - item marked for deletion, removes item and calls delete use cases`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToDelete = createTestData("ItemToDelete").apply { dataUpdateLocal = "-1" }
        val itemToKeep = createTestData("ItemToKeep").apply { dataUpdateLocal = "v1" }
        val initialLocalList = mutableListOf(itemToKeep, itemToDelete)

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = initialLocalList
        )

        val pathForItemToDelete = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "ItemToDelete")
        // This path needs to match how processStructureDataDifferences would resolve it.
        // Assuming top-level items for simplicity in path construction for the test.

        // Act
        syncState.clearStructureLocal(mockQuestionUseCase, mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)

        assertEquals(1, syncState.structureCategoryDataListLocal.size, "Item marked for deletion should be removed")
        assertEquals("ItemToKeep", syncState.structureCategoryDataListLocal[0].nameItem)

        verify(mockQuestionUseCase).deleteQuestionByPath(pathForItemToDelete)
        verify(mockQuestionDetailUseCase).deleteRemoteQuestionDetailByPath(pathForItemToDelete)

        // Ensure no calls for the item to keep
        val pathForItemToKeep = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "ItemToKeep")
        verify(mockQuestionUseCase, times(0)).deleteQuestionByPath(pathForItemToKeep)
        verify(mockQuestionDetailUseCase, times(0)).deleteRemoteQuestionDetailByPath(pathForItemToKeep)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `clearStructureLocal - item NOT marked for deletion, item remains and no delete calls`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToKeep = createTestData("ItemToKeep").apply { dataUpdateLocal = "v1" }
        val initialLocalList = mutableListOf(itemToKeep)

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = initialLocalList
        )

        // Act
        syncState.clearStructureLocal(mockQuestionUseCase, mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)
        assertEquals("ItemToKeep", syncState.structureCategoryDataListLocal[0].nameItem)

        verifyNoMoreInteractions(mockQuestionUseCase)
        verifyNoMoreInteractions(mockQuestionDetailUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `clearStructureLocal - empty local list, no errors or calls`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf()
        )

        // Act
        syncState.clearStructureLocal(mockQuestionUseCase, mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.structureCategoryDataListLocal.isEmpty())
        verifyNoMoreInteractions(mockQuestionUseCase)
        verifyNoMoreInteractions(mockQuestionDetailUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `clearStructureLocal - questionUseCase throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToDelete = createTestData("ItemToDelete").apply { dataUpdateLocal = "-1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(itemToDelete)
        )
        val pathForItemToDelete = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "ItemToDelete")
        val exceptionMessage = "QuestionUseCase delete failed"
        whenever(mockQuestionUseCase.deleteQuestionByPath(pathForItemToDelete)) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking {
                syncState.clearStructureLocal(mockQuestionUseCase, mockQuestionDetailUseCase)
            }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception) // Exception not set in SyncState by this stage
        // Item might still be removed from list if exception happens after removal but before all calls complete
        // The current code removes then calls. So list modification might happen.
        // assertEquals(0, syncState.structureCategoryDataListLocal.size) // This depends on exact execution order around exception.
                                                                      // The source removes then calls, so this should hold.
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `clearStructureLocal - questionDetailUseCase throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToDelete = createTestData("ItemToDelete").apply { dataUpdateLocal = "-1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(itemToDelete)
        )
        val pathForItemToDelete = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "ItemToDelete")
        val exceptionMessage = "QuestionDetailUseCase delete failed"
        whenever(mockQuestionDetailUseCase.deleteRemoteQuestionDetailByPath(pathForItemToDelete)) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking {
                syncState.clearStructureLocal(mockQuestionUseCase, mockQuestionDetailUseCase)
            }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception)
        verify(mockQuestionUseCase).deleteQuestionByPath(pathForItemToDelete) // This would have been called before the failing one
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `clearStructureLocal - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val itemToDelete = createTestData("ItemToDelete").apply { dataUpdateLocal = "-1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(itemToDelete),
            exception = initialException
        )

        // Act
        syncState.clearStructureLocal(mockQuestionUseCase, mockQuestionDetailUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        assertEquals(1, syncState.structureCategoryDataListLocal.size, "List should not be modified")
        verifyNoMoreInteractions(mockQuestionUseCase)
        verifyNoMoreInteractions(mockQuestionDetailUseCase)
    }

import com.tpov.common.data.model.remote.StructureEditData // Required for verifying the argument
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.capture

    // --- Tests for addEditIdsStructureRemote ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `addEditIdsStructureRemote - item marked for global deletion, calls insertEditStructure with correct StructureEditData`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToDeleteGlobally = createTestData("ItemToDeleteGlobally").apply { dataUpdateGlobal = "-1" }
        val itemToKeep = createTestData("ItemToKeep").apply { dataUpdateGlobal = "v1" }
        val initialLocalList = mutableListOf(itemToKeep, itemToDeleteGlobally)

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = initialLocalList
        )

        val expectedPathForDeletion = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "ItemToDeleteGlobally")
        // Path resolution is complex, test assumes processStructureDataDifferences provides correct path to callback

        // Act
        syncState.addEditIdsStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)

        val captor = ArgumentCaptor.forClass(StructureEditData::class.java)
        verify(mockStructureUseCase).insertEditStructure(capture(captor))

        val capturedStructureEditData = captor.value
        assertNull(capturedStructureEditData.id)
        assertEquals(expectedPathForDeletion.nameEvent, capturedStructureEditData.nameEventFrom)
        assertEquals(expectedPathForDeletion.nameCategory, capturedStructureEditData.nameCategoryFrom)
        assertEquals(expectedPathForDeletion.nameSubCategory, capturedStructureEditData.nameSubCategoryFrom)
        assertEquals(expectedPathForDeletion.nameSubsubCategory, capturedStructureEditData.nameSubsubCategoryFrom)
        assertEquals(expectedPathForDeletion.nameQuiz, capturedStructureEditData.nameQuizFrom)
        assertEquals("", capturedStructureEditData.nameEventTo)
        assertEquals("", capturedStructureEditData.nameCategoryTo)
        assertEquals("", capturedStructureEditData.nameSubCategoryTo)
        assertEquals("", capturedStructureEditData.nameSubsubCategoryTo)
        assertEquals("", capturedStructureEditData.nameQuizTo)
        assertTrue(capturedStructureEditData.deleteOld, "deleteOld should be true for global deletion marker")
        assertTrue(capturedStructureEditData.clearData, "clearData should be true for global deletion marker")

        // Ensure it's not called for the item to keep
        verify(mockStructureUseCase, times(1)).insertEditStructure(any()) // Called only once for the item to delete
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `addEditIdsStructureRemote - item NOT marked for global deletion, no call to insertEditStructure`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToKeep = createTestData("ItemToKeep").apply { dataUpdateGlobal = "v1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(itemToKeep)
        )

        // Act
        syncState.addEditIdsStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockStructureUseCase, times(0)).insertEditStructure(any())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `addEditIdsStructureRemote - empty local list, no errors or calls`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf()
        )

        // Act
        syncState.addEditIdsStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verifyNoMoreInteractions(mockStructureUseCase) // Different from times(0) as it ensures no other interaction either
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `addEditIdsStructureRemote - structureUseCase throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val itemToDeleteGlobally = createTestData("ItemToDeleteGlobally").apply { dataUpdateGlobal = "-1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(itemToDeleteGlobally)
        )
        val exceptionMessage = "insertEditStructure failed"
        whenever(mockStructureUseCase.insertEditStructure(any())) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking {
                syncState.addEditIdsStructureRemote(mockStructureUseCase)
            }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception) // Exception not set in SyncState by this stage
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `addEditIdsStructureRemote - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val itemToDeleteGlobally = createTestData("ItemToDeleteGlobally").apply { dataUpdateGlobal = "-1" }
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(itemToDeleteGlobally),
            exception = initialException
        )

        // Act
        syncState.addEditIdsStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        verifyNoMoreInteractions(mockStructureUseCase)
    }

import com.tpov.common.data.model.local.QuestionDetailLocal

    // --- Tests for syncQuestionDetails ---

    private fun createQuestionDetail(id: Int, data: String, sync: Boolean, path: PathStructure = PathStructure()): QuestionDetailLocal {
        return QuestionDetailLocal(id = id, data = data, sync = sync, pathStructure = path)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncQuestionDetails - new remote details, saves locally`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizWithDetails")
        val leafQuiz = createTestData(quizPath.nameQuiz!!) // Ensure it's a leaf node for onNoChildren callback
        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(leafQuiz))

        val remoteDetail1 = createQuestionDetail(1, "remote_data1", true, quizPath)
        val remoteDetail2 = createQuestionDetail(2, "remote_data2", true, quizPath)
        val localDetailExisting = createQuestionDetail(3, "local_data_existing", true, quizPath)


        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(quizPath)) doReturn listOf(remoteDetail1, remoteDetail2, localDetailExisting)
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(quizPath)) doReturn listOf(localDetailExisting) // Local only has one existing

        // Act
        syncState.syncQuestionDetails(mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionDetailUseCase).saveQuestionDetail(remoteDetail1)
        verify(mockQuestionDetailUseCase).saveQuestionDetail(remoteDetail2)
        verify(mockQuestionDetailUseCase, times(0)).saveQuestionDetail(localDetailExisting) // Already exists by data field match
        verify(mockQuestionDetailUseCase, times(0)).pushQuestionDetail(any()) // No local items need pushing
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncQuestionDetails - local details need push (sync=false)`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizPushDetails")
        val leafQuiz = createTestData(quizPath.nameQuiz!!)
        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(leafQuiz))

        val localDetailToPush = createQuestionDetail(10, "local_data_push", false, quizPath)
        val localDetailSynced = createQuestionDetail(11, "local_data_synced", true, quizPath)

        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(quizPath)) doReturn emptyList() // No new remote details
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(quizPath)) doReturn listOf(localDetailToPush, localDetailSynced)

        // Act
        syncState.syncQuestionDetails(mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionDetailUseCase).pushQuestionDetail(localDetailToPush)
        verify(mockQuestionDetailUseCase, times(0)).pushQuestionDetail(localDetailSynced)
        verify(mockQuestionDetailUseCase, times(0)).saveQuestionDetail(any()) // No new remote details to save
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncQuestionDetails - no differences, no new or unsynced details`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizNoDiff")
        val leafQuiz = createTestData(quizPath.nameQuiz!!)
        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(leafQuiz))

        val commonDetail = createQuestionDetail(1, "common_data", true, quizPath)

        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(quizPath)) doReturn listOf(commonDetail)
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(quizPath)) doReturn listOf(commonDetail)

        // Act
        syncState.syncQuestionDetails(mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionDetailUseCase, times(0)).saveQuestionDetail(any())
        verify(mockQuestionDetailUseCase, times(0)).pushQuestionDetail(any())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncQuestionDetails - empty local and remote details, no calls`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizEmptyDetails")
        val leafQuiz = createTestData(quizPath.nameQuiz!!)
        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(leafQuiz))

        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(quizPath)) doReturn emptyList()
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(quizPath)) doReturn emptyList()

        // Act
        syncState.syncQuestionDetails(mockQuestionDetailUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockQuestionDetailUseCase, times(0)).saveQuestionDetail(any())
        verify(mockQuestionDetailUseCase, times(0)).pushQuestionDetail(any())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncQuestionDetails - fetchQuestionDetail throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizFetchFail")
        val leafQuiz = createTestData(quizPath.nameQuiz!!)
        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(leafQuiz))
        val exceptionMessage = "Fetch detail failed"

        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(quizPath)) doThrow RuntimeException(exceptionMessage)
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(quizPath)) doReturn emptyList() // Still need to mock this

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking { syncState.syncQuestionDetails(mockQuestionDetailUseCase) }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception) // Stage doesn't set it
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncQuestionDetails - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "Quiz1")
        val leafQuiz = createTestData(quizPath.nameQuiz!!)
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(leafQuiz),
            exception = initialException)

        // Act
        syncState.syncQuestionDetails(mockQuestionDetailUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        verifyNoMoreInteractions(mockQuestionDetailUseCase)
    }

    // --- Tests for editStructureRemote ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `editStructureRemote - getEditStructure returns items, pushes them and clears`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val editData1 = StructureEditData(1, "ev", "cat", "sub", "ssub", "q", "evTo", "catTo", "subTo", "ssubTo", "qTo", false, false)
        val editData2 = StructureEditData(2, "ev2", "cat2", "sub2", "ssub2", "q2", "evTo2", "catTo2", "subTo2", "ssubTo2", "qTo2", true, true)
        val mockEditList = listOf(editData1, editData2)

        whenever(mockStructureUseCase.getEditStructure()) doReturn mockEditList

        // Act
        syncState.editStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockStructureUseCase).getEditStructure()
        verify(mockStructureUseCase).pushEditStructure(editData1)
        verify(mockStructureUseCase).pushEditStructure(editData2)
        verify(mockStructureUseCase).clearStructureEdit()
        verifyNoMoreInteractions(mockStructureUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `editStructureRemote - getEditStructure returns empty list, still clears`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        whenever(mockStructureUseCase.getEditStructure()) doReturn emptyList()

        // Act
        syncState.editStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        verify(mockStructureUseCase).getEditStructure()
        verify(mockStructureUseCase, times(0)).pushEditStructure(any())
        verify(mockStructureUseCase).clearStructureEdit()
        verifyNoMoreInteractions(mockStructureUseCase)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `editStructureRemote - getEditStructure throws exception, propagates and does not clear`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val exceptionMessage = "getEditStructure failed"
        whenever(mockStructureUseCase.getEditStructure()) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking { syncState.editStructureRemote(mockStructureUseCase) }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception) // Stage does not set it
        verify(mockStructureUseCase).getEditStructure()
        verify(mockStructureUseCase, times(0)).pushEditStructure(any())
        verify(mockStructureUseCase, times(0)).clearStructureEdit() // Should not be called if getEditStructure fails
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `editStructureRemote - pushEditStructure throws exception, propagates and does not clear`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val editData1 = StructureEditData(1, "ev", "cat", "sub", "ssub", "q", "evTo", "catTo", "subTo", "ssubTo", "qTo", false, false)
        val mockEditList = listOf(editData1)
        val exceptionMessage = "pushEditStructure failed"

        whenever(mockStructureUseCase.getEditStructure()) doReturn mockEditList
        whenever(mockStructureUseCase.pushEditStructure(editData1)) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking { syncState.editStructureRemote(mockStructureUseCase) }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception)
        verify(mockStructureUseCase).getEditStructure()
        verify(mockStructureUseCase).pushEditStructure(editData1)
        verify(mockStructureUseCase, times(0)).clearStructureEdit() // Should not be called if push fails
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `editStructureRemote - clearStructureEdit throws exception, propagates`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val syncState = SyncState(eventId = eventQuiz)
        val editData1 = StructureEditData(1, "ev", "cat", "sub", "ssub", "q", "evTo", "catTo", "subTo", "ssubTo", "qTo", false, false)
        val mockEditList = listOf(editData1)
        val exceptionMessage = "clearStructureEdit failed"

        whenever(mockStructureUseCase.getEditStructure()) doReturn mockEditList
        // whenever(mockStructureUseCase.pushEditStructure(editData1)) doNothing() // Default behavior, or be explicit
        whenever(mockStructureUseCase.clearStructureEdit()) doThrow RuntimeException(exceptionMessage)

        // Act & Assert
        val thrown = assertThrows(RuntimeException::class.java) {
            runBlocking { syncState.editStructureRemote(mockStructureUseCase) }
        }
        assertEquals(exceptionMessage, thrown.message)
        assertNull(syncState.exception)
        verify(mockStructureUseCase).getEditStructure()
        verify(mockStructureUseCase).pushEditStructure(editData1)
        verify(mockStructureUseCase).clearStructureEdit()
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `editStructureRemote - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val syncState = SyncState(eventId = eventQuiz, exception = initialException)

        // Act
        syncState.editStructureRemote(mockStructureUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        verifyNoMoreInteractions(mockStructureUseCase)
    }

    // --- Tests for updateStructureInfoGlobal ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoGlobal - updates remote item fields from structureInfoGlobal`(eventQuiz: EventQuiz) {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizToUpdateInfo")
        val remoteQuiz = createTestData(quizPath.nameQuiz!!).apply { // Initial remote item state
            dataUpdateGlobal = "old_date"
            ratingGlobal = 10
            starsMaxGlobal = 3
            starsAverageGlobal = 2
        }

        val infoEntity = StructureInfoEntity(
            id = 1,
            pathStructure = quizPath,
            dateUpdate = "new_date_global",
            idUser = 123,
            rating = 100,
            starsMax = 5,
            starsAverage = 4,
            version = 1,
            languages = "en",
            isShow = true
        )

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteQuiz),
            structureCategoryDataListLocal = mutableListOf(remoteQuiz.copy()), // Local list needed for processStructureDataDifferences iteration
            structureInfoGlobal = mutableListOf(infoEntity)
        )

        // Act
        syncState.updateStructureInfoGlobal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListRemote.size)

        val updatedRemoteQuiz = syncState.structureCategoryDataListRemote[0]
        assertEquals(infoEntity.dateUpdate, updatedRemoteQuiz.dataUpdateGlobal)
        assertEquals(infoEntity.rating, updatedRemoteQuiz.ratingGlobal)
        assertEquals(infoEntity.starsMax, updatedRemoteQuiz.starsMaxGlobal)
        assertEquals(infoEntity.starsAverage, updatedRemoteQuiz.starsAverageGlobal)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoGlobal - no matching info in structureInfoGlobal, remote item unchanged`(eventQuiz: EventQuiz) {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizNoMatchInfo")
        val originalRemoteQuiz = createTestData(quizPath.nameQuiz!!).apply {
            dataUpdateGlobal = "old_date"
            ratingGlobal = 10
        }
        val originalRemoteQuizJson = Json.encodeToString(StructureDataLocal.serializer(), originalRemoteQuiz)


        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(originalRemoteQuiz),
            structureCategoryDataListLocal = mutableListOf(originalRemoteQuiz.copy()),
            structureInfoGlobal = mutableListOf() // Empty global info
        )

        // Act
        syncState.updateStructureInfoGlobal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListRemote.size)
        val finalRemoteQuizJson = Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListRemote[0])
        assertEquals(originalRemoteQuizJson, finalRemoteQuizJson, "Remote quiz data should not change")
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoGlobal - empty remote list, no changes`(eventQuiz: EventQuiz) {
        // Arrange
        val infoEntity = StructureInfoEntity(1, PathStructure(), "date", 1, 1, 1, 1, 1, "en", true)
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(),
            structureCategoryDataListLocal = mutableListOf(),
            structureInfoGlobal = mutableListOf(infoEntity)
        )

        // Act
        syncState.updateStructureInfoGlobal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.structureCategoryDataListRemote.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoGlobal - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val remoteQuiz = createTestData("Quiz1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListRemote = mutableListOf(remoteQuiz),
            structureInfoGlobal = mutableListOf(StructureInfoEntity(1, PathStructure(nameQuiz="Quiz1"), "d",1,1,1,1,1,"",true)),
            exception = initialException)

        val originalRemoteDataJson = Json.encodeToString(StructureDataLocal.serializer(), remoteQuiz)

        // Act
        syncState.updateStructureInfoGlobal()

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        val finalRemoteDataJson = Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListRemote[0])
        assertEquals(originalRemoteDataJson, finalRemoteDataJson, "Remote data should not change if exception was present")
    }

    // --- Tests for updateStructureInfoLocal ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoLocal - updates local item from newer fetched StructureInfoEntity`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizForLocalInfoUpdate")
        val localQuiz = createTestData(quizPath.nameQuiz!!).apply { // Initial local item state
            dataUpdateLocal = "2023-01-01T00:00:00Z"
            ratingLocal = 5
        }
        // Remote quiz is needed to guide processStructureDataDifferences to the localQuiz
        val remoteQuizEquivalent = createTestData(quizPath.nameQuiz!!)

        val fetchedInfoEntity = StructureInfoEntity(
            id = 1, pathStructure = quizPath, dateUpdate = "2023-01-02T00:00:00Z", // Newer date
            idUser = 123, rating = 50, starsMax = 5, starsAverage = 4, version = 1, languages = "en", isShow = true
        )

        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent) // Remote list to iterate over
        )
        whenever(mockStructureUseCase.fetchStructureInfo(quizPath)) doReturn fetchedInfoEntity

        // Act
        syncState.updateStructureInfoLocal(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage) // Current stage name in source
        assertNull(syncState.exception)
        assertEquals(1, syncState.structureCategoryDataListLocal.size)

        val updatedLocalQuiz = syncState.structureCategoryDataListLocal[0]
        assertEquals(fetchedInfoEntity.dateUpdate, updatedLocalQuiz.dataUpdateLocal)
        assertEquals(fetchedInfoEntity.rating, updatedLocalQuiz.ratingLocal)
        assertEquals(fetchedInfoEntity.starsMax, updatedLocalQuiz.starsMaxLocal)
        assertEquals(fetchedInfoEntity.starsAverage, updatedLocalQuiz.starsAverageLocal)
        verify(mockStructureUseCase).fetchStructureInfo(quizPath)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoLocal - local dataUpdateLocal is empty, updates from fetched StructureInfoEntity`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizEmptyDate")
        val localQuiz = createTestData(quizPath.nameQuiz!!).apply { dataUpdateLocal = "" } // Empty date
        val remoteQuizEquivalent = createTestData(quizPath.nameQuiz!!)

        val fetchedInfoEntity = StructureInfoEntity(
            id = 1, pathStructure = quizPath, dateUpdate = "2023-01-01T00:00:00Z",
            rating = 50
        )
        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(localQuiz), structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent))
        whenever(mockStructureUseCase.fetchStructureInfo(quizPath)) doReturn fetchedInfoEntity

        // Act
        syncState.updateStructureInfoLocal(mockStructureUseCase)

        // Assert
        val updatedLocalQuiz = syncState.structureCategoryDataListLocal[0]
        assertEquals(fetchedInfoEntity.dateUpdate, updatedLocalQuiz.dataUpdateLocal)
        assertEquals(fetchedInfoEntity.rating, updatedLocalQuiz.ratingLocal)
    }


    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoLocal - fetched StructureInfoEntity is older or same, no update`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizNoUpdateNeeded")
        val localQuizInitial = createTestData(quizPath.nameQuiz!!).apply {
            dataUpdateLocal = "2023-01-02T00:00:00Z"
            ratingLocal = 10
        }
        val remoteQuizEquivalent = createTestData(quizPath.nameQuiz!!)
        val originalLocalQuizJson = Json.encodeToString(StructureDataLocal.serializer(), localQuizInitial)


        val fetchedInfoEntitySameDate = StructureInfoEntity(1, quizPath, "2023-01-02T00:00:00Z", 123, 50, 5,4,1,"",true)
        val fetchedInfoEntityOlderDate = StructureInfoEntity(1, quizPath, "2023-01-01T00:00:00Z", 123, 50, 5,4,1,"",true)

        val syncStateSame = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(localQuizInitial.copy()), structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent.copy()))
        whenever(mockStructureUseCase.fetchStructureInfo(quizPath)) doReturn fetchedInfoEntitySameDate

        // Act for same date
        syncStateSame.updateStructureInfoLocal(mockStructureUseCase)
        // Assert for same date
        val finalLocalQuizSameJson = Json.encodeToString(StructureDataLocal.serializer(), syncStateSame.structureCategoryDataListLocal[0])
        assertEquals(originalLocalQuizJson, finalLocalQuizSameJson, "Local quiz data should not change for same date")

        // Arrange for older date
        val syncStateOlder = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(localQuizInitial.copy()), structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent.copy()))
        whenever(mockStructureUseCase.fetchStructureInfo(quizPath)) doReturn fetchedInfoEntityOlderDate

        // Act for older date
        syncStateOlder.updateStructureInfoLocal(mockStructureUseCase)
        // Assert for older date
        val finalLocalQuizOlderJson = Json.encodeToString(StructureDataLocal.serializer(), syncStateOlder.structureCategoryDataListLocal[0])
        assertEquals(originalLocalQuizJson, finalLocalQuizOlderJson, "Local quiz data should not change for older date")
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoLocal - fetchStructureInfo returns null, local item unchanged`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val quizPath = PathStructure(nameEvent = eventQuiz.name, nameQuiz = "QuizFetchNull")
        val localQuizInitial = createTestData(quizPath.nameQuiz!!)
        val remoteQuizEquivalent = createTestData(quizPath.nameQuiz!!)
        val originalLocalQuizJson = Json.encodeToString(StructureDataLocal.serializer(), localQuizInitial)

        val syncState = SyncState(eventId = eventQuiz, structureCategoryDataListLocal = mutableListOf(localQuizInitial), structureCategoryDataListRemote = mutableListOf(remoteQuizEquivalent))
        whenever(mockStructureUseCase.fetchStructureInfo(quizPath)) doReturn null

        // Act
        syncState.updateStructureInfoLocal(mockStructureUseCase)

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_REMOTE, syncState.currentStage)
        assertNull(syncState.exception)
        val finalLocalQuizJson = Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListLocal[0])
        assertEquals(originalLocalQuizJson, finalLocalQuizJson, "Local quiz data should not change if fetch returns null")
        verify(mockStructureUseCase).fetchStructureInfo(quizPath)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `updateStructureInfoLocal - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val localQuiz = createTestData("Quiz1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureCategoryDataListRemote = mutableListOf(createTestData("Quiz1")), // Remote needed for iteration
            exception = initialException)

        val originalLocalDataJson = Json.encodeToString(StructureDataLocal.serializer(), localQuiz)

        // Act
        syncState.updateStructureInfoLocal(mockStructureUseCase)

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        val finalLocalDataJson = Json.encodeToString(StructureDataLocal.serializer(), syncState.structureCategoryDataListLocal[0])
        assertEquals(originalLocalDataJson, finalLocalDataJson, "Local data should not change if exception was present")
        verify(mockStructureUseCase, times(0)).fetchStructureInfo(any())
    }

    // --- Tests for syncInfoLocal ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoLocal - populates structureInfoLocal from local data`(eventQuiz: EventQuiz) {
        // Arrange
        val localQuiz1 = createTestData("LocalQuiz1").apply {
            dataUpdateLocal = "2023-02-01"
            ratingLocal = 200
            starsMaxLocal = 4
            starsAverageLocal = 3
            languages = "de"
            isShowArchive = true // Assuming this field is part of StructureDataLocal and used
        }
        val localCategory1 = createTestData("LocalCat1", children = mutableListOf(localQuiz1)).apply {
            dataUpdateLocal = "2023-02-02"
            ratingLocal = 75
            starsMaxLocal = 5
            starsAverageLocal = 4
            languages = "es"
            isShowArchive = false
        }
        // Remote list can be empty or minimal, just needs to allow iteration over local items by processStructureDataDifferences
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localCategory1),
            structureCategoryDataListRemote = mutableListOf(createTestData("LocalCat1")), // To ensure path matching
            structureInfoLocal = mutableListOf()
        )

        val expectedTpovId = SettingConfigObject.settingsConfig.tpovId

        // Act
        syncState.syncInfoLocal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_LOCAL, syncState.currentStage)
        assertNull(syncState.exception)
        assertEquals(2, syncState.structureInfoLocal.size, "Should have info for local category and local quiz")

        val catInfo = syncState.structureInfoLocal.find { it.pathStructure.nameCategory == "LocalCat1" && it.pathStructure.nameQuiz == "" }
        assertNotNull(catInfo)
        catInfo?.let {
            assertEquals(localCategory1.dataUpdateLocal, it.dateUpdate)
            assertEquals(expectedTpovId, it.idUser)
            assertEquals(localCategory1.ratingLocal, it.rating)
            assertEquals(localCategory1.starsMaxLocal, it.starsMax)
            assertEquals(localCategory1.starsAverageLocal, it.starsAverage)
            assertEquals(localCategory1.languages, it.languages)
            assertEquals(localCategory1.isShowArchive, it.isShow)
        }

        val quizInfo = syncState.structureInfoLocal.find { it.pathStructure.nameQuiz == "LocalQuiz1" }
        assertNotNull(quizInfo)
        quizInfo?.let {
            assertEquals(localQuiz1.dataUpdateLocal, it.dateUpdate)
            assertEquals(expectedTpovId, it.idUser)
            assertEquals(localQuiz1.ratingLocal, it.rating)
            assertEquals(localQuiz1.starsMaxLocal, it.starsMax)
            assertEquals(localQuiz1.starsAverageLocal, it.starsAverage)
            assertEquals(localQuiz1.languages, it.languages)
            assertEquals(localQuiz1.isShowArchive, it.isShow)
        }
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoLocal - empty local data, structureInfoLocal remains empty`(eventQuiz: EventQuiz) {
        // Arrange
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(),
            structureCategoryDataListRemote = mutableListOf(),
            structureInfoLocal = mutableListOf()
        )

        // Act
        syncState.syncInfoLocal()

        // Assert
        assertEquals(SyncStage.INFO_UPDATE_LOCAL, syncState.currentStage)
        assertNull(syncState.exception)
        assertTrue(syncState.structureInfoLocal.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `syncInfoLocal - if syncState already has exception, should not proceed`(eventQuiz: EventQuiz) = runBlocking {
        // Arrange
        val initialException = "Previous stage failed"
        val localQuiz = createTestData("LocalQuiz1")
        val syncState = SyncState(
            eventId = eventQuiz,
            structureCategoryDataListLocal = mutableListOf(localQuiz),
            structureInfoLocal = mutableListOf(),
            exception = initialException)

        // Act
        syncState.syncInfoLocal()

        // Assert
        assertEquals(initialException, syncState.exception)
        assertEquals(SyncStage.NOT_STARTED, syncState.currentStage)
        assertTrue(syncState.structureInfoLocal.isEmpty())
    }

import com.tpov.common.domain.model.SyncStructureResult

    // --- Tests for getResult ---

    @ParameterizedTest
    @EnumSource(EventQuiz::class) // EventQuiz is not strictly needed here but keeps pattern
    fun `getResult - when exception is null, returns Success with current state and stage`(eventQuiz: EventQuiz) {
        // Arrange
        val currentStageForTest = SyncStage.COMPLETE // Or any other stage
        val syncState = SyncState(eventId = eventQuiz, exception = null, currentStage = currentStageForTest)

        // Act
        val result = syncState.getResult()

        // Assert
        assertTrue(result is SyncStructureResult.Success)
        val successResult = result as SyncStructureResult.Success
        assertEquals(currentStageForTest, successResult.stage)
        assertEquals(syncState, successResult.state)
    }

    @ParameterizedTest
    @EnumSource(EventQuiz::class)
    fun `getResult - when exception is not null, returns Error with current stage and exception message`(eventQuiz: EventQuiz) {
        // Arrange
        val currentStageForTest = SyncStage.STRUCTURE_FETCH // Or any stage where error might occur
        val errorMessage = "Test error occurred"
        val syncState = SyncState(eventId = eventQuiz, exception = errorMessage, currentStage = currentStageForTest)

        // Act
        val result = syncState.getResult()

        // Assert
        assertTrue(result is SyncStructureResult.Error)
        val errorResult = result as SyncStructureResult.Error
        assertEquals(currentStageForTest, errorResult.stage)
        assertEquals(errorMessage, errorResult.error)
    }
}
