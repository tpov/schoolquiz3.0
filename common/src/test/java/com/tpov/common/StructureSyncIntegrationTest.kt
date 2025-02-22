package com.tpov.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.repository.RepositoryException
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.StructureDataUtils.findStructureDataOld
import com.tpov.common.domain.utils.StructureDataUtils.updateNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Type

/**
 * Пример интеграционного теста, который:
 * 1. Загружает тестовые данные из JSON (локальные и удалённые).
 * 2. Настраивает моки репозиториев с помощью тестовых данных.
 * 3. Вызывает функцию синхронизации.
 * 4. Проверяет, что итоговые (синхронизированные) модели совпадают с ожидаемыми.
 */
@RunWith(RobolectricTestRunner::class)
class StructureSyncTest {
    @Mock
    private lateinit var repositoryStructureImpl: RepositoryStuctureImpl

    @Mock
    private lateinit var repositoryQuestionImpl: RepositoryQuestionImpl

    @Mock
    private lateinit var repositoryException: RepositoryException

    @Mock
    private lateinit var interactor: Interactor

    private lateinit var structureUseCase: StructureUseCase

    private lateinit var expectedOutputLocal: List<StructureDataLocal>
    private lateinit var inputDataLocal: List<StructureDataLocal>
    private lateinit var expectedOutputRemote: List<StructureDataLocal>
    private lateinit var inputDataRemote: List<StructureDataLocal>
    private val gson = Gson()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        structureUseCase = StructureUseCase(
            repositoryStructureImpl,
            repositoryQuestionImpl,
            repositoryException,
            interactor
        )

        inputDataLocal = loadJson<StructureDataLocal>("MockStructureDataInputLocal.json")
        expectedOutputLocal = loadJson<StructureDataLocal>("MockStructureDataOutputLocal.json")
        inputDataRemote = loadJson<StructureDataLocal>("MockStructureDataInputRemote.json")
        expectedOutputRemote = loadJson<StructureDataLocal>("MockStructureDataOutputRemote.json")

        runBlocking {
            `when`(repositoryStructureImpl.getStructureEventData(1))
                .thenReturn(inputDataLocal)
            `when`(repositoryStructureImpl.fetchStructureCategoryDataList(1))
                .thenReturn(inputDataRemote)
        }
    }

    @Test
    fun `test full sync process`() = runBlocking {
        val eventId = 1
        val result = structureUseCase.syncStructureDataAndQuestions(eventId)

        inputDataLocal.map { it.printFullStructure("inputDataLocal") }
        inputDataRemote.map { it.printFullStructure("inputDataRemote") }
        expectedOutputLocal.map { it.printFullStructure("expectedOutputLocal") }

        when (result) {
            is SyncStructureResult.Success -> {
                result.state.structureCategoryDataListLocal.map { it.printFullStructure("result") }
               result.state.structureInfoRemote.forEach {
                   StructureUseCase.Log.d("TestDebug", "structureInfoRemote: ${it}")
                   val oldStructureData = findStructureDataOld(
                        result.state.structureCategoryDataListRemote,
                        result.state.structureCategoryDataListLocal,
                        it.pathStructure
                    )

                   if (oldStructureData.structureData != null)result.state.structureCategoryDataListRemote.updateNode(oldStructureData.structureData!!, oldStructureData.pathOld)

                   result.state.structureCategoryDataListLocal.map { it.printFullStructure("result updateNode") }
               }

                result.state.structureInfoLocal.forEach {
                    val oldStructureData = findStructureDataOld(
                        result.state.structureCategoryDataListLocal,
                        result.state.structureCategoryDataListRemote,
                        it.pathStructure
                    )
                    if (oldStructureData.structureData != null)result.state.structureCategoryDataListLocal.updateNode(oldStructureData.structureData!!, oldStructureData.pathOld)

                    result.state.structureCategoryDataListLocal.map { it.printFullStructure("result structureInfoLocal updateNode") }
                }

            }

            is SyncStructureResult.Error -> StructureUseCase.Log.d(
                "TestDebug",
                "Sync result: ${result.stage}"
            )

        }

        assertTrue(result is SyncStructureResult.Success)
        testInputStructureDataLocal(result)
    }

    private fun testInputStructureDataLocal(result: SyncStructureResult) {

        when (result) {
            is SyncStructureResult.Success -> {
                result.state.structureCategoryDataListLocal.map { it.printFullStructure("result Success") }

                assertEquals(expectedOutputLocal, result.state.structureCategoryDataListLocal)
            }

            is SyncStructureResult.Error -> StructureUseCase.Log.d(
                "TestDebug",
                "Sync result: ${result.stage}"
            )
        }
    }

    private inline fun <reified T> loadJson(filename: String): MutableList<T> {
        val json = this::class.java.classLoader?.getResource(filename)?.readText()
            ?: throw IllegalStateException("Файл $filename не найден")
        val type: Type = object : TypeToken<MutableList<T>>() {}.type
        return gson.fromJson(json, type)
    }
}

data class TestData(
    val local: StructureDataLocal,
    val remote: List<StructureDataLocal>,
    val expectedOutput: SyncState
)