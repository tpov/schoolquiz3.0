package com.tpov.schoolquiz.shared.core.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Три очереди становятся одной.
 *
 * `lesson_result_attempt_outbox`, `quest_rating_outbox` и `quest_arena_submission_outbox` жили
 * каждая своей формой, со своим понятием «отправлено» и своим отсутствием ключа идемпотентности.
 * Пока их три, ключ, приёмник и карантин работают для половины действий: заявка на арену вообще
 * уходила прямой записью в Firestore. Строки переезжают в общую `outbox` (AD-5), тип действия
 * становится строкой `operation` в пространстве имён владеющей фичи, а прежние колонки — телом
 * `payload`, в которое ядро не смотрит (AD-7, NFR1).
 *
 * **Ключ выводится из прежнего идентификатора**, а не выдаётся заново: `attempt_id`, `rating_id` и
 * `id` заявки уже уникальны и уже прожили с этим действием всю его жизнь. Случайный ключ на
 * миграции означал бы, что повторный прогон (а он бывает — миграция может упасть после части
 * работы и пойти заново) поставит то же действие в очередь второй раз. Здесь же повтор ловится
 * условием `NOT EXISTS` по уже занятому ключу — и только он (AD-2).
 *
 * **Именно `NOT EXISTS`, а не `INSERT OR IGNORE`.** `OR IGNORE` подавляет любое нарушение
 * ограничения: и занятый ключ, ради которого он тут стоял, и `NOT NULL` в склеиваемом столбце — а
 * такая строка при этом молча пропадает вместе с действием игрока. В схеме 5 столбца, способного
 * дать `NULL`, сейчас нет, но «дубликат отсеян» и «строка потеряна» обязаны выглядеть по-разному
 * до того, как разница появится: занятый ключ отсекается условием, видимым в самом SQL, а любое
 * другое нарушение роняет миграцию.
 *
 * Переезжает только неотправленное. `sent_at_ms IS NOT NULL` — уже доехавшее до сервера; очередь
 * не архив (AD-4), а перенос такой строки под новым ключом был бы вторым применением ровно того,
 * от чего ключ и защищает. Заявки на арену такого столбца не имели — отправленная удалялась, — так
 * что там переезжает всё, что лежит.
 *
 * `owner_uid` очереди — это чей аккаунт, а не чей контент (AD-8). У прохождений и оценок это
 * `user_id`; прежняя нулевая колонка `owner_uid` означала владельца приватного квеста и уезжает в
 * `payload` под своим именем.
 */
val Migration5to6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(MOVE_ATTEMPTS)
            db.execSQL(MOVE_RATINGS)
            db.execSQL(MOVE_ARENA_SUBMISSIONS)

            db.execSQL("DROP TABLE IF EXISTS `lesson_result_attempt_outbox`")
            db.execSQL("DROP TABLE IF EXISTS `quest_rating_outbox`")
            db.execSQL("DROP TABLE IF EXISTS `quest_arena_submission_outbox`")
        }
    }

/**
 * Типы действий в пространстве имён владеющей фичи (AD-5).
 *
 * Те же строки регистрирует у себя серверный реестр обработчиков: незнакомая операция — отказ без
 * повтора, поэтому имя здесь и имя там обязаны совпадать буквально.
 */
private const val OPERATION_SUBMIT_ATTEMPT = "lesson_runner.SUBMIT_ATTEMPT"
private const val OPERATION_SUBMIT_RATING = "lesson_runner.SUBMIT_RATING"
private const val OPERATION_SUBMIT_ARENA = "quest_authoring.SUBMIT_ARENA"

/**
 * Приставка ключа. Точка в `operation` тут не годится: сервер принимает ключ только из
 * `[A-Za-z0-9_-]`, поэтому разделитель — дефис.
 */
private const val KEY_SUBMIT_ATTEMPT = "lesson_runner-SUBMIT_ATTEMPT-"
private const val KEY_SUBMIT_RATING = "lesson_runner-SUBMIT_RATING-"
private const val KEY_SUBMIT_ARENA = "quest_authoring-SUBMIT_ARENA-"

/** Новая запись начинает жизнь ожидающей, без попыток за спиной и без отсрочки. */
private const val STATE_WAITING = "WAITING"

private const val OUTBOX_COLUMNS =
    "`mutation_id`, `owner_uid`, `operation`, `payload`, `entity_ref`, `expected_version`, " +
        "`state`, `attempt_count`, `next_retry_at_ms`, `last_error`, `created_at_ms`"

private val MOVE_ATTEMPTS =
    """
    INSERT INTO `outbox` ($OUTBOX_COLUMNS)
    SELECT
        '$KEY_SUBMIT_ATTEMPT' || `attempt_id`,
        `user_id`,
        '$OPERATION_SUBMIT_ATTEMPT',
        ${jsonObject(
        "attemptId" to text("attempt_id"),
        "userId" to text("user_id"),
        "scope" to text("scope"),
        "ownerUid" to nullableText("owner_uid"),
        "catalogId" to text("catalog_id"),
        "questId" to text("quest_id"),
        "sectionId" to text("section_id"),
        "themeId" to text("theme_id"),
        "lessonId" to text("lesson_id"),
        "lessonVersion" to number("lesson_version"),
        "sourceShelf" to text("source_shelf"),
        "difficulty" to text("difficulty"),
        "codeAnswer" to text("code_answer"),
        "percentScore" to number("percent_score"),
        "completedAtMs" to number("completed_at_ms"),
        "createdAtMs" to number("created_at_ms"),
        "answers" to answersOfAttempt(),
    )},
        'lesson_runner:attempt:' || `attempt_id`,
        NULL,
        '$STATE_WAITING',
        0,
        0,
        `last_error`,
        `created_at_ms`
    FROM `lesson_result_attempt_outbox`
    WHERE `sent_at_ms` IS NULL
      AND ${keyIsFree("'$KEY_SUBMIT_ATTEMPT' || `lesson_result_attempt_outbox`.`attempt_id`")}
    """.trimIndent()

private val MOVE_RATINGS =
    """
    INSERT INTO `outbox` ($OUTBOX_COLUMNS)
    SELECT
        '$KEY_SUBMIT_RATING' || `rating_id`,
        `user_id`,
        '$OPERATION_SUBMIT_RATING',
        ${jsonObject(
        "ratingId" to text("rating_id"),
        "userId" to text("user_id"),
        "scope" to text("scope"),
        "ownerUid" to nullableText("owner_uid"),
        "catalogId" to text("catalog_id"),
        "questId" to text("quest_id"),
        "sectionId" to text("section_id"),
        "themeId" to text("theme_id"),
        "lessonId" to text("lesson_id"),
        "lessonVersion" to number("lesson_version"),
        "sourceShelf" to text("source_shelf"),
        "rating" to number("rating"),
        "ratedAtMs" to number("rated_at_ms"),
        "createdAtMs" to number("created_at_ms"),
    )},
        'lesson_runner:rating:' || `rating_id`,
        NULL,
        '$STATE_WAITING',
        0,
        0,
        `last_error`,
        `created_at_ms`
    FROM `quest_rating_outbox`
    WHERE `sent_at_ms` IS NULL
      AND ${keyIsFree("'$KEY_SUBMIT_RATING' || `quest_rating_outbox`.`rating_id`")}
    """.trimIndent()

/**
 * Заявка на арену несёт с собой уже накопленные попытки: их счётчик — единственное, что осталось
 * от прежней истории строки, и обнулить его значило бы подарить безнадёжной заявке новый круг.
 */
private val MOVE_ARENA_SUBMISSIONS =
    """
    INSERT INTO `outbox` ($OUTBOX_COLUMNS)
    SELECT
        '$KEY_SUBMIT_ARENA' || `id`,
        `ownerUid`,
        '$OPERATION_SUBMIT_ARENA',
        ${jsonObject(
        "submissionId" to text("id"),
        "draftId" to text("draftId"),
        "ownerUid" to text("ownerUid"),
        "localRevision" to number("localRevision"),
        "requestedAtMs" to number("requestedAtMs"),
        // Колонка прежней таблицы звалась `lessonIds`, поле тела зовётся `targetLessonIds`: так его
        // пишет писатель новой заявки и так его читает сервер. Имя колонки в теле было бы вторым
        // форматом одного и того же тела — приёмник знает один.
        "targetLessonIds" to unitSeparatedArray("lessonIds"),
        "targetShelf" to text("targetShelf"),
    )},
        'quest_authoring:draft:' || `draftId`,
        NULL,
        '$STATE_WAITING',
        `attemptCount`,
        0,
        `lastError`,
        `requestedAtMs`
    FROM `quest_arena_submission_outbox`
    WHERE ${keyIsFree("'$KEY_SUBMIT_ARENA' || `quest_arena_submission_outbox`.`id`")}
    """.trimIndent()

/**
 * Ответы игрока, которые относятся к этому прохождению, — тем же переносом.
 *
 * Прежняя строка очереди их не хранила: старый отправитель дочитывал `question_answers` в момент
 * отправки. Новое тело собирается один раз, в момент намерения (AD-2), и дочитать что-либо
 * позднее уже нельзя — значит, ответы обязаны уехать вместе с прохождением здесь, иначе
 * перенесённое прохождение приедет на сервер пустым, и обновление приложения отняло бы у игрока
 * ровно то, что без обновления уехало бы целым.
 *
 * Строки живы: `question_answers` переезд переживает и в схеме 6 остаётся неизменной, поэтому
 * подтянуть их можно тем же `INSERT ... SELECT`.
 *
 * Порядок — тот, в котором строки отдаёт первичный ключ `(attempt_id, question_id)`. `ORDER BY`
 * внутри `group_concat` появился только в SQLite 3.44, а миграция обязана пройти на той версии,
 * что стоит на устройстве; приёмник же читает ответы по `questionId`, а не по месту в списке.
 *
 * Функция, а не значение: свойства файла инициализируются в порядке объявления, и `MOVE_ATTEMPTS`
 * получил бы вместо подзапроса пустоту.
 */
private fun answersOfAttempt(): String =
    "(SELECT '[' || IFNULL(group_concat(" +
        jsonObject(
            "questionId" to text("question_id"),
            "codeAnswerIndex" to number("code_answer_index"),
            "score" to number("score"),
            "answerPayload" to text("answer_payload"),
            "answeredAtMs" to number("answered_at_ms"),
            "durationMs" to number("duration_ms"),
            "wasTimeout" to boolean("was_timeout"),
        ) +
        ", ','), '') || ']' FROM `question_answers` " +
        "WHERE `question_answers`.`attempt_id` = `lesson_result_attempt_outbox`.`attempt_id`)"

/**
 * «Этот ключ ещё не занят» — единственный повтор, который переносу позволено пропустить.
 *
 * Столбец источника называется полным именем таблицы намеренно: у `outbox` есть свой `id`, и
 * неквалифицированное имя внутри подзапроса связалось бы с ним, а не с переносимой строкой.
 */
private fun keyIsFree(keyExpression: String): String =
    "NOT EXISTS (SELECT 1 FROM `outbox` WHERE `outbox`.`mutation_id` = $keyExpression)"

/**
 * Собирает JSON-объект средствами самого SQLite.
 *
 * Расширение JSON1 не используется: оно есть не на всякой версии системной SQLite, а миграция
 * обязана пройти на любой, где приложение вообще запускается. Конкатенация с экранированием
 * работает везде и одинаково.
 */
private fun jsonObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(prefix = "'{' || ", separator = " || ',' || ", postfix = " || '}'") { (name, value) ->
        """'"$name":' || $value"""
    }

/** Строковое поле: значение в кавычках, содержимое экранировано. */
private fun text(column: String): String = """'"' || ${escaped(column)} || '"'"""

/** Обнуляемое строковое поле. Пусто — это `null`, а не пустая строка: разница здесь значимая. */
private fun nullableText(column: String): String =
    """CASE WHEN `$column` IS NULL THEN 'null' ELSE '"' || ${escaped(column)} || '"' END"""

/** Числовое поле — без кавычек. */
private fun number(column: String): String = "CAST(`$column` AS TEXT)"

/**
 * Логическое поле. В SQLite оно лежит целым, в JSON обязано быть `true`/`false`: приёмник читает
 * `wasTimeout` как булево, а `0` он принял бы за истину.
 */
private fun boolean(column: String): String = "CASE WHEN `$column` = 0 THEN 'false' ELSE 'true' END"

/**
 * Список, лежавший строкой через разделитель U+001F, становится JSON-массивом.
 *
 * Пустая строка — это пустой список, а не список из одной пустой строки, поэтому случай разведён
 * явно: наивная замена разделителя дала бы `[""]`.
 */
private fun unitSeparatedArray(column: String): String =
    """CASE WHEN `$column` = '' THEN '[]' ELSE '["' || replace(${escaped(column)}, char(31), '","') || '"]' END"""

/**
 * Экранирование внутри JSON-строки.
 *
 * Обратная косая идёт первой — иначе следующая замена наплодит косых, которые сама же и испортит.
 * Перевод строки и возврат каретки — единственные управляющие символы, которые реально встречаются
 * в ответах игрока; без них строка была бы синтаксически неверным JSON.
 */
private fun escaped(column: String): String =
    """replace(replace(replace(replace(`$column`, '\', '\\'), '"', '\"'), char(10), '\n'), char(13), '\r')"""
