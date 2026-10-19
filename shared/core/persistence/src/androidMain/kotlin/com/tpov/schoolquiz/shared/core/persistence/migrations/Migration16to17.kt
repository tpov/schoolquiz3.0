package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Проводка заявок подарков (epic-commerce 7.4): две новые таблицы, прежние строки не тронуты.
 *
 * `gift_claim_intents` — незавершённое намерение заявки (operationId + тело) на пару
 * «владелец + вид»: лежит до запроса и до ответа, переживает смерть процесса. Создаётся пустой —
 * миграции нечего переносить: заявок до этой версии клиент не отправлял вовсе.
 * `gift_box_local_streak` — местный счётчик серии между словами сервера, тоже с нуля.
 */
val Migration16to17 =
    object : Migration(GIFT_CLAIM_WIRING_SCHEMA_FROM, GIFT_CLAIM_WIRING_SCHEMA_TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `gift_claim_intents` (" +
                    "`owner_uid` TEXT NOT NULL, `kind` TEXT NOT NULL, `operation_id` TEXT NOT NULL, " +
                    "`payload` TEXT NOT NULL, `created_at_ms` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`owner_uid`, `kind`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `gift_box_local_streak` (" +
                    "`owner_uid` TEXT NOT NULL, `base_box_count` INTEGER NOT NULL, " +
                    "`base_streak_days` INTEGER NOT NULL, `base_next_box_at_ms` INTEGER NOT NULL, " +
                    "`box_count` INTEGER NOT NULL, `streak_days` INTEGER NOT NULL, " +
                    "`next_box_at_ms` INTEGER NOT NULL, `updated_at_ms` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`owner_uid`))",
            )
        }
    }

private const val GIFT_CLAIM_WIRING_SCHEMA_FROM = 16
private const val GIFT_CLAIM_WIRING_SCHEMA_TO = 17
