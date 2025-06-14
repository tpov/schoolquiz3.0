package com.tpov.schoolquiz.presentation.create.strategy

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.tpov.common.Core
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.NamesUtils
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
import com.tpov.schoolquiz.presentation.create.model.isUiState


class CreateQuizRegimeStrategy(
    val structureUseCase: StructureUseCase,
    val questionUseCase: QuestionUseCase
) : QuizRegimeStrategy {

    override fun setupUiState() = QuizUiModelState(
        quizNameUiState = TextUiState.Visible(),
        quizImageUiState = ImageUiState.Visible(),
        categorySpinnerUiState = SpinnerUiState.Visible(),
        subCategorySpinnerUiState = SpinnerUiState.Visible(),
        subsubCategorySpinnerUiState = SpinnerUiState.Visible(),

        llCreateNewCategory = isUiState.Hidden,
        stroceTop = isUiState.Visible,
        questionImageUiState = ImageUiState.Visible(),
        fullscreenButtonUiState = CheckBoxUiState.Visible(false),
        questionNumberSpinnerUiState = SpinnerUiState.Visible(),
        stroceBottom = isUiState.Visible,
        bBeforeEditTranslate = TextUiState.Hidden,
        bAfterEditTranslate = TextUiState.Hidden,
        typeQuestionCheckBoxState = CheckBoxUiState.Visible(true),
        cancelButtonUiState = TextUiState.Visible(),
        addAnswerButtonUiState = TextUiState.Visible(),
        addTranslateButtonUiState = TextUiState.Visible(),
        addGapButtonUiState = TextUiState.Visible(),
        saveQuizButtonUiState = TextUiState.Visible()
    )

    override suspend fun loadData(pathStructure: PathStructure): QuizUiModelState {
        val homeQuizCategoryStructure = structureUseCase.getStructureEventData(EventQuiz.QUIZ_HOME)
        val userQuizCategoryStructure = structureUseCase.getStructureEventData(EventQuiz.QUIZ_BY_USER)

        val categoryList: MutableList<String> = mutableListOf()
        val subcategoryList: MutableList<String> = mutableListOf()
        val subsubCategoryList: MutableList<String> = mutableListOf()

        homeQuizCategoryStructure?.forEach { structure -> structure.children?.mapTo(categoryList) { it.nameItem } }
        homeQuizCategoryStructure?.getOrNull(0)?.children?.forEach { structure ->
            structure.children?.mapTo(subcategoryList) { it.nameItem }
        }
        userQuizCategoryStructure?.forEach { structure -> structure.children?.mapTo(subcategoryList) { it.nameItem } }
        homeQuizCategoryStructure?.getOrNull(0)?.children?.getOrNull(0)?.children?.forEach { structure ->
            structure.children?.mapTo(subsubCategoryList) { it.nameItem }
        }

        return QuizUiModelState(
            categorySpinnerUiState = SpinnerUiState.Visible(categoryList, 0),
            subCategorySpinnerUiState = SpinnerUiState.Visible(subcategoryList, 0),
            subsubCategorySpinnerUiState = SpinnerUiState.Visible(subsubCategoryList, 0),
            questionList = listOf(
                QuestionLocal().copy(numQuestion = 1, language = settingsConfig.languages.first(), nameAnswers = "|"))
        )
    }

    override fun fullscreen(isFullscreen: Boolean)  = QuizUiModelState(

    )



    override suspend fun saveData(
        questionList: List<QuestionLocal>,
        bitmapList: MutableMap<Pair<Int, Boolean>, BitmapDrawable>,
        structureList: List<Pair<String, BitmapDrawable>>,
        defaultImage: Drawable
    ) {
        var structureCategoryHomeList = structureUseCase.getStructureEventData(EventQuiz.QUIZ_BY_USER)?.toMutableList() ?: mutableListOf()

        var structureCategoryHome = structureCategoryHomeList.find { it.nameItem == structureList[0].first }
        if (structureCategoryHome == null) {
            structureCategoryHome = StructureDataLocal(nameItem = structureList[0].first, children = mutableListOf())
            structureCategoryHomeList.add(structureCategoryHome)
        }
        structureCategoryHome.printFullStructure("drl;gklpsdre 1")
        // Save category image if not default
        if (!isDefaultImage(structureList[0].second, defaultImage)) {
            val categoryImagePath = NamesUtils().getPathPicture()
            Core.savePicture(categoryImagePath, structureList[0].second.bitmap)
            structureCategoryHome.picture = categoryImagePath
        } else {
            structureCategoryHome.picture = ""
        }

        var structureSubCategoryHome = structureCategoryHome.children?.find { it.nameItem == structureList[1].first }
        if (structureSubCategoryHome == null) {
            structureSubCategoryHome = StructureDataLocal(nameItem = structureList[1].first, children = mutableListOf())
            structureCategoryHome.children?.add(structureSubCategoryHome)
        }
        // Save subcategory image if not default
        if (!isDefaultImage(structureList[1].second, defaultImage)) {
            val subCategoryImagePath = NamesUtils().getPathPicture()
            Core.savePicture(subCategoryImagePath, structureList[1].second.bitmap)
            structureSubCategoryHome.picture = subCategoryImagePath
        } else {
            structureSubCategoryHome.picture = ""
        }

        var structureSubSubCategoryHome = structureSubCategoryHome.children?.find { it.nameItem == structureList[2].first }
        if (structureSubSubCategoryHome == null) {
            structureSubSubCategoryHome = StructureDataLocal(nameItem = structureList[2].first, children = mutableListOf())
            structureSubCategoryHome.children?.add(structureSubSubCategoryHome)
        }
        // Save subsubcategory image if not default
        if (!isDefaultImage(structureList[2].second, defaultImage)) {
            val subSubCategoryImagePath = NamesUtils().getPathPicture()
            Core.savePicture(subSubCategoryImagePath, structureList[2].second.bitmap)
            structureSubSubCategoryHome.picture = subSubCategoryImagePath
        } else {
            structureSubSubCategoryHome.picture = ""
        }

        Log.d("drl;gklpsdre", "4")
        // Save questions
        questionList.forEach { question ->
            val questionImage = bitmapList[question.numQuestion to false]
            val answerImage = bitmapList[question.numQuestion to true]

            // Save question image if not default
            if (!isDefaultImage(questionImage, defaultImage)) {
                val questionImagePath = NamesUtils().getPathPicture()
                Core.savePicture(questionImagePath, questionImage!!.bitmap)
                question.pathPictureQuestion = questionImagePath
            } else {
                question.pathPictureQuestion = ""
            }

            // Save answer image if not default
            if (!isDefaultImage(answerImage, defaultImage)) {
                val answerImagePath = NamesUtils().getPathPicture()
                Core.savePicture(answerImagePath, answerImage!!.bitmap)
                question.pathPictureQuestion = answerImagePath
            } else {
                question.pathPictureQuestion = null
            }

            questionUseCase.insertQuestion(question)
        }

        structureCategoryHome.printFullStructure("drl;gklpsdre 2")
        structureUseCase.insertStructureData(structureCategoryHome, EventQuiz.QUIZ_BY_USER)
    }

    private fun isDefaultImage(drawable: Drawable?, defaultImage: Drawable): Boolean {
        if (drawable == null) return true
        return drawable.constantState == defaultImage.constantState
    }

}
