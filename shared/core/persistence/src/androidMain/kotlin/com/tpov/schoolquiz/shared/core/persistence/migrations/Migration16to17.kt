package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN boxCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN boxStreakDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN nextBoxAtMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN premiumUntilMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN trophies INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN ownedLogos TEXT NOT NULL DEFAULT ''")
    }
}
