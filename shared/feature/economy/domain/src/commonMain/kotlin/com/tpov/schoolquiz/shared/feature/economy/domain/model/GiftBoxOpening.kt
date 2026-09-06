package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class GiftBoxOpening(
    val reward: GiftBoxReward,
    val profileSynced: Boolean,
    val remainingBoxCount: Int?,
    val openingId: String? = null,
    val ownerUid: String? = null,
)

/** Only an active local marker can recover a result; old receipt history is not a new opening. */
data class PendingGiftBoxOpening(
    val openingId: String,
    val ownerUid: String,
    val receiptAvailable: Boolean,
)

enum class GiftBoxFailureReason { NO_BOXES, OFFLINE, PENDING, RECEIPT_INVALID, AUTH_CHANGED, REQUIRES_ATTENTION }

class GiftBoxOpeningException(
    val reason: GiftBoxFailureReason,
    val openingId: String? = null,
    val ownerUid: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause)
