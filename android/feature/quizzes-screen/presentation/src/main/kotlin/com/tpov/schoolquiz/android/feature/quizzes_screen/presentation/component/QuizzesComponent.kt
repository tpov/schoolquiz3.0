package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.navigation.QuizzesNavigator

interface QuizzesComponent : QuizzesNavigator {
    val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>>

    /**
     * Breadcrumb segment tap.
     * @param uiLevel 0-based index of the user-visible segment (catalog=0, quest=1, ...).
     * Internally calls navigation.popTo(uiLevel + 1) — offset +1 for Idle anchor at stack[0].
     */
    fun popToLevel(uiLevel: Int)
}
