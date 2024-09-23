package com.tpov.schoolquiz

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity

object Quiz5 {
    val quizEntity5 = QuizEntity(
        nameQuiz = "Великие сражения",
        numQ = 2,
        numHQ = 2,
        languages = "ru|ua|en",
        dataUpdate = (System.currentTimeMillis() / 1000).toString(),
        userName = "TPOV",
        event = 8
    )

    val questionsEntity11 = QuestionEntity(
        nameQuestion = "Когда произошло сражение при Ватерлоо?", // Русский
        answer = 1, // 1815 — правильный ответ на первом месте
        nameAnswers = "1815|1805|1799|1821",
        hardQuestion = true,
        language = "ru",
        numQuestion = 1,
    )

    val questionsEntity11Ua = QuestionEntity(
        nameQuestion = "Коли відбулася битва під Ватерлоо?", // Украинский
        answer = 1, // 1815 — правильный ответ на первом месте
        nameAnswers = "1815|1805|1799|1821",
        hardQuestion = true,
        language = "ua",
        numQuestion = 1,
    )

    val questionsEntity11En = QuestionEntity(
        nameQuestion = "When did the Battle of Waterloo take place?", // Английский
        answer = 1, // 1815 — правильный ответ на первом месте
        nameAnswers = "1815|1805|1799|1821",
        hardQuestion = true,
        language = "en",
        numQuestion = 1,
    )

    val questionsEntity12 = QuestionEntity(
        nameQuestion = "Когда состоялась битва при Гастингсе?", // Русский
        answer = 1, // 1066 — правильный ответ на первом месте
        nameAnswers = "1066|1075|1056|1081",
        hardQuestion = true,
        language = "ru",
        numQuestion = 2,
    )

    val questionsEntity12Ua = QuestionEntity(
        nameQuestion = "Коли відбулася битва під Гастінгсом?", // Украинский
        answer = 1, // 1066 — правильный ответ на первом месте
        nameAnswers = "1066|1075|1056|1081",
        hardQuestion = true,
        language = "ua",
        numQuestion = 2,
    )

    val questionsEntity12En = QuestionEntity(
        nameQuestion = "When did the Battle of Hastings take place?", // Английский
        answer = 1, // 1066 — правильный ответ на первом месте
        nameAnswers = "1066|1075|1056|1081",
        hardQuestion = true,
        language = "en",
        numQuestion = 2,
    )

    val questionsEntityHistory = arrayListOf(
        questionsEntity11, questionsEntity11Ua, questionsEntity11En,
        questionsEntity12, questionsEntity12Ua, questionsEntity12En
    )

    val structureCategoryDataEntityHistory = StructureCategoryDataEntity(
        newEventId = 8,
        newCategoryName = "История",
        newSubCategoryName = "Великие события",
        newSubsubCategoryName = "Сражения",
        newQuizName = "Великие сражения",
    )
}