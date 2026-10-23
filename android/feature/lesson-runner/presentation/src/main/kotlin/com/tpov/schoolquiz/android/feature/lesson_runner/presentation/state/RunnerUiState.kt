package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ResultAdvice
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptSyncMark
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

sealed interface RunnerUiState {
    data object Loading : RunnerUiState

    data class InitFailed(
        val reason: InitFailureReason,
    ) : RunnerUiState

    data class Question(
        val questionUiState: QuestionUiState,
        val indexInPool: Int,
        val totalInPool: Int,
        val deadlineMs: Long,
        val isPaused: Boolean,
        val isHard: Boolean,
        /** Whether the right answer may be shown after answering (practice on easy only). */
        val revealCorrect: Boolean = true,
        val showExitConfirmDialog: Boolean,
        val currentDraft: UserAnswerDraft? = null,
        /**
         * Hearts left this session, mirrored locally for the header pill and hint affordability.
         * Null when the profile is unreadable — the pill and hint stay hidden/disabled rather
         * than guessing. Server-side charging is out of scope here.
         */
        val lives: Int? = null,
        /** Raw timeouts submit only actual input, never a key-dependent guessed answer. */
        val serverScored: Boolean = false,
    ) : RunnerUiState

    data class Result(
        /** Provisional local calculation; result UI must use serverSettlement for score/reward. */
        val percentScore: PercentScore?,
        val mode: Difficulty,
        val completedAt: Long,
        val hardUnlocked: Boolean,
        val bestStarsRawTenths: Int,
        /** Stars earned in THIS attempt (spec §148), distinct from best stars across all attempts. */
        val currentAttemptStarsRawTenths: Int?,
        val lessonAverageRating: Float?,
        val lessonRatingCount: Int,
        val top3: List<TopParticipant>,
        val userAttemptCount: Int,
        val userAveragePercentScore: Int?,
        val userBestPercentScore: Int? = percentScore?.raw,
        /** What to study next, or null when the run was good enough not to need telling. */
        val advice: ResultAdvice? = null,
        /**
         * Per-question score digits ('0'..'9', '0' = not shown) in play order — the raw material
         * of the accuracy chart on the result card.
         */
        val questionScores: List<Int> = emptyList(),
        /**
         * Full hearts still standing after this attempt, over capacity. Null when the profile is
         * not readable; the lives figure stays hidden rather than showing a made-up count.
         */
        val livesRemainingHearts: Int? = null,
        val livesMaxHearts: Int? = null,
        /** Local intent only. Render/submit the prompt exclusively through canSubmitRating. */
        val showRatingPrompt: Boolean,
        val saveWarning: Boolean,
        /**
         * Что очередь знает про это прохождение (AD-14): ждёт отправки, ушло в карантин или
         * ждать нечего. Приезжает ответом ядра по `entity_ref`, а не чтением таблицы очереди
         * этой фичей — и не `Boolean`, потому что карантин и ожидание игроку говорятся разными
         * словами.
         */
        val syncMark: AttemptSyncMark = AttemptSyncMark.NONE,
        val ratingSubmissionState: RatingSubmissionState = RatingSubmissionState.Idle,
        val attemptId: AttemptId? = null,
        val serverSettlement: ServerAttemptSettlement = ServerAttemptSettlement(ServerAttemptSettlementState.PENDING),
        /**
         * Приватный прогон с выключенным показом результата (AD-29/UX-16): сервер прислал
         * только подтверждение завершения — без счёта, процента и по-вопросных цифр. Карточка
         * результата показывает завершение и НЕ рисует прочерки вместо скрытых чисел: процент,
         * звёзды и разбор вопросов у приватного прогона скрытого результата не существуют.
         */
        val privateCompletionOnly: Boolean = false,
    ) : RunnerUiState {
        val displayedPercentScore: PercentScore?
            get() = serverSettlement.takeIf { it.hasAccountingProof }?.percentScore

        val displayedReward
            get() = serverSettlement.takeIf { it.hasAccountingProof }?.reward

        val canRequestSync: Boolean
            get() =
                attemptId != null && !saveWarning && syncMark == AttemptSyncMark.NOT_SENT &&
                    serverSettlement.ownerVerified && serverSettlement.state in
                    setOf(
                        ServerAttemptSettlementState.PENDING,
                        ServerAttemptSettlementState.UNSCORABLE,
                    )

        val canSubmitRating: Boolean
            get() =
                showRatingPrompt && !saveWarning && syncMark != AttemptSyncMark.QUARANTINED &&
                    serverSettlement.canQueueRating
    }

    sealed interface RatingSubmissionState {
        data object Idle : RatingSubmissionState

        data object InProgress : RatingSubmissionState

        data object Failed : RatingSubmissionState

        data object Done : RatingSubmissionState
    }

    enum class InitFailureReason {
        AuthRequired,
        LessonNotFound,
        EmptyPool,
        NoValidQuestions,

        /**
         * These public questions lack a supported start capability (raw EASY or unknown difficulty). Its
         * own case rather than a flavour of [NoValidQuestions]: broken bytes and a deliberately
         * withheld answer are different facts, and the screen says so.
         *
         * Raw HARD is supported; this gate must not imply that its keys should be downloaded.
         */
        RedactedNotSupported,
    }
}
