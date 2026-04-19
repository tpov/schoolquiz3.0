package com.tpov.schoolquiz.presentation.model

    data class LivesState(
        val standardLife: Int = 100,
        val standardHearts: Int = 1,
        val goldLife: Int = 0,
        val goldHearts: Int = 0,
        val updateTime: Long = 0
    )
