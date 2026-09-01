package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration1to2
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration2to3
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration3to4
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration4to5
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration5to6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema step has to be a real migration, and every real migration has to be proven.
 *
 * `runMigrationsAndValidate` is the proof: it opens a database created at the previous version,
 * applies the migration, and then compares the result against the exported `N.json`. A migration
 * that forgets a column, an index or a default fails here rather than on a player's device.
 *
 * The pairing between `schemas/N.json` and a test in this file is enforced on the JVM side by
 * [MigrationCoverageTest], which runs in `ciCheck` — these instrumented tests need a device.
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate_1_to_2_validates_against_exported_schema() {
        helper.createDatabase(DB_NAME, version = 1).close()

        helper.runMigrationsAndValidate(DB_NAME, 2, true, Migration1to2).close()
    }

    @Test
    fun migrate_2_to_3_validates_and_keeps_data_written_before_it() {
        helper.createDatabase(DB_NAME, version = 2).use { db ->
            // A sync cursor is the cheapest row that must never be lost: losing it silently
            // re-reads or, worse, skips a slice of the change journal.
            db.execSQL("INSERT INTO sync_state (collectionId, cursor) VALUES ('catalogs', 1700)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, Migration2to3).use { db ->
            db.query("SELECT cursor FROM sync_state WHERE collectionId = 'catalogs'").use { c ->
                assertTrue("cursor written before the migration disappeared", c.moveToFirst())
                assertEquals(1700L, c.getLong(0))
            }
        }
    }

    @Test
    fun migrate_3_to_4_validates_against_exported_schema() {
        helper.createDatabase(DB_NAME, version = 3).close()

        helper.runMigrationsAndValidate(DB_NAME, 4, true, Migration3to4).close()
    }

    @Test
    fun migrate_4_to_5_validates_against_exported_schema() {
        helper.createDatabase(DB_NAME, version = 4).close()

        helper.runMigrationsAndValidate(DB_NAME, 5, true, Migration4to5).close()
    }

    @Test
    fun migrate_5_to_6_moves_the_three_queues_into_one_and_drops_them() {
        helper.createDatabase(DB_NAME, version = 5).use { db ->
            // По строке в каждую из трёх очередей. Ответ игрока намеренно содержит кавычку и
            // перевод строки: тело собирается конкатенацией в SQL, и неэкранированный символ дал
            // бы синтаксически неверный JSON, который заметили бы уже на сервере.
            db.execSQL(
                """
                INSERT INTO lesson_result_attempt_outbox (
                    attempt_id, user_id, scope, owner_uid, catalog_id, quest_id, section_id,
                    theme_id, lesson_id, lesson_version, source_shelf, difficulty, code_answer,
                    percent_score, completed_at_ms, created_at_ms, sent_at_ms, last_error
                ) VALUES (
                    'attempt-1', 'user-1', 'public', NULL, 'courses', 'quest-1', 'section-1',
                    'theme-1', 'lesson-1', 3, 'arena', 'EASY', 'say "hi"' || char(10) || 'twice',
                    100, 1000, 900, NULL, NULL
                )
                """.trimIndent(),
            )
            // Ответы игрока лежат отдельной таблицей и в строке очереди их не было: прежний
            // отправитель дочитывал их перед отправкой. Новое тело собирается один раз, поэтому
            // ответы обязаны уехать вместе с прохождением — иначе обновление приложения отнимает
            // у игрока то, что без обновления уехало бы целым.
            db.execSQL(
                """
                INSERT INTO question_answers (
                    attempt_id, question_id, user_id, lesson_id, lesson_version, is_hard,
                    code_answer_index, score, answer_payload, answered_at_ms, duration_ms, was_timeout
                ) VALUES
                    ('attempt-1', 'question-1', 'user-1', 'lesson-1', 3, 0, 0, 9,
                     '{"text":"da"}', 950, 40, 0),
                    ('attempt-1', 'question-2', 'user-1', 'lesson-1', 3, 0, 1, 0,
                     '{"text":"ne"}', 990, 60, 1)
                """.trimIndent(),
            )
            // Ответы чужого прохождения не должны попасть в это тело.
            db.execSQL(
                """
                INSERT INTO question_answers (
                    attempt_id, question_id, user_id, lesson_id, lesson_version, is_hard,
                    code_answer_index, score, answer_payload, answered_at_ms, duration_ms, was_timeout
                ) VALUES (
                    'attempt-other', 'question-9', 'user-1', 'lesson-1', 3, 0, 0, 5,
                    '{}', 800, 10, 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO quest_rating_outbox (
                    rating_id, user_id, scope, owner_uid, catalog_id, quest_id, section_id,
                    theme_id, lesson_id, lesson_version, source_shelf, rating, rated_at_ms,
                    created_at_ms, sent_at_ms, last_error
                ) VALUES (
                    'rating-1', 'user-1', 'private', 'author-1', 'courses', 'quest-1', 'section-1',
                    'theme-1', 'lesson-1', 3, 'home', 5, 2000, 1900, NULL, 'network'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO quest_arena_submission_outbox (
                    id, draftId, ownerUid, localRevision, requestedAtMs, lessonIds, targetShelf,
                    attemptCount, lastError
                ) VALUES (
                    'submission-1', 'draft-1', 'author-1', 7, 3000,
                    'lesson-1' || char(31) || 'lesson-2', 'arena', 2, NULL
                )
                """.trimIndent(),
            )
            // Уже уехавшее не переезжает: очередь не архив, а перенос доставленной строки под
            // новым ключом был бы вторым применением.
            db.execSQL(
                """
                INSERT INTO quest_rating_outbox (
                    rating_id, user_id, scope, owner_uid, catalog_id, quest_id, section_id,
                    theme_id, lesson_id, lesson_version, source_shelf, rating, rated_at_ms,
                    created_at_ms, sent_at_ms, last_error
                ) VALUES (
                    'rating-sent', 'user-1', 'public', NULL, 'courses', 'quest-1', 'section-1',
                    'theme-1', 'lesson-1', 3, 'home', 4, 2500, 2400, 2600, NULL
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 6, true, Migration5to6).use { db ->
            val rows = mutableListOf<List<String?>>()
            db.query(
                "SELECT mutation_id, operation, owner_uid, entity_ref, state, attempt_count, " +
                    "created_at_ms, last_error, payload FROM outbox ORDER BY operation",
            ).use { c ->
                while (c.moveToNext()) {
                    rows += (0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }
                }
            }

            assertEquals("три очереди — три записи, отправленная не в счёт", 3, rows.size)

            val attempt = rows[0]
            assertEquals("lesson_runner.SUBMIT_ATTEMPT", attempt[1])
            assertEquals("lesson_runner-SUBMIT_ATTEMPT-attempt-1", attempt[0])
            // Владелец записи — чей это аккаунт, а не чей контент: у прохождения это user_id.
            assertEquals("user-1", attempt[2])
            assertEquals("lesson_runner:attempt:attempt-1", attempt[3])
            assertEquals("900", attempt[6])
            assertEquals(
                """{"attemptId":"attempt-1","userId":"user-1","scope":"public","ownerUid":null,""" +
                    """"catalogId":"courses","questId":"quest-1","sectionId":"section-1",""" +
                    """"themeId":"theme-1","lessonId":"lesson-1","lessonVersion":3,""" +
                    """"sourceShelf":"arena","difficulty":"EASY",""" +
                    """"codeAnswer":"say \"hi\"\ntwice","percentScore":100,""" +
                    """"completedAtMs":1000,"createdAtMs":900,""" +
                    // Ответы приезжают тем же переносом и в том же виде, в каком их пишет
                    // сегодняшний писатель: `wasTimeout` — булево, а не 0/1.
                    """"answers":[""" +
                    """{"questionId":"question-1","codeAnswerIndex":0,"score":9,""" +
                    """"answerPayload":"{\"text\":\"da\"}","answeredAtMs":950,""" +
                    """"durationMs":40,"wasTimeout":false},""" +
                    """{"questionId":"question-2","codeAnswerIndex":1,"score":0,""" +
                    """"answerPayload":"{\"text\":\"ne\"}","answeredAtMs":990,""" +
                    """"durationMs":60,"wasTimeout":true}]}""",
                attempt[8],
            )

            val rating = rows[1]
            assertEquals("lesson_runner.SUBMIT_RATING", rating[1])
            assertEquals("lesson_runner-SUBMIT_RATING-rating-1", rating[0])
            assertEquals("user-1", rating[2])
            assertEquals("lesson_runner:rating:rating-1", rating[3])
            assertEquals("network", rating[7])
            assertEquals(
                """{"ratingId":"rating-1","userId":"user-1","scope":"private",""" +
                    """"ownerUid":"author-1","catalogId":"courses","questId":"quest-1",""" +
                    """"sectionId":"section-1","themeId":"theme-1","lessonId":"lesson-1",""" +
                    """"lessonVersion":3,"sourceShelf":"home","rating":5,"ratedAtMs":2000,""" +
                    """"createdAtMs":1900}""",
                rating[8],
            )

            val arena = rows[2]
            assertEquals("quest_authoring.SUBMIT_ARENA", arena[1])
            assertEquals("quest_authoring-SUBMIT_ARENA-submission-1", arena[0])
            assertEquals("author-1", arena[2])
            assertEquals("quest_authoring:draft:draft-1", arena[3])
            assertEquals("WAITING", arena[4])
            // Накопленные попытки едут с записью: обнулить их значило бы дать безнадёжной заявке
            // новый круг.
            assertEquals("2", arena[5])
            assertEquals("3000", arena[6])
            assertEquals(
                """{"submissionId":"submission-1","draftId":"draft-1","ownerUid":"author-1",""" +
                    """"localRevision":7,"requestedAtMs":3000,""" +
                    // Колонка звалась `lessonIds`, поле тела зовётся `targetLessonIds` — так его
                    // пишет писатель новой заявки и так его читает приёмник.
                    """"targetLessonIds":["lesson-1","lesson-2"],"targetShelf":"arena"}""",
                arena[8],
            )

            for (table in OLD_QUEUE_TABLES) {
                db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { c ->
                    assertFalse("таблица $table пережила миграцию", c.moveToFirst())
                }
            }
        }
    }

    /**
     * Занятый ключ — единственное, что переносу позволено пропустить, и пропускает он его явно.
     *
     * Так выглядит повторный прогон: миграция успела перенести строку и упала до `DROP`, а на
     * следующем запуске идёт по тем же данным заново. Прежняя запись обязана остаться собой —
     * со своим телом и со своими накопленными попытками, — а не смениться свежей копией и не
     * лечь второй строкой того же действия.
     *
     * Здесь же и вторая половина: раз повтор отсекается условием, `OR IGNORE` над вставкой не
     * нужен, а значит нарушение `NOT NULL` больше не выглядит как отсеянный дубликат — оно
     * роняет миграцию.
     */
    @Test
    fun migrate_5_to_6_leaves_an_intent_that_is_already_queued_alone() {
        helper.createDatabase(DB_NAME, version = 5).use { db ->
            db.execSQL(
                """
                INSERT INTO lesson_result_attempt_outbox (
                    attempt_id, user_id, scope, owner_uid, catalog_id, quest_id, section_id,
                    theme_id, lesson_id, lesson_version, source_shelf, difficulty, code_answer,
                    percent_score, completed_at_ms, created_at_ms, sent_at_ms, last_error
                ) VALUES (
                    'attempt-1', 'user-1', 'public', NULL, 'courses', 'quest-1', 'section-1',
                    'theme-1', 'lesson-1', 3, 'arena', 'EASY', '99',
                    100, 1000, 900, NULL, NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO outbox (
                    mutation_id, owner_uid, operation, payload, entity_ref, expected_version,
                    state, attempt_count, next_retry_at_ms, last_error, created_at_ms
                ) VALUES (
                    'lesson_runner-SUBMIT_ATTEMPT-attempt-1', 'user-1',
                    'lesson_runner.SUBMIT_ATTEMPT', '{"already":"queued"}',
                    'lesson_runner:attempt:attempt-1', NULL, 'WAITING', 4, 0, NULL, 700
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 6, true, Migration5to6).use { db ->
            db.query(
                "SELECT COUNT(*), payload, attempt_count FROM outbox " +
                    "WHERE mutation_id = 'lesson_runner-SUBMIT_ATTEMPT-attempt-1'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("одно действие — одна запись в очереди", 1, c.getInt(0))
                assertEquals("""{"already":"queued"}""", c.getString(1))
                assertEquals("накопленные попытки прежней записи не обнулились", 4, c.getInt(2))
            }
        }
    }

    @Test
    fun migrate_all_the_way_from_1_validates_against_the_current_schema() {
        helper.createDatabase(DB_NAME, version = 1).close()

        // The path a device that skipped several releases actually takes.
        helper.runMigrationsAndValidate(
            DB_NAME,
            CURRENT_VERSION,
            true,
            Migration1to2,
            Migration2to3,
            Migration3to4,
            Migration4to5,
            Migration5to6,
        ).close()
    }

    private companion object {
        const val DB_NAME = "migration_test"

        /** Must match `@Database(version = …)` on [AppDatabase]; [MigrationCoverageTest] pins it. */
        const val CURRENT_VERSION = 6

        /** Три очереди, сведённые в одну миграцией 5 -> 6. Ни одна не должна пережить её. */
        val OLD_QUEUE_TABLES = listOf(
            "lesson_result_attempt_outbox",
            "quest_rating_outbox",
            "quest_arena_submission_outbox",
        )
    }
}
