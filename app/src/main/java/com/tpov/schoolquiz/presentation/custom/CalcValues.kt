package com.tpov.schoolquiz.presentation.custom

import com.tpov.schoolquiz.data.database.entities.ProfileEntity

object CalcValues {

    private const val COEF_COIN_HARD_QUIZ = 3
    private const val COEF_SKILL_NOLIC_FOR_GAME = 1

    fun getValueSkillForFame(hardQuestion: Boolean, result: Int, event: Int, first: Boolean, profile: ProfileEntity) =
        getValueNolicForGame(hardQuestion, result, event, first, profile) / COEF_SKILL_NOLIC_FOR_GAME

    fun getValueNolicForGame(hardQuestion: Boolean, result: Int, event: Int, first: Boolean, profile: ProfileEntity): Int {
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

        when (event) {
            1 -> counts /= 10
            2 -> counts = profile.tester?.times(2)!!
            3 -> counts = profile.moderator?.times(3)!!
            4 -> counts = profile.admin?.times(4)!!
            5 -> counts
            6 -> counts * 5
            7 -> counts * 10
            8 -> if (result == 100 && first) counts * 2
            else -> counts /= 2
        }

        return counts
    }

    fun getSkillByTimeInGame(time: Int) = (time / 30_000) //max const = 100 000

    fun getSkillByTimeInChat(time: Int) = time / 30_000
}
