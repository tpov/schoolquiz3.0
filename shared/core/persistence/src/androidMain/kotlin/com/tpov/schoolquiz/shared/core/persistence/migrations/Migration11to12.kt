package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add only an immutable local intent marker. Legacy rows are deliberately not backfilled. */
val Migration11to12 =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `lesson_attempt_intents` (" +
                    "`attempt_id` TEXT NOT NULL, `owner_uid` TEXT NOT NULL, `mutation_id` TEXT NOT NULL, " +
                    "`operation` TEXT NOT NULL, `payload` TEXT NOT NULL, `entity_ref` TEXT, " +
                    "`expected_version` INTEGER, PRIMARY KEY(`attempt_id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_lesson_attempt_intents_owner_uid` " +
                    "ON `lesson_attempt_intents` (`owner_uid`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_attempt_intents_mutation_id` " +
                    "ON `lesson_attempt_intents` (`mutation_id`)",
            )
        }
    }
