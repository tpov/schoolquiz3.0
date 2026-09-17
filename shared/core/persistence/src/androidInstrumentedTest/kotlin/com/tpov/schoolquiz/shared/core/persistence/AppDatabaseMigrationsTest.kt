package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration1to2
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration2to3
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration3to4
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration4to5
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration5to6
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration6to7
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration7to8
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration8to9
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration9to10
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
            // Заявка на арену и её локальная половина — запертый черновик.
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
            db.execSQL(
                """
                INSERT INTO quest_drafts (
                    id, ownerUid, catalogId, title, description, defaultLanguage,
                    defaultDifficulty, status, localRevision, serverRevision, publicQuestId,
                    createdAtMs, updatedAtMs, isActive, rejectionReason
                ) VALUES (
                    'draft-1', 'author-1', 'courses', 'Черновик', NULL, 'ru',
                    'EASY', 'REVIEW_QUEUED', 7, NULL, NULL, 100, 3000, 1, NULL
                )
                """.trimIndent(),
            )
            // Чужой черновик той же формы: миграция трогает только те, чья заявка лежала.
            db.execSQL(
                """
                INSERT INTO quest_drafts (
                    id, ownerUid, catalogId, title, description, defaultLanguage,
                    defaultDifficulty, status, localRevision, serverRevision, publicQuestId,
                    createdAtMs, updatedAtMs, isActive, rejectionReason
                ) VALUES (
                    'draft-2', 'author-1', 'courses', 'Другой', NULL, 'ru',
                    'EASY', 'REVIEW_QUEUED', 1, NULL, NULL, 100, 3000, 1, NULL
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

            // Заявка на арену не переезжает вовсе: её тело из прежних колонок не собирается, и
            // приёмник отверг бы такую запись всегда (см. заголовок Migration5to6).
            assertEquals("две переносимые очереди — две записи, отправленная не в счёт", 2, rows.size)

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

            // Локальная половина заявки снята здесь же: черновик снова редактируемый, причина
            // написана для автора, а не для разработчика.
            db.query("SELECT id, status, rejectionReason, updatedAtMs FROM quest_drafts ORDER BY id").use { c ->
                assertTrue("черновик пропал", c.moveToFirst())
                assertEquals("draft-1", c.getString(0))
                assertEquals("DRAFT", c.getString(1))
                val reason = c.getString(2)
                assertTrue("причина обязана быть названа автору", !reason.isNullOrBlank())
                assertFalse("причина не должна быть текстом приёмника", reason!!.contains("draft.catalogId"))
                // Часов у миграции нет, а поле участвует в правиле расхождения с сервером.
                assertEquals(3000L, c.getLong(3))

                assertTrue(c.moveToNext())
                assertEquals("draft-2", c.getString(0))
                assertEquals("черновик без заявки не трогается", "REVIEW_QUEUED", c.getString(1))
                assertEquals(null, c.getString(2))
            }

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

    /**
     * Конфликт обязан пережить обновление приложения так же, как переживает перезапуск.
     *
     * Запись, лежащая в очереди в момент выката, — это действие игрока, которое ещё не доехало.
     * Миграция добавляет колонку под серверную версию, и всё, чем эта запись была, обязано
     * остаться собой: ключ, состояние, накопленные попытки и момент намерения. Новая колонка у
     * неё пуста — конфликта она не видела.
     */
    @Test
    fun migrate_6_to_7_adds_the_server_version_and_keeps_the_queued_record_intact() {
        helper.createDatabase(DB_NAME, version = 6).use { db ->
            db.execSQL(
                """
                INSERT INTO outbox (
                    mutation_id, owner_uid, operation, payload, entity_ref, expected_version,
                    state, attempt_count, next_retry_at_ms, last_error, created_at_ms
                ) VALUES (
                    'quest_authoring-SUBMIT_ARENA-submission-1', 'author-1',
                    'quest_authoring.SUBMIT_ARENA', '{"draftId":"draft-1"}',
                    'quest_authoring:draft:draft-1', 4, 'WAITING', 3, 1500, 'network', 900
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 7, true, Migration6to7).use { db ->
            db.query(
                "SELECT mutation_id, state, attempt_count, created_at_ms, expected_version, " +
                    "server_version FROM outbox",
            ).use { c ->
                assertTrue("запись, лежавшая в очереди до миграции, исчезла", c.moveToFirst())
                assertEquals("quest_authoring-SUBMIT_ARENA-submission-1", c.getString(0))
                assertEquals("WAITING", c.getString(1))
                assertEquals(3, c.getInt(2))
                assertEquals(900L, c.getLong(3))
                assertEquals("ожидаемая версия не относится к этой миграции", 4L, c.getLong(4))
                assertTrue("новая колонка обязана быть пустой у записи без конфликта", c.isNull(5))
            }

            // Колонка пишется, а не только существует: пустое значение отличимо от числа.
            db.execSQL("UPDATE outbox SET state = 'CONFLICT', server_version = 7")
            db.query("SELECT server_version FROM outbox").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(7L, c.getLong(0))
            }
        }
    }

    /**
     * Курсор получает вторую половину, а первая остаётся тем же числом (AD-31).
     *
     * Курсор — самая дешёвая строка, которую нельзя терять молча: потеря значит перечитать журнал
     * или, хуже, перескочить его кусок. У строки, дожившей с прежней схемы, вторая половина пуста:
     * по лексикографическому сравнению это граница не позже прежней, и первый проход после
     * обновления перечитает миллисекунду курсора целиком, а не пропустит её.
     */
    @Test
    fun migrate_7_to_8_adds_the_document_half_of_the_cursor_and_keeps_the_time_half() {
        helper.createDatabase(DB_NAME, version = 7).use { db ->
            db.execSQL("INSERT INTO sync_state (collectionId, cursor) VALUES ('catalog_sync:cat-1', 1700)")
            db.execSQL("INSERT INTO sync_state (collectionId, cursor) VALUES ('review_assignments:u-1', 2600)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 8, true, Migration7to8).use { db ->
            db.query("SELECT cursor, doc_id FROM sync_state WHERE collectionId = 'catalog_sync:cat-1'").use { c ->
                assertTrue("курсор, записанный до миграции, исчез", c.moveToFirst())
                assertEquals("время читается тем же числом", 1700L, c.getLong(0))
                assertEquals("вторая половина у прежней строки пуста", "", c.getString(1))
            }
            db.query("SELECT COUNT(*) FROM sync_state").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
            // Колонка пишется, а не только существует.
            db.execSQL("UPDATE sync_state SET doc_id = 'quest_q-1' WHERE collectionId = 'catalog_sync:cat-1'")
            db.query("SELECT doc_id FROM sync_state WHERE collectionId = 'catalog_sync:cat-1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("quest_q-1", c.getString(0))
            }
        }
    }

    /**
     * След прогона заводится пустой таблицей, и ничего из прежнего он не трогает (история 4.2).
     *
     * До этой версии времени последнего успеха на устройстве не было вовсе — оно жило в памяти
     * процесса, и холодный старт стирал его. Поэтому переносить нечего: проверяется, что таблица
     * появилась, пишется по владельцу и читается по нему же, а строки соседей целы.
     */
    @Test
    fun migrate_8_to_9_adds_the_run_trace_table_and_keeps_the_neighbours_intact() {
        helper.createDatabase(DB_NAME, version = 8).use { db ->
            db.execSQL(
                "INSERT INTO sync_state (collectionId, cursor, doc_id) " +
                    "VALUES ('catalog_sync:cat-1', 1700, 'quest_q-1')",
            )
            db.execSQL(
                """
                INSERT INTO outbox (
                    mutation_id, owner_uid, operation, payload, entity_ref, expected_version,
                    server_version, state, attempt_count, next_retry_at_ms, last_error, created_at_ms
                ) VALUES (
                    'lesson_runner-SUBMIT_ATTEMPT-attempt-1', 'uid-A',
                    'lesson_runner.SUBMIT_ATTEMPT', '{"a":1}',
                    'lesson_runner:attempt:attempt-1', NULL, NULL, 'WAITING', 0, 0, NULL, 900
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 9, true, Migration8to9).use { db ->
            db.query("SELECT COUNT(*) FROM sync_run_trace").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("на прежнем устройстве следа не было — таблица пуста", 0, c.getInt(0))
            }

            // Таблица пишется, а не только существует, и ключ у неё — владелец.
            db.execSQL(
                "INSERT INTO sync_run_trace (owner_uid, last_success_at_ms, last_error_branch, " +
                    "last_error_reason, last_error_server_version) " +
                    "VALUES ('uid-A', 1700, NULL, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO sync_run_trace (owner_uid, last_success_at_ms, last_error_branch, " +
                    "last_error_reason, last_error_server_version) " +
                    "VALUES ('uid-B', 0, 'REFUSED', 'not enough gold', NULL)",
            )
            db.query("SELECT last_success_at_ms FROM sync_run_trace WHERE owner_uid = 'uid-A'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1700L, c.getLong(0))
            }
            db.query("SELECT last_error_branch FROM sync_run_trace WHERE owner_uid = 'uid-B'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ветвь лежит именем в своей колонке, а не текстом ошибки", "REFUSED", c.getString(0))
            }

            // Соседи целы: миграция ничего не пересоздаёт.
            db.query("SELECT doc_id FROM sync_state WHERE collectionId = 'catalog_sync:cat-1'").use { c ->
                assertTrue("курсор, записанный до миграции, исчез", c.moveToFirst())
                assertEquals("quest_q-1", c.getString(0))
            }
            db.query("SELECT state FROM outbox").use { c ->
                assertTrue("запись очереди, лежавшая до миграции, исчезла", c.moveToFirst())
                assertEquals("WAITING", c.getString(0))
            }
        }
    }

    /**
     * След прогона разводится по расписаниям: ключом становится пара «владелец + вид».
     *
     * До этой версии контентный и профильный прогоны писали в одну строку, и последний доложивший
     * затирал соседа. Прежние строки не переносятся: вида у них нет, и приписать его нечем —
     * записать чужой успех контентным значило бы повторить ровно ту ложь, которую эта версия
     * убирает. Проверяется поэтому и то, что таблица после миграции пуста, и то, что она стала
     * различать виды, и то, что соседние таблицы миграция не тронула.
     */
    @Test
    fun migrate_9_to_10_keys_the_run_trace_by_kind_and_leaves_the_neighbours_intact() {
        helper.createDatabase(DB_NAME, version = 9).use { db ->
            db.execSQL(
                "INSERT INTO sync_run_trace (owner_uid, last_success_at_ms, last_error_branch, " +
                    "last_error_reason, last_error_server_version) " +
                    "VALUES ('uid-A', 1700, 'NO_NETWORK', NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO sync_state (collectionId, cursor, doc_id) " +
                    "VALUES ('catalog_sync:cat-1', 1700, 'quest_q-1')",
            )
            db.execSQL(
                """
                INSERT INTO outbox (
                    mutation_id, owner_uid, operation, payload, entity_ref, expected_version,
                    server_version, state, attempt_count, next_retry_at_ms, last_error, created_at_ms
                ) VALUES (
                    'lesson_runner-SUBMIT_ATTEMPT-attempt-1', 'uid-A',
                    'lesson_runner.SUBMIT_ATTEMPT', '{"a":1}',
                    'lesson_runner:attempt:attempt-1', NULL, NULL, 'WAITING', 0, 0, NULL, 900
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 10, true, Migration9to10).use { db ->
            db.query("SELECT COUNT(*) FROM sync_run_trace").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(
                    "строку без вида приписать некому — таблица начинается пустой",
                    0,
                    c.getInt(0),
                )
            }

            // Ключ — пара: у одного владельца теперь по строке на расписание, и вторая вставка
            // не заменяет первую.
            db.execSQL(
                "INSERT INTO sync_run_trace (owner_uid, kind, last_success_at_ms, last_error_branch, " +
                    "last_error_reason, last_error_server_version) " +
                    "VALUES ('uid-A', 'CONTENT', 0, 'NO_NETWORK', NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO sync_run_trace (owner_uid, kind, last_success_at_ms, last_error_branch, " +
                    "last_error_reason, last_error_server_version) " +
                    "VALUES ('uid-A', 'PROFILE', 2700, NULL, NULL, NULL)",
            )
            db.query("SELECT COUNT(*) FROM sync_run_trace WHERE owner_uid = 'uid-A'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("два расписания — две строки, а не одна затёртая", 2, c.getInt(0))
            }
            db.query(
                "SELECT last_error_branch FROM sync_run_trace WHERE owner_uid = 'uid-A' AND kind = 'CONTENT'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("удачный профильный прогон не снял ветвь контентного", "NO_NETWORK", c.getString(0))
            }

            // Уборка прежнего владельца забирает все его виды разом (AD-8).
            db.execSQL("DELETE FROM sync_run_trace WHERE owner_uid = 'uid-A'")
            db.query("SELECT COUNT(*) FROM sync_run_trace").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("удаление по владельцу обязано забрать все его строки", 0, c.getInt(0))
            }

            // Соседи целы: миграция трогает только след.
            db.query("SELECT doc_id FROM sync_state WHERE collectionId = 'catalog_sync:cat-1'").use { c ->
                assertTrue("курсор, записанный до миграции, исчез", c.moveToFirst())
                assertEquals("quest_q-1", c.getString(0))
            }
            db.query("SELECT state FROM outbox").use { c ->
                assertTrue("запись очереди, лежавшая до миграции, исчезла", c.moveToFirst())
                assertEquals("WAITING", c.getString(0))
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
            Migration6to7,
            Migration7to8,
            Migration8to9,
            Migration9to10,
        ).close()
    }

    private companion object {
        const val DB_NAME = "migration_test"

        /** Must match `@Database(version = …)` on [AppDatabase]; [MigrationCoverageTest] pins it. */
        const val CURRENT_VERSION = 10

        /** Три очереди, сведённые в одну миграцией 5 -> 6. Ни одна не должна пережить её. */
        val OLD_QUEUE_TABLES = listOf(
            "lesson_result_attempt_outbox",
            "quest_rating_outbox",
            "quest_arena_submission_outbox",
        )
    }
}
