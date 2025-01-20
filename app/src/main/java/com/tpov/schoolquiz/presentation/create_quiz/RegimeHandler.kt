package com.tpov.schoolquiz.presentation.create_quiz

interface RegimeHandler  {
    fun initViews()
    fun initData()
    suspend fun saveData()
}