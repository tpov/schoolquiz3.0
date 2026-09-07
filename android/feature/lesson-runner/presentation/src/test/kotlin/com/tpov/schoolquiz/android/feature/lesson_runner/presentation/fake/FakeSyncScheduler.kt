package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler

class FakeSyncScheduler : SyncScheduler {
    var manualRequests = 0
        private set

    override fun enqueueManualSync() {
        manualRequests++
    }

    override fun enqueueManualProfileSync() = Unit

    override fun applyFrequency(frequency: SyncFrequency) = Unit

    override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
}
