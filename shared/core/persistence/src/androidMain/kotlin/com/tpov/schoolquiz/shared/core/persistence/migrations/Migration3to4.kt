package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Заводит общую очередь отложенных действий.
 *
 * Спайн (AD-16) разрешает пересоздать базу одной чистой схемой, раз живых установок нет. Здесь
 * выбрана миграция, а не пересоздание, по причине из того же AD: пересоздавать можно только при
 * пустой очереди и опубликованных черновиках, а неопубликованный черновик квеста серверного
 * двойника не имеет и восстановлению не подлежит. Добавление таблицы этой опасности не несёт
 * вовсе, поэтому расчищать историю миграций отложено — см. `deferred-work.md`.
 *
 * Уникальный индекс по `mutation_id` держит обещание AD-2: один ключ на одно намерение игрока.
 */
val Migration3to4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outbox` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `mutation_id` TEXT NOT NULL,
                    `owner_uid` TEXT NOT NULL,
                    `operation` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `entity_ref` TEXT,
                    `expected_version` INTEGER,
                    `state` TEXT NOT NULL,
                    `attempt_count` INTEGER NOT NULL,
                    `next_retry_at_ms` INTEGER NOT NULL,
                    `last_error` TEXT,
                    `created_at_ms` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_mutation_id` ON `outbox` (`mutation_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_outbox_owner_uid_state_next_retry_at_ms` " +
                    "ON `outbox` (`owner_uid`, `state`, `next_retry_at_ms`)",
            )
        }
    }
