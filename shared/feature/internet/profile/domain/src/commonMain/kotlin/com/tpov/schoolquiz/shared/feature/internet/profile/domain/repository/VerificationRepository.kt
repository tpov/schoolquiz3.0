package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnVerification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDecision
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDetails
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationRequest

/**
 * Account verification: a human decision, carried by three calls.
 *
 * Filing and deciding both go through the server. Reading one's own application does not — the
 * rules let an owner read it directly, and routing that through a function would add a hop for
 * nothing.
 */
interface VerificationRepository {
    /** Refused for an anonymous account: there is no person there to check. */
    suspend fun submit(details: VerificationDetails)

    suspend fun own(): OwnVerification

    /** Admins and developers only. */
    suspend fun pending(limit: Int = DEFAULT_PENDING_LIMIT): List<VerificationRequest>

    /** Nobody decides their own. A rejection should carry a reason the applicant can act on. */
    suspend fun decide(uid: String, decision: VerificationDecision, reason: String? = null)

    companion object {
        const val DEFAULT_PENDING_LIMIT = 20
    }
}
