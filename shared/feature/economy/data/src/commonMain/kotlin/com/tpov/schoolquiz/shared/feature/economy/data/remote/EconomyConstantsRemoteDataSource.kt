package com.tpov.schoolquiz.shared.feature.economy.data.remote

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants

/** Ответ сервера на запрос таблицы настроек. */
sealed interface EconomyConstantsResponse {
    /**
     * У устройства уже та же версия.
     *
     * Отдельный случай, а не «прислали то же самое»: таблица меняется редко, синхронизация идёт
     * часто, и гонять её целиком каждый раз незачем.
     */
    data object Unchanged : EconomyConstantsResponse

    data class Table(val constants: EconomyConstants) : EconomyConstantsResponse
}

interface EconomyConstantsRemoteDataSource {
    /** @param knownVersion версия, которая уже есть у устройства. */
    suspend fun fetch(knownVersion: Long): EconomyConstantsResponse
}
