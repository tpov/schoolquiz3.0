package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Даёт черновику место под причину отказа.
 *
 * До этого проверка умела только молчать: низкая оценка оставляла квест «на проверке» навсегда, и
 * автор не узнавал ни что случилось, ни что чинить. Причина приходит с сервера вместе со статусом
 * REJECTED и живёт на черновике, пока автор не отправит его заново.
 */
val Migration4to5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `quest_drafts` ADD COLUMN `rejectionReason` TEXT")
        }
    }
