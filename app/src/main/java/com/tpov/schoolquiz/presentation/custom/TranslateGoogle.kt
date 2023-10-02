package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.widget.Toast
import com.google.cloud.translate.TranslateOptions
import com.google.cloud.translate.Translation
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.custom.CoastValues.CoastValuesNolics.COEF_COAST_GOOGLE_TRANSLATE
import com.tpov.schoolquiz.presentation.custom.Values.loadText
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.network.event.log
import com.tpov.schoolquiz.secure.secureCode.getTranslateKey
import kotlinx.coroutines.InternalCoroutinesApi
import org.jetbrains.anko.runOnUiThread
import java.util.*

object TranslateGoogle {
    var nolicResult = 0

    @OptIn(InternalCoroutinesApi::class)
    suspend fun translateText(
        viewModel: MainActivityViewModel,
        context: Context,
        quizEntity: QuizEntity
    ) {
        val questionList = viewModel.getQuestionListByIdQuiz(quizEntity.id!!)
        var countTranslateQuestion = 0
        loadText.postValue(context.getString(R.string.do_translate_massage))
        // Создаем мапу, где ключом будет numQuestion, а значением - элемент Question с наибольшим lvlTranslate
        val questionMap: MutableMap<Int, QuestionEntity> = mutableMapOf()
        val questionHardMap: MutableMap<Int, QuestionEntity> = mutableMapOf()

        for (question in questionList) {
            log("dawdawdf $question")
            val existingQuestion = questionMap[question.numQuestion]
            val existingHardQuestion = questionHardMap[question.numQuestion]

            if (!question.hardQuestion) {
                if (existingQuestion == null || question.lvlTranslate > existingQuestion.lvlTranslate) {
                    log("dawdawdf add !hardQuestion")
                    questionMap[question.numQuestion] = question
                }
            } else {
                if (existingHardQuestion == null || question.lvlTranslate > existingHardQuestion.lvlTranslate) {
                    log("dawdawdf add hardQuestion")
                    questionHardMap[question.numQuestion] = question
                }
            }
        }

        var filteredQuestionList: List<QuestionEntity> =
            questionMap.values.toList() + questionHardMap.values.toList()
        var i = filteredQuestionList.size
        Thread {

            val userLanguage: String = Locale.getDefault().language
            val trueQuestionsMap = mutableMapOf<Int, QuestionEntity>()
            val falseQuestionsMap = mutableMapOf<Int, QuestionEntity>()

            filteredQuestionList
                .filter { it.language == userLanguage || it.language.isEmpty() }
                .forEach { question ->
                    if (question.hardQuestion) trueQuestionsMap[question.numQuestion] = question
                    else falseQuestionsMap[question.numQuestion] = question
                }

            val numSizeList = filteredQuestionList.size
            val filteredQuestions = mutableListOf<QuestionEntity>()
            filteredQuestions.addAll(trueQuestionsMap.values)
            filteredQuestions.addAll(falseQuestionsMap.values)


            translateToUserLanguage(filteredQuestionList).forEach {
                countTranslateQuestion++
                viewModel.insertQuestion(it.copy(id = null))
                log("dawdawdf $i")
                i--
                if (i == 0) context.runOnUiThread {
                    viewModel.updateQuiz(
                        quizEntity.copy(
                            languages = removeDuplicateWordsFromLanguages(
                                if (quizEntity.languages.isNotEmpty()) "${quizEntity.languages}$SPLIT_BETWEEN_LANGUAGES${Locale.getDefault().language}$SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG$LVL_GOOGLE_TRANSLATOR"
                                else "${Locale.getDefault().language}$SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG$LVL_GOOGLE_TRANSLATOR"
                            ), versionQuiz = quizEntity.versionQuiz + 1
                        )
                    )

                    loadText.postValue("")
                    Toast.makeText(context, R.string.translation_success, Toast.LENGTH_SHORT)
                        .show()
                }
                setLoadPB(countTranslateQuestion, numSizeList)
            }
            val profile = viewModel.getProfile()
            viewModel.updateProfileUseCase(profile.copy(
                addMassage = context.getString(R.string.translation_failed),
                addPointsNolics = profile.addPointsNolics?.plus(nolicResult)))
            nolicResult = 0
        }.start()
    }

    private fun setLoadPB(value: Int, max: Int) {
        log("ioioioio fun ${(value * 100) / max}%")
        Values.loadProgress.postValue((value * 100) / max)
    }

    private fun translateToUserLanguage(questionList: List<QuestionEntity>): List<QuestionEntity> {

        val translate: com.google.cloud.translate.Translate? = TranslateOptions.newBuilder()
            .setApiKey(getTranslateKey())
            .build()
            .service

        val translatedQuestionList: MutableList<QuestionEntity> = mutableListOf()

        log("dawdawdf questionList:$questionList ")
        for (question in questionList) {
            // Получаем текст для перевода
            val textToTranslate = question.nameQuestion
            val sourceLanguage = question.language

            val userLanguage: String = Locale.getDefault().language

            log("dawdawdf question $question")
            try {
                if (sourceLanguage != userLanguage) {
                    val translation: Translation = translate!!.translate(
                        textToTranslate,
                        com.google.cloud.translate.Translate.TranslateOption.sourceLanguage(
                            sourceLanguage
                        ),
                        com.google.cloud.translate.Translate.TranslateOption.targetLanguage(
                            userLanguage
                        )
                    )
                    log("dawdawdf translation:$translation")
                    log("dawdawdf translation.translatedText:${translation.translatedText}")
                    translatedQuestionList.add(
                        question.copy(
                            nameQuestion = translation.translatedText,
                            language = userLanguage,
                            lvlTranslate = LVL_GOOGLE_TRANSLATOR,
                            infoTranslater = DEFAULT_INFO_TRANSLATOR_BY_GOOGLE_TRANSL
                        )
                    )
                } else nolicResult += COEF_COAST_GOOGLE_TRANSLATE
            } catch (e: Exception) {
                log("dawdawdf error: $e")
            }

        }
        return translatedQuestionList
    }

    private fun removeDuplicateWordsFromLanguages(input: String): String {
        val languages = input.split(SPLIT_BETWEEN_LANGUAGES) // Разделение строки на отдельные языки
        val uniqueLanguages =
            languages.map { removeDuplicateWords(it) } // Удаление дубликатов слов для каждого языка
        return uniqueLanguages.joinToString(SPLIT_BETWEEN_LANGUAGES) // Объединение языков обратно в строку
    }

    //TODO  testing this function
    private fun removeDuplicateWords(input: String): String {
        val words = input.split(" ")
        val uniqueWords = words.distinct()
        return uniqueWords.joinToString(" ")
    }
}