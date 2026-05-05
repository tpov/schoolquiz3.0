package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS review_assignments (
                id TEXT NOT NULL PRIMARY KEY,
                ownerUid TEXT NOT NULL,
                submissionId TEXT NOT NULL,
                catalogId TEXT NOT NULL,
                draftId TEXT NOT NULL,
                questId TEXT NOT NULL,
                lessonId TEXT NOT NULL,
                title TEXT NOT NULL,
                createdAtMs INTEGER NOT NULL,
                taskKinds TEXT NOT NULL,
                sourceLanguages TEXT NOT NULL,
                newTranslationLanguages TEXT NOT NULL,
                reviewLanguages TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_review_assignments_ownerUid
            ON review_assignments (ownerUid)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_review_assignments_lessonId
            ON review_assignments (lessonId)
            """.trimIndent(),
        )
    }
}
