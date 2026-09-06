package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeServerAttemptSettlementRepository : ServerAttemptSettlementRepository {
    val settlement = MutableStateFlow(ServerAttemptSettlement(ServerAttemptSettlementState.PENDING, ownerVerified = true))
    val observedAttempts = mutableListOf<AttemptIdentity>()

    override fun observe(attempt: AttemptIdentity): MutableStateFlow<ServerAttemptSettlement> {
        observedAttempts += attempt
        return settlement
    }
}
