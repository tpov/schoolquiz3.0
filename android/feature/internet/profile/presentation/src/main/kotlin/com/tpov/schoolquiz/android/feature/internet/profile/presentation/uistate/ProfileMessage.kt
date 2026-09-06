package com.tpov.schoolquiz.android.feature.internet.profile.presentation.uistate

/**
 * What the screen may announce in its toast.
 *
 * Neutral data rather than finished sentences: wording is a screen concern, so the component only
 * says what happened and lets the composable pick the words — including which language they are.
 * [Failure] carries the raw detail from the layer below because a platform error text cannot be
 * enumerated here; when there is nothing readable, the screen falls back to its own line.
 */
sealed interface ProfileMessage {
    data class NicknameActivated(val nickname: String) : ProfileMessage

    data object ProfileSynced : ProfileMessage

    data object NicknameUpdated : ProfileMessage

    data object GoogleLinked : ProfileMessage

    data object GoogleSwitchedToExisting : ProfileMessage

    data class Failure(val detail: String?) : ProfileMessage
}
