package com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome

/**
 * Turning an anonymous account into a registered one.
 *
 * Deliberately not called "sign in": the game starts anonymous and everything a player has done up
 * to this point lives on that account. The job is to attach a Google identity to the account that
 * already exists, and only fall back to signing in as someone else when that identity is already
 * spoken for.
 */
interface GoogleSignInRepository {
    suspend fun linkGoogleAccount(host: AccountChooserHost): Result<GoogleLinkOutcome>
}
