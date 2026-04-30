package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import com.tpov.schoolquiz.shared.core.sync.SyncScheduler

class FakeSyncScheduler : SyncScheduler {
    var enqueueManualSyncCalls: Int = 0

    override fun enqueueManualSync() {
        enqueueManualSyncCalls++
    }
}
