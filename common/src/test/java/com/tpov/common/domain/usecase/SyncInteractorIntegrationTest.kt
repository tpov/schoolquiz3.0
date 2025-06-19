package com.tpov.common.domain.usecase

import com.tpov.common.ExceptionInteractor
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class SyncInteractorIntegrationTest {

    @Mock
    lateinit var mockSettingServerDBUseCase: SettingServerDBUseCase
    @Mock
    lateinit var mockSettingLocalDBUseCase: SettingLocalDBUseCase
    @Mock
    lateinit var mockStructureUseCase: StructureUseCase
    @Mock
    lateinit var mockQuestionUseCase: QuestionUseCase
    @Mock
    lateinit var mockQuestionDetailUseCase: QuestionDetailUseCase
    @Mock
    lateinit var mockExceptionInteractor: ExceptionInteractor // Real one has TODOs

    private lateinit var syncInteractor: SyncInteractor

    private lateinit var originalSettingsConfig: com.tpov.common.data.model.SettingConfigModel

    @BeforeEach
    fun setUp() {
        syncInteractor = SyncInteractor(
            mockSettingServerDBUseCase,
            mockSettingLocalDBUseCase,
            mockStructureUseCase,
            mockQuestionUseCase,
            mockQuestionDetailUseCase
        )

        // Setup SettingConfigObject for consistent testable values
        originalSettingsConfig = SettingConfigObject.settingsConfig
        SettingConfigObject.updateSettings(
            originalSettingsConfig.copy(
                tpovId = 123, // Test User ID
                languages = listOf(LanguageUtils.ENGLISH) // Test Language
            )
        )

        // Initialize StructureDataExtention's exceptionHandler
        // (as SyncInteractor internally initializes it for SyncState)
        val domainExceptions = com.tpov.common.domain.DomainExceptions(
            beforeException = {},
            afterException = {},
            interactor = mockExceptionInteractor
        )
        StructureDataExtention.init(domainExceptions)
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        SettingConfigObject.updateSettings(originalSettingsConfig)
    }

    private fun createStructureData(name: String, version: Int = 0, dataUpdate: String = "", children: MutableList<StructureDataLocal>? = null): StructureDataLocal {
        return StructureDataLocal(
            nameItem = name,
            version = version,
            dataUpdateGlobal = dataUpdate,
            dataUpdateLocal = dataUpdate,
            children = children ?: mutableListOf()
        )
    }

    @Test
    fun `syncQuizes - successful fresh sync for QUIZ_HOME`() = runBlocking {
        // Arrange
        val event = EventQuiz.QUIZ_HOME
        val remoteCategory = createStructureData("RemoteCat1", children = mutableListOf(createStructureData("RemoteQuiz1")))

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(event)) doReturn mutableListOf(remoteCategory)
        whenever(mockStructureUseCase.getStructureEventData(event)) doReturn mutableListOf() // No local data

        // Mock calls expected during the QUESTION_CHANGE_LIST and updateLocalQuestion stages for "RemoteQuiz1"
        val quizPath = PathStructure(nameEvent = event.name, nameCategory = "RemoteCat1", nameQuiz = "RemoteQuiz1")
        val fetchedQuestion = QuestionLocal(id = 1, nameQuestion = "Fetched Q1", pathStructure = quizPath)
        whenever(mockQuestionUseCase.fetchQuestion(eq(quizPath), any())) doReturn arrayListOf(fetchedQuestion)
        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(eq(quizPath))) doReturn emptyList()
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(eq(quizPath))) doReturn emptyList()


        // Act
        val result = syncInteractor.syncQuizes(event, mockExceptionInteractor)

        // Assert
        assertTrue(result is SyncStructureResult.Success, "Sync should be successful. Error: ${(result as? SyncStructureResult.Error)?.error}")
        val successResult = result as SyncStructureResult.Success
        assertNotNull(successResult.state.structureCategoryDataListLocal.find { it.nameItem == "RemoteCat1" })
        assertNotNull(successResult.state.structureCategoryDataListLocal.find { it.nameItem == "RemoteCat1" }
            ?.children?.find { it.nameItem == "RemoteQuiz1" })

        verify(mockStructureUseCase).fetchStructureCategoryDataList(event)
        verify(mockStructureUseCase).getStructureEventData(event)
        verify(mockQuestionUseCase).fetchQuestion(eq(quizPath), any())
        verify(mockQuestionUseCase).insertQuestion(argThat { this.nameQuestion == "Fetched Q1" && this.pathStructure.nameQuiz == "RemoteQuiz1"})
        // Add more verifications as necessary for other use case interactions based on stage logic
    }

    @Test
    fun `syncQuizes - error during QuestionUseCase fetchQuestion, returns Error result`() = runBlocking {
        // Arrange
        val event = EventQuiz.QUIZ_HOME
        val remoteCategory = createStructureData("RemoteCat1", children = mutableListOf(createStructureData("RemoteQuiz1")))
        val errorMessage = "Network failed during question fetch"

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(event)) doReturn mutableListOf(remoteCategory)
        whenever(mockStructureUseCase.getStructureEventData(event)) doReturn mutableListOf()

        val quizPath = PathStructure(nameEvent = event.name, nameCategory = "RemoteCat1", nameQuiz = "RemoteQuiz1")
        whenever(mockQuestionUseCase.fetchQuestion(eq(quizPath), any())) doThrow RuntimeException(errorMessage)

        // Act
        val result = syncInteractor.syncQuizes(event, mockExceptionInteractor)

        // Assert
        assertTrue(result is SyncStructureResult.Error)
        val errorResult = result as SyncStructureResult.Error
        assertTrue(errorResult.error.contains(errorMessage) || errorResult.error.contains("Error during structure fetch")) // The internal handler might wrap it

        // Verify that an attempt was made to fetch questions
        verify(mockStructureUseCase).fetchStructureCategoryDataList(event)
        verify(mockQuestionUseCase).fetchQuestion(eq(quizPath), any())
        // Verify that subsequent operations like insertQuestion were likely not called
        verify(mockQuestionUseCase, times(0)).insertQuestion(any())
    }

    @Test
    fun `syncQuizes - QUIZ_BY_USER - local changes are pushed`() = runBlocking {
        // Arrange
        val event = EventQuiz.QUIZ_BY_USER

        // Local data has a new quiz not present in remote
        val localOnlyQuiz = createStructureData("LocalOnlyQuiz", version = 1, dataUpdate = "local_v1")
        localOnlyQuiz.dataUpdateLocal = "local_v1" // Mark it as a local state
        val localCategory = createStructureData("UserCat1", children = mutableListOf(localOnlyQuiz))

        // Remote data is empty or different
        val remoteCategoryEquivalent = createStructureData("UserCat1") // Same category name, but no "LocalOnlyQuiz"

        whenever(mockStructureUseCase.fetchStructureCategoryDataList(event)) doReturn mutableListOf(remoteCategoryEquivalent) // Remote has category but not the new quiz
        whenever(mockStructureUseCase.getStructureEventData(event)) doReturn mutableListOf(localCategory) // Local has the new quiz

        val pathForLocalOnlyQuiz = PathStructure(nameEvent = event.name, nameCategory = "UserCat1", nameQuiz = "LocalOnlyQuiz")
        val localQuestionToPush = QuestionLocal(id = 100, nameQuestion = "Local Q to Push", pathStructure = pathForLocalOnlyQuiz)

        // When updateRemoteQuestion stage runs for LocalOnlyQuiz (isCreate=true)
        whenever(mockQuestionUseCase.getQuestionByPath(pathForLocalOnlyQuiz)) doReturn arrayListOf(localQuestionToPush)
        // For other stages that might run on "UserCat1" if it's treated as a leaf for some operations by processStructureDataDifferences
        whenever(mockQuestionUseCase.getQuestionByPath(PathStructure(nameEvent = event.name, nameCategory = "UserCat1"))) doReturn arrayListOf()
        whenever(mockQuestionUseCase.fetchQuestion(any(), any())) doReturn arrayListOf() // Default for fetches not specifically mocked
        whenever(mockQuestionDetailUseCase.fetchQuestionDetail(any())) doReturn emptyList()
        whenever(mockQuestionDetailUseCase.getQuestionDetailByPath(any())) doReturn emptyList()


        // Act
        val result = syncInteractor.syncQuizes(event, mockExceptionInteractor)

        // Assert
        assertTrue(result is SyncStructureResult.Success, "Sync should be successful. Error: ${(result as? SyncStructureResult.Error)?.error}")

        // Verify that changedListQuestionRemote was populated for the new local quiz
        // This happens in syncChangeListQuestionsRemote stage
        // Then updateRemoteQuestion stage should call getQuestionByPath and pushQuestion

        verify(mockStructureUseCase).fetchStructureCategoryDataList(event)
        verify(mockStructureUseCase).getStructureEventData(event)

        // Verification for updateRemoteQuestion stage
        verify(mockQuestionUseCase).getQuestionByPath(pathForLocalOnlyQuiz)
        verify(mockQuestionUseCase).pushQuestion(localQuestionToPush)

        // Verify no deletion attempt for the new quiz on remote
        verify(mockQuestionUseCase, times(0)).deleteQuestionByPath(pathForLocalOnlyQuiz)
    }

    // TODO: Add test for error during StructureUseCase.insertEditStructure (if applicable for QUIZ_BY_USER scenario)
    // TODO: Add test for local item marked for deletion (dataUpdateLocal = "-1") for QUIZ_BY_USER
}
