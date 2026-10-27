package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Мир живых проверяющих удалён (4.19, UX-17): локальные таблицы заданий на ревью больше никем
 * не пишутся и не читаются — ревью делает модельный конвейер, автор видит исход в My Quests,
 * админ — во вкладке «Решения». Дропаем обе таблицы целиком: данные заданий — серверный мир,
 * локальная копия после удаления читателей стала мёртвым грузом.
 */
val Migration18to19 =
    object : Migration(REVIEW_ASSIGNMENT_SCHEMA_FROM, REVIEW_ASSIGNMENT_SCHEMA_TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `review_assignment_questions`")
            db.execSQL("DROP TABLE IF EXISTS `review_assignments`")
        }
    }

private const val REVIEW_ASSIGNMENT_SCHEMA_FROM = 18
private const val REVIEW_ASSIGNMENT_SCHEMA_TO = 19
