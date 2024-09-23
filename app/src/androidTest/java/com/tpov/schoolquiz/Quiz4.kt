package com.tpov.schoolquiz

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity

object Quiz4 {
    val quizEntity4 = QuizEntity(
        nameQuiz = "Столицы Северной Америки",
        numQ = 2,
        numHQ = 2,
        languages = "ru|ua|en",
        dataUpdate = (System.currentTimeMillis() / 1000).toString(),
        userName = "TPOV",
        event = 8
    )

    val questionsEntity9 = QuestionEntity(
        nameQuestion = "Как называется столица США?", // Русский
        answer = 1, // Вашингтон — правильный ответ на первом месте
        nameAnswers = "Вашингтон|Оттава|Мехико|Гавана",
        hardQuestion = false,
        language = "ru",
        numQuestion = 1,
    )

    val questionsEntity9Ua = QuestionEntity(
        nameQuestion = "Як називається столиця США?", // Украинский
        answer = 1, // Вашингтон — правильный ответ на первом месте
        nameAnswers = "Вашингтон|Оттава|Мехіко|Гавана",
        hardQuestion = false,
        language = "ua",
        numQuestion = 1,
    )

    val questionsEntity9En = QuestionEntity(
        nameQuestion = "What is the capital of the USA?", // Английский
        answer = 1, // Вашингтон — правильный ответ на первом месте
        nameAnswers = "Washington|Ottawa|Mexico City|Havana",
        hardQuestion = false,
        language = "en",
        numQuestion = 1,
    )

    val questionsEntity10 = QuestionEntity(
        nameQuestion = "Как называется столица Канады?", // Русский
        answer = 1, // Оттава — правильный ответ на первом месте
        nameAnswers = "Оттава|Вашингтон|Мехико|Гавана",
        hardQuestion = false,
        language = "ru",
        numQuestion = 2,
    )

    val questionsEntity10Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Канади?", // Украинский
        answer = 1, // Оттава — правильный ответ на первом месте
        nameAnswers = "Оттава|Вашингтон|Мехіко|Гавана",
        hardQuestion = false,
        language = "ua",
        numQuestion = 2,
    )

    val questionsEntity10En = QuestionEntity(
        nameQuestion = "What is the capital of Canada?", // Английский
        answer = 1, // Оттава — правильный ответ на первом месте
        nameAnswers = "Ottawa|Washington|Mexico City|Havana",
        hardQuestion = false,
        language = "en",
        numQuestion = 2,
    )

    val questionsEntityNorthAmerica = arrayListOf(
        questionsEntity9, questionsEntity9Ua, questionsEntity9En,
        questionsEntity10, questionsEntity10Ua, questionsEntity10En
    )

    val structureCategoryDataEntityNorthAmerica = StructureCategoryDataEntity(
        newEventId = 8,
        newCategoryName = "География",
        newSubCategoryName = "Северная Америка",
        newSubsubCategoryName = "Столицы",
        newQuizName = "Столицы Северной Америки",
    )
}