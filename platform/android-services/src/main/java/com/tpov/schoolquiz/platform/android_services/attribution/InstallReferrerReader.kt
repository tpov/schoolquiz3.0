package com.tpov.schoolquiz.platform.android_services.attribution

import android.content.Context
import android.content.SharedPreferences
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsEvent
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.analytics.UserProperty

/**
 * Reads the Play install referrer once, and reports where the install came from.
 *
 * This is the free half of attribution. It needs no third-party SDK, no account and no dev key,
 * and it answers the question that decides the acquisition budget: did this install come from a
 * Telegram post, a creator's video, or organic store search. A paid attribution vendor buys
 * deeper cohort analysis on top; it does not buy this.
 *
 * The referrer is available exactly once, shortly after install, so the result is recorded to
 * preferences and the client is never asked again.
 */
class InstallReferrerReader(
    private val context: Context,
    private val analytics: AnalyticsTracker,
    private val appVersion: String,
) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readOnce() {
        if (prefs.getBoolean(KEY_READ, false)) return

        val client = InstallReferrerClient.newBuilder(context).build()
        client.startConnection(
            object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching {
                            val referrer = client.installReferrer.installReferrer.orEmpty()
                            report(referrer)
                        }
                    }
                    // Any terminal response means we will not get a better answer later:
                    // FEATURE_NOT_SUPPORTED and SERVICE_UNAVAILABLE are both permanent for
                    // this install, so retrying on every launch would only burn a connection.
                    prefs.edit().putBoolean(KEY_READ, true).apply()
                    runCatching { client.endConnection() }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            },
        )
    }

    private fun report(referrer: String) {
        analytics.track(
            AnalyticsEvent.InstallAttributed(referrer = referrer, installVersion = appVersion),
        )
        analytics.setUserProperty(UserProperty.ACQUISITION_SOURCE, referrer.toSourceBucket())
    }

    /**
     * Collapses a referrer string to something worth segmenting by.
     *
     * The raw referrer is a URL-encoded query with campaign, medium and content in it — far too
     * granular to be a user property, and Firebase caps property values at 36 characters anyway.
     * The bucket is the part a budget decision actually turns on.
     */
    private fun String.toSourceBucket(): String {
        val source =
            split('&')
                .firstOrNull { it.startsWith("utm_source=") }
                ?.removePrefix("utm_source=")
                ?.takeIf { it.isNotBlank() }
        return when {
            source != null -> source.take(MAX_PROPERTY_LENGTH)
            isBlank() -> "unknown"
            contains("not%20set") || contains("not set") -> "organic"
            else -> "other"
        }
    }

    private companion object {
        const val PREFS = "attribution_state"
        const val KEY_READ = "install_referrer_read"
        const val MAX_PROPERTY_LENGTH = 36
    }
}
