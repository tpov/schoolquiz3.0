package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.PrivateQuizRunnerComponent

/**
 * Адрес попытки приватного квиза — только указатель. Право решает дверь на сервере, поэтому
 * «разрешение на прогон» здесь не живёт и в состояние процесса не сериализуется: после смерти
 * процесса ребёнок навигации снимается, и повторный вход снова проходит дверь (AD-6/AD-29).
 */
data class PrivateQuizRunnerRequest(
    val ownerUid: String,
    val catalogId: String,
    val questId: String,
    val title: String,
)

/** Отдельная фабрика: обычный четырёхаргументный SAM ранеров не меняется. */
fun interface PrivateQuizRunnerComponentFactory {
    fun create(
        context: ComponentContext,
        request: PrivateQuizRunnerRequest,
    ): PrivateQuizRunnerComponent
}
