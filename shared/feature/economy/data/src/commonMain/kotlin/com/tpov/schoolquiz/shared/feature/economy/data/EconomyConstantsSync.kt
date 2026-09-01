package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository

/**
 * Шаг синхронизации: спросить сервер про таблицу настроек.
 *
 * Отдельным классом, а не `Syncable` напрямую, потому что `Syncable` живёт в `shared/core/sync`, а
 * тащить весь синк со всеми доменами содержимого в модуль экономики ради одного интерфейса дороже,
 * чем переходник в композиционном корне.
 */
class EconomyConstantsSync(
    private val repository: EconomyConstantsRepository,
) {
    suspend fun sync(): Result<Unit> = repository.refresh()
}
