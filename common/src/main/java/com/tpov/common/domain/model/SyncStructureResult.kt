package com.tpov.common.domain.model

sealed class SyncStructureResult {
        data class Success(val stage: SyncStage, val state: SyncState) : SyncStructureResult()
        data class Error(val stage: SyncStage, val error: String) : SyncStructureResult()
    }