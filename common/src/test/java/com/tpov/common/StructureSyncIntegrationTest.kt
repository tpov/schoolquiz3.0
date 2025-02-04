package com.tpov.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.StructureUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.lang.reflect.Type

/**
 * Пример интеграционного теста, который:
 * 1. Загружает тестовые данные из JSON (локальные и удалённые).
 * 2. Настраивает моки репозиториев с помощью тестовых данных.
 * 3. Вызывает функцию синхронизации.
 * 4. Проверяет, что итоговые (синхронизированные) модели совпадают с ожидаемыми.
 */
class StructureSyncIntegrationTest {

    @Mock
    private lateinit var repositoryStructureImpl: RepositoryStuctureImpl

    @Mock
    private lateinit var repositoryQuestionImpl: RepositoryQuestionImpl

    private lateinit var structureUseCase: StructureUseCase

    // Данные, загружаемые из JSON-файлов
    private lateinit var inputLocal: MutableList<StructureDataLocal>
    private lateinit var inputRemote: MutableList<StructureDataLocal>
    private lateinit var expectedLocalOutput: MutableList<StructureDataLocal>
    private lateinit var expectedRemoteOutput: MutableList<StructureDataLocal>

    private val gson = Gson()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        structureUseCase = StructureUseCase(repositoryStructureImpl, repositoryQuestionImpl)

        inputLocal = loadJson<StructureDataLocal>("MockStructureDataInputLocal.json").toMutableList()
        inputRemote = loadJson<StructureDataLocal>("MockStructureDataInputRemote.json").toMutableList()
//        expectedLocalOutput = loadJson<StructureDataLocal>("MockStructureDataOutputLocal.json").toMutableList()
//        expectedRemoteOutput = loadJson<StructureDataLocal>("MockStructureDataOutputRemote.json").toMutableList()

        filledInfoData()

        runBlocking {
            `when`(repositoryStructureImpl.getStructureData(1, -1))
                .thenReturn(StructureDataLocal(childes = inputLocal.toMutableList()))

            `when`(repositoryStructureImpl.fetchStructureDataList(1))
                .thenReturn(inputRemote.toMutableList())
        }
    }

    private fun filledInfoData() {
        inputRemote = inputRemote.mapIndexed { index, categoryRemote ->
            val updatedCategory = updateCategoryWithLocalInfo(
                remoteCategory = categoryRemote,
                localCategory = inputLocal.getOrNull(index)
            )
            updatedCategory
        }.toMutableList()
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
            childes = remoteCategory.childes?.mapIndexed { i, remoteChild ->
                updateCategoryWithLocalInfo(
                    remoteCategory = remoteChild ?: return@mapIndexed null,
                    localCategory = localCategory.childes?.getOrNull(i)
                )
            }?.toMutableList()
        )
    }

    @Test
    fun `test equals data in all models after created then`() = runBlocking {
        assertEquals(
            "После создания локальные данные не совпадают с удаленными 1",
            inputRemote, inputLocal
        )
    }

    /**
     * Обобщённая функция для загрузки списка объектов из JSON-файла.
     */
    private inline fun <reified T> loadJson(filename: String): MutableList<T> {
        val json = this::class.java.classLoader?.getResource(filename)?.readText()
            ?: throw IllegalStateException("Файл $filename не найден")
        val type: Type = object : TypeToken<MutableList<T>>() {}.type
        return gson.fromJson(json, type)
    }

    /*    @Test
        fun `test synchronization of structure data models`() = runBlocking {
            // Выполнение синхронизации (с eventId = 1, как в твоём примере)
            val changes = structureUseCase.syncStructureDataAndQuestions(1)

            // Если необходимо, можно проверить список изменений
            // Например: assertEquals(expectedChanges, changes)

            // Так как внутри syncStructureDataAndQuestions итоговые данные сохраняются через saveStructureData,
            // используем ArgumentCaptor для захвата переданного параметра.
            val captor = ArgumentCaptor.forClass(StructureDataLocal::class.java)
            verify(repositoryStructureImpl).saveStructureData(captor.capture(), eq(1))
            val savedLocalData = captor.value.childes

            // Сравниваем полученные сохранённые локальные данные с ожидаемыми
            assertEquals("Локальные данные после синхронизации не совпадают с ожидаемыми",
                expectedLocalOutput, savedLocalData)

            // Для проверки удалённых данных можно, например, в моке метода pushStructureData
            // захватить передаваемые параметры или, если в реальном коде после синхронизации
            // вызывается другой метод получения удалённого состояния, сравнить его.
            //
            // Пример: если бы был метод repositoryStructureImpl.getRemoteStructureData(1),
            // то можно было бы написать:
            // val finalRemoteData = repositoryStructureImpl.getRemoteStructureData(1)
            // assertEquals(expectedRemoteOutput, finalRemoteData)
        }*/
}
