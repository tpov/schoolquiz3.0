package com.tpov.common.domain.model

sealed class SyncStage {
        data object NOT_STARTED : SyncStage()
        data object STRUCTURE_FETCH : SyncStage()
        data object STRUCTURE_LOCAL_SYNC : SyncStage()
        data object QUESTION_CHANGE_LIST : SyncStage()
        data object QUESTIONS_FETCH : SyncStage()
        data object INFO_UPDATE_LOCAL : SyncStage()
        data object INFO_UPDATE_REMOTE : SyncStage()
        data object COMPLETE : SyncStage()
    }