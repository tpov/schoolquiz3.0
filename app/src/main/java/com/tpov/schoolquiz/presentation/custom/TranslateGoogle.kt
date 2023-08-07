package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.widget.Toast
import com.google.cloud.translate.TranslateOptions
import com.google.cloud.translate.Translation
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.network.event.log
import com.tpov.schoolquiz.secure.secureCode.getTranslateKey
import kotlinx.coroutines.InternalCoroutinesApi
import org.jetbrains.anko.runOnUiThread
import java.util.*

object TranslateGoogle {
    @OptIn(InternalCoroutinesApi::class)
    suspend fun translateText(
        viewModel: MainActivityViewModel,
        context: Context,
        quizEntity: QuizEntity
    ) {
        val questionList = viewModel.getQuestionListByIdQuiz(quizEntity.id!!)

        // Создаем мапу, где ключом будет numQuestion, а значением - элемент Question с наибольшим lvlTranslate
        val questionMap: MutableMap<Int, QuestionEntity> = mutableMapOf()
        val questionHardMap: MutableMap<Int, QuestionEntity> = mutableMapOf()

        for (question in questionList) {

            com.tpov.schoolquiz.presentation.network.event.log("dawdawdf $question")
            val existingQuestion = questionMap[question.numQuestion]
            val existingHardQuestion = questionHardMap[question.numQuestion]

            if (!question.hardQuestion) {
                if (existingQuestion == null || question.lvlTranslate > existingQuestion.lvlTranslate) {
                    com.tpov.schoolquiz.presentation.network.event.log("dawdawdf add !hardQuestion")
                    questionMap[question.numQuestion] = question
                }
            } else if (question.hardQuestion) {
                if (existingHardQuestion == null || question.lvlTranslate > existingHardQuestion.lvlTranslate) {
                    com.tpov.schoolquiz.presentation.network.event.log("dawdawdf add hardQuestion")
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
                    if (question.hardQuestion) {
                        trueQuestionsMap[question.numQuestion] = question
                    } else {
                        falseQuestionsMap[question.numQuestion] = question
                    }
                }

            val filteredQuestions = mutableListOf<QuestionEntity>()
            filteredQuestions.addAll(trueQuestionsMap.values)
            filteredQuestions.addAll(falseQuestionsMap.values)

            translateToUserLanguage(filteredQuestionList).forEach {
                viewModel.insertQuestion(it.copy(id = null))
                com.tpov.schoolquiz.presentation.network.event.log("dawdawdf $i")
                i--
                if (i == 0) context.runOnUiThread {
                    viewModel.updateQuiz(
                        quizEntity.copy(
                            languages = removeDuplicateWordsFromLanguages(
                                if (quizEntity.languages.isNotEmpty()) "${quizEntity.languages}|${Locale.getDefault().language}-100"
                                else "${Locale.getDefault().language}-100"
                            ), versionQuiz = quizEntity.versionQuiz + 1
                        )
                    )
                    Toast.makeText(context, "Перевод завершен успешно", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }.start()
    }

    private fun translateToUserLanguage(questionList: List<QuestionEntity>): List<QuestionEntity> {
        // Инициализируем объект Translate с помощью ключа API

        val translate: com.google.cloud.translate.Translate? = TranslateOptions.newBuilder()
            .setApiKey(getTranslateKey())
            .build()
            .service

        // Создаем пустой список для хранения переведенных вопросов
        val translatedQuestionList: MutableList<QuestionEntity> = mutableListOf()

        log("dawdawdf questionList:$questionList ")
        // Перебираем каждый вопрос в исходном списке и выполняем перевод
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
                            lvlTranslate = 100,
                            infoTranslater = "0|0"
                        )
                    )
                }
            } catch (e: Exception) {

            }

        }

        return translatedQuestionList
    }

    private fun removeDuplicateWordsFromLanguages(input: String): String {
        val languages = input.split("|") // Разделение строки на отдельные языки
        val uniqueLanguages =
            languages.map { removeDuplicateWords(it) } // Удаление дубликатов слов для каждого языка
        return uniqueLanguages.joinToString("|") // Объединение языков обратно в строку
    }

    private fun removeDuplicateWords(input: String): String {
        val words = input.split(" ")
        val uniqueWords = words.distinct()
        return uniqueWords.joinToString(" ")
    }
}