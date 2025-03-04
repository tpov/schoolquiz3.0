package com.tpov.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.repository.RepositoryException
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.CallbackDifferences
import com.tpov.common.domain.utils.StructureDataUtils
import com.tpov.common.domain.utils.StructureDataUtils.findStructureDataOld
import com.tpov.common.domain.utils.StructureDataUtils.updateNode
import com.tpov.common.presentation.model.PathStructure
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
import java.io.File
import java.io.FileWriter
import java.lang.reflect.Type

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

    private var inputQuestionLocal: MutableList<QuestionEntity> = mutableListOf()
    private var expectedQuestionLocal: MutableList<QuestionEntity> = mutableListOf()
    private var inputQuestionRemote: MutableList<QuestionEntity> = mutableListOf()
    private var expectedQuestionRemote: MutableList<QuestionEntity> = mutableListOf()
    private lateinit var expectedOutputLocal: MutableList<StructureDataLocal>
    private lateinit var inputDataLocal: MutableList<StructureDataLocal>
    private lateinit var expectedOutputRemote: MutableList<StructureDataLocal>
    private lateinit var inputDataRemote: MutableList<StructureDataLocal>
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

        //inputQuestionLocal = loadJson<QuestionEntity>("MockQuestionInputLocal.json").toMutableList()
        //expectedQuestionLocal =
        //    loadJson<QuestionEntity>("MockQuestionOutputLocal.json").toMutableList()
        //inputQuestionRemote =
        //    loadJson<QuestionEntity>("MockQuestionInputRemote.json").toMutableList()
        expectedQuestionRemote =
            loadJson<QuestionEntity>("MockQuestionOutputRemote.json").toMutableList()
        inputDataLocal =
            loadJson<StructureDataLocal>("MockStructureDataInputLocal.json").toMutableList()
        expectedOutputLocal =
            loadJson<StructureDataLocal>("MockStructureDataOutputLocal.json").toMutableList()
        inputDataRemote =
            loadJson<StructureDataLocal>("MockStructureDataInputRemote.json").toMutableList()
        expectedOutputRemote =
            loadJson<StructureDataLocal>("MockStructureDataOutputRemote.json").toMutableList()

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

        val result = structureUseCase.syncStructureDataAndGetChangeLists(eventId)

        when (result) {
            is SyncStructureResult.Success -> {
                result.state.structureInfoRemote.forEach {
                    val oldStructureData = findStructureDataOld(
                        result.state.structureCategoryDataListRemote,
                        result.state.structureCategoryDataListLocal,
                        it.pathStructure
                    )

                    if (oldStructureData.structureData != null) result.state.structureCategoryDataListRemote.updateNode(
                        oldStructureData.structureData!!,
                        oldStructureData.pathOld
                    )
                }

                result.state.structureInfoLocal.forEach {
                    val oldStructureData = findStructureDataOld(
                        result.state.structureCategoryDataListLocal,
                        result.state.structureCategoryDataListRemote,
                        it.pathStructure
                    )
                    if (oldStructureData.structureData != null) result.state.structureCategoryDataListLocal.updateNode(
                        oldStructureData.structureData!!,
                        oldStructureData.pathOld
                    )
                }
            }

            is SyncStructureResult.Error -> StructureUseCase.Log.d(
                "TestDebug",
                "Sync result: ${result.stage}"
            )
        }

        assertTrue(result is SyncStructureResult.Success)

        editQuestionList(inputQuestionLocal, inputDataLocal)
        editQuestionList(inputQuestionRemote, inputDataRemote)
        editQuestionList(expectedQuestionLocal, expectedOutputLocal)

        inputQuestionLocal.forEachIndexed { index, item ->
            if (index % 16 == 15) {
                StructureUseCase.Log.d(
                    "inputQuestionLocal before testInputStructureDataLocal item",
                    "${item.idCategory}, ${item.idSubCategory}, ${item.idSubsubCategory}, ${item.idQuiz}"
                )
            }
        }
        inputQuestionRemote.forEachIndexed { index, item ->
            if (index % 16 == 15) {
                StructureUseCase.Log.d(
                    "assertQuestionRemote before testInputStructureDataLocal item",
                    "${item.idCategory}, ${item.idSubCategory}, ${item.idSubsubCategory}, ${item.idQuiz}"
                )
            }
        }
        testInputStructureDataLocal(result)
        testInputQuestionLocal(result)
    }

    // Метод для сохранения списка вопросов в новый файл
    private fun saveQuestionsToFile(questions: List<QuestionEntity>, filePath: String) {
        val json = gson.toJson(questions)
        val file = File(filePath)
        file.parentFile?.mkdirs()
        FileWriter(file).use { writer ->
            writer.write(json)
        }
        StructureUseCase.Log.d("TestDebug", "Generated questions saved to $filePath")
    }

    private fun editQuestionList(
        questionList: MutableList<QuestionEntity>,
        structureData: MutableList<StructureDataLocal>
    ) {
        var counter = 0
        var countQuiz = 1

        questionList.filter { it.id!! <= counter }.toMutableList()
        StructureDataUtils.processStructureDataDifferences(
            structureData.toMutableList(),
            structureData.toMutableList(),
            1,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, _, _ -> },
                onNoChildren = { structureNodeListOld, structureNodeNew, currentPath ->
                    StructureUseCase.Log.d("countQuiz", "$countQuiz")
                    StructureUseCase.Log.d("currentPath", "$currentPath")
                    countQuiz++
                    structureNodeNew.printFullStructure("structureNodeNew")
                    structureNodeListOld?.get(0)?.printFullStructure("structureNodeListOld")
                    arrayListOf(1).toList().forEach { eventid ->
                        arrayListOf(50, 200).forEach { lvlTranslate ->
                            arrayListOf("ua", "ru").forEach { language ->
                                arrayListOf(true, false).forEach { hardQuestion ->
                                    arrayListOf(1, 2).forEach { numQuestion ->

                                        resetQuestionEntity(
                                            questionList,
                                            counter,
                                            currentPath,
                                            language,
                                            lvlTranslate,
                                            hardQuestion,
                                            numQuestion,
                                            eventid
                                        )
                                        counter++

                                        StructureUseCase.Log.d("counter", "$counter")
                                        StructureUseCase.Log.d("size", "${questionList.size}")
                                    }
                                }
                            }
                        }
                    }
                }
            )
        )

        questionList.filter { it.id!! <= counter }.toMutableList()
    }

    private fun resetQuestionEntity(
        questionList: MutableList<QuestionEntity>,
        counter: Int,
        currentPath: PathStructure,
        lang: String,
        lvlTranslate: Int,
        hardQuestion: Boolean,
        numQuestion: Int,
        eventid: Int
    ) {
        if (counter >= questionList.size) {
            questionList.add(expectedQuestionRemote[0].copy())
        }

        questionList[counter].idEvent = eventid
        questionList[counter].id = counter
        questionList[counter].idCategory = currentPath.idCategory
        questionList[counter].idSubCategory = currentPath.idSubCategory
        questionList[counter].idSubsubCategory = currentPath.idSubsubCategory
        questionList[counter].idQuiz = currentPath.idQuiz
        questionList[counter].language = lang
        questionList[counter].lvlTranslate = lvlTranslate
        questionList[counter].hardQuestion = hardQuestion
        questionList[counter].numQuestion = numQuestion
    }

    private fun testInputQuestionLocal(result: SyncStructureResult) {
        when (result) {
            is SyncStructureResult.Success -> {
                var inputQuestionRemoteVar = inputQuestionRemote
                var inputQuestionLocalVar = inputQuestionLocal

                val assertQuestionRemote = inputQuestionRemoteVar.addList(
                    getQuestionListByPath(
                        result.state.changedListRemote,
                        inputQuestionLocal,
                        inputDataLocal,
                        inputDataRemote
                    )
                ).toMutableList()

                val assertQuestionLocal = inputQuestionLocalVar.addList(
                    getQuestionListByPath(
                        result.state.changedListLocal,
                        inputQuestionRemote,
                        inputDataLocal,
                        inputDataRemote
                    )
                ).toMutableList()

                inputQuestionLocalVar.forEach { assertQuestion ->
                    assertQuestionLocal.remove(assertQuestionLocal.find { it.copy(id = assertQuestion.id) == assertQuestion }!!)
                }

                assertEquals(assertQuestionLocal.size, 0)

            }

            is SyncStructureResult.Error -> StructureUseCase.Log.d(
                "TestDebug",
                "Sync result: ${result.stage}"
            )
        }
    }

    fun MutableList<QuestionEntity>.addList(questionList: List<QuestionEntity>): List<QuestionEntity> {
        questionList.forEach {
            this.add(it)
        }
        this.sortBy { it.id }
        return this
    }

    fun getQuestionListByPath(
        changedList: MutableList<ChangeVersionStructure>,
        questionListOld: List<QuestionEntity>,
        structureDataOld: MutableList<StructureDataLocal>,
        structureDataNew: MutableList<StructureDataLocal>,
    ): List<QuestionEntity> {
        val newQuestionList: MutableList<QuestionEntity> = mutableListOf()
        changedList.forEach { change ->

            questionListOld.filter { question ->
                change.pathStructure.idQuiz == question.idQuiz
                        && change.pathStructure.idEvent == question.idEvent
                        && change.pathStructure.idCategory == question.idCategory
                        && change.pathStructure.idSubCategory == question.idSubCategory
                        && change.pathStructure.idSubsubCategory == question.idSubsubCategory
            }.forEach {
                newQuestionList.add(
                    it.editPath(
                        findStructureDataOld(
                            structureDataOld,
                            structureDataNew,
                            change.pathStructure
                        ).pathOld
                    )
                )
            }
        }
        return newQuestionList
    }

    private fun QuestionEntity.editPath(pathStructure: PathStructure): QuestionEntity {
        return this.copy(
            idEvent = pathStructure.idEvent,
            idCategory = pathStructure.idCategory,
            idSubCategory = pathStructure.idSubCategory,
            idSubsubCategory = pathStructure.idSubsubCategory
        )
    }

    private fun testInputStructureDataLocal(result: SyncStructureResult) {
        when (result) {
            is SyncStructureResult.Success -> {
                assertEquals(expectedOutputLocal, result.state.structureCategoryDataListLocal)
            }

            is SyncStructureResult.Error -> StructureUseCase.Log.d(
                "TestDebug",
                "Sync result: ${result.stage}"
            )
        }
    }

    private inline fun <reified T> loadJson(filename: String): List<T> {
        val json = this::class.java.classLoader?.getResource(filename)?.readText()
            ?: throw IllegalStateException("Файл $filename не найден")
        val type: Type = object : TypeToken<MutableList<T>>() {}.type
        return gson.fromJson(json, type)
    }
}