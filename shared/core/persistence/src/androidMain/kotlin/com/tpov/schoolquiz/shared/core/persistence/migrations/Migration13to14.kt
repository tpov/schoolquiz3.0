package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE quest_arena_submission_outbox
            ADD COLUMN lessonIds TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE quest_arena_submission_outbox
            ADD COLUMN targetShelf TEXT NOT NULL DEFAULT 'arena'
            """.trimIndent(),
        )
    }
}
