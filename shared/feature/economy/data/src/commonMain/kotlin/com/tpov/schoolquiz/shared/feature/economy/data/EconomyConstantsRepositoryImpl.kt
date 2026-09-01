package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.remote.EconomyConstantsResponse
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyConstants
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Таблица настроек: то, что приехало, иначе загрузочная копия.
 *
 * Не auth-scoped и намеренно: таблица одна на всех, она про экономику игры, а не про аккаунт. Смена
 * аккаунта её не трогает — цены от того, кто вошёл, не зависят.
 */
class EconomyConstantsRepositoryImpl(
    private val remote: EconomyConstantsRemoteDataSource,
    private val store: EconomyConstantsStore,
) : EconomyConstantsRepository {

    private val state = MutableStateFlow(store.read() ?: EconomyConstants.BOOTSTRAP)

    override fun observe(): Flow<EconomyConstants> = state.asStateFlow()

    override suspend fun refresh(): Result<Unit> =
        try {
            when (val response = remote.fetch(state.value.version)) {
                // Совпала версия — писать нечего, и трогать хранилище незачем.
                EconomyConstantsResponse.Unchanged -> Unit
                is EconomyConstantsResponse.Table -> {
                    store.write(response.constants)
                    state.value = response.constants
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Неудача не отменяет уже известную таблицу: играть по прошлой честнее, чем по нулям.
            Result.failure(e)
        }
}
