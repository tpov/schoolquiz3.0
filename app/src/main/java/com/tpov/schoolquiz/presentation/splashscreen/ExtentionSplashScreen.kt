package com.tpov.schoolquiz.presentation.splashscreen

import com.tpov.schoolquiz.data.database.entities.ApiQuestion

fun apiQuestion(it: ApiQuestion) = ApiQuestion(
    it.id,
    it.date,
    it.question,
    it.answer,
    it.questionTranslate,
    it.answerTranslate
)

fun SplashScreenViewModel.getApiQuestion(i: Int) = ApiQuestion(
    null,
    "0",
    questionApiArray!![i],
    answerApiArray!![i],
    questionApiArray!![i],
    answerApiArray!![i]
)