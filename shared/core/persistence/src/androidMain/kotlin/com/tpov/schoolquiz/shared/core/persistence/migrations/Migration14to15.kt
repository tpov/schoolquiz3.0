package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lesson_result_attempt_outbox (
                attempt_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                scope TEXT NOT NULL,
                owner_uid TEXT,
                catalog_id TEXT NOT NULL,
                quest_id TEXT NOT NULL,
                section_id TEXT NOT NULL,
                theme_id TEXT NOT NULL,
                lesson_id TEXT NOT NULL,
                lesson_version INTEGER NOT NULL,
                source_shelf TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                code_answer TEXT NOT NULL,
                percent_score INTEGER NOT NULL,
                completed_at_ms INTEGER NOT NULL,
                created_at_ms INTEGER NOT NULL,
                sent_at_ms INTEGER,
                last_error TEXT,
                PRIMARY KEY(attempt_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_result_attempt_outbox_pending
            ON lesson_result_attempt_outbox(sent_at_ms, completed_at_ms)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_result_attempt_outbox_content
            ON lesson_result_attempt_outbox(scope, catalog_id, quest_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quest_rating_outbox (
                rating_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                scope TEXT NOT NULL,
                owner_uid TEXT,
                catalog_id TEXT NOT NULL,
                quest_id TEXT NOT NULL,
                section_id TEXT NOT NULL,
                theme_id TEXT NOT NULL,
                lesson_id TEXT NOT NULL,
                lesson_version INTEGER NOT NULL,
                source_shelf TEXT NOT NULL,
                rating INTEGER NOT NULL,
                rated_at_ms INTEGER NOT NULL,
                created_at_ms INTEGER NOT NULL,
                sent_at_ms INTEGER,
                last_error TEXT,
                PRIMARY KEY(rating_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_quest_rating_outbox_pending
            ON quest_rating_outbox(sent_at_ms, rated_at_ms)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_quest_rating_outbox_content
            ON quest_rating_outbox(scope, catalog_id, quest_id)
            """.trimIndent(),
        )
    }
}
