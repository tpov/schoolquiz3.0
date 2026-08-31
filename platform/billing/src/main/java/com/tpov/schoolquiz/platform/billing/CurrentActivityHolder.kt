package com.tpov.schoolquiz.platform.billing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Keeps a weak handle on whichever Activity is currently resumed.
 *
 * `launchBillingFlow` needs an Activity, but a domain repository must not take one — an Android
 * type in a domain signature is exactly what the layering rules forbid. So the Activity is found
 * here, on the platform side, and the domain contract stays `purchase(productId)`.
 *
 * The reference is weak and cleared on pause. A strong static reference to an Activity is the
 * textbook memory leak, and a billing flow launched into a destroyed Activity crashes.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity> = WeakReference(null)

    val activity: Activity?
        get() = current.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.get() === activity) {
            current = WeakReference(null)
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (current.get() === activity) {
            current = WeakReference(null)
        }
    }
}
