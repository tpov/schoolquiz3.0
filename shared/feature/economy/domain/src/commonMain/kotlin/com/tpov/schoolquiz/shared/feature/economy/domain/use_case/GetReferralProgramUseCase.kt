package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository

class GetReferralProgramUseCase(
    private val repository: EconomyRepository,
) {
    suspend fun execute() = repository.referralProgram()
}
