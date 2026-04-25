package com.tpov.schoolquiz.shared.core.foundation

enum class QualificationLevel(val points: Int) {
    LEVEL_1(100),
    LEVEL_2(200),
    LEVEL_3(300),
}

fun QualificationLevel.isReachedBy(points: Int): Boolean = points >= this.points
