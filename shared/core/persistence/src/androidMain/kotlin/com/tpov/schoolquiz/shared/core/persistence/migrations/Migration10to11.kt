package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN isTested INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN testingScore REAL")
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN isLogicReviewed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN logicScore REAL")
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN isTranslationReviewed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN translationScore INTEGER")
        db.execSQL("ALTER TABLE review_assignments ADD COLUMN translatedLanguages TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS review_assignment_questions (
                ownerUid TEXT NOT NULL,
                assignmentId TEXT NOT NULL,
                questionId TEXT NOT NULL,
                draftId TEXT NOT NULL,
                lessonId TEXT NOT NULL,
                type TEXT NOT NULL,
                language TEXT NOT NULL,
                languageLevel INTEGER NOT NULL,
                difficulty TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                text TEXT NOT NULL,
                imagePath TEXT,
                payload TEXT NOT NULL,
                updatedAtMs INTEGER NOT NULL,
                PRIMARY KEY(ownerUid, assignmentId, questionId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_review_assignment_questions_ownerUid
            ON review_assignment_questions (ownerUid)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_review_assignment_questions_assignmentId
            ON review_assignment_questions (assignmentId)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_review_assignment_questions_lessonId
            ON review_assignment_questions (lessonId)
            """.trimIndent(),
        )
    }
}
