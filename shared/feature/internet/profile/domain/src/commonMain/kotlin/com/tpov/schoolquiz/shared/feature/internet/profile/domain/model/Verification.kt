package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * What a person submits so a human can check who they are.
 *
 * Deliberately small: enough to hold a conversation and decide, and nothing more. The birthday is
 * an ISO date, and the telegram handle is stored without its @ so a reviewer always sees one form.
 */
data class VerificationDetails(
    val realName: String,
    val birthday: String,
    val city: String,
    val telegram: String,
)

/** A pending application, as an admin or developer sees it. */
data class VerificationRequest(
    val uid: String,
    val realName: String,
    val birthday: String,
    val city: String,
    val telegram: String,
    val submittedAtMs: Long,
)

/**
 * Where an application stands, from its owner's side.
 *
 * These are the states a screen has to cover. [NONE] and [REJECTED] both allow applying, and they
 * are not the same thing: a rejection carries a reason that has to be shown, or the person will
 * simply resubmit exactly what was refused.
 */
enum class VerificationState {
    /** Nothing submitted, or an anonymous account that cannot submit at all. */
    NONE,
    PENDING,
    REJECTED,
    APPROVED,
}

/** The owner's view: state plus, when refused, why. */
data class OwnVerification(
    val state: VerificationState,
    val rejectionReason: String? = null,
    val details: VerificationDetails? = null,
) {
    /** A rejection is meant to be answered; a pending one must not be replaced mid-review. */
    val canSubmit: Boolean get() = state == VerificationState.NONE || state == VerificationState.REJECTED
}

enum class VerificationDecision {
    APPROVED,
    REJECTED,
}
