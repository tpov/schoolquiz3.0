package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

/**
 * Курсор чтения одного журнала — пара «время, id документа» (AD-31).
 *
 * Пустой [docId] — «в этой миллисекунде ещё ничего не прочитано». Так выглядит строка, дожившая
 * с прежней схемы, где курсор был одним числом: по лексикографическому сравнению это граница не
 * позже прежней, и читатель перечитает миллисекунду целиком, а не перескочит её.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val collectionId: String,
    val cursor: Long,
    @ColumnInfo(name = "doc_id", defaultValue = "") val docId: String = "",
) {
    fun toCursor(): SyncCursor = SyncCursor(changedAtMs = cursor, docId = docId)

    companion object {
        fun of(
            collectionId: String,
            cursor: SyncCursor,
        ): SyncStateEntity =
            SyncStateEntity(collectionId = collectionId, cursor = cursor.changedAtMs, docId = cursor.docId)
    }
}
