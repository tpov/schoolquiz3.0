package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAnswerReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSubmitOutcome

/**
 * Проводная граница шести экзаменационных вызовов (протокол §Calls; серверная половина —
 * `startThemeTest`/`startFinalExam`/`acknowledgeQuestion`/`submitAnswer`/`finishSitting`/
 * `fetchSittingResult`).
 *
 * Только явные вызовы: никаких автоматических повторов и локальных записей — списание делает
 * сервер в транзакции старта, повтор того же ключа операции возвращает сохранённый результат.
 * Личность на сервере поставляет Auth; тело называет операцию: ключ идемпотентности (operationId),
 * epoch аккаунта (A→B→A) и согласие на платные условия.
 */
interface ExamSittingRemoteDataSource {

    /** Старт теста темы: новый сидинг со списанием, восстановление открытого или отказ. */
    suspend fun startThemeTest(
        operationId: String,
        accountEpoch: Long,
        themeId: String,
        terms: ExamTermsWire,
    ): Result<ExamStartOutcome>

    /** Старт финала курса: та же семантика, списокание 200. */
    suspend fun startFinalExam(
        operationId: String,
        accountEpoch: Long,
        courseId: String,
        terms: ExamTermsWire,
    ): Result<ExamStartOutcome>

    /** Первый ограниченный ACK: закрепляет дедлайн; дубликат возвращает закреплённое. */
    suspend fun acknowledgeQuestion(
        sittingId: String,
        questionIndex: Int,
        operationId: String,
    ): Result<ExamAckOutcome>

    /** Ответ: первый зафиксированный побеждает; одинаковый повтор — та же квитанция. */
    suspend fun submitAnswer(
        sittingId: String,
        questionIndex: Int,
        revision: Long,
        answerPayload: String,
        operationId: String,
    ): Result<ExamSubmitOutcome>

    /** Запрос закрытия: открытая попытка просит терминализации, worker закрывает. */
    suspend fun finishSitting(sittingId: String): Result<ExamFinishVerdict>

    /** Чтение результата: явное открытое состояние или авторитетный терминальный результат. */
    suspend fun fetchSittingResult(sittingId: String): Result<ExamFetchOutcome>
}

/**
 * Типовой отказ транспортной границы: режимный адаптер решает повтор по виду, не по тексту.
 * Живёт в общем слое, чтобы состояние прогона читало причину без зависимости от платформы.
 */
enum class ExamTransportFailureKind {
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_CHANGED,
    INVALID_REQUEST,
    MALFORMED_RESPONSE,
    SERVER_FAILURE,
    NO_NETWORK,
}

/** Отказ границы с его видом. */
class ExamTransportFailure(val kind: ExamTransportFailureKind) : Exception(kind.name)

/** Согласие на платные условия в теле запроса старта. */
data class ExamTermsWire(
    val shown: Boolean,
    val acceptedRevision: Long,
)

/** Ответ `finishSitting`: запрошено или уже закрыто — оба состояния безопасны для повтора. */
data class ExamFinishVerdict(
    val state: String,
    val alreadyRequested: Boolean,
    val alreadyClosed: Boolean,
)
