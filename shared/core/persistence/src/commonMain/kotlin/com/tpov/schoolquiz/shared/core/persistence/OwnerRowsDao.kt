package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.tpov.schoolquiz.shared.core.outbox.OwnerDataKind

/**
 * Локальные строки прежнего владельца и его личные курсоры — одной транзакцией (AD-8).
 *
 * Перечень закрыт и объявлен в [OwnerScopedTables]: пока курсор снимался, а строки оставались,
 * прежний владелец жил на устройстве вечно. Перезаписать их было уже нечем — курсора нет, а
 * читающий проход шёл по локальному набору и требовал у сервера чужие документы, получая отказ
 * правила на каждом прогоне.
 *
 * Одна транзакция, а не два вызова: между ними помещается смерть процесса, и устройство осталось бы
 * с курсором без строк или со строками без курсора — оба состояния хуже любого из двух целых.
 *
 * Схему не меняет: удаление строк выражается запросом, миграция здесь не при чём (NFR6).
 */
@Dao
interface OwnerRowsDao {
    @Query("DELETE FROM review_assignment_questions WHERE ownerUid = :uid")
    suspend fun deleteReviewAssignmentQuestions(uid: String)

    @Query("DELETE FROM review_assignments WHERE ownerUid = :uid")
    suspend fun deleteReviewAssignments(uid: String)

    // Дерево черновика режется от листьев к корню и по родителю: колонки владельца у веток нет,
    // владелец лежит только на самом черновике. Каскад в схеме описан, но полагаться на него —
    // значит зависеть от того, включены ли внешние ключи в этом соединении.
    @Query("DELETE FROM draft_questions WHERE draftId IN (SELECT id FROM quest_drafts WHERE ownerUid = :uid)")
    suspend fun deleteDraftQuestions(uid: String)

    @Query("DELETE FROM draft_lessons WHERE draftId IN (SELECT id FROM quest_drafts WHERE ownerUid = :uid)")
    suspend fun deleteDraftLessons(uid: String)

    @Query("DELETE FROM draft_themes WHERE draftId IN (SELECT id FROM quest_drafts WHERE ownerUid = :uid)")
    suspend fun deleteDraftThemes(uid: String)

    @Query("DELETE FROM draft_sections WHERE draftId IN (SELECT id FROM quest_drafts WHERE ownerUid = :uid)")
    suspend fun deleteDraftSections(uid: String)

    @Query("DELETE FROM quest_drafts WHERE ownerUid = :uid")
    suspend fun deleteQuestDrafts(uid: String)

    @Query("DELETE FROM user_stats WHERE uid = :uid")
    suspend fun deleteUserStats(uid: String)

    @Query("DELETE FROM user_profiles WHERE uid = :uid")
    suspend fun deleteUserProfiles(uid: String)

    @Query("DELETE FROM lesson_attempts WHERE user_id = :uid")
    suspend fun deleteLessonAttempts(uid: String)

    @Query("DELETE FROM question_answers WHERE user_id = :uid")
    suspend fun deleteQuestionAnswers(uid: String)

    @Query("DELETE FROM question_repetition WHERE user_id = :uid")
    suspend fun deleteQuestionRepetition(uid: String)

    @Query("DELETE FROM lesson_rating_submitted_local WHERE user_id = :uid")
    suspend fun deleteLessonRatingsSubmitted(uid: String)

    /**
     * Снимает след прогона прежнего владельца.
     *
     * Чтение и так идёт по ключу владельца, поэтому чужого времени на экране не бывает и без
     * удаления. Строка всё равно уходит: это личная строка без серверного двойника, а AD-8 не
     * оставляет на устройстве личных строк того, кого забыли.
     */
    @Query("DELETE FROM sync_run_trace WHERE owner_uid = :uid")
    suspend fun deleteSyncRunTrace(uid: String)

    @Query("DELETE FROM mutation_receipts WHERE owner_uid = :uid")
    suspend fun deleteMutationReceipts(uid: String)

    @Query("DELETE FROM lesson_attempt_intents WHERE owner_uid = :uid")
    suspend fun deleteLessonAttemptIntents(uid: String)

    /**
     * Снимает названные курсоры — удалением строки, а не записью нуля.
     *
     * `setCursor` монотонен (`SyncStateDao.setCursor`): записанный ноль не дал бы следующему
     * проходу под новым владельцем поставить меньшее значение. Удалённая строка читается как
     * «никогда не было».
     */
    @Query("DELETE FROM sync_state WHERE collectionId IN (:collectionIds)")
    suspend fun deleteCursors(collectionIds: List<String>)

    /**
     * Все, чьи личные строки лежат на устройстве, — объединением по всему перечню.
     *
     * Спрашивается там же, где очередь спрашивают про своих владельцев, и по той же причине.
     * Пустая очередь — обычное стационарное состояние: запись уехала и исчезла, а черновик остался
     * в `REVIEW_SENT`. Пока набор владельцев выводился из одной очереди, такой игрок менял аккаунт
     * без вопроса и без уборки.
     *
     * Перечень таблиц здесь обязан совпадать с перечнем удаления: расхождение ловит
     * `OwnerScopedTablesGuardTest`.
     */
    @Query(
        """
        SELECT ownerUid FROM review_assignments
        UNION SELECT ownerUid FROM review_assignment_questions
        UNION SELECT ownerUid FROM quest_drafts
        UNION SELECT uid FROM user_stats
        UNION SELECT uid FROM user_profiles
        UNION SELECT user_id FROM lesson_attempts
        UNION SELECT user_id FROM question_answers
        UNION SELECT user_id FROM question_repetition
        UNION SELECT user_id FROM lesson_rating_submitted_local
        UNION SELECT owner_uid FROM sync_run_trace
        UNION SELECT owner_uid FROM mutation_receipts
        UNION SELECT owner_uid FROM lesson_attempt_intents
        """,
    )
    suspend fun ownersWithTraces(): List<String>

    /**
     * Есть ли у этого владельца то, что называется игроку, — по одному признаку на вид.
     *
     * Только таблицы без серверного двойника: остальные после входа вернутся, и предупреждать про
     * них не о чем. Признаки, а не числа: вопрос звучит «история ответов исчезнет», а не «исчезнет
     * 412 строк», и лишнее число здесь было бы именем таблицы наружу (NFR11).
     */
    @Query(
        """
        SELECT
            EXISTS(SELECT 1 FROM question_answers WHERE user_id = :uid) AS hasAnswerHistory,
            EXISTS(SELECT 1 FROM question_repetition WHERE user_id = :uid) AS hasRepetitionSchedule,
            EXISTS(SELECT 1 FROM quest_drafts WHERE ownerUid = :uid) AS hasUnpublishedDrafts
        """,
    )
    suspend fun namedTraces(uid: String): OwnerNamedTracesRow

    /** Весь перечень одним движением. Очередь сюда не входит: её снимает `OutboxStore.forget`. */
    @Transaction
    suspend fun forgetOwner(
        uid: String,
        privateCursorIds: List<String>,
    ) {
        deleteReviewAssignmentQuestions(uid)
        deleteReviewAssignments(uid)
        deleteDraftQuestions(uid)
        deleteDraftLessons(uid)
        deleteDraftThemes(uid)
        deleteDraftSections(uid)
        deleteQuestDrafts(uid)
        deleteUserStats(uid)
        deleteUserProfiles(uid)
        deleteLessonAttempts(uid)
        deleteQuestionAnswers(uid)
        deleteQuestionRepetition(uid)
        deleteLessonRatingsSubmitted(uid)
        deleteSyncRunTrace(uid)
        deleteMutationReceipts(uid)
        deleteLessonAttemptIntents(uid)
        if (privateCursorIds.isNotEmpty()) deleteCursors(privateCursorIds)
    }
}

/**
 * Что из названного игроку у владельца есть — по признаку на каждый [OwnerDataKind].
 *
 * Признаки, а не список: имена видов живут в ядре очереди, и собирает из них перечень
 * [RoomOwnerTraces] исчерпывающим `when` — новый вид роняет компиляцию, а не тихо выпадает из
 * вопроса.
 */
data class OwnerNamedTracesRow(
    val hasAnswerHistory: Boolean,
    val hasRepetitionSchedule: Boolean,
    val hasUnpublishedDrafts: Boolean,
)

/**
 * Какая из двух операций AD-8 чистит таблицу.
 *
 * Признак нужен ровно затем, чтобы очередь была в перечне названа, а не забыта: страж перечня
 * иначе уронил бы сборку на самой очереди, у которой колонка владельца тоже есть.
 */
enum class OwnerCleanup {
    /** Записи очереди: `OutboxStore.forget`. */
    QUEUE,

    /** Личные следы вне очереди: [OwnerRowsDao.forgetOwner]. */
    TRACES,
}

/**
 * Таблица, привязанная к владельцу, и два независимых признака про неё.
 *
 * @property table имя таблицы в схеме.
 * @property ownerColumn колонка владельца — та самая, по которой страж находит новые сущности.
 * @property cleanedBy какой из двух операций она снимается.
 * @property hasServerTwin есть ли у строк серверный двойник, то есть вернутся ли они после входа.
 * @property namedToPlayer каким словом потеря называется игроку. `null` — не называется: либо
 *   двойник есть, либо это не пользовательские данные. Значения по умолчанию нет ни у одного из
 *   двух признаков: забыть проставить их нельзя.
 */
data class OwnerScopedTable(
    val table: String,
    val ownerColumn: String,
    val cleanedBy: OwnerCleanup,
    val hasServerTwin: Boolean,
    val namedToPlayer: OwnerDataKind?,
)

/**
 * Закрытый перечень таблиц с колонкой владельца (история 6.2).
 *
 * Единственный источник двух вещей: что удаляет вторая операция и что называется игроку перед
 * сменой аккаунта. Новая сущность с колонкой владельца, не внесённая сюда, роняет сборку —
 * `OwnerScopedTablesGuardTest`.
 *
 * Ветки дерева черновика (`draft_sections`, `draft_themes`, `draft_lessons`, `draft_questions`)
 * сюда не входят: колонки владельца у них нет, владелец лежит на `quest_drafts`, и режутся они
 * вместе с ним.
 */
object OwnerScopedTables {
    val ALL: List<OwnerScopedTable> =
        listOf(
            // Очередь: её судьбу решает первая операция, а не эта. Названа, чтобы страж не считал
            // её забытой.
            OwnerScopedTable("outbox", "owner_uid", OwnerCleanup.QUEUE, hasServerTwin = false, namedToPlayer = null),
            OwnerScopedTable(
                "mutation_receipts",
                "owner_uid",
                OwnerCleanup.TRACES,
                hasServerTwin = true,
                namedToPlayer = null,
            ),
            OwnerScopedTable(
                "lesson_attempt_intents",
                "owner_uid",
                OwnerCleanup.TRACES,
                hasServerTwin = false,
                namedToPlayer = null,
            ),
            OwnerScopedTable(
                "review_assignments",
                "ownerUid",
                OwnerCleanup.TRACES,
                hasServerTwin = true,
                namedToPlayer = null,
            ),
            OwnerScopedTable(
                "review_assignment_questions",
                "ownerUid",
                OwnerCleanup.TRACES,
                hasServerTwin = true,
                namedToPlayer = null,
            ),
            // Неопубликованный черновик серверу неизвестен: после переключения он не вернётся.
            OwnerScopedTable(
                "quest_drafts",
                "ownerUid",
                OwnerCleanup.TRACES,
                hasServerTwin = false,
                namedToPlayer = OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS,
            ),
            OwnerScopedTable("user_stats", "uid", OwnerCleanup.TRACES, hasServerTwin = true, namedToPlayer = null),
            OwnerScopedTable("user_profiles", "uid", OwnerCleanup.TRACES, hasServerTwin = true, namedToPlayer = null),
            OwnerScopedTable(
                "lesson_attempts",
                "user_id",
                OwnerCleanup.TRACES,
                hasServerTwin = true,
                namedToPlayer = null,
            ),
            OwnerScopedTable(
                "question_answers",
                "user_id",
                OwnerCleanup.TRACES,
                hasServerTwin = false,
                namedToPlayer = OwnerDataKind.ANSWER_HISTORY,
            ),
            OwnerScopedTable(
                "question_repetition",
                "user_id",
                OwnerCleanup.TRACES,
                hasServerTwin = false,
                namedToPlayer = OwnerDataKind.REPETITION_SCHEDULE,
            ),
            OwnerScopedTable(
                "lesson_rating_submitted_local",
                "user_id",
                OwnerCleanup.TRACES,
                hasServerTwin = true,
                namedToPlayer = null,
            ),
            // След прогона: время последнего успеха и ветвь последней ошибки этого владельца
            // (история 4.2). Серверного двойника нет — но и называть игроку нечего: после
            // переключения его собственный след напишет первая же удачная синхронизация, а
            // спрашивать согласие на потерю чужой даты значило бы спрашивать не о данных.
            OwnerScopedTable(
                "sync_run_trace",
                "owner_uid",
                OwnerCleanup.TRACES,
                hasServerTwin = false,
                namedToPlayer = null,
            ),
        )

    /** Что называется игроку — в порядке перечисления вида, а не таблицы. */
    val NAMED_TO_PLAYER: List<OwnerDataKind> =
        OwnerDataKind.entries.filter { kind -> ALL.any { it.namedToPlayer == kind } }
}
