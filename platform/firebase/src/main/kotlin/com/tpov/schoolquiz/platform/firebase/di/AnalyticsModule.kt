package com.tpov.schoolquiz.platform.firebase.di

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tpov.schoolquiz.platform.firebase.analytics.FirebaseAnalyticsTracker
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The one place Firebase is named as the analytics backend.
 *
 * Everything upstream depends on [AnalyticsTracker]; swapping or adding a second backend is a
 * change to this file and nowhere else.
 */
val analyticsModule =
    module {
        single { FirebaseAnalytics.getInstance(androidContext()) }
        single { FirebaseCrashlytics.getInstance() }
        single<AnalyticsTracker> { FirebaseAnalyticsTracker(analytics = get(), crashlytics = get()) }
    }
