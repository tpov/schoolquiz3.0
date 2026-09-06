package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "mutation_receipts",
    primaryKeys = ["owner_uid", "mutation_id"],
    indices = [Index(value = ["owner_uid", "entity_ref"])],
)
data class MutationReceiptEntity(
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "entity_ref") val entityRef: String?,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "received_at_ms") val receivedAtMs: Long,
)
