package com.tpov.schoolquiz.platform.firebase.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsEvent
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsValue
import com.tpov.schoolquiz.shared.core.analytics.UserProperty

/**
 * Delivers funnel events to Firebase, and identity to Crashlytics alongside.
 *
 * Nothing here throws. Analytics is instrumentation, and instrumentation that can take a lesson
 * down with it is worse than no instrumentation — so every call is wrapped and a failure is
 * dropped rather than propagated.
 */
class FirebaseAnalyticsTracker(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        runCatching {
            analytics.logEvent(event.name, event.params.toBundle())
        }
    }

    override fun setUserProperty(
        property: UserProperty,
        value: String,
    ) {
        runCatching {
            analytics.setUserProperty(property.wireName, value)
            // The same property on a crash report is what makes "only Ukrainian users hit this"
            // a question you can answer.
            crashlytics.setCustomKey(property.wireName, value)
        }
    }

    override fun setUserId(userId: String?) {
        runCatching {
            analytics.setUserId(userId)
            crashlytics.setUserId(userId.orEmpty())
        }
    }

    private fun Map<String, AnalyticsValue>.toBundle(): Bundle =
        Bundle().apply {
            forEach { (key, value) ->
                when (value) {
                    is AnalyticsValue.Text -> putString(key, value.value)
                    is AnalyticsValue.Count -> putLong(key, value.value)
                    is AnalyticsValue.Amount -> putDouble(key, value.value)
                    is AnalyticsValue.Flag -> putLong(key, if (value.value) 1L else 0L)
                }
            }
        }
}
