package com.tpov.schoolquiz.platform.firebase.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.journalPage
import kotlinx.coroutines.tasks.await

/** Поле времени в теле записи журнала — `{type, id, changedAtMs}` (AD-11). */
internal const val SYNC_CHANGE_CHANGED_AT_MS = "changedAtMs"

/**
 * Страница журнала `sync_changes` из Firestore (AD-31) — одна на все читатели, которые ходят в
 * Firestore напрямую: каталог, урок, приватные квесты. Разница между ними — только разбор
 * документа.
 *
 * Порядок задаёт пара «время, id документа», и продолжение идёт через `startAfter` по той же
 * паре. Одного времени мало: две сущности, изменённые одним пакетом, получают одну миллисекунду,
 * и строгое сравнение по времени потеряло бы вторую навсегда.
 *
 * Пустой `docId` — это не «начать после документа с пустым именем», а «в этой миллисекунде ещё
 * ничего не прочитано». Отдать его в `startAfter` нельзя: Firestore достраивает путь документа
 * из этого значения и на пустой строке падает с `IllegalArgumentException`, то есть журнал не
 * синхронизировался бы вовсе. `startAt` перечитает всю миллисекунду курсора заново — дешевле,
 * чем потерять запись: применение идёт через `refreshByIds` и повтор безвреден.
 *
 * Курсор двигается по последнему ПРОЧИТАННОМУ документу, а не по последней распознанной записи:
 * иначе битая запись в конце страницы читалась бы вечно. Сама она считается в
 * [SyncChangePage.unreadable]: три формы документа сосуществуют в журналах, и терпимость
 * снимается только после backfill (AD-11), а до тех пор пропуск обязан оставлять след.
 */
internal suspend fun <T> Query.fetchSyncChangePage(
    cursor: SyncCursor,
    limit: Int,
    parse: (DocumentSnapshot) -> T?,
): SyncChangePage<T> {
    val documents =
        orderBy(SYNC_CHANGE_CHANGED_AT_MS)
            .orderBy(FieldPath.documentId())
            .let {
                if (cursor.docId.isEmpty()) {
                    it.startAt(cursor.changedAtMs)
                } else {
                    it.startAfter(cursor.changedAtMs, cursor.docId)
                }
            }
            .limit(limit.toLong())
            .get()
            .await()
            .documents
    return journalPage(
        documents = documents,
        limit = limit,
        cursorOf = { SyncCursor(it.millisField(SYNC_CHANGE_CHANGED_AT_MS) ?: cursor.changedAtMs, it.id) },
        parse = parse,
    )
}
