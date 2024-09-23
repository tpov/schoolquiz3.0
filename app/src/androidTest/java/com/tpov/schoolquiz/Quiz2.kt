package com.tpov.schoolquiz

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity

object Quiz2 {
    // Новый квиз для Азии
    val quizEntity2 = QuizEntity(
        nameQuiz = "Столицы Азии",
        numQ = 2,
        numHQ = 2,
        languages = "ru|ua|en",
        dataUpdate = (System.currentTimeMillis() / 1000).toString(),
        userName = "TPOV",
        event = 1
    )

    val questionsEntity5 = QuestionEntity(
        nameQuestion = "Как называется столица Японии?", // Русский
        answer = 1, // Токио — правильный ответ на первом месте
        nameAnswers = "Токио|Пекин|Сеул|Бангкок",
        hardQuestion = false,
        language = "ru",
        numQuestion = 1,
    )

    val questionsEntity5Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Японії?", // Украинский
        answer = 1, // Токио — правильный ответ на первом месте
        nameAnswers = "Токіо|Пекін|Сеул|Бангкок",
        hardQuestion = false,
        language = "ua",
        numQuestion = 1,
    )

    val questionsEntity5En = QuestionEntity(
        nameQuestion = "What is the capital of Japan?", // Английский
        answer = 1, // Токио — правильный ответ на первом месте
        nameAnswers = "Tokyo|Beijing|Seoul|Bangkok",
        hardQuestion = false,
        language = "en",
        numQuestion = 1,
    )

    val questionsEntity6 = QuestionEntity(
        nameQuestion = "Как называется столица Китая?", // Русский
        answer = 1, // Пекин — правильный ответ на первом месте
        nameAnswers = "Пекин|Токио|Сеул|Бангкок",
        hardQuestion = false,
        language = "ru",
        numQuestion = 2,
    )

    val questionsEntity6Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Китаю?", // Украинский
        answer = 1, // Пекин — правильный ответ на первом месте
        nameAnswers = "Пекін|Токіо|Сеул|Бангкок",
        hardQuestion = false,
        language = "ua",
        numQuestion = 2,
    )

    val questionsEntity6En = QuestionEntity(
        nameQuestion = "What is the capital of China?", // Английский
        answer = 1, // Пекин — правильный ответ на первом месте
        nameAnswers = "Beijing|Tokyo|Seoul|Bangkok",
        hardQuestion = false,
        language = "en",
        numQuestion = 2,
    )

    // Добавляем все вопросы для квиза "Азия" в список
    val questionsEntityAsia = arrayListOf(
        questionsEntity5, questionsEntity5Ua, questionsEntity5En,
        questionsEntity6, questionsEntity6Ua, questionsEntity6En
    )

    val structureCategoryDataEntityAsia = StructureCategoryDataEntity(
        newEventId = 1,
        newCategoryName = "География",
        newSubCategoryName = "Азия",
        newSubsubCategoryName = "Столицы",
        newQuizName = "Столицы Азии",
    )
}