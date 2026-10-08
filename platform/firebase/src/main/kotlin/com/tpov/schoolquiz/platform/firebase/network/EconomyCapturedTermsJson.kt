package com.tpov.schoolquiz.platform.firebase.network

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.feature.economy.data.PayableTermsCodec
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyTermsConsentGate

/**
 * Захваченное согласие операции — из хранилища согласия, строкой для конверта мутации.
 *
 * Реестр операций с релевантными платными условиями знает ядро
 * (`OutboxOperations.termsConsentOperations`, клиентский двойник серверного
 * `MUTATION_TERMS_OPERATIONS`): чужая операция согласия не получает, даже если хранилище
 * непусто — согласие едет только с той мутацией, которую сервер по нему сверяет. Согласия нет —
 * поле не ставится: сервер ответит `terms-consent-required`, и запись переедет в ветвь ожидания
 * согласия; приписывать операции согласие, которого игрок не давал, — противоположно смыслу
 * проверки (AD-5).
 */
class EconomyCapturedTermsJson(
    private val gate: EconomyTermsConsentGate,
) : CapturedTermsJson {
    override fun of(operation: String): String? {
        if (operation !in OutboxOperations.termsConsentOperations) return null
        return gate.capturedTerms()?.let(PayableTermsCodec::toJson)
    }
}
