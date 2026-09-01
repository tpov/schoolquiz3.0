package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quest_drafts",
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["catalogId"]),
        Index(value = ["updatedAtMs"]),
        Index(value = ["isActive"]),
    ],
)
data class QuestDraftEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val catalogId: String,
    val title: String,
    val description: String?,
    val defaultLanguage: String,
    val defaultDifficulty: String,
    val status: String,
    val localRevision: Long,
    val serverRevision: Long?,
    val publicQuestId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val isActive: Boolean,
    /**
     * Why a reviewer sent this draft back, verbatim. Null unless the last submission was
     * rejected; cleared when the author resubmits.
     */
    val rejectionReason: String? = null,
)
