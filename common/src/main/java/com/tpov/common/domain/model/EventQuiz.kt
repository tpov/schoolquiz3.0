package com.tpov.common.domain.model

enum class EventQuiz(val id: Int) {
    QUIZ_HOME(8),
    QUIZ_TOURNIRE_LEADER(7),
    QUIZ_TOURNIRE(6),
    QUIZ_ARENA(5),
    QUIZ_FOR_ADMIN(4),
    QUIZ_FOR_TRANSLATOR(3),
    QUIZ_FOR_TESTER(2),
    QUIZ_BY_USER(1);

    companion object {
       private fun fromId(id: Int): EventQuiz? = entries.find { it.id == id }

        fun fromInput(input: Any): EventQuiz? = when (input) {
            is Int -> fromId(input)
            is String -> entries.find { it.name.equals(input, ignoreCase = true) }
            else -> null
        }
    }
}
