package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE questions ADD COLUMN languageLevel INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE draft_questions ADD COLUMN languageLevel INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quest_arena_submission_outbox (
                id TEXT NOT NULL PRIMARY KEY,
                draftId TEXT NOT NULL,
                ownerUid TEXT NOT NULL,
                localRevision INTEGER NOT NULL,
                requestedAtMs INTEGER NOT NULL,
                attemptCount INTEGER NOT NULL,
                lastError TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_quest_arena_submission_outbox_draftId
            ON quest_arena_submission_outbox (draftId)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_quest_arena_submission_outbox_requestedAtMs
            ON quest_arena_submission_outbox (requestedAtMs)
            """.trimIndent(),
        )
    }
}
