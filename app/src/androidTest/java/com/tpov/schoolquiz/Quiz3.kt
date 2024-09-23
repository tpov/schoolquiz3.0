package com.tpov.schoolquiz

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity

object Quiz3 {
    val quizEntity3 = QuizEntity(
        nameQuiz = "Столицы Африки",
        numQ = 2,
        numHQ = 2,
        languages = "ru|ua|en",
        dataUpdate = (System.currentTimeMillis() / 1000).toString(),
        userName = "TPOV",
        event = 8
    )

    val questionsEntity7 = QuestionEntity(
        nameQuestion = "Как называется столица Египта?", // Русский
        answer = 1, // Каир — правильный ответ на первом месте
        nameAnswers = "Каир|Лагос|Кейптаун|Рабат",
        hardQuestion = false,
        language = "ru",
        numQuestion = 1,
    )

    val questionsEntity7Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Єгипту?", // Украинский
        answer = 1, // Каир — правильный ответ на первом месте
        nameAnswers = "Каїр|Лагос|Кейптаун|Рабат",
        hardQuestion = false,
        language = "ua",
        numQuestion = 1,
    )

    val questionsEntity7En = QuestionEntity(
        nameQuestion = "What is the capital of Egypt?", // Английский
        answer = 1, // Каир — правильный ответ на первом месте
        nameAnswers = "Cairo|Lagos|Cape Town|Rabat",
        hardQuestion = false,
        language = "en",
        numQuestion = 1,
    )

    val questionsEntity8 = QuestionEntity(
        nameQuestion = "Как называется столица Нигерии?", // Русский
        answer = 1, // Абуджа — правильный ответ на первом месте
        nameAnswers = "Абуджа|Каир|Кейптаун|Рабат",
        hardQuestion = false,
        language = "ru",
        numQuestion = 2,
    )

    val questionsEntity8Ua = QuestionEntity(
        nameQuestion = "Як називається столиця Нігерії?", // Украинский
        answer = 1, // Абуджа — правильный ответ на первом месте
        nameAnswers = "Абуджа|Каїр|Кейптаун|Рабат",
        hardQuestion = false,
        language = "ua",
        numQuestion = 2,
    )

    val questionsEntity8En = QuestionEntity(
        nameQuestion = "What is the capital of Nigeria?", // Английский
        answer = 1, // Абуджа — правильный ответ на первом месте
        nameAnswers = "Abuja|Cairo|Cape Town|Rabat",
        hardQuestion = false,
        language = "en",
        numQuestion = 2,
    )

    val questionsEntityAfrica = arrayListOf(
        questionsEntity7, questionsEntity7Ua, questionsEntity7En,
        questionsEntity8, questionsEntity8Ua, questionsEntity8En
    )

    val structureCategoryDataEntityAfrica = StructureCategoryDataEntity(
        newEventId = 8,
        newCategoryName = "География",
        newSubCategoryName = "Африка",
        newSubsubCategoryName = "Столицы",
        newQuizName = "Столицы Африки",
    )
}