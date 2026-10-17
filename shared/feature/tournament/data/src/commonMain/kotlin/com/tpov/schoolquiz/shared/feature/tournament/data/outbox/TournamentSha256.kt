package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

/**
 * SHA-256 для детерминированных operationId кодека. Ожидание в общем коде, реализация — в общей
 * половине JVM и Android (у других целей проекта нет); дайджест canonical-строки sourceId,
 * шестнадцатеричными парами в нижнем регистре — та же форма, что у `journalSha256` арены.
 */
internal expect fun tournamentSha256(text: String): String
