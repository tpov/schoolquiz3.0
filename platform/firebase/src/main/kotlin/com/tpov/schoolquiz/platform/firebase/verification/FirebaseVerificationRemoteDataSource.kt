package com.tpov.schoolquiz.platform.firebase.verification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.VerificationRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnVerification
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDecision
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationDetails
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.VerificationState
import kotlinx.coroutines.tasks.await

/**
 * Verification over callables, with one deliberate exception.
 *
 * Filing and deciding go through functions, because both change what an account is allowed to do
 * and neither may be settled by a client. Reading one's own application does not: the rules already
 * let an owner read that document, so a function would be a hop for nothing.
 */
class FirebaseVerificationRemoteDataSource(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : VerificationRemoteDataSource {
    override suspend fun submit(details: VerificationDetails) {
        functions
            .getHttpsCallable(SUBMIT)
            .withAppTimeout()
            .call(
                mapOf(
                    REAL_NAME to details.realName,
                    BIRTHDAY to details.birthday,
                    CITY to details.city,
                    TELEGRAM to details.telegram,
                ),
            )
            .await()
    }

    override suspend fun own(): OwnVerification {
        val uid = auth.currentUser?.uid
        val snapshot = uid?.let { firestore.collection(REQUESTS).document(it).get().await() }
        if (snapshot == null || !snapshot.exists()) {
            // No account, or nothing submitted — the same thing from the screen's point of view.
            return OwnVerification(VerificationState.NONE)
        }

        val status = snapshot.getString(STATUS).orEmpty().uppercase()
        val state =
            when (status) {
                "APPROVED" -> VerificationState.APPROVED
                "REJECTED" -> VerificationState.REJECTED
                "PENDING" -> VerificationState.PENDING
                // A document with an unrecognised status is still an application in flight; showing
                // the form again would let somebody replace it while a reviewer is reading it.
                else -> VerificationState.PENDING
            }
        return OwnVerification(
            state = state,
            rejectionReason = snapshot.getString(REJECTION_REASON)?.takeIf { it.isNotBlank() },
            details =
                VerificationDetails(
                    realName = snapshot.getString(REAL_NAME).orEmpty(),
                    birthday = snapshot.getString(BIRTHDAY).orEmpty(),
                    city = snapshot.getString(CITY).orEmpty(),
                    telegram = snapshot.getString(TELEGRAM).orEmpty(),
                ),
        )
    }

    override suspend fun pending(limit: Int): List<VerificationRequest> {
        val data =
            functions.getHttpsCallable(FETCH_PENDING)
                .withAppTimeout().call(mapOf(LIMIT to limit)).await().data
        val requests = (data as? Map<*, *>)?.get(REQUESTS_FIELD) as? List<*> ?: return emptyList()
        return requests.mapNotNull { it as? Map<*, *> }.map { entry ->
            VerificationRequest(
                uid = entry[UID]?.toString().orEmpty(),
                realName = entry[REAL_NAME]?.toString().orEmpty(),
                birthday = entry[BIRTHDAY]?.toString().orEmpty(),
                city = entry[CITY]?.toString().orEmpty(),
                telegram = entry[TELEGRAM]?.toString().orEmpty(),
                submittedAtMs = (entry[SUBMITTED_AT_MS] as? Number)?.toLong() ?: 0L,
            )
        }
    }

    override suspend fun decide(
        uid: String,
        decision: VerificationDecision,
        reason: String?,
    ) {
        functions
            .getHttpsCallable(DECIDE)
            .withAppTimeout()
            .call(
                buildMap {
                    put(UID, uid)
                    put(DECISION, decision.name)
                    reason?.takeIf { it.isNotBlank() }?.let { put(REASON, it) }
                },
            )
            .await()
    }

    private companion object {
        const val SUBMIT = "submitVerificationRequest"
        const val FETCH_PENDING = "fetchVerificationRequests"
        const val DECIDE = "decideVerification"

        const val REQUESTS = "verification_requests"
        const val REQUESTS_FIELD = "requests"
        const val UID = "uid"
        const val REAL_NAME = "realName"
        const val BIRTHDAY = "birthday"
        const val CITY = "city"
        const val TELEGRAM = "telegram"
        const val STATUS = "status"
        const val REJECTION_REASON = "rejectionReason"
        const val SUBMITTED_AT_MS = "submittedAtMs"
        const val DECISION = "decision"
        const val REASON = "reason"
        const val LIMIT = "limit"
    }
}
