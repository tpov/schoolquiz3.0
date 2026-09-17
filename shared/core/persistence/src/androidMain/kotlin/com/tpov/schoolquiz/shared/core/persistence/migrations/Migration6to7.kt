package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Даёт записи очереди место под версию, названную сервером при конфликте (AD-24).
 *
 * До этого у записи было только состояние `CONFLICT` и текст последней ошибки. Разрешать по ним
 * нечего: текст разбирать запрещено (AD-15), а без числа «отправить заново поверх версии 7» не
 * собирается — оставалось спросить сервер вслепую ещё раз и получить тот же отказ.
 *
 * `ALTER TABLE ... ADD COLUMN`, а не пересоздание: очередь может быть непустой, и в ней лежат
 * действия игрока, которые ещё не доехали. Колонка обнуляемая — у записей, конфликта не видевших,
 * версии нет и быть не должно.
 *
 * Индекс по `(owner_uid, entity_ref)` — вторая половина той же истории: экран черновика
 * спрашивает очередь про свою сущность при каждой отрисовке, и без индекса каждый такой вопрос
 * читает таблицу целиком.
 */
val Migration6to7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `outbox` ADD COLUMN `server_version` INTEGER")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_outbox_owner_uid_entity_ref` " +
                    "ON `outbox` (`owner_uid`, `entity_ref`)",
            )
        }
    }
