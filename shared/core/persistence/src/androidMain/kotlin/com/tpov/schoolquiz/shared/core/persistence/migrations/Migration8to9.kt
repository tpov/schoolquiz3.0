package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Заводит след прогона синхронизации — строку на владельца (история 4.2, AD-14).
 *
 * До этого время последнего успеха и ветвь последней ошибки жили в памяти процесса: холодный старт
 * показывал «синхронизации ещё не было» игроку, у которого она прошла минуту назад. Числа очереди
 * при этом перезапуск переживали — расхождение видел любой, кто закрывал приложение.
 *
 * Новая таблица, а не колонка: след принадлежит владельцу, и читается он по его ключу — чужого
 * времени после смены аккаунта на экране не оказывается. Данных миграция не трогает: у существующих
 * устройств следа не было вовсе, и первая же удачная синхронизация напишет свой.
 *
 * Пустая таблица на старте честна: «этот аккаунт на этом устройстве ещё не синхронизировался» —
 * ровно то, что приложение про него знает.
 */
val Migration8to9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_run_trace` (" +
                    "`owner_uid` TEXT NOT NULL, " +
                    "`last_success_at_ms` INTEGER NOT NULL, " +
                    "`last_error_branch` TEXT, " +
                    "`last_error_reason` TEXT, " +
                    "`last_error_server_version` INTEGER, " +
                    "PRIMARY KEY(`owner_uid`))",
            )
        }
    }
