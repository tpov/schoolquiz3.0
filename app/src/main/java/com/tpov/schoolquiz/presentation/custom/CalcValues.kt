package com.tpov.schoolquiz.presentation.custom

object CalcValues {

    private const val COEF_COIN_HARD_QUIZ = 3
    private const val COEF_SKILL_NOLIC_FOR_GAME = 100_000

    fun getValueSkillForFame(hardQuestion: Boolean, result: Int) = getValueNolicForGame(hardQuestion, result) / COEF_SKILL_NOLIC_FOR_GAME

    fun getValueNolicForGame(hardQuestion: Boolean, result: Int): Int {
        var counts = 0

        for (i in 0..result) {

            if (!hardQuestion) {
                when (i) {
                    in 0..50 -> counts += 1
                    in 51..85 -> counts += 3
                    in 85..95 -> counts += 5
                    in 95..100 -> counts += 10
                }

            } else {
                when (i) {
                    in 0..50 -> counts += 1 * COEF_COIN_HARD_QUIZ
                    in 51..85 -> counts += 3 * COEF_COIN_HARD_QUIZ
                    in 85..95 -> counts += 5 * COEF_COIN_HARD_QUIZ
                    in 95..100 -> counts += 10 * COEF_COIN_HARD_QUIZ
                }
            }
        }

        if (result == 100) counts * 2
        return counts

    }
}
