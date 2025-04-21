package com.tpov.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tpov.common.Core.tpovId
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.data.model.remote.StructureDataRemote
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.repository.RepositoryException
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.CallbackDifferences
import com.tpov.common.domain.utils.StructureDataUtils
import com.tpov.common.domain.utils.StructureDataUtils.addList
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
import org.mockito.kotlin.anyOrNull
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
    private lateinit var inputLocalInfoRemote: MutableList<StructureInfoEntity>
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
        inputLocalInfoRemote =
            loadJson<StructureInfoEntity>("StructureInfoLocalRemoteList.json").toMutableList()

        inputDataRemote =
            StructureDataRemote(children = loadJson<StructureDataRemote>("MockStructureDataInputRemote.json")).toStructureDataLocal().children!!
        expectedOutputRemote =
            StructureDataRemote(children = loadJson<StructureDataRemote>("MockStructureDataOutputRemote.json")).toStructureDataLocal().children!!

        //repositoryStructureImpl.fetchStructureInfo(currentPathRemote)

        runBlocking {
            `when`(repositoryStructureImpl.getStructureEventData(1))
                .thenReturn(inputDataLocal)
            `when`(repositoryStructureImpl.fetchStructureCategoryDataList(1))
                .thenReturn(inputDataRemote)

            `when`(repositoryStructureImpl.fetchStructureInfo(anyOrNull())).thenAnswer { invocation ->
                getInfoItemLocal(invocation.getArgument<PathStructure>(0))
            }
        }
    }

    fun getInfoItemLocal(pathStructure: PathStructure): StructureInfoEntity? {
        val findLocalInfo = inputLocalInfoRemote.find {
            it.pathStructure == pathStructure
        }


        val logFind = findStructureDataOld(inputDataLocal, inputDataRemote, PathStructure(1,1,1,2,2))
        StructureUseCase.Log.d("klsdhjgdkser find", "logFind: $logFind")


        StructureUseCase.Log.d(
            "fdrkfjskefjl345",
            "pathStructure: ${pathStructure}, findLocalInfo: $findLocalInfo"
        )
        return findLocalInfo?.copy()
    }

    @Test
    fun `test full sync process`() = runBlocking {
        val eventId = 1
        //generateStructureInfo()
            //generateStructureInfoLocal()

        val result = structureUseCase.syncStructureDataAndGetChangeLists(eventId)

        when (result) {
            is SyncStructureResult.Success -> {
                result.state.structureInfoGlobal.forEach {
                    StructureUseCase.Log.d("sefsdfsdfsdxfsd", "dateUpdate: ${it}")
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

        testInputStructureDataLocal(result)
        testInputQuestionLocal(result)
    }

    private fun generateStructureInfoLocal() {
        val localList: MutableList<StructureInfoEntity> = mutableListOf()


        StructureDataUtils.processStructureDataDifferences(
            inputDataRemote.toMutableList(),
            expectedOutputLocal.toMutableList(),
            1,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { structureDataRemote, structureDataOutput, path ->

                    val inputDataLocal =
                        findStructureDataOld(inputDataLocal, expectedOutputLocal, path.copy())

                    structureDataOutput.ratingLocal = (Math.random() * 100).toInt()
                    structureDataOutput.starsMaxLocal = (Math.random() * 100).toInt()
                    structureDataOutput.starsAverageLocal = (Math.random() * 100).toInt()

                    if (inputDataLocal.structureData == null) {

                        structureDataRemote?.get(0)!!.let {
                            localList.add(
                                StructureInfoEntity(
                                    null,
                                    path.copy(),
                                    structureDataOutput.dataUpdateLocal,
                                    tpovId,
                                    structureDataOutput.ratingLocal,
                                    structureDataOutput.starsMaxLocal,
                                    structureDataOutput.starsAverageLocal,
                                    0
                                )
                            )
                        }

                    } else {
                        inputDataLocal.structureData!!.ratingLocal = structureDataOutput.ratingLocal
                        inputDataLocal.structureData!!.starsMaxLocal = structureDataOutput.starsMaxLocal
                        inputDataLocal.structureData!!.starsAverageLocal =
                            structureDataOutput.starsAverageLocal
                        if (inputDataLocal.structureData?.dataUpdateLocal!! < structureDataOutput.dataUpdateLocal) {

                            structureDataRemote?.get(0).let {
                                localList.add(
                                    StructureInfoEntity(
                                        null,
                                        path.copy(),
                                        structureDataOutput.dataUpdateLocal,
                                        tpovId,
                                        structureDataOutput.ratingLocal,
                                        structureDataOutput.starsMaxLocal,
                                        structureDataOutput.starsAverageLocal,
                                        0
                                    )
                                )
                            }

                        }
                    }


                },
                onNoChildren = { structureDataRemote, structureDataOutput, path ->

                    val inputDataLocal =
                        findStructureDataOld(inputDataLocal, expectedOutputLocal, path.copy())

                    structureDataOutput.ratingLocal = (Math.random() * 100).toInt()
                    structureDataOutput.starsMaxLocal = (Math.random() * 100).toInt()
                    structureDataOutput.starsAverageLocal = (Math.random() * 100).toInt()

                    if (inputDataLocal.structureData == null) {

                        structureDataRemote?.get(0)!!.let {
                            localList.add(
                                StructureInfoEntity(
                                    null,
                                    path.copy(),
                                    structureDataOutput.dataUpdateLocal,
                                    tpovId,
                                    structureDataOutput.ratingLocal,
                                    structureDataOutput.starsMaxLocal,
                                    structureDataOutput.starsAverageLocal,
                                    0
                                )
                            )
                        }

                    } else {
                        inputDataLocal.structureData!!.ratingLocal = structureDataOutput.ratingLocal
                        inputDataLocal.structureData!!.starsMaxLocal = structureDataOutput.starsMaxLocal
                        inputDataLocal.structureData!!.starsAverageLocal =
                            structureDataOutput.starsAverageLocal
                        if (inputDataLocal.structureData?.dataUpdateLocal!! < structureDataOutput.dataUpdateLocal) {

                            structureDataRemote?.get(0).let {
                                localList.add(
                                    StructureInfoEntity(
                                        null,
                                        path.copy(),
                                        structureDataOutput.dataUpdateLocal,
                                        tpovId,
                                        structureDataOutput.ratingLocal,
                                        structureDataOutput.starsMaxLocal,
                                        structureDataOutput.starsAverageLocal,
                                        0
                                    )
                                )
                            }
                        }
                    }
                }
            )
        )

        saveFile(localList, "localList")
        saveFile(expectedOutputLocal, "expectedOutputLocal")
        saveFile(inputDataLocal, "inputDataLocal")
    }

    private fun generateStructureInfo() {

        val structureDataInfoList = insertStructureInfoLocal()

        structureDataInfoList.forEach { structureDataInfo ->
            structureDataInfo.rating = (Math.random() * 100).toInt()
            structureDataInfo.starsMax = (Math.random() * 100).toInt()
            structureDataInfo.starsAverage = (Math.random() * 100).toInt()
        }

        inputDataLocal.forEach {

            StructureUseCase.Log.d(
                "onHasChildren forEach",
                "structureData.dataUpdateLocal: ${it.dataUpdateLocal}"
            )
        }

        StructureDataUtils.processStructureDataDifferences(
            inputDataLocal.toMutableList(),
            inputDataLocal.toMutableList(),
            1,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureData, path ->
                    structureDataInfoList.find {
                        it.pathStructure.idEvent == path.idEvent
                                && it.pathStructure.idCategory == path.idCategory
                                && it.pathStructure.idSubCategory == path.idSubCategory
                                && it.pathStructure.idSubsubCategory == path.idSubsubCategory
                                && it.pathStructure.idQuiz == path.idQuiz
                    }!!.apply {
                        structureData.ratingLocal = rating
                        structureData.starsMaxLocal = starsMax
                        structureData.starsAverageLocal = starsAverage
                    }
                    StructureUseCase.Log.d(
                        "onHasChildren",
                        "structureData.dataUpdateLocal: ${structureData.dataUpdateLocal}"
                    )
                    StructureUseCase.Log.d(
                        "onHasChildren",
                        "structureData.dataUpdateLocal: ${structureData.dataUpdateGlobal}"
                    )
                },

                onNoChildren = { _, structureData, path ->
                    structureDataInfoList.find {
                        it.pathStructure.idEvent == path.idEvent
                                && it.pathStructure.idCategory == path.idCategory
                                && it.pathStructure.idSubCategory == path.idSubCategory
                                && it.pathStructure.idSubsubCategory == path.idSubsubCategory
                                && it.pathStructure.idQuiz == path.idQuiz
                    }!!.apply {
                        structureData.ratingLocal = rating
                        structureData.starsMaxLocal = starsMax
                        structureData.starsAverageLocal = starsAverage
                    }
                    StructureUseCase.Log.d(
                        "onHasChildren",
                        "structureData.dataUpdateLocal: ${structureData.dataUpdateLocal}"
                    )
                    StructureUseCase.Log.d(
                        "onHasChildren",
                        "structureData.dataUpdateLocal: ${structureData.dataUpdateGlobal}"
                    )
                }
            )
        )

        StructureDataUtils.processStructureDataDifferences(
            expectedOutputLocal.toMutableList(),
            expectedOutputLocal.toMutableList(),
            1,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureData, path ->
                    structureDataInfoList.find {
                        it.pathStructure.idEvent == path.idEvent
                                && it.pathStructure.idCategory == path.idCategory
                                && it.pathStructure.idSubCategory == path.idSubCategory
                                && it.pathStructure.idSubsubCategory == path.idSubsubCategory
                                && it.pathStructure.idQuiz == path.idQuiz
                    }!!.apply {
                        structureData.ratingLocal = rating
                        structureData.starsMaxLocal = starsMax
                        structureData.starsAverageLocal = starsAverage
                    }
                },

                onNoChildren = { _, structureData, path ->
                    structureDataInfoList.find {
                        it.pathStructure.idEvent == path.idEvent
                                && it.pathStructure.idCategory == path.idCategory
                                && it.pathStructure.idSubCategory == path.idSubCategory
                                && it.pathStructure.idSubsubCategory == path.idSubsubCategory
                                && it.pathStructure.idQuiz == path.idQuiz
                    }!!.apply {
                        structureData.ratingLocal = rating
                        structureData.starsMaxLocal = starsMax
                        structureData.starsAverageLocal = starsAverage
                    }
                }
            )
        )
        saveFile(inputDataLocal, "inputDataLocal")
        saveFile(expectedOutputLocal, "expectedOutputLocal")
        saveFile(structureDataInfoList, "structureDataInfoList")

    }

    private fun <T> saveFile(file: T, fileName: String) {
        val json = gson.toJson(file)
        val file = File("$fileName.json")
        file.parentFile?.mkdirs()
        FileWriter(file).use { writer ->
            writer.write(json)
        }
    }

    private fun insertStructureInfoLocal(): MutableList<StructureInfoEntity> {
        var structureInfoRemoteList: MutableList<StructureInfoEntity> = mutableListOf()

        StructureDataUtils.processStructureDataDifferences(
            expectedOutputLocal.toMutableList(),
            expectedOutputLocal.toMutableList(),
            1,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureData, path ->
                    structureInfoRemoteList.add(
                        StructureInfoEntity(
                            structureData.ratingLocal,
                            path.copy(),
                            structureData.dataUpdateGlobal,
                            tpovId,
                            structureData.ratingLocal,
                            structureData.starsMaxLocal,
                            structureData.starsAverageLocal,
                            0
                        )
                    )
                },

                onNoChildren = { _, structureData, path ->
                    structureInfoRemoteList.add(
                        StructureInfoEntity(
                            structureData.ratingLocal,
                            path.copy(),
                            structureData.dataUpdateGlobal,
                            tpovId,
                            structureData.ratingLocal,
                            structureData.starsMaxLocal,
                            structureData.starsAverageLocal,
                            0
                        )
                    )
                }
            )
        )

        return structureInfoRemoteList

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
                    countQuiz++
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

                val updatedQuestions = inputQuestionRemoteVar.map { question ->
                    val edit = result.state.editIdsList.find { edit ->
                        question.idEvent == edit.idEventFrom
                                && question.idCategory == edit.idCategoryFrom
                                && question.idSubCategory == edit.idSubCategoryFrom
                                && question.idSubsubCategory == edit.idSubsubCategoryFrom
                                && question.idQuiz == edit.idQuizFrom
                    }

                    if (edit != null) {
                        val edited = question.copy(
                            idEvent = edit.idEventTo,
                            idCategory = edit.idCategoryTo,
                            idSubCategory = edit.idSubCategoryTo,
                            idSubsubCategory = edit.idSubsubCategoryTo,
                            idQuiz = edit.idQuizTo
                        )

                        edited
                    } else {
                        question
                    }
                }

                inputQuestionRemoteVar = updatedQuestions.toMutableList()

                var assertQuestionRemote = inputQuestionRemoteVar.addList(
                    getQuestionListByPath(
                        result.state.changedListQuestionRemote,
                        inputQuestionLocal,
                        inputDataRemote,
                        inputDataLocal
                    )
                ).toMutableList()

                val assertQuestionLocal = inputQuestionLocalVar.toMutableList()

                expectedQuestionLocal.forEach { assertQuestion ->
//                    StructureUseCase.Log.d(
//                        "expectedQuestionLocal.forEach",
//                        "${assertQuestion.idCategory}, ${assertQuestion.idSubCategory}, ${assertQuestion.idSubsubCategory}, ${assertQuestion.idQuiz}"
//                    )

                    assertQuestionLocal.remove(assertQuestionLocal.find { it.copy(id = assertQuestion.id) == assertQuestion }!!)
                    assertQuestionRemote.remove(assertQuestionRemote.find { it.copy(id = assertQuestion.id) == assertQuestion }!!)
                }

                assertEquals(assertQuestionLocal.size, 0)

            }

            is SyncStructureResult.Error -> StructureUseCase.Log.d(
                "TestDebug",
                "Sync result: ${result.stage}"
            )
        }
    }


    fun getQuestionListByPath(
        changedList: MutableList<ChangeVersionStructure>,
        questionListNew: List<QuestionEntity>,
        structureDataOld: MutableList<StructureDataLocal>,
        structureDataNew: MutableList<StructureDataLocal>,
    ): List<QuestionEntity> {
        val newQuestionList: MutableList<QuestionEntity> = mutableListOf()
        changedList.forEach { change ->

            questionListNew.filter { questions ->
                change.pathStructure.idQuiz == questions.idQuiz
                        && change.pathStructure.idEvent == questions.idEvent
                        && change.pathStructure.idCategory == questions.idCategory
                        && change.pathStructure.idSubCategory == questions.idSubCategory
                        && change.pathStructure.idSubsubCategory == questions.idSubsubCategory
            }.forEach { question ->
                newQuestionList.add(question)
            }
        }
        newQuestionList.forEach {
            StructureUseCase.Log.d(
                "getQuestionListByPath return newQuestionList",
                "${it.idCategory}, ${it.idSubCategory}, ${it.idSubsubCategory}, ${it.idQuiz}"
            )
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