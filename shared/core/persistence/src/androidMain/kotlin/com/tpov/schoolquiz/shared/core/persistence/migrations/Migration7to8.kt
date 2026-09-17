package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Даёт курсору чтения вторую половину — id документа журнала (AD-31).
 *
 * До этого курсор был одним числом. Внутри прохода пара жила в памяти, но перезапуск оставлял
 * только время: чтение возвращалось к началу той же миллисекунды и перечитывало её целиком. Это
 * не потеря, а повторная работа — и она становится тупиком, когда в одной миллисекунде больше
 * записей, чем помещается на страницу: каждый запуск читал бы ту же первую страницу.
 *
 * `ALTER TABLE ... ADD COLUMN`, а не пересоздание (NFR11): курсоры — то, что нельзя терять молча,
 * иначе журнал перечитывается или, хуже, пропускается. У существующих строк вторая половина пуста:
 * по лексикографическому сравнению пара с пустым id — граница не позже прежней, поэтому записи не
 * теряются, а повторное применение идемпотентно.
 */
val Migration7to8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `sync_state` ADD COLUMN `doc_id` TEXT NOT NULL DEFAULT ''")
        }
    }
