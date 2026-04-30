package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_state (
                collectionId TEXT NOT NULL,
                cursor INTEGER NOT NULL,
                PRIMARY KEY(collectionId)
            )
            """.trimIndent(),
        )
    }
}
