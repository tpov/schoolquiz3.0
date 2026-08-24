package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * Whatever the platform needs in order to put an account chooser on screen.
 *
 * Opaque on purpose. Android needs a live Activity to show the sheet, and naming that type here
 * would drag the framework into the domain; the implementation unwraps its own kind and refuses
 * anything else.
 */
interface AccountChooserHost

/**
 * The default carrier: whatever screen object the platform needs, held as [platformHost].
 *
 * Typed as Any so the domain never names Activity. The implementation unwraps the kind it knows
 * and refuses anything else, which keeps the mistake at runtime in one place instead of spreading
 * the framework through every layer that passes this along.
 */
class PlatformAccountChooserHost(val platformHost: Any) : AccountChooserHost

/**
 * What happened when somebody signed in with Google.
 *
 * The distinction matters and is not cosmetic. [LINKED] keeps the anonymous account and everything
 * earned on it. [SWITCHED] means the Google account already belonged to another player, so this
 * device is now signed in as them and whatever the anonymous account held is no longer in reach —
 * which is worth telling somebody, not swallowing.
 */
enum class GoogleLinkOutcome {
    LINKED,
    SWITCHED,
}
