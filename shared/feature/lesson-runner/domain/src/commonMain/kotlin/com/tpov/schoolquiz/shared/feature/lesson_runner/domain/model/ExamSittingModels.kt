package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Экзаменационный сидинг — доменные проекции шести вызовов протокола (CAP-21/22).
 *
 * Здесь только то, что сервер отдаёт клиенту: проекция сидинга с текущим вопросом, типизированные
 * отказы допуска, прочные квитанции ответов и терминальный результат. Клиент НИЧЕГО из этого не
 * пересчитывает: процент, проход, звёзды и совет — серверные значения, отрисованные дословно
 * (протокол §Redaction/scoring/result). Правила отображения — таймер и линейные звёзды — живут в
 * `ExamRunRules` и являются только зеркалом серверной арифметики для обратного отсчёта.
 *
 * Приватное остаётся приватным: ни одна модель здесь не несёт `canonicalContent`, ключей,
 * `codeAnswer` или повопросных вердиктов. Публичная половина вопроса приезжает строкой
 * `publicPayload` и разбирается в `RedactedQuestionContent` (родственная иерархия, НЕ
 * `QuestionContent` с nullable-полями — протокол §Redaction).
 */

/** Вид экзаменационной оценки: контрольная темы или финал курса. */
@Serializable
enum class ExamMode {
    @SerialName("ThemeTest")
    THEME_TEST,

    @SerialName("FinalExam")
    FINAL_EXAM,
    ;

    /** Идентичность оценки на проводе: themeId или courseId. */
    val requestField: String get() = if (this == THEME_TEST) "themeId" else "courseId"
}

/** Состояние жизненного цикла сидинга: Ready→Open→FinishRequested→Closed. */
@Serializable
enum class ExamSittingState {
    @SerialName("OPEN")
    OPEN,

    @SerialName("FINISH_REQUESTED")
    FINISH_REQUESTED,

    @SerialName("CLOSED")
    CLOSED,
    ;

    /** Закрыт ли сидинг для ответов: после запроса закрытия отвечать нельзя. */
    val acceptsAnswers: Boolean get() = this == OPEN
}

/** Показанная сложность позиции: состав темы (5 EASY + 15 HARD) сервер замораживает сам. */
@Serializable
enum class ExamShownDifficulty {
    @SerialName("EASY")
    EASY,

    @SerialName("HARD")
    HARD,
}

/**
 * Закреплённый первым ACK дедлайн ответа.
 *
 * Первый ограниченный ACK «текст отрисован» ставит часы; дубликат возвращает то же закреплённое.
 * ACK никогда не перезапускает и не удлиняет открытые часы.
 */
@Serializable
data class ExamAck(
    val ackedAtMs: Long,
    val answerDeadlineMs: Long,
)

/**
 * Пока ACK не пришёл: часы первого-ACK разрыва идут (для нулевого вопроса — от создания сидинга,
 * для следующих — от окна загрузки следующего вопроса).
 */
@Serializable
data class ExamAwaitingAck(
    val windowDeadlineMs: Long?,
)

/**
 * Публичная проекция текущего вопроса — единственный вид слота, который уезжает на устройство.
 *
 * @property publicPayload редактированная половина (`SingleChoiceRedacted`, …): правильные
 *   варианты отсутствуют, порядок Ordering перемешан сервером, mapping FillBlank отсутствует.
 * @property answerSeconds серверная заморозка ответного времени; клиентский отсчёт — отображение.
 */
@Serializable
data class ExamQuestionProjection(
    val index: Int,
    val questionId: String,
    val revision: Long,
    val shownDifficulty: ExamShownDifficulty,
    val answerSeconds: Int,
    val publicPayload: String,
    val ack: ExamAck? = null,
    val awaitingAck: ExamAwaitingAck? = null,
    val serverNowMs: Long = 0L,
) {
    init {
        require(answerSeconds >= 5) { "answerSeconds must respect the 5-second floor" }
        require(index >= 0) { "index must be non-negative" }
    }
}

/** Замороженные правила сидинга: таймер, окно, проходной балл. */
@Serializable
data class ExamSittingRules(
    val nextQuestionWindowSeconds: Int,
    val firstAckGapSeconds: Int,
    val timerRule: String = "ALL_HARD_ORDINARY",
    val timerRuleVersion: Int = 1,
)

/**
 * Проекция сидинга для ответов вызовов.
 *
 * @property nextQuestion присутствует, только когда текущий слот ждёт игрока; будущие вопросы
 *   не проектируются никогда.
 */
@Serializable
data class ExamSittingProjection(
    val schemaVersion: Int = 1,
    val sittingId: String,
    val mode: ExamMode,
    val assessmentId: String,
    val state: ExamSittingState,
    val currentIndex: Int,
    val totalQuestions: Int,
    val rules: ExamSittingRules,
    val nextQuestion: ExamQuestionProjection? = null,
)

/**
 * Прочная квитанция первого зафиксированного ответа.
 *
 * Владелец, редакция и тело связаны; серверное время прибытия; одинаковый повтор возвращает ту же
 * квитанцию. Витрина квитанции не имеет цветов, пометок и длительностей, зависящих от
 * правильности.
 */
@Serializable
data class ExamAnswerReceipt(
    val receiptId: String,
    val sittingId: String,
    val index: Int,
    val revision: Long,
    val receivedAtMs: Long,
)

/**
 * Терминальный результат — единственный авторитетный итог сидинга.
 *
 * @property percent агрегатный процент сервера; клиент не пересчитывает.
 * @property passed проход против захваченного балла; сравнение с сырым процентом, не со звёздами.
 * @property starsTenths линейные звёзды темы в десятых (0..30); для финала — null (Q-EX-7 вне
 *   объёма).
 * @property advice безопасный совет фиксированной длины; провалившиеся вопросы не называются.
 */
@Serializable
data class ExamResultProjection(
    val schemaVersion: Int = 1,
    val sittingId: String,
    val mode: ExamMode,
    val assessmentId: String,
    val resultVersion: Int,
    val percent: Int,
    val passed: Boolean,
    val starsTenths: Int?,
    val advice: List<String>,
    val closedAtMs: Long,
    val nextAvailableAtMs: Long? = null,
) {
    init {
        require(percent in 0..100) { "percent must be 0..100, got $percent" }
        require(starsTenths == null || starsTenths in 0..30) {
            "starsTenths must be 0..30, got $starsTenths"
        }
    }

    /** Звёзды для отображения в звёздах (не в десятых). Никакого пересчёта — только деление. */
    val starsDisplay: Double? get() = starsTenths?.let { it / 10.0 }
}

/**
 * Типизированные отказы допуска (протокол §Admission): каждый отказ — свой тип, а не строка.
 *
 * Неизвестное не прикидывается ни истёкшим кулдауном, ни бесплатной ценой, ни подтверждённым
 * нулём, ни укороченным набором. Отказ и восстановление открытой попытки ничего не стоят.
 */
@Serializable
sealed interface ExamRefusal {
    /** Непройденное требуемое содержимое (тема) или непройденная обязательная тема (финал). */
    @Serializable
    @SerialName("locked")
    data class Locked(val reason: String) : ExamRefusal

    /** Заработанное доказательство есть, но материализация покрытия не догнала. */
    @Serializable
    @SerialName("results_not_synced")
    data object ResultsNotSynced : ExamRefusal

    /** Прокрутка от терминального закрытия: 24 часа тема, 168 часов финал. */
    @Serializable
    @SerialName("cooldown")
    data class Cooldown(val availableAtMs: Long) : ExamRefusal

    /** Стандартных очков не хватает; плазма плату за экзамен не платит никогда. */
    @Serializable
    @SerialName("insufficient_charge")
    data object InsufficientCharge : ExamRefusal

    /** Банк короче границы набора (5 EASY / 15 HARD / 20 HARD). */
    @Serializable
    @SerialName("pool_too_small")
    data object PoolTooSmall : ExamRefusal

    /** Явная недоступность сервера: ручка, курс, банк, поколение коррекции. */
    @Serializable
    @SerialName("unavailable")
    data class Unavailable(val reason: String) : ExamRefusal
}

/** Согласие на платные условия экзамена: изменённые условия требуют пересогласования. */
@Serializable
data class ExamTermsAcceptance(
    val shown: Boolean,
    val acceptedRevision: Long,
    val requiredRevision: Long,
) {
    /** Показано и покрывает текущую ревизию. */
    val accepted: Boolean get() = shown && acceptedRevision >= requiredRevision
}

/**
 * Ответ вызова старта: существующий открытый сидинг, новый сидинг или типизированный отказ.
 */
@Serializable
sealed interface ExamStartOutcome {
    /** Новый сидинг: списокание уже внутри той же транзакции. */
    @Serializable
    @SerialName("started")
    data class Started(val debited: Int, val sitting: ExamSittingProjection) : ExamStartOutcome

    /** Восстановление открытого сидинга: исходные дедлайны, без второго списания. */
    @Serializable
    @SerialName("recovered")
    data class Recovered(val sitting: ExamSittingProjection) : ExamStartOutcome

    /** Типизированный отказ: ничего не списано, сидинга нет. */
    @Serializable
    @SerialName("refused")
    data class Refused(val refusal: ExamRefusal) : ExamStartOutcome
}

/**
 * Ответ acknowledgeQuestion: первое закрепление, дубликат или отказ.
 */
@Serializable
sealed interface ExamAckOutcome {
    @Serializable
    @SerialName("acknowledged")
    data class Acknowledged(val ack: ExamAck, val duplicate: Boolean) : ExamAckOutcome

    @Serializable
    @SerialName("ack_refused")
    data class Refused(val reason: String) : ExamAckOutcome
}

/**
 * Ответ submitAnswer: приём с прочной квитанцией, повтор с той же или отказ без перезаписи.
 */
@Serializable
sealed interface ExamSubmitOutcome {
    @Serializable
    @SerialName("submitted")
    data class Submitted(
        val receipt: ExamAnswerReceipt,
        val duplicate: Boolean,
        val sitting: ExamSittingProjection,
    ) : ExamSubmitOutcome

    @Serializable
    @SerialName("submit_refused")
    data class Refused(val reason: String) : ExamSubmitOutcome
}

/**
 * Ответ fetchSittingResult: явное открытое состояние или авторитетный терминальный результат.
 * Чтение не терминализирует: до закрытия worker'ом результата не существует.
 */
@Serializable
sealed interface ExamFetchOutcome {
    @Serializable
    @SerialName("still_open")
    data class Open(val sittingId: String, val state: ExamSittingState) : ExamFetchOutcome

    @Serializable
    @SerialName("terminal")
    data class Terminal(val result: ExamResultProjection) : ExamFetchOutcome
}
