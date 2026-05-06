package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_profiles (
                uid TEXT NOT NULL,
                nickname TEXT NOT NULL,
                status TEXT NOT NULL,
                avatarUrl TEXT,
                knownLanguages TEXT NOT NULL,
                createdAtMs INTEGER NOT NULL,
                updatedAtMs INTEGER NOT NULL,
                skillPoints INTEGER NOT NULL,
                gold INTEGER NOT NULL,
                nolics INTEGER NOT NULL,
                standardHearts INTEGER NOT NULL,
                goldHearts INTEGER NOT NULL,
                sponsorLevel INTEGER NOT NULL,
                testerLevel INTEGER NOT NULL,
                translatorLevel INTEGER NOT NULL,
                moderatorLevel INTEGER NOT NULL,
                adminLevel INTEGER NOT NULL,
                developerLevel INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent(),
        )
    }
}
