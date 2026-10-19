package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Часы премиального суточного подарка доезжают до клиента (epic-commerce 7.3/7.4).
 *
 * Только новая колонка со значением по умолчанию: прежние строки — аккаунты, чьё слово о бонусе
 * ещё не приходило. Ни сброса, ни переписывания профиля.
 */
val Migration15to16 =
    object : Migration(GIFT_BONUS_CLOCK_SCHEMA_FROM, GIFT_BONUS_CLOCK_SCHEMA_TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `nextBonusBoxAtMs` INTEGER NOT NULL DEFAULT 0")
        }
    }

private const val GIFT_BONUS_CLOCK_SCHEMA_FROM = 15
private const val GIFT_BONUS_CLOCK_SCHEMA_TO = 16
