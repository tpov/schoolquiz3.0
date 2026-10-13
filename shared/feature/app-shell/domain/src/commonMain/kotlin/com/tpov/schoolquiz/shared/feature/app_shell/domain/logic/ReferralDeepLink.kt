package com.tpov.schoolquiz.shared.feature.app_shell.domain.logic

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink

/**
 * Разбор реферальной ссылки (история 5.3, монетизация).
 *
 * Единственный формат, который приложение само раздаёт: `schoolquiz://referral?id=<uid>`
 * (его же генерирует referralProgram в экономике). Код приглашающего — это uid его документа,
 * с теми же ограничениями, что сервер накладывает на referredByUid: непустая строка без «/»,
 * не точка-имя, разумной длины. Всё остальное — не наша ссылка, разбор честно отвечает null.
 */
object ReferralDeepLink {
    const val SCHEME: String = "schoolquiz"
    const val HOST: String = "referral"
    const val CODE_PARAM: String = "id"

    /** Зеркало предела сервера (referral-registration.js, documentUid) — чтобы не спорить с ним. */
    const val MAX_CODE_LENGTH: Int = 128

    fun referrerCode(link: DeepLink): String? {
        val prefix = "$SCHEME://$HOST?"
        if (!link.uri.startsWith(prefix)) return null
        return link.uri
            .removePrefix(prefix)
            .split('&')
            .firstOrNull { it.startsWith("$CODE_PARAM=") }
            ?.removePrefix("$CODE_PARAM=")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.isDocumentUid() }
    }
}

private fun String.isDocumentUid(): Boolean =
    this != "." && this != ".." && !contains('/') && length <= ReferralDeepLink.MAX_CODE_LENGTH
