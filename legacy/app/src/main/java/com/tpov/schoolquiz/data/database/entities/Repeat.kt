package com.tpov.schoolquiz.data.database.entities

//Этоот обьект будет только локальным, пользователь сможет сгененрировать этот список заново в нстройках в пункте "Сгенерировать вопросы на повторения"
data class RepeatEntity(
    val dataRepeat: String,
    val dayRepeat: Int,
    val timesInRow: Int
)

val listDaysRepeat = listOf<Int>(1, 3, 7, 14, 30, 60, 120, 240)
