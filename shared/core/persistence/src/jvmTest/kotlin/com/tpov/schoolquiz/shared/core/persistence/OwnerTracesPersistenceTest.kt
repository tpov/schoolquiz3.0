package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxRemainder
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OwnerDataKind
import com.tpov.schoolquiz.shared.core.outbox.OwnerForgetReason
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Две операции AD-8 против настоящего SQLite: каждая трогает ровно свой перечень.
 *
 * Снятие курсора — удаление строки, а не ноль: `setCursor` берёт максимум из старого и нового, и
 * записанный ноль не дал бы следующему проходу под новым владельцем поставить меньшее значение.
 *
 * Локальные строки прежнего владельца снимаются вместе с курсорами. Пока снимался только курсор,
 * его черновики и задания оставались на устройстве навсегда: перезаписать их было уже нечем, а
 * читающий проход шёл по ним и просил у сервера чужие документы, получая отказ правила.
 */
class OwnerTracesPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var syncState: SyncStateDao
    private lateinit var store: RoomOutboxStore
    private lateinit var traces: RoomOwnerTraces

    private val previous = "uid-A"
    private val next = "uid-B"

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .addTypeConverter(StringSetConverter())
                .addTypeConverter(TopParticipantListConverter())
                .build()
        syncState = db.syncStateDao()
        store = RoomOutboxStore(db.outboxDao())
        traces =
            RoomOwnerTraces(db.ownerRowsDao()) { uid ->
                listOf("private_quests:$uid")
            }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun personalCursorsOfThePreviousOwnerAreDeletedAndTheSharedOnesStay() =
        runTest {
            syncState.setCursor("private_quests:$previous", SyncCursor(5_000L))
            syncState.setCursor("private_quests:$next", SyncCursor(7_000L))
            syncState.setCursor("catalog_sync:cat-1", SyncCursor(8_000L))
            syncState.setCursor("catalogs", SyncCursor(9_000L))

            traces.forget(previous)

            assertNull("удалена строка, а не записан ноль", cursorOf("private_quests:$previous"))
            assertEquals(SyncCursor(7_000L), cursorOf("private_quests:$next"))
            assertEquals(SyncCursor(8_000L), cursorOf("catalog_sync:cat-1"))
            assertEquals(SyncCursor(9_000L), cursorOf("catalogs"))
        }

    @Test
    fun afterTheCursorIsGoneTheNextPassMayWriteAnyValueIncludingASmallerOne() =
        runTest {
            syncState.setCursor("private_quests:$previous", SyncCursor(5_000L))

            traces.forget(previous)
            syncState.setCursor("private_quests:$previous", SyncCursor(1_000L))

            assertEquals(SyncCursor(1_000L), cursorOf("private_quests:$previous"))
        }

    @Test
    fun theTracesOperationDoesNotTouchTheQueue() =
        runTest {
            store.enqueue(record("m-1", previous))

            traces.forget(previous)

            assertEquals(1, store.countPending(previous))
        }

    @Test
    fun localRowsOfThePreviousOwnerAreDeletedWithHisCursors() =
        runTest {
            // Перечень истории 6.2 целиком: каждая таблица с колонкой владельца получает по строке
            // прежнего и по строке нового. После снятия следов у прежнего не остаётся ни одной, у
            // нового — все.
            seedOwnedRows(previous)
            seedOwnedRows(next)
            syncState.setCursor("private_quests:$previous", SyncCursor(5_000L))

            traces.forget(previous)

            OwnerScopedTables.ALL
                .filter { it.cleanedBy == OwnerCleanup.TRACES }
                .forEach { table ->
                    assertEquals(
                        "строки прежнего владельца остались в ${table.table}",
                        0,
                        countIn(table.table, table.ownerColumn, previous),
                    )
                    assertEquals(
                        "строки нового владельца пропали из ${table.table}",
                        1,
                        countIn(table.table, table.ownerColumn, next),
                    )
                }
        }

    @Test
    fun theDraftTreeOfThePreviousOwnerGoesWithTheDraftAndTheOtherTreeStays() =
        runTest {
            // У веток дерева колонки владельца нет — они режутся по родителю. Пока их не резал никто,
            // на устройстве оставались вопросы и уроки неопубликованного черновика чужого игрока.
            seedOwnedRows(previous)
            seedOwnedRows(next)

            traces.forget(previous)

            listOf("draft_sections", "draft_themes", "draft_lessons", "draft_questions").forEach { table ->
                assertEquals(
                    "ветка $table прежнего владельца осталась",
                    0,
                    countIn(table, "draftId", "draft-$previous"),
                )
                assertEquals("ветка $table нового владельца пропала", 1, countIn(table, "draftId", "draft-$next"))
            }
        }

    @Test
    fun theTracesOperationDoesNotTouchSharedContent() =
        runTest {
            // Общий контент от аккаунта не зависит: каталоги и уроки остаются на месте.
            seedOwnedRows(previous)
            db.catalogDao().insertOrReplace(catalog())

            traces.forget(previous)

            assertEquals(1, countIn("catalogs", "id", "cat-1"))
        }

    @Test
    fun theNamedLossesComeFromTheRegistryAndNameOnlyWhatHasNoServerTwin() =
        runTest {
            assertEquals(
                listOf(
                    OwnerDataKind.ANSWER_HISTORY,
                    OwnerDataKind.REPETITION_SCHEDULE,
                    OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS,
                ),
                traces.namedLosses,
            )
            assertTrue(
                "названо только то, чего сервер не хранит",
                OwnerScopedTables.ALL.filter { it.namedToPlayer != null }.none { it.hasServerTwin },
            )
        }

    @Test
    fun forgettingTheQueueRemovesEveryStateOfTheOwnerAndNothingOfTheOther() =
        runTest {
            val states = listOf("WAITING", "WAITING_PRECONDITION", "CONFLICT", "QUARANTINED", "SYNCHRONOUS")
            states.forEachIndexed { i, state ->
                val queued = store.enqueue(record("m-$i", previous))
                db.outboxDao().applyDecision(queued.id, state, 0L, 1, null, null)
            }
            store.enqueue(record("m-other", next))
            syncState.setCursor("private_quests:$previous", SyncCursor(5_000L))

            store.forget(previous)

            assertEquals(0, countIn("outbox", "owner_uid", previous))
            assertEquals(1, countIn("outbox", "owner_uid", next))
            assertEquals(
                "очередь — не курсоры: их снимает другая операция",
                SyncCursor(5_000L),
                cursorOf("private_quests:$previous"),
            )
        }

    @Test
    fun theRemainderCountsEveryStateSeparatelyAndOnlyThisOwner() =
        runTest {
            // Остаток считается тем же перечнем, что удаляет `forget`: пять состояний, не два.
            val states = listOf("WAITING", "WAITING", "WAITING_PRECONDITION", "CONFLICT", "QUARANTINED", "SYNCHRONOUS")
            states.forEachIndexed { i, state ->
                val queued = store.enqueue(record("m-$i", previous))
                db.outboxDao().applyDecision(queued.id, state, 0L, 1, null, null)
            }
            store.enqueue(record("m-other", next))

            assertEquals(
                OutboxRemainder(
                    waiting = 2,
                    waitingPrecondition = 1,
                    conflicted = 1,
                    quarantined = 1,
                    synchronous = 1,
                ),
                store.remainderOf(previous),
            )
            assertEquals(OutboxRemainder(waiting = 1), store.remainderOf(next))
        }

    @Test
    fun anUnknownStateNameCountsAsQuarantinedSoItIsStillWarnedAbout() =
        runTest {
            // Строка из будущей версии не уедет — но исчезнет вместе с аккаунтом, значит про неё надо
            // предупредить. Читатель уже считает такую запись карантинной; остаток считает так же.
            val queued = store.enqueue(record("m-future", previous))
            db.outboxDao().applyDecision(queued.id, "SOMETHING_NEW", 0L, 1, null, null)

            assertEquals(OutboxRemainder(quarantined = 1), store.remainderOf(previous))
        }

    @Test
    fun theQueueNamesItsOwnersWhenThereIsNoSessionToAsk() =
        runTest {
            store.enqueue(record("m-1", previous))
            store.enqueue(record("m-2", previous))
            store.enqueue(record("m-3", next))

            assertEquals(setOf(previous, next), store.ownersWithRecords().toSet())
        }

    @Test
    fun theLocalRowsNameTheirOwnerEvenWhenTheQueueIsEmpty() =
        runTest {
            // Пустая очередь — обычное стационарное состояние: запись уехала и исчезла, а черновик
            // остался лежать. Пока набор владельцев спрашивался у одной очереди, прежний владелец жил
            // на устройстве вечно, а читающий проход просил у сервера его документы и получал отказ.
            seedOwnedRows(previous, withQueueRow = false)

            assertTrue("очередь и правда пуста", store.ownersWithRecords().isEmpty())
            assertEquals(listOf(previous), traces.ownersWithTraces())
        }

    @Test
    fun theOwnersAreNamedOnceEachAndTheOtherOneIsNamedToo() =
        runTest {
            seedOwnedRows(previous, withQueueRow = false)
            seedOwnedRows(next, withQueueRow = false)

            assertEquals(listOf(previous, next), traces.ownersWithTraces().sorted())
        }

    @Test
    fun afterTheTracesAreGoneNobodyIsNamed() =
        runTest {
            seedOwnedRows(previous, withQueueRow = false)

            traces.forget(previous)

            assertTrue("следы сняты — называть некого", traces.ownersWithTraces().isEmpty())
        }

    @Test
    fun theNamedLossesOfAnOwnerAreOnlyTheOnesHeActuallyHas() =
        runTest {
            // Предупреждение — это согласие. Называть историю ответов игроку, у которого её нет, значит
            // спрашивать не про то, что удаляется.
            db.questAuthoringDao().insertDraft(draft(previous))

            assertEquals(listOf(OwnerDataKind.UNPUBLISHED_QUEST_DRAFTS), traces.lossesOf(previous))
            assertTrue("у другого владельца нет ничего", traces.lossesOf(next).isEmpty())
        }

    @Test
    fun everyKindWithoutAServerTwinIsFoundWhenItIsThere() =
        runTest {
            seedOwnedRows(previous, withQueueRow = false)

            assertEquals(OwnerScopedTables.NAMED_TO_PLAYER, traces.lossesOf(previous))
        }

    @Test
    fun rowsWithAServerTwinAreNotNamedToThePlayer() =
        runTest {
            // Профиль, статистика и локальные оценки вернутся после входа: снимать их надо,
            // а спрашивать про них не о чем.
            db.userProfileDao().upsert(userProfile(previous))
            db.userStatsDao().upsert(userStats(previous))
            db.lessonRatingLocalDao().upsert(LessonRatingSubmittedLocalEntity(previous, "lesson-1", 1L))
            db.lessonAttemptDao().upsert(lessonAttempt(previous))

            assertTrue("названо только то, чего сервер не хранит", traces.lossesOf(previous).isEmpty())
            assertEquals("но забыть его всё равно есть кому", listOf(previous), traces.ownersWithTraces())
        }

    @Test
    fun frozenArenaRatingIntentIsRemovedOnSwitchAndExplicitErasureWithoutTouchingTheOtherOwner() =
        runTest {
            seedFrozenRatingTrace(previous)
            seedFrozenRatingTrace(next)

            assertEquals(setOf(previous, next), traces.ownersWithTraces().toSet())
            assertTrue(
                "очередное намерение не добавляет новый названный вид потери",
                traces.lossesOf(previous).isEmpty(),
            )

            traces.forget(previous, OwnerForgetReason.SWITCH)

            assertEquals(0, countIn("arena_frozen_rating_intents", "owner_uid", previous))
            assertEquals(0, countIn("lesson_rating_submitted_local", "user_id", previous))
            assertEquals(1, countIn("arena_frozen_rating_intents", "owner_uid", next))

            seedFrozenRatingTrace(previous)
            traces.forget(previous, OwnerForgetReason.ERASE_LOCAL_DATA)

            assertEquals(0, countIn("arena_frozen_rating_intents", "owner_uid", previous))
            assertEquals(1, countIn("arena_frozen_rating_intents", "owner_uid", next))
            assertEquals(listOf(next), traces.ownersWithTraces())
        }

    private suspend fun cursorOf(collectionId: String): SyncCursor? = syncState.find(collectionId)?.toCursor()

    /**
     * По строке владельца в каждую таблицу перечня, плюс дерево его черновика.
     *
     * [withQueueRow] выключается там, где проверяется именно случай «очередь пуста, а следы
     * лежат»: он и есть обычное стационарное состояние.
     */
    private suspend fun seedOwnedRows(
        owner: String,
        withQueueRow: Boolean = true,
    ) {
        db.questAuthoringDao().insertDraft(draft(owner))
        db.questAuthoringDao().insertSections(listOf(DraftSectionEntity("sec-$owner", "draft-$owner", "S", 0)))
        db.questAuthoringDao().insertThemes(
            listOf(DraftThemeEntity("th-$owner", "draft-$owner", "sec-$owner", "T", 0)),
        )
        db.questAuthoringDao().insertLessons(
            listOf(DraftLessonEntity("les-$owner", "draft-$owner", "th-$owner", "L", 0)),
        )
        db.questAuthoringDao().insertQuestions(listOf(draftQuestion(owner)))
        db.userStatsDao().upsert(userStats(owner))
        db.userProfileDao().upsert(userProfile(owner))
        db.lessonAttemptDao().upsert(lessonAttempt(owner))
        db.questionAnswerDao().upsertAll(listOf(questionAnswer(owner)))
        db.questionRepetitionDao().upsertAll(listOf(questionRepetition(owner)))
        db.lessonRatingLocalDao().upsert(LessonRatingSubmittedLocalEntity(owner, "lesson-1", 1L))
        seedFrozenRatingIntent(owner, "lesson-1")
        // След прогона — такая же личная строка владельца, как остальные (история 4.2).
        db.syncRunTraceDao().recordSuccess(owner, SyncRunKind.CONTENT.column(), atMs = 1_000L)
        db.mutationReceiptDao().persist(
            MutationReceiptEntity(
                owner,
                "receipt-$owner",
                "lesson_runner.SUBMIT_ATTEMPT",
                "lesson_runner:attempt:attempt-$owner",
                "{}",
                1_000L,
            ),
        )
        db.lessonAttemptDao().insertAttemptIntent(
            LessonAttemptIntentEntity(
                "attempt-$owner",
                owner,
                "intent-$owner",
                "lesson_runner.SUBMIT_ATTEMPT",
                "{}",
                "lesson_runner:attempt:attempt-$owner",
                null,
            ),
        )
        // Проводка заявок подарков — такие же личные строки владельца (epic-commerce 7.4).
        db.giftClaimIntentDao().insert(
            GiftClaimIntentEntity(owner, "PREMIUM_DAILY", "daily-$owner", "{}", 1_000L),
        )
        db.giftBoxLocalStreakDao().upsert(
            GiftBoxLocalStreakEntity(
                ownerUid = owner,
                baseBoxCount = 1, baseStreakDays = 10, baseNextBoxAtMs = 100L,
                boxCount = 3, streakDays = 12, nextBoxAtMs = 300L, updatedAtMs = 400L,
            ),
        )
        if (withQueueRow) store.enqueue(record("m-seed-$owner", owner))
    }

    private suspend fun seedFrozenRatingTrace(owner: String) {
        db.lessonRatingLocalDao().upsert(LessonRatingSubmittedLocalEntity(owner, "arena-lesson", 1L))
        seedFrozenRatingIntent(owner, "arena-lesson")
    }

    private suspend fun seedFrozenRatingIntent(
        owner: String,
        lessonId: String,
    ) {
        db.arenaFrozenRatingDao().insertIntent(
            ArenaFrozenRatingIntentEntity(
                ownerUid = owner,
                ticketId = "ticket-$owner",
                attemptId = "attempt-$owner",
                lessonId = lessonId,
                ratingId = "rating-$owner",
                mutationId = "lesson_runner.SUBMIT_RATING:rating-$owner",
                operation = "lesson_runner.SUBMIT_RATING",
                payload = "{\"rating\":2}",
                entityRef = "lesson_runner:rating:rating-$owner",
                createdAtMs = 1L,
            ),
        )
    }

    /** Считает строки без DAO: перечень таблиц один, и проверять его надо им же. */
    private suspend fun countIn(
        table: String,
        column: String,
        value: String,
    ): Int =
        db.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM `$table` WHERE `$column` = ?") { statement ->
                statement.bindText(1, value)
                statement.step()
                statement.getInt(0)
            }
        }

    private fun record(
        mutationId: String,
        owner: String,
    ) = OutboxRecord(
        id = 0L,
        mutationId = mutationId,
        ownerUid = owner,
        operation = "OP",
        payload = "{}",
        state = OutboxState.WAITING,
        createdAtMs = 1L,
    )

    private fun catalog() =
        CatalogEntity(
            id = "cat-1",
            name = "Catalog",
            picturePath = null,
            pictureUrl = null,
        )

    private fun draft(owner: String) =
        QuestDraftEntity(
            id = "draft-$owner",
            ownerUid = owner,
            catalogId = "cat-1",
            title = "Draft",
            description = null,
            defaultLanguage = "ru",
            defaultDifficulty = "EASY",
            status = "DRAFT",
            localRevision = 1L,
            serverRevision = null,
            publicQuestId = null,
            createdAtMs = 1L,
            updatedAtMs = 1L,
            isActive = true,
        )

    private fun draftQuestion(owner: String) =
        DraftQuestionEntity(
            id = "dq-$owner",
            draftId = "draft-$owner",
            lessonId = "les-$owner",
            type = "SINGLE",
            language = "ru",
            difficulty = "EASY",
            order = 0,
            text = "Q",
            imagePath = null,
            payload = null,
            validationState = "VALID",
            updatedAtMs = 1L,
        )

    private fun userStats(owner: String) =
        UserStatsEntity(
            uid = owner,
            nickname = "n",
            avatarUrl = null,
            hasPremium = false,
            streakDays = 0,
            stars = 0L,
            nolics = 0L,
            standardHearts = 0,
            goldHearts = 0,
            gold = 0L,
            currentSkill = 0,
            testerLevel = 0,
            moderatorLevel = 0,
            sponsorLevel = 0,
            translatorLevel = 0,
            adminLevel = 0,
            developerLevel = 0,
        )

    private fun userProfile(owner: String) =
        UserProfileEntity(
            uid = owner,
            nickname = "n",
            status = "ANONYMOUS",
            avatarUrl = null,
            knownLanguages = emptyList(),
            createdAtMs = 1L,
            updatedAtMs = 1L,
            skillPoints = 0,
            gold = 0L,
            nolics = 0L,
            standardHearts = 0,
            goldHearts = 0,
            sponsorLevel = 0,
            testerLevel = 0,
            translatorLevel = 0,
            moderatorLevel = 0,
            adminLevel = 0,
            developerLevel = 0,
            boxCount = 0,
            boxStreakDays = 0,
            nextBoxAtMs = 0L,
            premiumUntilMs = 0L,
            trophies = emptyList(),
            ownedLogos = emptyList(),
        )

    private fun lessonAttempt(owner: String) =
        LessonAttemptEntity(
            attemptId = "att-$owner",
            userId = owner,
            lessonId = "lesson-1",
            lessonVersion = 1L,
            isHard = 0,
            codeAnswer = "9",
            percentScore = 100,
            completedAt = 1L,
        )

    private fun questionAnswer(owner: String) =
        QuestionAnswerEntity(
            attemptId = "att-$owner",
            questionId = "q-1",
            userId = owner,
            lessonId = "lesson-1",
            lessonVersion = 1L,
            isHard = 0,
            codeAnswerIndex = 0,
            score = 9,
            answerPayload = "{}",
            answeredAtMs = 1L,
            durationMs = 1L,
            wasTimeout = 0,
        )

    private fun questionRepetition(owner: String) =
        QuestionRepetitionEntity(
            userId = owner,
            questionId = "q-1",
            lessonId = "lesson-1",
            intervalDays = 1,
            easeFactorMilli = 2_500,
            repetitions = 1,
            lastAnsweredAtMs = 1L,
            nextReviewAtMs = 2L,
        )
}
