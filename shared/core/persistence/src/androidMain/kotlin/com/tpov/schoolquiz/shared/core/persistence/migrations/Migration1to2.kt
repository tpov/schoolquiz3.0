package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the details somebody submits for verification, and the date they were confirmed.
 *
 * The table is rebuilt rather than altered because `trophies` also changed shape — it was a count
 * and is now a list of badge names, so the column moved from INTEGER to TEXT, and SQLite cannot
 * alter a column's type in place.
 *
 * The old rows are dropped rather than converted. This table is a cache of the account as the
 * server knows it: everything in it comes back on the next sync, and the one thing that could be
 * converted — a trophy count — cannot be turned into names, because the count never recorded which
 * badges they were.
 */
val Migration1to2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `user_profiles`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_profiles` (" +
                    "`uid` TEXT NOT NULL, `nickname` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                    "`avatarUrl` TEXT, `knownLanguages` TEXT NOT NULL, " +
                    "`createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, " +
                    "`skillPoints` INTEGER NOT NULL, `gold` INTEGER NOT NULL, " +
                    "`nolics` INTEGER NOT NULL, `standardHearts` INTEGER NOT NULL, " +
                    "`goldHearts` INTEGER NOT NULL, `sponsorLevel` INTEGER NOT NULL, " +
                    "`testerLevel` INTEGER NOT NULL, `translatorLevel` INTEGER NOT NULL, " +
                    "`moderatorLevel` INTEGER NOT NULL, `adminLevel` INTEGER NOT NULL, " +
                    "`developerLevel` INTEGER NOT NULL, `boxCount` INTEGER NOT NULL, " +
                    "`boxStreakDays` INTEGER NOT NULL, `nextBoxAtMs` INTEGER NOT NULL, " +
                    "`premiumUntilMs` INTEGER NOT NULL, `trophies` TEXT NOT NULL, " +
                    "`ownedLogos` TEXT NOT NULL, `lifePoints` INTEGER NOT NULL, " +
                    "`lifePointsUpdatedAtMs` INTEGER NOT NULL, `realName` TEXT, " +
                    "`birthday` TEXT, `city` TEXT, `telegram` TEXT, " +
                    "`verifiedAtMs` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
            )
        }
    }
