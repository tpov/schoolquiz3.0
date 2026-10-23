package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Настройки приватной ссылки в черновике автора (AD-29, эпик 8, история 8.3).
 *
 * Три nullable-колонки в `quest_drafts`, по одной на авторское поле. Только новые колонки:
 * прежние строки — обычные квесты, настроек у них нет, и «нет» здесь выражается пустотой всех
 * трёх столбцов. Ни сброса, ни переписывания черновиков.
 */
val Migration17to18 =
    object : Migration(PRIVATE_QUIZ_SETTINGS_SCHEMA_FROM, PRIVATE_QUIZ_SETTINGS_SCHEMA_TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `quest_drafts` ADD COLUMN `privateQuizAttemptLimit` INTEGER")
            db.execSQL("ALTER TABLE `quest_drafts` ADD COLUMN `privateQuizShowResult` INTEGER")
            db.execSQL("ALTER TABLE `quest_drafts` ADD COLUMN `privateQuizLinkExpiresAtMs` INTEGER")
        }
    }

private const val PRIVATE_QUIZ_SETTINGS_SCHEMA_FROM = 17
private const val PRIVATE_QUIZ_SETTINGS_SCHEMA_TO = 18
