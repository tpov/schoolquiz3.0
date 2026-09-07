package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Original consent/body survives uncertain replies and ordinary account-switch cleanup. */
@Entity(
    tableName = "arena_command_journal",
    primaryKeys = ["owner_uid", "command_kind", "command_id"],
    indices = [Index(value = ["owner_uid", "ticket_id", "command_kind"], unique = true)],
)
data class ArenaCommandEntity(
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "command_kind") val commandKind: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "ticket_id") val ticketId: String?,
    @ColumnInfo(name = "attempt_id") val attemptId: String?,
    @ColumnInfo(name = "body_json") val bodyJson: String,
    @ColumnInfo(name = "body_sha256") val bodySha256: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "last_failure") val lastFailure: String?,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

internal fun ArenaCommandEntity.sameIntent(other: ArenaCommandEntity): Boolean =
    ownerUid == other.ownerUid && commandKind == other.commandKind && commandId == other.commandId &&
        ticketId == other.ticketId && attemptId == other.attemptId && bodyJson == other.bodyJson &&
        bodySha256 == other.bodySha256
