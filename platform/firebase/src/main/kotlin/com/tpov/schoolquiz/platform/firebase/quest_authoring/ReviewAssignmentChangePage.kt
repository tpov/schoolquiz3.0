package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.journalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto

/**
 * Тело запроса к `fetchReviewAssignmentChanges` (AD-31).
 *
 * Курсор уходит в двух видах, потому что сервер обязан понимать оба, пока в проде есть клиенты
 * прежней версии (NFR10): пара `cursor: {changedAtMs, docId}` — для сервера, который её умеет;
 * скаляр `cursorMs` — для того, который умеет только его. Скаляр стоит на миллисекунду раньше
 * пары: сервер, читающий только его, отдаёт всю миллисекунду курсора, и уже прочитанное отсекает
 * пара на клиенте в [reviewAssignmentChangePage]. Так две записи в одну миллисекунду не теряются
 * ни с каким сервером.
 */
internal fun reviewAssignmentChangeRequest(
    cursor: SyncCursor,
    limit: Int,
): Map<String, Any> =
    mapOf(
        REQUEST_CURSOR_MS to cursor.changedAtMs - 1,
        REQUEST_CURSOR to mapOf(CHANGED_AT_MS to cursor.changedAtMs, DOC_ID to cursor.docId),
        REQUEST_LIMIT to limit,
    )

/**
 * Страница журнала ревью из ответа callable.
 *
 * Ответ — `{changes: [{id, changedAtMs, docId?}], hasMore?}`. `docId` — id документа журнала;
 * пока сервер его не присылает, им служит id задания: журнал ревью пишет документ на задание, и
 * это одно и то же. Записи упорядочиваются и отсекаются по паре здесь же — сервер прежней версии
 * сортирует по одному времени и сравнивает строго с числом.
 *
 * Запись без id или без времени непонятна: она считается в [SyncChangePage.unreadable] и курсор
 * её проходит, а не пропускает молча (AD-11).
 *
 * Записи, из которых не собрать даже пару курсора, до [journalPage] не доживают — считать их там
 * не из чего. Поэтому они считаются здесь и прибавляются к [SyncChangePage.unreadable] страницы:
 * прежде такая запись исчезала до счёта, и страница из одних только записей без `changedAtMs`
 * выглядела как «журнал кончился, изменений нет». Рецензент не получал новых заданий, и ни одно
 * число об этом не говорило.
 */
internal fun reviewAssignmentChangePage(
    root: Map<*, *>?,
    cursor: SyncCursor,
    limit: Int,
    policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
): SyncChangePage<ReviewAssignmentChangeDto> {
    val documents = root.list(CHANGES)
    val entries = documents.mapNotNull { it.toJournalEntry(policy) }
    // Всё, что даже пары не дало: и не-объекты в массиве, и записи без времени или без id.
    val withoutCursor = root.rawSize(CHANGES) - entries.size
    val visible =
        entries
            .filter { it.cursor > cursor }
            .sortedBy { it.cursor }
            .take(limit)
    val page = journalPage(visible, limit, { it.cursor }, { it.change })
    val counted = page.copy(unreadable = page.unreadable + withoutCursor)
    val hasMore = root?.get(HAS_MORE) as? Boolean
    return if (hasMore == null) counted else counted.copy(hasMore = hasMore)
}

/** Документ журнала, как его видит читатель: пара курсора и разобранная запись — или ничего. */
private data class JournalEntry(val cursor: SyncCursor, val change: ReviewAssignmentChangeDto?)

private fun Map<*, *>.toJournalEntry(policy: JournalReadPolicy): JournalEntry? {
    val id = string(ID)
    // Прежняя форма: сервер не присылал `docId`, и им служил id задания. Терпится до конца
    // backfill — тем же переключателем, что и в остальных трёх журналах (AD-11).
    val docId = string(DOC_ID) ?: if (policy.strict) null else id
    val changedAtMs = longOrNull(CHANGED_AT_MS)
    // Ни времени, ни id: даже посчитать пару не из чего. Такой записи в ответе callable быть не
    // может — сервер ставит id документа, — но упасть здесь значило бы уронить весь проход.
    if (docId == null || changedAtMs == null || changedAtMs < 0L) return null
    val change = if (id == null) null else ReviewAssignmentChangeDto(id = id, changedAtMs = changedAtMs)
    return JournalEntry(SyncCursor(changedAtMs, docId), change)
}

private fun Map<*, *>?.list(field: String): List<Map<*, *>> =
    (this?.get(field) as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

/** Сколько элементов пришло в массиве — включая те, что даже объектом не оказались. */
private fun Map<*, *>?.rawSize(field: String): Int = (this?.get(field) as? List<*>).orEmpty().size

private fun Map<*, *>?.string(field: String): String? = this?.get(field)?.toString()?.takeIf { it.isNotBlank() }

private fun Map<*, *>?.longOrNull(field: String): Long? =
    when (val value = this?.get(field)) {
        is Long -> value
        is Int -> value.toLong()
        is Number -> value.toLong()
        else -> null
    }

private const val REQUEST_CURSOR_MS = "cursorMs"
private const val REQUEST_CURSOR = "cursor"
private const val REQUEST_LIMIT = "limit"
private const val CHANGES = "changes"
private const val HAS_MORE = "hasMore"
private const val ID = "id"
private const val DOC_ID = "docId"
private const val CHANGED_AT_MS = "changedAtMs"
