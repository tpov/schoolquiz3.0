package com.tpov.schoolquiz.shared.core.analytics

/**
 * Drops everything.
 *
 * The binding for JVM tests and for any build where no analytics backend is wired. It exists so
 * that "analytics is not configured here" never becomes a null check at every call site.
 */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit

    override fun setUserProperty(property: UserProperty, value: String) = Unit

    override fun setUserId(userId: String?) = Unit
}

/**
 * Keeps what it was given, in order.
 *
 * For tests that assert a funnel step fired. Lives in main rather than test source so that any
 * module's tests can use it without a test-fixtures dependency — the project convention is fakes
 * over mocks, and this is the canonical one for analytics.
 */
class RecordingAnalyticsTracker : AnalyticsTracker {
    private val recorded = mutableListOf<AnalyticsEvent>()
    private val properties = mutableMapOf<UserProperty, String>()
    private var currentUserId: String? = null

    val events: List<AnalyticsEvent> get() = recorded.toList()
    val userProperties: Map<UserProperty, String> get() = properties.toMap()
    val userId: String? get() = currentUserId

    override fun track(event: AnalyticsEvent) {
        recorded += event
    }

    override fun setUserProperty(property: UserProperty, value: String) {
        properties[property] = value
    }

    override fun setUserId(userId: String?) {
        currentUserId = userId
    }

    fun clear() {
        recorded.clear()
        properties.clear()
        currentUserId = null
    }
}
