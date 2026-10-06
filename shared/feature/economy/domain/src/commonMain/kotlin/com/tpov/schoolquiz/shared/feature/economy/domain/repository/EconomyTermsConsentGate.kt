package com.tpov.schoolquiz.shared.feature.economy.domain.repository

import com.tpov.schoolquiz.shared.feature.economy.domain.logic.TermsConsentVerdict
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms

/**
 * Согласие игрока на платные условия (CAP-16, клиентская половина AD-5).
 *
 * Платная операция ведётся по условиям, на которые игрок согласился, а не по тем, что приехали
 * последними: сервер вправе переписать таблицу, но не задним числом переоценивать уже согласованное.
 * Поэтому согласие захватывается снимком платных условий и хранится отдельно от таблицы — таблицу
 * перезаписывает синхронизация, согласие переписывает только сам игрок.
 *
 * Как и таблица, согласие не auth-scoped: цены от того, кто вошёл, не зависят.
 */
interface EconomyTermsConsentGate {
    /**
     * Захваченные условия последнего согласия. `null` — игрок ещё ни на что не соглашался
     * (например, запись в хранилище испорчена и отброшена целиком).
     */
    fun capturedTerms(): PayableTerms?

    /**
     * Сверка захваченных условий с текущей таблицей.
     *
     * Платную операцию ведут только при [TermsConsentVerdict.Sufficient]; иначе игрока просят
     * согласиться с новыми условиями, и лишь после [acceptCurrentTerms] операция продолжается.
     */
    fun verdict(): TermsConsentVerdict

    /** Зафиксировать согласие с таблицей, действующей на устройстве сейчас. */
    fun acceptCurrentTerms()
}
