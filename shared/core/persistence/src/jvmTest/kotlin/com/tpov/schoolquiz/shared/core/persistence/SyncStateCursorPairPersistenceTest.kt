package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Курсор чтения против настоящего SQLite: пара «время, id документа» ложится на диск и оттуда же
 * поднимается (AD-31), а строка прежней схемы — с одним числом — читается как пара с пустой второй
 * половиной.
 *
 * Перезапуск приложения — это перечитывание из базы, и здесь оно настоящее.
 */
class SyncStateCursorPairPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomSyncStateRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        repository = RoomSyncStateRepository(db.syncStateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aRowWrittenByTheOldSchemaReadsAsAPairWithAnEmptyDocumentId() = runTest {
        // Так выглядит строка после миграции 7 → 8: время есть, второй половины нет — колонка
        // заполнена своим `DEFAULT ''`. Сюда она вставляется тем же SQL, каким её оставила бы
        // прежняя версия приложения.
        db.useWriterConnection { transactor ->
            transactor.usePrepared(
                "INSERT INTO sync_state (collectionId, cursor) VALUES ('catalog_sync:cat-1', 1700)",
            ) { it.step() }
        }

        assertEquals(SyncCursor(1700L, ""), repository.getCursor("catalog_sync:cat-1"))
    }

    @Test
    fun theWholePairSurvivesARereadAndAMissingRowIsTheBeginning() = runTest {
        repository.setCursor("catalog_sync:cat-1", SyncCursor(1700L, "quest_q-2"))

        assertEquals(SyncCursor(1700L, "quest_q-2"), repository.getCursor("catalog_sync:cat-1"))
        assertEquals(SyncCursor.BEGINNING, repository.getCursor("catalog_sync:cat-2"))
    }

    @Test
    fun theCursorMovesForwardByThePairAndNeverBack() = runTest {
        repository.setCursor("j", SyncCursor(1000L, "b"))

        // То же время, меньший id — раньше по паре, хотя по числу «равно».
        repository.setCursor("j", SyncCursor(1000L, "a"))
        assertEquals(SyncCursor(1000L, "b"), repository.getCursor("j"))

        repository.setCursor("j", SyncCursor(999L, "z"))
        assertEquals(SyncCursor(1000L, "b"), repository.getCursor("j"))

        repository.setCursor("j", SyncCursor(1000L, "c"))
        assertEquals(SyncCursor(1000L, "c"), repository.getCursor("j"))

        repository.setCursor("j", SyncCursor(1001L, "a"))
        assertEquals(SyncCursor(1001L, "a"), repository.getCursor("j"))
    }

    @Test
    fun anOldCursorWithoutADocumentIdIsOvertakenByAnyDocumentInTheSameMillisecond() = runTest {
        // Пустой id — «в этой миллисекунде ещё ничего не прочитано»: первый же документ этой
        // миллисекунды стоит позже и записывается.
        db.useWriterConnection { transactor ->
            transactor.usePrepared("INSERT INTO sync_state (collectionId, cursor) VALUES ('j', 1700)") { it.step() }
        }

        repository.setCursor("j", SyncCursor(1700L, "a"))

        assertEquals(SyncCursor(1700L, "a"), repository.getCursor("j"))
    }

    @Test
    fun aResetPutsEveryCursorBackToTheBeginning() = runTest {
        repository.setCursor("catalog_sync:cat-1", SyncCursor(1700L, "quest_q-2"))
        repository.setCursor("review_assignments:u-1", SyncCursor(2600L, "a-1"))

        repository.resetAllCursors()

        assertEquals(SyncCursor.BEGINNING, repository.getCursor("catalog_sync:cat-1"))
        assertEquals(SyncCursor.BEGINNING, repository.getCursor("review_assignments:u-1"))
    }
}
