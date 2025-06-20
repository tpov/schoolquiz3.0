package com.tpov.schoolquiz.presentation.create.manager

import android.util.Log
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.domain.utils.QuestionUtils
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.schoolquiz.presentation.create.CreateQuizViewModel.Companion.MAX_ANSWER_OPTIONS_LIMIT
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
import com.tpov.schoolquiz.presentation.create.model.TranslateAnswer
import com.tpov.schoolquiz.presentation.create.model.TranslateQuestion
import com.tpov.schoolquiz.presentation.create.model.getNumQuestion
import com.tpov.schoolquiz.presentation.create.model.getType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class QuestionStateManager(private val uiState: MutableStateFlow<QuizUiModelState>) {

    fun selectQuestion(questionNumber: Int? = null, isHard: Boolean? = null) {
        uiState.update { state ->
            questionNumber ?: state.questionNumberSpinnerUiState?.getNumQuestion()
            isHard ?: state.typeQuestionCheckBoxState?.getType()

            val language = settingsConfig.languages[0]
            val spQuestionList = state.questionList?.filter { it.language == language }
                ?.map { "${it.numQuestion}. ${it.nameQuestion}" }
            val questionListThisNumberQuestionList = state.questionList
                ?.filter { it.numQuestion == questionNumber && it.hardQuestion == isHard }

            state.copy(
                questionNumberSpinnerUiState = SpinnerUiState.Visible(spQuestionList, questionNumber),
                rvQuestionTranslate = questionListThisNumberQuestionList?.map {
                    TranslateQuestion(it.nameQuestion, it.language)
                },
                rvAnswerTranslate = questionListThisNumberQuestionList?.map {
                    TranslateAnswer(it.nameAnswers.split(SPLIT_BETWEEN_ANSWERS).toMutableList(), it.language)
                }
            )
        }
    }

    fun updateQuestionText(question: String, language: LanguageUtils) {
        uiState.update { state ->
            val updatedQuestions = state.questionList?.map { q ->
                if (q.numQuestion == state.questionNumberSpinnerUiState?.getNumQuestion() &&
                    q.hardQuestion == state.typeQuestionCheckBoxState?.getType() &&
                    q.language == language
                ) {
                    q.copy(nameQuestion = question)
                } else q
            }?.toMutableList()

            state.copy(questionList = updatedQuestions)
        }
    }

    fun updateAnswerOptions(updatedTranslateAnswer: TranslateAnswer) {
        uiState.update { state ->
            val updatedQuestions = state.questionList?.map { question ->
                if (question.numQuestion == state.questionNumberSpinnerUiState?.getNumQuestion() &&
                    question.hardQuestion == state.typeQuestionCheckBoxState?.getType() &&
                    question.language == updatedTranslateAnswer.language
                ) {
                question.copy(
                    nameAnswers = updatedTranslateAnswer.listAnswer.joinToString(SPLIT_BETWEEN_ANSWERS)
                )
                } else {
                    question
            }
            }?.toMutableList()

            val newAddAnswerButtonState = if (updatedTranslateAnswer.listAnswer.size >= MAX_ANSWER_OPTIONS_LIMIT) {
                TextUiState.Visible(isEnabled = false)
            } else {
                TextUiState.Visible(isEnabled = true)
            }

            state.copy(
                questionList = updatedQuestions,
                addAnswerButtonUiState = newAddAnswerButtonState
            )
        }
    }

    fun addAnswerOption() {
        uiState.update { state ->
            var isFullCountAnswer = false
            val updatedQuestions: MutableList<QuestionLocal> = state.questionList?.map { question ->
                if (question.numQuestion == state.questionNumberSpinnerUiState?.getNumQuestion() &&
                    question.hardQuestion == state.typeQuestionCheckBoxState?.getType()
                ) {
                    isFullCountAnswer = question.nameAnswers.split(SPLIT_BETWEEN_ANSWERS).size == 4
                    question.copy(nameAnswers = question.nameAnswers + SPLIT_BETWEEN_ANSWERS)
                } else question

            }?.toMutableList() ?: mutableListOf(QuestionLocal())

            state.copy(
                questionList = updatedQuestions,
                addAnswerButtonUiState = if (isFullCountAnswer) TextUiState.Visible(isEnabled = false)
                else TextUiState.Visible(isEnabled = true)
            )
        }
    }

    fun addTranslation() {
        uiState.update { state ->
            val currentLanguages = state.rvQuestionTranslate?.map { it.language } ?: emptyList()
            val newLanguage = LanguageUtils.entries.firstOrNull { it !in currentLanguages } ?: return@update state

            val existingQuestion = state.questionList?.find {
                it.numQuestion == state.questionNumberSpinnerUiState?.getNumQuestion() &&
                it.hardQuestion == state.typeQuestionCheckBoxState?.getType()
            }

            val newQuestion = QuestionLocal(
                numQuestion = state.questionNumberSpinnerUiState?.getNumQuestion() ?: 0,
                hardQuestion = state.typeQuestionCheckBoxState?.getType() ?: false,
                language = newLanguage,
                nameAnswers = List(
                    (existingQuestion?.nameAnswers?.split(SPLIT_BETWEEN_ANSWERS)?.size?.minus(1)) ?: 1
                ) { SPLIT_BETWEEN_ANSWERS }.joinToString("")
            )

            state.copy(questionList = state.questionList?.plus(newQuestion)?.toMutableList())
        }
    }

    fun createNewQuestion(): Pair<Int, Boolean> {
        var resultPair: Pair<Int, Boolean> = Pair(0, false)
        uiState.update { state ->
            val hardQuestion = state.typeQuestionCheckBoxState?.getType() ?: false
            val language = settingsConfig.languages[0]
            val newNumQuestionAllType = QuestionUtils.getNumsQuestion(state.questionList!!, language)
            val newNumQuestionThisType: Int = if (hardQuestion) newNumQuestionAllType.second + 1
            else newNumQuestionAllType.first + 1

            val newQuestion = QuestionLocal(
                id = null,
                numQuestion = newNumQuestionThisType,
                nameAnswers = SPLIT_BETWEEN_ANSWERS,
                hardQuestion = hardQuestion,
                language = language
            )

            resultPair = Pair(newNumQuestionThisType, hardQuestion)
            state.copy(questionList = state.questionList.apply { add(newQuestion) })
        }
        return resultPair
    }


    fun toggleQuestionType() {
        uiState.update { state ->
            val newIsHard = !state.typeQuestionCheckBoxState?.getType()!! ?: false

            val updatedQuestions = state.questionList?.map { question ->
                if (question.numQuestion == state.questionNumberSpinnerUiState?.getNumQuestion() &&
                    question.hardQuestion == state.typeQuestionCheckBoxState.getType()
                ) {
                    question.copy(hardQuestion = newIsHard)
                } else question
            }

            state.copy(
                questionList = updatedQuestions?.toMutableList(),
                typeQuestionCheckBoxState = CheckBoxUiState.Visible(newIsHard, isInit = false)
            )
        }

    }

    fun updateQuestionLanguage(oldLanguage: LanguageUtils, newLanguage: LanguageUtils) {
        uiState.update { state ->
            val updatedQuestions = state.questionList?.map { question ->
                if (question.numQuestion == state.questionNumberSpinnerUiState?.getNumQuestion() &&
                    question.hardQuestion == state.typeQuestionCheckBoxState?.getType() &&
                    question.language == oldLanguage
                ) {
                question.copy(language = newLanguage)
                } else question
            }?.toMutableList() ?: state.questionList

            state.copy(questionList = updatedQuestions)
        }
    }
}
