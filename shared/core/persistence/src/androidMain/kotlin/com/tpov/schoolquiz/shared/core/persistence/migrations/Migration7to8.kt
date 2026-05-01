package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quest_drafts (
                id TEXT NOT NULL PRIMARY KEY,
                ownerUid TEXT NOT NULL,
                catalogId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                defaultLanguage TEXT NOT NULL,
                defaultDifficulty TEXT NOT NULL,
                status TEXT NOT NULL,
                localRevision INTEGER NOT NULL,
                serverRevision INTEGER,
                publicQuestId TEXT,
                createdAtMs INTEGER NOT NULL,
                updatedAtMs INTEGER NOT NULL,
                isActive INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_drafts_ownerUid ON quest_drafts (ownerUid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_drafts_catalogId ON quest_drafts (catalogId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_drafts_updatedAtMs ON quest_drafts (updatedAtMs)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_drafts_isActive ON quest_drafts (isActive)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS draft_sections (
                id TEXT NOT NULL PRIMARY KEY,
                draftId TEXT NOT NULL,
                title TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                FOREIGN KEY(draftId) REFERENCES quest_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_sections_draftId ON draft_sections (draftId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS draft_themes (
                id TEXT NOT NULL PRIMARY KEY,
                draftId TEXT NOT NULL,
                sectionId TEXT NOT NULL,
                title TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                FOREIGN KEY(draftId) REFERENCES quest_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(sectionId) REFERENCES draft_sections(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_themes_draftId ON draft_themes (draftId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_themes_sectionId ON draft_themes (sectionId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS draft_lessons (
                id TEXT NOT NULL PRIMARY KEY,
                draftId TEXT NOT NULL,
                themeId TEXT NOT NULL,
                title TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                FOREIGN KEY(draftId) REFERENCES quest_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(themeId) REFERENCES draft_themes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_lessons_draftId ON draft_lessons (draftId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_lessons_themeId ON draft_lessons (themeId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS draft_questions (
                id TEXT NOT NULL PRIMARY KEY,
                draftId TEXT NOT NULL,
                lessonId TEXT NOT NULL,
                type TEXT NOT NULL,
                language TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                text TEXT NOT NULL,
                imagePath TEXT,
                payload TEXT,
                validationState TEXT NOT NULL,
                updatedAtMs INTEGER NOT NULL,
                FOREIGN KEY(draftId) REFERENCES quest_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(lessonId) REFERENCES draft_lessons(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_questions_draftId ON draft_questions (draftId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_questions_lessonId ON draft_questions (lessonId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_questions_updatedAtMs ON draft_questions (updatedAtMs)")
    }
}
