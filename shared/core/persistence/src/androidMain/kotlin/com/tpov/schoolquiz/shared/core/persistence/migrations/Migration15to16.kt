package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE quest_drafts
            SET status = 'SAVED'
            WHERE status IN ('DRAFT', 'PRIVATE')
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE quest_drafts
            SET status = 'SYNCED'
            WHERE status = 'SYNCED_PRIVATE'
            """.trimIndent(),
        )
    }
}
