package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository

class ObserveEconomyBalanceUseCase(
    private val repository: EconomyRepository,
) {
    fun execute() = repository.observeBalance()
}
