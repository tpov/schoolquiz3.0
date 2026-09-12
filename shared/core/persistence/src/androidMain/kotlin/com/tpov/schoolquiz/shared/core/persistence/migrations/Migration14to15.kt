package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Correlates an awaiting author draft with the one review submission allowed to settle it. */
val Migration14to15 =
    object : Migration(ACTIVE_REVIEW_SUBMISSION_SCHEMA_FROM, ACTIVE_REVIEW_SUBMISSION_SCHEMA_TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `quest_drafts` ADD COLUMN `activeSubmissionId` TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_quest_drafts_ownerUid_status_activeSubmissionId` " +
                    "ON `quest_drafts` (`ownerUid`, `status`, `activeSubmissionId`)",
            )
        }
    }

private const val ACTIVE_REVIEW_SUBMISSION_SCHEMA_FROM = 14
private const val ACTIVE_REVIEW_SUBMISSION_SCHEMA_TO = 15
