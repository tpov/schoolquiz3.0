package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Retained random-id arena rating intents; no legacy marker is promoted into a fabricated intent. */
val Migration13to14 =
    object : Migration(ARENA_FROZEN_RATING_SCHEMA_FROM, ARENA_FROZEN_RATING_SCHEMA_TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `arena_frozen_rating_intents` (" +
                    "`owner_uid` TEXT NOT NULL, `ticket_id` TEXT NOT NULL, `attempt_id` TEXT NOT NULL, " +
                    "`lesson_id` TEXT NOT NULL, `rating_id` TEXT NOT NULL, `mutation_id` TEXT NOT NULL, " +
                    "`operation` TEXT NOT NULL, `payload` TEXT NOT NULL, `entity_ref` TEXT NOT NULL, " +
                    "`created_at_ms` INTEGER NOT NULL, PRIMARY KEY(`owner_uid`, `ticket_id`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_arena_frozen_rating_intents_owner_uid_mutation_id` " +
                    "ON `arena_frozen_rating_intents` (`owner_uid`, `mutation_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_arena_frozen_rating_intents_owner_uid_lesson_id` " +
                    "ON `arena_frozen_rating_intents` (`owner_uid`, `lesson_id`)",
            )
        }
    }

private const val ARENA_FROZEN_RATING_SCHEMA_FROM = 13
private const val ARENA_FROZEN_RATING_SCHEMA_TO = 14
