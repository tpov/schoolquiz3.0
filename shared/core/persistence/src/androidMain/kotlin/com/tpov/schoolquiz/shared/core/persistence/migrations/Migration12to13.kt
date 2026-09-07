package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Financial journal only. No legacy attempt promotion, backfill, dropped table or balance rewrite. */
val Migration12to13 =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `arena_ticket_records` (" +
                    "`owner_uid` TEXT NOT NULL, `ticket_id` TEXT NOT NULL, `attempt_id` TEXT NOT NULL, " +
                    "`request_id` TEXT NOT NULL, `terms_json` TEXT NOT NULL, `snapshot_json` TEXT, " +
                    "`snapshot_sha256` TEXT, `server_status` TEXT NOT NULL, `terminal_json` TEXT, " +
                    "`local_use` TEXT NOT NULL, `decision` TEXT NOT NULL, `observed_at_ms` INTEGER NOT NULL, " +
                    "`runner_checkpoint_json` TEXT, `runner_checkpoint_revision` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`owner_uid`, `ticket_id`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_arena_ticket_records_owner_uid_attempt_id` " +
                    "ON `arena_ticket_records` (`owner_uid`, `attempt_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `arena_command_journal` (" +
                    "`owner_uid` TEXT NOT NULL, `command_kind` TEXT NOT NULL, `command_id` TEXT NOT NULL, " +
                    "`ticket_id` TEXT, `attempt_id` TEXT, `body_json` TEXT NOT NULL, `body_sha256` TEXT NOT NULL, " +
                    "`state` TEXT NOT NULL, `last_failure` TEXT, `created_at_ms` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`owner_uid`, `command_kind`, `command_id`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_arena_command_journal_owner_uid_ticket_id_command_kind` " +
                    "ON `arena_command_journal` (`owner_uid`, `ticket_id`, `command_kind`)",
            )
        }
    }
