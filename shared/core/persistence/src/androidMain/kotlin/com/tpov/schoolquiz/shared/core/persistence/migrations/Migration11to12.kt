package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS review_assignments_new (
                id TEXT NOT NULL,
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
                reviewLanguages TEXT NOT NULL,
                isTested INTEGER NOT NULL,
                testingScore REAL,
                isLogicReviewed INTEGER NOT NULL,
                logicScore REAL,
                isTranslationReviewed INTEGER NOT NULL,
                translationScore INTEGER,
                translatedLanguages TEXT NOT NULL,
                PRIMARY KEY(ownerUid, id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO review_assignments_new (
                id,
                ownerUid,
                submissionId,
                catalogId,
                draftId,
                questId,
                lessonId,
                title,
                createdAtMs,
                taskKinds,
                sourceLanguages,
                newTranslationLanguages,
                reviewLanguages,
                isTested,
                testingScore,
                isLogicReviewed,
                logicScore,
                isTranslationReviewed,
                translationScore,
                translatedLanguages
            )
            SELECT
                id,
                ownerUid,
                submissionId,
                catalogId,
                draftId,
                questId,
                lessonId,
                title,
                createdAtMs,
                taskKinds,
                sourceLanguages,
                newTranslationLanguages,
                reviewLanguages,
                isTested,
                testingScore,
                isLogicReviewed,
                logicScore,
                isTranslationReviewed,
                translationScore,
                translatedLanguages
            FROM review_assignments
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE review_assignments")
        db.execSQL("ALTER TABLE review_assignments_new RENAME TO review_assignments")
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
