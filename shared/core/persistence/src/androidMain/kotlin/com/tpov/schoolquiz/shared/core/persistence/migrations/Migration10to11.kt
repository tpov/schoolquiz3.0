package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive: queue, attempts, balances and cursors are not rewritten. */
val Migration10to11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mutation_receipts` (" +
                    "`owner_uid` TEXT NOT NULL, `mutation_id` TEXT NOT NULL, " +
                    "`operation` TEXT NOT NULL, `entity_ref` TEXT, " +
                    "`payload_json` TEXT NOT NULL, `received_at_ms` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`owner_uid`, `mutation_id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_mutation_receipts_owner_uid_entity_ref` " +
                    "ON `mutation_receipts` (`owner_uid`, `entity_ref`)",
            )
        }
    }
