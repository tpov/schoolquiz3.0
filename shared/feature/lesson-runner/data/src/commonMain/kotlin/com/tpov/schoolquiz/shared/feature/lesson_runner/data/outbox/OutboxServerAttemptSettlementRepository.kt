package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class OutboxServerAttemptSettlementRepository(
    private val receipts: MutationReceiptStore,
    private val currentUidFlow: () -> Flow<String?>,
    private val parser: AttemptSettlementReceiptParser = AttemptSettlementReceiptParser(),
) : ServerAttemptSettlementRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(attempt: AttemptIdentity): Flow<ServerAttemptSettlement> =
        currentUidFlow().flatMapLatest { uid ->
            if (uid == null || uid != attempt.userId) {
                flowOf(ServerAttemptSettlement(ServerAttemptSettlementState.ACCOUNT_UNAVAILABLE))
            } else {
                receipts.observeEntity(uid, LessonResultEntityRef.attempt(attempt.id.value)).map { rows ->
                    val parsed = rows.map { parser.parse(it, attempt) }.distinct()
                    when (parsed.size) {
                        0 -> ServerAttemptSettlement(ServerAttemptSettlementState.PENDING, ownerVerified = true)
                        1 -> parsed.single()
                        // Do not guess which contradictory immutable receipt is authoritative.
                        else -> ServerAttemptSettlement(ServerAttemptSettlementState.INVALID_RECEIPT)
                    }
                }
            }
        }.distinctUntilChanged()
}
