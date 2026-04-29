package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lesson_attempts (
                attempt_id      TEXT    NOT NULL PRIMARY KEY,
                user_id         TEXT    NOT NULL,
                lesson_id       TEXT    NOT NULL,
                lesson_version  INTEGER NOT NULL,
                is_hard         INTEGER NOT NULL DEFAULT 0,
                code_answer     TEXT    NOT NULL,
                percent_score   INTEGER NOT NULL,
                completed_at    INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_lesson_attempts_user_id ON lesson_attempts (user_id)")
        db.execSQL("CREATE INDEX idx_lesson_attempts_lesson_id ON lesson_attempts (lesson_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lesson_rating_submitted_local (
                user_id         TEXT    NOT NULL,
                lesson_id       TEXT    NOT NULL,
                submitted_at    INTEGER NOT NULL,
                PRIMARY KEY (user_id, lesson_id)
            )
            """.trimIndent(),
        )

        db.execSQL("ALTER TABLE lessons ADD COLUMN average_rating REAL")
        db.execSQL("ALTER TABLE lessons ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lessons ADD COLUMN top3 TEXT NOT NULL DEFAULT '[]'")
    }
}
