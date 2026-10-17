package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

import java.security.MessageDigest

/** Реализация ожидания: дайджест canonical-строки, шестнадцатеричными парами в нижнем регистре. */
internal actual fun tournamentSha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
