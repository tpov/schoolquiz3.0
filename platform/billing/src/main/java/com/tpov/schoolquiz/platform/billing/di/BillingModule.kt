package com.tpov.schoolquiz.platform.billing.di

import android.app.Application
import com.tpov.schoolquiz.platform.billing.CurrentActivityHolder
import com.tpov.schoolquiz.platform.billing.PlayBillingRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.BillingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Billing wiring.
 *
 * [CurrentActivityHolder] registers itself against the Application here rather than in
 * `AppApplication`, so nothing outside this module needs to know that launching a purchase flow
 * requires an Activity at all.
 */
val billingModule =
    module {
        single {
            CurrentActivityHolder().apply { register(androidApplication() as Application) }
        }

        single<BillingRepository> {
            PlayBillingRepository(
                context = androidContext(),
                activityHolder = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }
    }
