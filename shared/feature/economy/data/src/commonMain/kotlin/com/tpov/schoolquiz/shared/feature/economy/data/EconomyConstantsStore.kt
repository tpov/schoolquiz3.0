package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants

/**
 * Где лежит последняя приехавшая таблица.
 *
 * Отдельно от общей базы: это не содержимое и не состояние аккаунта, а настройка приложения, и
 * живёт она там же, где выбор каденции синхронизации. Реализация платформенная — интерфейс здесь,
 * чтобы репозиторий не знал, на чём именно она написана.
 */
interface EconomyConstantsStore {
    /** Прочитанное. `null`, если ничего ещё не приезжало. */
    fun read(): EconomyConstants?

    fun write(constants: EconomyConstants)
}
