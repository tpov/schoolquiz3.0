package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.VerificationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnVerification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDecision
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDetails
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.VerificationRepository

/** Pass-through: a decision made elsewhere must never be answered from a local copy. */
class VerificationRepositoryImpl(
    private val remote: VerificationRemoteDataSource,
) : VerificationRepository {
    override suspend fun submit(details: VerificationDetails) = remote.submit(details)

    override suspend fun own(): OwnVerification = remote.own()

    override suspend fun pending(limit: Int): List<VerificationRequest> = remote.pending(limit)

    override suspend fun decide(uid: String, decision: VerificationDecision, reason: String?) =
        remote.decide(uid, decision, reason)
}
