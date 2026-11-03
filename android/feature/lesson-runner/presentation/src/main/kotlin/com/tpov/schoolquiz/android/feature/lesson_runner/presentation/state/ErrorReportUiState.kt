package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state

/**
 * Состояние подачи отчёта об ошибке (история 4.22): кнопка диалога читает его, чтобы показать
 * «Отправляется…» в полёте и подтверждение после ответа очереди. Постановка в очередь — вся
 * локальная semantика: исход проверки и решение автора приходят сервером («Предложения», 4.25).
 */
enum class ErrorReportSubmissionState {
    Idle,
    Sending,
    Done,
    Failed,
}
