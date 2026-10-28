package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.ErrorReportSubmissionState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface LessonRunnerRootComponent {
    val uiState: StateFlow<RunnerUiState>
    val events: Flow<RunnerEvent>

    /** Newest-last discussion comments for the current lesson. */
    val comments: StateFlow<List<LessonComment>>

    /** True when this runner session is in HARD mode — used for FLAG_SECURE (AC-28). */
    val isHardMode: Boolean

    /**
     * Платная мутация застряла в отказе terms-consent (CAP-16): нужно решение игрока. True
     * поднимает диалог согласия на экране результата; после [acceptTerms] возвращается в false.
     */
    val needsTermsConsent: StateFlow<Boolean>

    /** Решение игрока за условиями: фиксирует согласие и возвращает запись в отправку. */
    fun acceptTerms()

    /**
     * «Позже»/dismiss по диалогу согласия (ретро L5): флаг сбрасывается, экран результата
     * остаётся на месте. Диалог может подняться снова — следующим явным запросом синхронизации,
     * у которого для этого прогона есть незакрытая работа.
     */
    fun onDismissTermsConsent()

    fun onAnswer(answer: UserAnswerDraft)

    /** Called while user edits the current question — persists draft across rotation. */
    fun onDraftChanged(draft: UserAnswerDraft)

    fun onTimeout()

    fun onContinue()

    fun onExit()

    fun onCrossButtonTap()

    fun onCrossConfirmed()

    fun onCrossCancelled()

    fun onSubmitRating(rating: Int)

    /** Explicit manual synchronization, without leaving the pending result or changing its ID. */
    fun onSyncResult()

    fun onFinish()

    /** Restart the same lesson as a fresh attempt (design decision F3). */
    fun onRunAgain()

    /** Open the next lesson of the theme, or fall back to [onFinish] when there is none. */
    fun onNextLesson()

    /**
     * Spend one life for the hint (design §4.4). Returns true when a life was spent; false when
     * no life budget is known or it is already empty.
     */
    fun hintRequested(): Boolean

    /** Publishes a trimmed, non-empty discussion comment for the current lesson. */
    fun onPostComment(text: String)

    /**
     * «Сообщить об ошибке» как предложение автору (CAP-6/27, история 4.22): цитата платы
     * предложения (UX-23). null — цитата ещё не приходила или сервер недоступен; строка платы
     * честно показывает «—», отправку это не блокирует.
     */
    val proposalFeeQuote: StateFlow<LessonProposalFeeQuote?>

    /** Состояние подачи отчёта: ожидание → «Отправляется…» → подано/отказ. */
    val errorReportState: StateFlow<ErrorReportSubmissionState>

    /** Диалог отчёта открыт: спрашиваем плату до отправки (показ, не власть — платит приёмка). */
    fun onReportDialogOpened()

    /** Подача отчёта: текст про весь урок + опциональный свой вариант (свободный текст). */
    fun onReportError(
        text: String,
        ownVariant: String?,
    )

    fun onBack()
}

/**
 * Ручки платного согласия хоста (CAP-16): [satisfied] сверяет захваченные условия с таблицей,
 * [accept] фиксирует решение игрока (acceptCurrentTerms) и возвращает ждущие записи в отправку.
 * null в конструкторах ранееров — хост без гейта: синхронизация идёт как раньше.
 */
data class LessonConsentHandles(
    val satisfied: () -> Boolean,
    val accept: suspend () -> Unit,
)
