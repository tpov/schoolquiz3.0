package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tpov.schoolquiz.shared.core.outbox.OutboxNamespace

/**
 * Одна таблица отложенных действий на все типы (AD-5).
 *
 * Заменяет три очереди, каждая со своей формой: результаты урока, оценки квестов и заявки на
 * арену переезжают сюда. Новый тип действия не требует правки схемы — тип лежит строкой в
 * [operation], тело в [payload], и ядро в него не смотрит.
 *
 * Уникальный индекс по [mutationId] — не украшение: ключ рождается вместе с намерением игрока и
 * должен быть один на одно действие (AD-2), иначе повторная постановка в очередь создаст вторую
 * операцию с тем же смыслом и разными ключами.
 *
 * Здесь же живёт правило NFR4 про пространства имён — см. [init]. Эта строка и есть общая форма,
 * через которую проходят все пути постановки: и хранилище очереди, и транзакции владеющих фич.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["mutation_id"], unique = true),
        Index(value = ["owner_uid", "state", "next_retry_at_ms"]),
        Index(value = ["owner_uid", "entity_ref"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "entity_ref") val entityRef: String?,
    @ColumnInfo(name = "expected_version") val expectedVersion: Long?,
    /** Версия, названная сервером при отказе по конфликту. Пусто, пока конфликта не было (AD-24). */
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_retry_at_ms") val nextRetryAtMs: Long,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
) {
    /**
     * Ссылка живёт в том же пространстве имён, что и операция (NFR4).
     *
     * Проверка стоит у самой строки, а не у хранилища очереди, потому что хранилище — только один
     * путь постановки из четырёх. Прохождение, оценка и заявка на арену кладут строку своей
     * транзакцией вместе с локальным изменением (AD-23), минуя хранилище совсем: пока правило
     * стояло в хранилище, для них его не существовало, и разъехавшиеся имена доезжали до базы
     * молча. Собрать строку очереди, не пройдя здесь, нельзя — обойти правило больше нечем.
     *
     * Отказ приходит раньше вставки и раньше открытия транзакции, поэтому отвергнутое действие не
     * оставляет ни строки в очереди, ни половины локального изменения рядом с ней.
     *
     * Ссылка при этом на части не разбирается (AD-7): сравнивается имя фичи до первого
     * разделителя, а всё, что стоит дальше, для ядра непрозрачно.
     *
     * Эту же форму собирает и чтение строки из базы, так что правило распространяется и на уже
     * лежащие строки. Для всего, что кладут миграция 5 → 6 и сегодняшние писатели, имена совпадают;
     * строка с разъехавшимися именами могла появиться только до этого правила, и лучше услышать её
     * отказом, чем пометкой, которая молча не встанет ни на одну сущность.
     */
    init {
        OutboxNamespace.requireSameNamespace(operation, entityRef)
    }
}
