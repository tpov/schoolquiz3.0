package com.tpov.schoolquiz

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity

object Quiz1 {
    val quizEntity1 = QuizEntity(
        nameQuiz = "Столицы Европы",
        numQ = 2,
        numHQ = 2,
        languages = "ru|ua|en",
        dataUpdate = (System.currentTimeMillis() / 1000).toString(),
        userName = "TPOV",
        event = 8
    )

    val questionsEntity1 = QuestionEntity(
        nameQuestion = "Как называется столица Франции?",
        answer = 1,
        nameAnswers = "Париж|Лондон|Берлин|Мадрид",
        hardQuestion = false,
        language = "ru", // Русский язык
        numQuestion = 1,
    )

    val questionsEntity1Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Франції?",
        answer = 1,
        nameAnswers = "Париж|Лондон|Берлін|Мадрид",
        hardQuestion = false,
        language = "ua", // Украинский язык
        numQuestion = 1,
    )

    val questionsEntity1En = QuestionEntity(
        nameQuestion = "What is the capital of France?", // Английский
        answer = 1, // Париж — правильный ответ на первом месте
        nameAnswers = "Paris|London|Berlin|Madrid",
        hardQuestion = false,
        language = "en", // Английский язык
        numQuestion = 1,
    )

    val questionsEntity2 = QuestionEntity(
        nameQuestion = "Как называется столица Германии?", // Русский
        answer = 1, // Берлин — правильный ответ на первом месте
        nameAnswers = "Берлин|Париж|Лондон|Мадрид",
        hardQuestion = false,
        language = "ru", // Русский язык
        numQuestion = 2,
    )

    val questionsEntity2Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Німеччини?", // Украинский
        answer = 1, // Берлин — правильный ответ на первом месте
        nameAnswers = "Берлін|Париж|Лондон|Мадрид",
        hardQuestion = false,
        language = "ua", // Украинский язык
        numQuestion = 2,
    )

    val questionsEntity2En = QuestionEntity(
        nameQuestion = "What is the capital of Germany?", // Английский
        answer = 1, // Берлин — правильный ответ на первом месте
        nameAnswers = "Berlin|Paris|London|Madrid",
        hardQuestion = false,
        language = "en", // Английский язык
        numQuestion = 2,
    )

    val questionsEntity3 = QuestionEntity(
        nameQuestion = "Как называется столица Испании?", // Русский
        answer = 1, // Мадрид — правильный ответ на первом месте
        nameAnswers = "Мадрид|Париж|Лондон|Берлин",
        hardQuestion = true,
        language = "ru", // Русский язык
        numQuestion = 3,
    )

    val questionsEntity3Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Іспанії?", // Украинский
        answer = 1, // Мадрид — правильный ответ на первом месте
        nameAnswers = "Мадрид|Париж|Лондон|Берлін",
        hardQuestion = true,
        language = "ua", // Украинский язык
        numQuestion = 3,
    )

    val questionsEntity3En = QuestionEntity(
        nameQuestion = "What is the capital of Spain?", // Английский
        answer = 1, // Мадрид — правильный ответ на первом месте
        nameAnswers = "Madrid|Paris|London|Berlin",
        hardQuestion = true,
        language = "en", // Английский язык
        numQuestion = 3,
    )

    val questionsEntity4 = QuestionEntity(
        nameQuestion = "Как называется столица Великобритании?", // Русский
        answer = 1, // Лондон — правильный ответ на первом месте
        nameAnswers = "Лондон|Мадрид|Берлин|Париж",
        hardQuestion = true,
        language = "ru", // Русский язык
        numQuestion = 4,
    )

    val questionsEntity4Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Великобританії?", // Украинский
        answer = 1, // Лондон — правильный ответ на первом месте
        nameAnswers = "Лондон|Мадрид|Берлін|Париж",
        hardQuestion = true,
        language = "ua", // Украинский язык
        numQuestion = 4,
    )

    val questionsEntity4En = QuestionEntity(
        nameQuestion = "What is the capital of the United Kingdom?", // Английский
        answer = 1,
        nameAnswers = "London|Madrid|Berlin|Paris",
        hardQuestion = true,
        language = "en",
        numQuestion = 4
    )

    // Добавляем все вопросы в список
    val questionsEntity = arrayListOf(
        questionsEntity1, questionsEntity1Ua, questionsEntity1En,
        questionsEntity2, questionsEntity2Ua, questionsEntity2En,
        questionsEntity3, questionsEntity3Ua, questionsEntity3En,
        questionsEntity4, questionsEntity4Ua, questionsEntity4En
    )

    val structureCategoryDataEntity = StructureCategoryDataEntity(
        newEventId = 8,
        newCategoryName = "География",
        newSubCategoryName = "Европа",
        newSubsubCategoryName = "Столицы",
        newQuizName = "Столицы Европы",
    )


}