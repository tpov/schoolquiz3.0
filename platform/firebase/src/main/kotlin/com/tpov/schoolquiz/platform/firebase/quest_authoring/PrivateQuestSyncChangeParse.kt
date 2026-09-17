package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange

/**
 * Разбор записи приватного журнала — отдельно от Firestore, чтобы его можно было проверить.
 *
 * В журнале сосуществуют две формы, и читатель обязан терпеть обе, пока backfill не привёл
 * записанное к одной (AD-11):
 *
 * - нынешняя: тело — `{type, id, changedAtMs}`, где `id` — квест, а каталог живёт в id документа
 *   `{catalogId}_{questId}`;
 * - прежняя: каталог и квест лежали в теле полями `catalogId` и `questId`.
 *
 * Каталог отрезается с конца по известному квесту, а не берётся до первого подчёркивания: и в
 * каталоге, и в квесте подчёркивание допустимо, и деление по первому дало бы чужой каталог —
 * приватный квест уехал бы в чужую папку или потерялся.
 *
 * Непонятая запись — `null`: страница считает такие в `unreadable`, и пропуск оставляет след,
 * а не исчезает молча. После backfill прежнюю форму снимает [JournalReadPolicy.STRICT] — тот же
 * переключатель, что у остальных трёх журналов, — и такая запись останавливает проход.
 */
internal fun privateQuestSyncChangeOrNull(
    documentId: String,
    bodyId: String?,
    bodyCatalogId: String?,
    bodyQuestId: String?,
    changedAtMs: Long?,
    policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
): PrivateQuestSyncChange? {
    if (changedAtMs == null) return null
    val pair =
        currentFormPair(documentId, bodyId)
            ?: if (policy.strict) null else legacyFormPair(bodyCatalogId, bodyQuestId)
    return pair?.let { (catalogId, questId) ->
        PrivateQuestSyncChange(catalogId = catalogId, questId = questId, changedAtMs = changedAtMs)
    }
}

/** Нынешняя форма: квест — в теле, каталог — начало id документа `{catalogId}_{questId}`. */
private fun currentFormPair(
    documentId: String,
    bodyId: String?,
): Pair<String, String>? {
    val questId = bodyId?.takeIf { it.isNotBlank() } ?: return null
    val suffix = "_$questId"
    val catalogId = documentId.takeIf { it.endsWith(suffix) }?.dropLast(suffix.length)
    return if (catalogId.isNullOrBlank()) null else catalogId to questId
}

/** Прежняя форма: каталог и квест лежали полями тела. Терпится до конца backfill (AD-11). */
private fun legacyFormPair(
    bodyCatalogId: String?,
    bodyQuestId: String?,
): Pair<String, String>? {
    val catalogId = bodyCatalogId?.takeIf { it.isNotBlank() }
    val questId = bodyQuestId?.takeIf { it.isNotBlank() }
    return if (catalogId != null && questId != null) catalogId to questId else null
}
