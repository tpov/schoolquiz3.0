package com.tpov.schoolquiz.shared.core.analytics

/**
 * Where funnel events go.
 *
 * The interface is pure Kotlin on purpose: features depend on this, never on Firebase. The
 * Firebase implementation lives in `platform/firebase`, and swapping or adding a backend is a
 * DI change rather than a change in every call site.
 *
 * Implementations must not throw. Analytics failing is never a reason for a lesson to fail, so
 * an implementation that cannot deliver an event drops it and reports nothing to the caller.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)

    /**
     * Sets a property that describes the user rather than a moment — locale, whether they have
     * ever paid, which market they are in. Properties segment every later event, so they are set
     * once and left alone rather than re-set per screen.
     */
    fun setUserProperty(property: UserProperty, value: String)

    /** Ties events to an account. Passing null detaches them, which is what logout must do. */
    fun setUserId(userId: String?)
}

/**
 * The user properties worth segmenting by. A closed set for the same reason [AnalyticsValue] is
 * narrow: an open string parameter here becomes a graveyard of one-off typos.
 */
enum class UserProperty(val wireName: String) {
    /** BCP-47 tag of the UI language actually resolved, e.g. `uk`, `ru`, `en`. */
    UI_LANGUAGE("ui_language"),

    /** Whether this account has ever completed a real-money purchase. */
    HAS_PAID("has_paid"),

    /** Play install referrer bucket, once attribution resolves it. */
    ACQUISITION_SOURCE("acquisition_source"),
}
