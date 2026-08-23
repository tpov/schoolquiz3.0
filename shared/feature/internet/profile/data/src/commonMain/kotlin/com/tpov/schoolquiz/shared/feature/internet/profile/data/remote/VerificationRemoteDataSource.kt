package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnVerification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDecision
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDetails
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationRequest

/** The platform side of [com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.VerificationRepository]. */
interface VerificationRemoteDataSource {
    suspend fun submit(details: VerificationDetails)

    suspend fun own(): OwnVerification

    suspend fun pending(limit: Int): List<VerificationRequest>

    suspend fun decide(uid: String, decision: VerificationDecision, reason: String?)
}
