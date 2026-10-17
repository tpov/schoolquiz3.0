package com.tpov.schoolquiz.shared.feature.tournament.domain.model

//
// Тела прямых запросов соревнования. Токен ссылки организатора и полезная нагрузка ответа
// здесь — входы игрока/ссылки (out-of-band), а не декодированные проекции сервера: в типах
// *прочитанного* их нет по построению, и в журналы они не пишутся.

/** Подготовка банка источника: reviewed-курс или аренный урок за requestId игрока. */
data class TournamentPrepareRequest(
    val ownerUid: String,
    val requestId: String,
    val source: TournamentSourceRef,
)

/** Операция организатора/участника: одно намерение под одним operationId (идемпотентность сервера). */
sealed interface TournamentOperateRequest {
    val ownerUid: String
    val operationId: String
    val eventId: String

    /** Создание события организатором: замороженный призовой список v1 и funded-депозит. */
    data class Create(
        override val ownerUid: String,
        override val operationId: String,
        override val eventId: String,
        val source: TournamentSourceRef,
        val snapshotId: String,
        val startsAtMs: Long,
        val prizeRows: List<TournamentPrizeRow>,
        val fundedBoxes: Long,
    ) : TournamentOperateRequest

    /** Начало квалификационного раунда; [linkToken] — представленный токен ссылки, если он есть. */
    data class StartQualification(
        override val ownerUid: String,
        override val operationId: String,
        override val eventId: String,
        val roundNumber: Int,
        val attemptId: String,
        val linkToken: String? = null,
    ) : TournamentOperateRequest

    /** Предложение слота встречи. */
    data class Propose(
        override val ownerUid: String,
        override val operationId: String,
        override val eventId: String,
        val matchId: String,
        val slotStartsAtMs: Long,
    ) : TournamentOperateRequest

    /** Подтверждение слота встречи. */
    data class Confirm(
        override val ownerUid: String,
        override val operationId: String,
        override val eventId: String,
        val matchId: String,
        val slotStartsAtMs: Long,
    ) : TournamentOperateRequest

    /** Готовность к слоту встречи. */
    data class Ready(
        override val ownerUid: String,
        override val operationId: String,
        override val eventId: String,
        val matchId: String,
    ) : TournamentOperateRequest
}

/** Запись «этот аккаунт скачал этот курс»: create-only серверная запись, повтор — с флагом. */
data class TournamentDownloadRequest(
    val ownerUid: String,
    val operationId: String,
    val courseId: String,
)
