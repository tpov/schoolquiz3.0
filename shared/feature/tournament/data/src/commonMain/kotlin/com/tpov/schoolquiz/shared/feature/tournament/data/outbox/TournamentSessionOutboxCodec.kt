package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Намерение игрока на мутацию сессии соревнования (история 5.9): ответ, закрытие вопроса и
 * квитанция готовности — всё едет из очереди единственным приёмником `tournament.SESSION_OP`.
 *
 * Тело каждого варианта — ровно allowlist приёмника `tournament.SESSION_OP`, то есть
 * `courseTournaments.session` в `functions/index.js` (прямой вызываемый
 * `submitCourseTournamentSession` клиентом не зовётся — мутации едут только из очереди):
 * ни часов, ни отпечатка формы (его сервер дописывает сам из хранимой сессии), ни счёта —
 * «клиентские оценки, часы и чужая личность — не вход» сервера соблюдается формой кодека.
 */
sealed interface TournamentSessionIntent {
    /** Серверная сессия (`q_…`/`m_…`), к которой относится намерение. */
    val sessionId: String

    /** Позиция вопроса в намерении; у [Advance] — закрываемая, у остальных — отвечаемая/подтверждаемая. */
    val questionIndex: Int

    /** Ответ на текущий вопрос: позиция, привязка вопроса и полезная нагрузка ответа игрока. */
    data class Answer(
        override val sessionId: String,
        override val questionIndex: Int,
        val questionId: String,
        val answerPayload: JsonObject,
    ) : TournamentSessionIntent

    /** Закрытие текущего вопроса сейчас: позиция фиксирует, о каком закрытии намерение. */
    data class Advance(
        override val sessionId: String,
        override val questionIndex: Int,
    ) : TournamentSessionIntent

    /** Квитанция готовности (ONLINE-O2 §4): позиция и окно, в котором вопрос был показан. */
    data class ReadyAck(
        override val sessionId: String,
        override val questionIndex: Int,
        val windowId: String,
    ) : TournamentSessionIntent
}

/**
 * Клиентская половина `tournament.SESSION_OP` (история 5.9) — кодирование намерения в строку
 * очереди, по образцу `ReadyAckOutboxCodec` (ONLINE-O2 §6).
 *
 * Ключ рождается вместе с намерением и не меняется ни при одной попытке (AD-2): sourceId
 * кодирует идентичность слота сессии — сессия, позиция вопроса и привязка (ответ/закрытие/окно),
 * — а не содержание ответа. Содержание замораживается в теле при постановке; повтор того же
 * намерения едет под тем же ключом и тем же operationId, и повтор ключа сервера возвращает ту же
 * квитанцию без второго эффекта. READY_ACK операции — постоянная квитанция: имя операции целиком
 * входит в `OutboxOperations.permanentReceiptOperations`, и старение в карантин ей не грозит.
 *
 * operationId выводится из sourceId хешем — тот же intent даёт тот же серверный operationId,
 * поэтому серверная идемпотентность (CONFLICTING_REPLAY чужому телу) и клиентский ключ сходятся
 * в одну идентичность, а вторую личность клиент не выдумывает.
 */
object TournamentSessionOutboxCodec {
    /** Префикс ссылки на сущность; пространство имён владеющей фичи `tournament`. */
    const val ENTITY_REF_PREFIX: String = "tournament:session:"

    /** operationId должен быть узнаваемым и не путаться с рабочими префиксами сервера. */
    private const val OPERATION_ID_PREFIX = "tsop_"

    /** Серверный бюджет ответа (MAX_ANSWER_BYTES в `tournament-session.js`) — проверяется до очереди. */
    const val MAX_ANSWER_BYTES = 16_384

    /** Имя серверной операции; совпадение с реестром `MUTATION_HANDLERS` сторожит реестровый тест. */
    val operation: String get() = OutboxOperations.TOURNAMENT_SESSION_OP

    /**
     * sourceId намерения: идентичность слота в одной строке; части — только [A-Za-z0-9_-].
     *
     * [sessionId] уже несёт своё `q_`/`m_`; [TournamentSessionIntent.ReadyAck.windowId] — `win_…`.
     * Контроль честнее перекодировки, которая спрятала бы испорченный идентификатор в валидный
     * с виду ключ (AD-6): сервер принимает ключ только из этого charset.
     */
    fun sourceId(intent: TournamentSessionIntent): String {
        require(
            intent.sessionId.matches(SAFE_CHARSET),
        ) { "sessionId must fit [A-Za-z0-9_-]: ${intent.sessionId.length} chars" }
        require(intent.questionIndex in 0 until MAX_QUESTIONS) {
            "questionIndex out of the served range: ${intent.questionIndex}"
        }
        return when (intent) {
            is TournamentSessionIntent.Answer -> "${intent.sessionId}-q${intent.questionIndex}-answer"
            is TournamentSessionIntent.Advance -> "${intent.sessionId}-q${intent.questionIndex}-advance"
            is TournamentSessionIntent.ReadyAck -> {
                require(intent.windowId.matches(SAFE_CHARSET)) { "windowId must fit [A-Za-z0-9_-]" }
                "${intent.sessionId}-q${intent.questionIndex}-window-${intent.windowId}"
            }
        }
    }

    /** Ссылка на сущность: квитанция и пометка «не отправлено» живут на сессии, а не на слоте. */
    fun entityRef(sessionId: String): String = ENTITY_REF_PREFIX + sessionId

    /** Ключ мутации — выведен из sourceId и потому одинаков при любой попытке отправки. */
    fun mutationKey(intent: TournamentSessionIntent): String =
        OutboxOperations.mutationKey(OutboxOperations.TOURNAMENT_SESSION_OP, sourceId(intent))

    /** Детерминированный серверный operationId намерения: один intent — одна серверная операция. */
    fun operationId(intent: TournamentSessionIntent): String = OPERATION_ID_PREFIX + tournamentSha256(sourceId(intent))

    /**
     * Тело мутации — ровно allowlist приёмника `tournament.SESSION_OP` (`courseTournaments.session`),
     * замороженное в момент намерения (AD-2). Часов в теле нет: серверные часы — факт сервера (§5).
     */
    fun payload(intent: TournamentSessionIntent): JsonObject {
        val body =
            buildJsonObject {
                put("kind", kindOf(intent))
                put("sessionId", intent.sessionId)
                put("operationId", operationId(intent))
                when (intent) {
                    is TournamentSessionIntent.Answer -> {
                        put("questionIndex", intent.questionIndex)
                        put("questionId", intent.questionId)
                        put("answerPayload", intent.answerPayload)
                    }
                    is TournamentSessionIntent.Advance -> Unit
                    is TournamentSessionIntent.ReadyAck -> {
                        put("questionIndex", intent.questionIndex)
                        put("windowId", intent.windowId)
                    }
                }
            }
        if (intent is TournamentSessionIntent.Answer) {
            val bytes = intent.answerPayload.toString().encodeToByteArray().size
            require(bytes <= MAX_ANSWER_BYTES) { "answerPayload exceeds the server storage budget: $bytes" }
        }
        return body
    }

    /**
     * Строка очереди для намерения. Запись неверсионируемая: у сессии соревнования нет
     * клиентской версии — идемпотентность держит operationId, конфликт версий здесь не прилетает.
     */
    fun buildRow(
        intent: TournamentSessionIntent,
        ownerUid: String,
        createdAtMs: Long,
    ): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = mutationKey(intent),
            ownerUid = ownerUid,
            operation = OutboxOperations.TOURNAMENT_SESSION_OP,
            payload = payload(intent).toString(),
            state = OutboxState.WAITING,
            createdAtMs = createdAtMs,
            entityRef = entityRef(intent.sessionId),
            expectedVersion = null,
        )

    private fun kindOf(intent: TournamentSessionIntent): String =
        when (intent) {
            is TournamentSessionIntent.Answer -> "ANSWER"
            is TournamentSessionIntent.Advance -> "ADVANCE"
            is TournamentSessionIntent.ReadyAck -> "READY_ACK"
        }

    /** Двадцать вопросов формы — серверная константа QUESTION_COUNT; вне её намерений нет. */
    private const val MAX_QUESTIONS = 20

    private val SAFE_CHARSET: Regex = Regex("[A-Za-z0-9_-]+")
}
