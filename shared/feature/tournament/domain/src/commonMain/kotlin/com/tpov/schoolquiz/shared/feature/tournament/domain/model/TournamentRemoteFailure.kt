package com.tpov.schoolquiz.shared.feature.tournament.domain.model

//
// Почему обращение к соревнованию не состоялось — до всякого разбора причин сервера.
//
// Три ветви — структурные: аккаунта нет, аккаунт сменился под запросом, ответ не разобран.
// Отказы сервера по причинам (`details.reason`) едут не здесь, а поверх существующей иерархии
// [com.tpov.schoolquiz.shared.core.network.SyncError] — тем же решением, что и у Arena: ветвь
// (Refused/повтор/неизвестно) решает платформа по структурной причине, никогда по тексту сообщения.
enum class TournamentRemoteFailureKind {
    /** Аутентифицированной сессии нет — отправлять некем. */
    ACCOUNT_UNAVAILABLE,

    /** Владелец или эпоха сменились, пока запрос летел: ответ чужой смены не принимается. */
    ACCOUNT_CHANGED,

    /** Ответ не разобран: неизвестный набор ключей, неверный тип, отсутствующее поле. */
    MALFORMED_RESPONSE,
}

/** Структурный отказ границы соревнования; причина — род ветви, не текст сообщения. */
data class TournamentRemoteFailure(
    val kind: TournamentRemoteFailureKind,
) : Exception(kind.name)
