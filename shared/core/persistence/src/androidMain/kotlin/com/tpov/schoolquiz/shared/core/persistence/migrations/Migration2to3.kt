package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the lessons a player has bought open to their stats row.
 *
 * Stored as the same comma-joined text every other string set in this database uses, so the
 * existing converter reads it without a second format to maintain. An empty default is the honest
 * starting value: nobody who upgrades has bought anything yet.
 *
 * A plain column addition rather than a table rebuild — the existing rows are correct as they are.
 */
val Migration2to3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_stats` ADD COLUMN `lessonUnlocks` TEXT NOT NULL DEFAULT ''")
        }
    }
