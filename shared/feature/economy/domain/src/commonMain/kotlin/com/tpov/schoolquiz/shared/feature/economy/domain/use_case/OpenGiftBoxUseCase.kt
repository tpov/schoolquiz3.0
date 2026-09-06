package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository

class OpenGiftBoxUseCase(
    private val repository: GiftBoxRepository,
) {
    suspend fun execute() = repository.openGiftBox()

    fun observePending() = repository.observePendingOpening()

    suspend fun resumePending() = repository.resumePendingOpening()

    suspend fun acknowledge(openingId: String) = repository.acknowledgeOpening(openingId)
}
