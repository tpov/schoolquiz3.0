package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import kotlinx.coroutines.flow.Flow

class ObserveEconomyReceiptsUseCase(
    private val repository: EconomyRepository,
) {
    fun execute(limit: Int = EconomyRepository.RECEIPTS_PAGE_LIMIT): Flow<List<EconomyReceipt>> =
        repository.observeReceipts(limit)
}
