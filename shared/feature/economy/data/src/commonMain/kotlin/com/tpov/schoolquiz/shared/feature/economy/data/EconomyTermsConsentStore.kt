package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.domain.model.PayableTerms

/**
 * Где лежит последнее согласие на платные условия.
 *
 * Отдельно от [EconomyConstantsStore] намеренно: таблицу перезаписывает синхронизация, а согласие
 * переписывает только игрок — иначе каждая удачная синхронизация молча «подтверждала бы» за него
 * согласие с новыми ценами, и гейт не имел бы с чем сверяться.
 *
 * Реализация платформенная — интерфейс здесь, чтобы репозиторий не знал, на чём именно он написан.
 */
interface EconomyTermsConsentStore {
    /** Захваченные условия последнего согласия. `null`, если согласия не было или запись испорчена. */
    fun read(): PayableTerms?

    fun write(terms: PayableTerms)
}
