package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler

class FakeSyncScheduler : SyncScheduler {
    var enqueueManualSyncCalls: Int = 0
    var appliedFrequencies: MutableList<SyncFrequency> = mutableListOf()

    var enqueueManualProfileSyncCalls: Int = 0

    override fun enqueueManualSync() {
        enqueueManualSyncCalls++
    }

    override fun enqueueManualProfileSync() {
        enqueueManualProfileSyncCalls++
    }

    var appliedProfileFrequencies: MutableList<SyncFrequency> = mutableListOf()

    override fun applyFrequency(frequency: SyncFrequency) {
        appliedFrequencies += frequency
    }

    override fun applyProfileFrequency(frequency: SyncFrequency) {
        appliedProfileFrequencies += frequency
    }
}
