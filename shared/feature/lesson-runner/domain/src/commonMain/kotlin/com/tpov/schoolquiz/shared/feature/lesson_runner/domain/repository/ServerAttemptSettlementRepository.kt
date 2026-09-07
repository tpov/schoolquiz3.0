package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.identity
import kotlinx.coroutines.flow.Flow

interface ServerAttemptSettlementRepository {
    /** Auth-session changes must immediately remove the previous owner's confirmation. */
    fun observe(attempt: AttemptIdentity): Flow<ServerAttemptSettlement>

    fun observe(attempt: Attempt): Flow<ServerAttemptSettlement> = observe(attempt.identity())
}
