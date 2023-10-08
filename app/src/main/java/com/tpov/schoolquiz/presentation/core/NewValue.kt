package com.tpov.schoolquiz.presentation.core

object NewValue {

    // Example 0.01 -> 1
    fun setNewSkill(skill: Int?) {
        if (skill != null) {
            if (skill >= 1_000) addBonusPlaceForUserQuiz()
        }
    }

    private fun addBonusPlaceForUserQuiz() {

    }

}