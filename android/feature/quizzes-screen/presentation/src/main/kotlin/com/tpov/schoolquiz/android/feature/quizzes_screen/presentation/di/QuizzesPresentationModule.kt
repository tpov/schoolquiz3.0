package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import org.koin.dsl.module

val quizzesPresentationModule = module {
    factory<QuizzesComponent> { (ctx: ComponentContext) ->
        DefaultQuizzesComponent(
            componentContext = ctx,
            questRepository = get(),
            sectionRepository = get(),
            themeRepository = get(),
            lessonRepository = get(),
        )
    }
}
