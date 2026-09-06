package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * След прогона синхронизации — строка на владельца и вид прогона (AD-14, истории 4.2 и 4.5).
 *
 * Строка, а не поле в настройках: след принадлежит аккаунту, и после переключения читается по
 * ключу нового владельца — чужого времени и чужой ошибки на экране не оказывается (инвариант 8).
 * Строка прежнего владельца снимается вместе с остальными его личными строками — таблица названа
 * в [OwnerScopedTables], и удаление идёт по одному владельцу, забирая все его виды разом.
 *
 * Вид прогона — вторая половина ключа, а не колонка рядом. Расписаний несколько (эпик 9), и общая
 * строка означала, что последний доложивший затирает исход соседа: удачный профильный прогон
 * снимал ветвь упавшего контентного и ставил поверх неё своё время успеха. Сводить их в одно
 * число запрещено (AD-14), значит и хранить их одной строкой нельзя.
 *
 * Ошибка лежит разобранной на колонки, а не текстом: ветвь отдельно, причина отказа отдельно,
 * серверная версия отдельным числом. Собирать ветвь разбором текста запрещено (AD-15), и хранить
 * её текстом значило бы вернуть тот же разбор на чтении.
 *
 * @property ownerUid чей это след.
 * @property kind какое расписание его оставило — имя из [SyncRunKindColumn].
 * @property lastSuccessAtMs время удачного прогона этого вида. Ноль — прогона ещё не было.
 * @property lastErrorBranch имя ветви [com.tpov.schoolquiz.shared.core.network.SyncError]. Пусто —
 *   последняя попытка не провалилась.
 * @property lastErrorReason причина отказа в том виде, в каком её назвал сервер. Только у ветви
 *   отказа.
 * @property lastErrorServerVersion версия, которую назвал сервер. Только у ветви расхождения версий.
 */
@Entity(tableName = "sync_run_trace", primaryKeys = ["owner_uid", "kind"])
data class SyncRunTraceEntity(
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "last_success_at_ms") val lastSuccessAtMs: Long = 0L,
    @ColumnInfo(name = "last_error_branch") val lastErrorBranch: String? = null,
    @ColumnInfo(name = "last_error_reason") val lastErrorReason: String? = null,
    @ColumnInfo(name = "last_error_server_version") val lastErrorServerVersion: Long? = null,
)
