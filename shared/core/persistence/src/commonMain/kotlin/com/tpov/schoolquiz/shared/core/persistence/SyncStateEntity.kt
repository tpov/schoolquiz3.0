package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val collectionId: String,
    val cursor: Long,
)
