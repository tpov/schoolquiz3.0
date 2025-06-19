package com.tpov.schoolquiz.presentation.create.strategy

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.create.model.*
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.data.model.QuestionType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope


class EditQuizRegimeStrategy(
    val structureUseCase: StructureUseCase,
    val questionUseCase: QuestionUseCase
) : QuizRegimeStrategy {
    override fun setupUiState() = QuizUiModelState(
        quizNameUiState = TextUiState.Visible(),
        quizImageUiState = ImageUiState.Visible(),
        categorySpinnerUiState = SpinnerUiState.Visible(),
        subCategorySpinnerUiState = SpinnerUiState.Visible(),
        subsubCategorySpinnerUiState = SpinnerUiState.Visible(),
        llCreateNewCategory = IsUiState.Hidden,
        stroceTop = IsUiState.Visible,
        questionImageUiState = ImageUiState.Visible(),
        fullscreenButtonUiState = CheckBoxUiState.Visible(false),
        questionNumberSpinnerUiState = SpinnerUiState.Visible(),
        stroceBottom = IsUiState.Visible,
        bBeforeEditTranslate = TextUiState.Visible(),
        bAfterEditTranslate = TextUiState.Visible(),
        typeQuestionCheckBoxState = CheckBoxUiState.Visible(true),
        cancelButtonUiState = TextUiState.Visible(),
        addAnswerButtonUiState = TextUiState.Visible(),
        addTranslateButtonUiState = TextUiState.Visible(),
        saveQuizButtonUiState = TextUiState.Visible()
    )

    override suspend fun loadData(pathStructure: PathStructure): QuizUiModelState {
        return coroutineScope {
            val structureDataDeferred = async { structureUseCase.getStructure(pathStructure) }
            val questionsLocalDeferred = async { questionUseCase.getQuestions(pathStructure) }

            val structure = structureDataDeferred.await()
            val questionsLocalList = questionsLocalDeferred.await() ?: emptyList()

            if (structure == null) {
                // Возвращаем начальное состояние с ошибкой, если структура не найдена
                return@coroutineScope setupUiState().copy(
                    errorState = "Quiz not found at path: $pathStructure" // Предполагается, что errorState есть в QuizUiModelState
                )
            }

            // Группировка вопросов и их переводов
            val questionMap = mutableMapOf<Pair<Int, Boolean>, InternalQuestion>()
            questionsLocalList.forEach { qLocal ->
                val key = Pair(qLocal.numQuestion, qLocal.hardQuestion)
                val existingInternalQuestion = questionMap[key]

                if (existingInternalQuestion == null) {
                    val answerOptions = qLocal.answer.split("\n").mapIndexedNotNull { index, answerText ->
                        if (answerText.isNotBlank()) { // Пропускаем пустые строки, если они есть
                            TranslateAnswer(
                                id = index,
                                answer = answerText.removeSuffix("*").trim(),
                                isCorrect = answerText.endsWith("*"),
                                language = qLocal.language // Ответы на том же языке, что и вопрос
                            )
                        } else null
                    }
                    questionMap[key] = InternalQuestion(
                        numQuestion = qLocal.numQuestion,
                        hardQuestion = qLocal.hardQuestion,
                        type = qLocal.type ?: QuestionType.SINGLE_CHOICE,
                        question = qLocal.question, // Основной текст на этом языке
                        image = qLocal.image, // Путь к картинке вопроса
                        answers = answerOptions.toMutableList(),
                        translateQuestion = mutableListOf(TranslateQuestion(qLocal.language, qLocal.question)),
                        translateAnswers = mutableListOf() // TODO: Загрузка/обработка переводов ответов, если они есть
                    )
                } else {
                    // Добавляем перевод текста вопроса
                    existingInternalQuestion.translateQuestion.add(TranslateQuestion(qLocal.language, qLocal.question))
                    // TODO: Логика для добавления/обновления переводов ответов, если структура это поддерживает
                }
            }
            val internalQuestions = questionMap.values.sortedBy { it.numQuestion }.toMutableList()

            val initialUiState = setupUiState() // Получаем базовый UI state для этого режима

            // Заполняем QuizUiModelState на основе загруженных данных
            initialUiState.copy(
                pathStructure = pathStructure, // Сохраняем PathStructure в стейт
                quizNameUiState = TextUiState.Visible(text = structure.nameItem, isEnabled = true), // Имя квиза
                quizImageUiState = structure.picture?.let { pic -> ImageUiState.Visible(imageUri = pic, isEnabled = true) } ?: initialUiState.quizImageUiState,

                // Категории: для QUIZ_BY_USER отображаем как нередактируемые поля, если они есть
                categorySpinnerUiState = structure.nameCategory?.takeIf { it.isNotBlank() }?.let {
                    SpinnerUiState.Visible(items = listOf(it), selectedIndex = 0, isEnabled = false)
                } ?: initialUiState.categorySpinnerUiState,
                tvCategory = structure.nameCategory?.takeIf { it.isNotBlank() }?.let {
                    TextUiState.Visible(text = it, isEnabled = false)
                } ?: initialUiState.tvCategory,

                subCategorySpinnerUiState = structure.nameSubcategory?.takeIf { it.isNotBlank() }?.let {
                    SpinnerUiState.Visible(items = listOf(it), selectedIndex = 0, isEnabled = false)
                } ?: initialUiState.subCategorySpinnerUiState,
                tvSubCategory = structure.nameSubcategory?.takeIf { it.isNotBlank() }?.let {
                    TextUiState.Visible(text = it, isEnabled = false)
                } ?: initialUiState.tvSubCategory,

                subsubCategorySpinnerUiState = structure.nameSubSubcategory?.takeIf { it.isNotBlank() }?.let {
                    SpinnerUiState.Visible(items = listOf(it), selectedIndex = 0, isEnabled = false)
                } ?: initialUiState.subsubCategorySpinnerUiState,
                tvSubsubCategory = structure.nameSubSubcategory?.takeIf { it.isNotBlank() }?.let {
                    TextUiState.Visible(text = it, isEnabled = false)
                } ?: initialUiState.tvSubsubCategory,

                llCreateNewCategory = IsUiState.Hidden, // Скрываем возможность создания категорий в режиме редактирования

                questionList = internalQuestions, // Загруженный список вопросов

                // UI для первого вопроса (если есть)
                questionNumberSpinnerUiState = if (internalQuestions.isNotEmpty()) {
                    SpinnerUiState.Visible(
                        items = internalQuestions.map { "${it.numQuestion}${if (it.hardQuestion) "*" else ""}" },
                        selectedIndex = 0, isEnabled = true
                    )
                } else initialUiState.questionNumberSpinnerUiState,

                typeQuestionCheckBoxState = if (internalQuestions.isNotEmpty()) {
                    CheckBoxUiState.Visible(
                        isChecked = internalQuestions.first().type == QuestionType.MULTIPLE_CHOICE,
                        isEnabled = true, isInit = true,
                        text = initialUiState.typeQuestionCheckBoxState?.text ?: "" // Сохраняем текст из setupUiState
                    )
                } else initialUiState.typeQuestionCheckBoxState,

                questionTextUiState = if (internalQuestions.isNotEmpty()) {
                    TextUiState.Visible(text = internalQuestions.first().question, isEnabled = true)
                } else initialUiState.questionTextUiState,

                questionImageUiState = if (internalQuestions.isNotEmpty() && internalQuestions.first().image != null) {
                    ImageUiState.Visible(imageUri = internalQuestions.first().image!!, isEnabled = true)
                } else initialUiState.questionImageUiState,

                // Списки для RecyclerView для первого вопроса
                rvQuestionTranslate = if (internalQuestions.isNotEmpty()) {
                    internalQuestions.first().translateQuestion
                } else mutableListOf(),

                rvAnswerTranslate = if (internalQuestions.isNotEmpty()) {
                    internalQuestions.first().answers
                } else mutableListOf()

                // TODO: questionDrawable - это Map<Pair<Int, Boolean>, BitmapDrawable>
                // Его нужно будет заполнить, если картинки вопросов загружаются как BitmapDrawable.
                // Сейчас imageUri хранятся в InternalQuestion.image, ViewModel должен будет их загрузить.
            )
        }
    }

    override fun fullscreen(isFullscreen: Boolean): QuizUiModelState {
        TODO("Not yet implemented")
    }

    override suspend fun saveData(
        questionList: List<QuestionLocal>,
        bitmapList: Map<Pair<Int, Boolean>, BitmapDrawable>,
        structureList: List<Pair<String, BitmapDrawable>>,
        defaultImage: Drawable
    ) {
        TODO("Not yet implemented")
    }



}
