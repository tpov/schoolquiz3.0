package com.tpov.common

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class StructureUseCaseTest {
    @Mock
    private lateinit var repositoryStructureImpl: RepositoryStuctureImpl
    @Mock
    private lateinit var repositoryQuestionImpl: RepositoryQuestionImpl
    private lateinit var structureUseCase: StructureUseCase

    // Структуры
    private val localStructure = listOf(
        StructureDataEntity(
            nameItem = "Local Category",
            dataUpdate = "1000",
            isShowDownload = true,
            childes = listOf(
                StructureDataEntity(
                    nameItem = "Local Quiz",
                    dataUpdate = "1000",
                    isShowDownload = true,
                    childes = emptyList()
                )
            )
        )
    )

    private val remoteStructure = listOf(
        StructureDataRemote(
            nameItem = "Remote Category",
            dataUpdate = "2000",
            childes = listOf(
                StructureDataRemote(
                    nameItem = "Remote Quiz",
                    dataUpdate = "2000",
                    childes = emptyList()
                )
            )
        )
    )

    private val expectedLocalStructure = listOf(
        StructureDataEntity(
            nameItem = "Expected Local Category",
            dataUpdate = "3000",
            isShowDownload = true,
            childes = listOf(
                StructureDataEntity(
                    nameItem = "Expected Local Quiz",
                    dataUpdate = "3000",
                    isShowDownload = true,
                    childes = emptyList()
                )
            )
        )
    )

    private val expectedRemoteStructure = listOf(
        StructureDataRemote(
            nameItem = "Expected Remote Category",
            dataUpdate = "3000",
            childes = listOf(
                StructureDataRemote(
                    nameItem = "Expected Remote Quiz",
                    dataUpdate = "3000",
                    childes = emptyList()
                )
            )
        )
    )

    // Вопросы
    private val localQuestions = listOf(
        QuestionEntity(
            id = 1,
            question = "Local Question",
            answer = "Local Answer",
            dataUpdate = "1000"
        )
    )

    private val remoteQuestions = listOf(
        QuestionRemote(
            id = 1,
            question = "Remote Question",
            answer = "Remote Answer",
            dataUpdate = "2000"
        )
    )

    private val expectedLocalQuestions = listOf(
        QuestionEntity(
            id = 1,
            question = "Expected Local Question",
            answer = "Expected Local Answer",
            dataUpdate = "3000"
        )
    )

    private val expectedRemoteQuestions = listOf(
        QuestionRemote(
            id = 1,
            question = "Expected Remote Question",
            answer = "Expected Remote Answer",
            dataUpdate = "3000"
        )
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        structureUseCase = StructureUseCase(repositoryStructureImpl, repositoryQuestionImpl)
        setupMocks()
    }

    private fun setupMocks() = runBlocking {
        // Настройка моков для структур
        `when`(repositoryStructureImpl.getStructureData(1, -1)).thenReturn(
            StructureCategoryEntity(childes = localStructure)
        )
        `when`(repositoryStructureImpl.fetchStructureData(1)).thenReturn(remoteStructure)

        // Настройка моков для вопросов
        `when`(repositoryQuestionImpl.getQuestions()).thenReturn(localQuestions)
        `when`(repositoryQuestionImpl.fetchQuestion(any(), eq("en"))).thenReturn(remoteQuestions)
    }

    @Test
    fun `test full synchronization process`() = runBlocking {
        // Выполнение синхронизации
        val changes = structureUseCase.syncStructureDataAndQuestions()

        // Проверка синхронизации структур
        verify(repositoryStructureImpl).pushStructureData(
            argThat { structure ->
                structure.nameItem == expectedLocalStructure[0].nameItem &&
                        structure.dataUpdate == expectedLocalStructure[0].dataUpdate
            },
            eq(0)
        )

        // Проверка синхронизации вопросов
        verify(repositoryQuestionImpl).deleteQuestionByIdQuiz()
        verify(repositoryQuestionImpl).fetchQuestion(any(), eq("en"))

        // Проверка финального состояния структур
        val finalLocalStructure = repositoryStructureImpl.getStructureData(1, -1)
        val finalRemoteStructure = repositoryStructureImpl.fetchStructureData(1)

        assert(finalLocalStructure?.childes == expectedLocalStructure) {
            "Local structure should match expected state"
        }
        assert(finalRemoteStructure == expectedRemoteStructure) {
            "Remote structure should match expected state"
        }

        // Проверка финального состояния вопросов
        val finalLocalQuestions = repositoryQuestionImpl.getQuestions()
        val finalRemoteQuestions = repositoryQuestionImpl.fetchQuestion(
            PathStructure(1, 0, -1, -1, -1),
            "en"
        )

        assert(finalLocalQuestions == expectedLocalQuestions) {
            "Local questions should match expected state"
        }
        assert(finalRemoteQuestions == expectedRemoteQuestions) {
            "Remote questions should match expected state"
        }
    }

    @Test
    fun `test structure state transitions`() = runBlocking {
        val initialLocal = repositoryStructureImpl.getStructureData(1, -1)
        val initialRemote = repositoryStructureImpl.fetchStructureData(1)

        structureUseCase.syncStructureDataAndQuestions()

        val finalLocal = repositoryStructureImpl.getStructureData(1, -1)
        val finalRemote = repositoryStructureImpl.fetchStructureData(1)

        assert(initialLocal?.childes == localStructure) { "Initial local structure incorrect" }
        assert(initialRemote == remoteStructure) { "Initial remote structure incorrect" }
        assert(finalLocal?.childes == expectedLocalStructure) { "Final local structure incorrect" }
        assert(finalRemote == expectedRemoteStructure) { "Final remote structure incorrect" }
    }

    @Test
    fun `test questions state transitions`() = runBlocking {
        // Проверка переходов состояний вопросов
        val initialLocal = repositoryQuestionImpl.getQuestions()
        val initialRemote = repositoryQuestionImpl.fetchQuestion(
            PathStructure(1, 0, -1, -1, -1),
            "en"
        )

        structureUseCase.syncStructureDataAndQuestions()

        val finalLocal = repositoryQuestionImpl.getQuestions()
        val finalRemote = repositoryQuestionImpl.fetchQuestion(
            PathStructure(1, 0, -1, -1, -1),
            "en"
        )

        assert(initialLocal == localQuestions) { "Initial local questions incorrect" }
        assert(initialRemote == remoteQuestions) { "Initial remote questions incorrect" }
        assert(finalLocal == expectedLocalQuestions) { "Final local questions incorrect" }
        assert(finalRemote == expectedRemoteQuestions) { "Final remote questions incorrect" }
    }