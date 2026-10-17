package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import java.security.MessageDigest

/** Тестовый дайджест для привязки фикстур к настоящим исходникам сервера (как у арены). */
internal object TournamentWireTestHash {
    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
