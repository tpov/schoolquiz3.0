package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE collectionId = :collectionId")
    suspend fun find(collectionId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SyncStateEntity)

    /**
     * Обнуляет все курсоры чтения (AD-30).
     *
     * Удаление, а не запись нулей: отсутствующий курсор и так читается как начало, а строка с нулём
     * пережила бы смысл — «здесь когда-то что-то было». Очередь отправки этим не затрагивается:
     * ресинк лечит только сторону чтения.
     */
    @Query("DELETE FROM sync_state")
    suspend fun clearAllCursors()

    /**
     * Снимает названные курсоры — строкой, а не началом (AD-8).
     *
     * `setCursor` монотонен: записанное начало не дало бы следующему проходу поставить значение
     * меньше прежнего. Удалённая строка читается как «никогда не было», и первый же проход под
     * новым владельцем пишет своё. Для личных курсоров прежнего владельца это и нужно.
     */
    @Query("DELETE FROM sync_state WHERE collectionId IN (:collectionIds)")
    suspend fun deleteCursors(collectionIds: List<String>)

    /**
     * Монотонно по паре: назад курсор не откатывается, так же как прежде не откатывался по числу.
     * Сравнивается пара целиком — число сравнивать нельзя: `(1000, "b")` позже `(1000, "a")`.
     */
    @Transaction
    suspend fun setCursor(
        collectionId: String,
        cursor: SyncCursor,
    ) {
        val current = find(collectionId)?.toCursor() ?: SyncCursor.BEGINNING
        if (cursor > current) insertOrReplace(SyncStateEntity.of(collectionId, cursor))
    }
}
