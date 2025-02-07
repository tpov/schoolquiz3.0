package com.tpov.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
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
class StructureSyncIntegrationTest {

    @Mock
    private lateinit var repositoryStructureImpl: RepositoryStuctureImpl

    @Mock
    private lateinit var repositoryQuestionImpl: RepositoryQuestionImpl

    private lateinit var structureUseCase: StructureUseCase
    val lang = "en"
    val path = PathStructure(
        idEvent = 1,
        idCategory = -1,
        idSubCategory = -1,
        idSubsubCategory = -1,
        idQuiz = -1
    )

    // Данные, загружаемые из JSON-файлов
    private lateinit var inputStructureLocal: MutableList<StructureDataLocal?>
    private lateinit var inputStructureRemote: MutableList<StructureDataLocal>
    private lateinit var inputQuestionLocal: MutableList<QuestionEntity?>
    private lateinit var inputQuestionRemote: MutableList<QuestionEntity>
    private lateinit var expectedLocalOutput: MutableList<StructureDataLocal>
    private lateinit var expectedRemoteOutput: MutableList<StructureDataLocal>

    private val gson = Gson()

    @Before
    fun setup() {

        MockitoAnnotations.openMocks(this)
        structureUseCase = StructureUseCase(repositoryStructureImpl, repositoryQuestionImpl)

        inputStructureLocal =
            loadJson<StructureDataLocal>("MockStructureDataInputLocal.json").toMutableList()
        inputStructureRemote =
            loadJson<StructureDataLocal>("MockStructureDataInputRemote.json").toMutableList()
        inputQuestionLocal = loadJson<QuestionEntity>("MockQuestionInputLocal.json").toMutableList()
        inputQuestionRemote =
            loadJson<QuestionEntity>("MockQuestionOutputLocal.json").toMutableList()
        expectedLocalOutput =
            loadJson<StructureDataLocal>("MockStructureDataOutputLocal.json").toMutableList()
        expectedRemoteOutput =
            loadJson<StructureDataLocal>("MockStructureDataOutputRemote.json").toMutableList()

        filledInfoData()

        runBlocking {
            `when`(repositoryStructureImpl.getStructureData(1, -1))
                .thenReturn(StructureDataLocal(children = inputStructureLocal.toMutableList()))

            `when`(repositoryStructureImpl.fetchStructureDataList(1))
                .thenReturn(inputStructureRemote.toMutableList())

            `when`(repositoryQuestionImpl.fetchQuestion(path, lang))
                .thenReturn(getQuestionByPath(path, inputQuestionRemote))
        }
    }

    private fun getQuestionByPath(
        pathStructure: PathStructure,
        questionList: List<QuestionEntity>
    ): List<QuestionEntity> {
        return questionList.filter {
            it.idCategory == pathStructure.idCategory
                    && it.idSubCategory == pathStructure.idSubCategory
                    && it.idSubsubCategory == pathStructure.idSubsubCategory
                    && it.idQuiz == pathStructure.idQuiz
        }
    }

    @Test
    fun `test syncStructureDataAndQuestions updates models as expected`() = runBlocking<Unit> {
        val eventId = 1

        `when`(repositoryStructureImpl.getStructureData(eventId, -1))
            .thenReturn(StructureDataLocal(children = inputStructureLocal))
        `when`(repositoryStructureImpl.fetchStructureDataList(eventId))
            .thenReturn(inputStructureRemote)

        val changes = structureUseCase.syncStructureDataAndQuestions(eventId)

        val captorStructure = argumentCaptor<StructureDataLocal>()

        verify(repositoryStructureImpl, timeout(5000))
            .saveStructureData(captorStructure.capture(), eq(eventId))

        val savedStructure = captorStructure.firstValue
        assertNotNull("Сохраненная структура не должна быть null", savedStructure)
        assertNotNull("Дети структуры не должны быть null", savedStructure.children)

        assertEquals(
            "Синхронизация данных не совпадает",
            expectedRemoteOutput,
            savedStructure.children
        )

        val captorQuestion = ArgumentCaptor.forClass(QuestionEntity::class.java)
        verify(repositoryQuestionImpl, atLeastOnce()).saveQuestion(captorQuestion.capture())
        assertTrue("Должны быть сохранены вопросы", captorQuestion.allValues.isNotEmpty())
    }


    private fun updateCategoryWithLocalInfo(
        remoteCategory: StructureDataLocal,
        localCategory: StructureDataLocal?
    ): StructureDataLocal {
        if (localCategory == null) return remoteCategory

        return remoteCategory.copy(
            ratingLocal = localCategory.ratingLocal,
            starsMaxLocal = localCategory.starsMaxLocal,
            starsAverageLocal = localCategory.starsAverageLocal,
            isShowDownload = localCategory.isShowDownload,
            numHQ = localCategory.numHQ,
            numQ = localCategory.numQ,
            children = remoteCategory.children?.mapIndexed { i, remoteChild ->
                updateCategoryWithLocalInfo(
                    remoteCategory = remoteChild ?: return@mapIndexed null,
                    localCategory = localCategory.children?.getOrNull(i)
                )
            }?.toMutableList()
        )
    }

    private fun filledInfoData() {
        inputStructureRemote = inputStructureRemote.mapIndexed { index, categoryRemote ->
            val updatedCategory = updateCategoryWithLocalInfo(
                categoryRemote, inputStructureLocal.getOrNull(index)
            )
            updatedCategory
        }.toMutableList()
    }

    /**
     * Обобщённая функция для загрузки списка объектов из JSON-файла.
     */
    private inline fun <reified T> loadJson(filename: String): MutableList<T> {
        val json = this::class.java.classLoader?.getResource(filename)?.readText()
            ?: throw IllegalStateException("Файл $filename не найден")
        StructureUseCase.Log.d("loadJson", "json: $json")
        val type: Type = object : TypeToken<MutableList<T>>() {}.type
        return gson.fromJson(json, type)
    }
}
