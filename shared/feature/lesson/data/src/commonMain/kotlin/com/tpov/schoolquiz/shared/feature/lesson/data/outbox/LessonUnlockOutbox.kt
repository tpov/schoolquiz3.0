package com.tpov.schoolquiz.shared.feature.lesson.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для разблокировки урока за нолики.
 *
 * Имена — в пространстве имён фичи lesson (NFR4): `lesson.UNLOCK` и `lesson.LESSON:<lessonId>`.
 * Операцию объявляет общий реестр, ссылку — эта фича, и обе половины принадлежат ей же, потому
 * что её Koin-модуль регистрирует и очередь разблокировки. Callable, репозиторий и датасорс
 * покупки живут в economy — переезжает сюда именно имя действия, а не деньги.
 *
 * Локальная половина у записи есть. Владелец решил 2026-09-03: разблокировка без сети открывает
 * урок сразу — «это не золотой материал». Нолики — мягкая валюта, её цену клиент знает заранее
 * (история 5.2), и AD-25 остаётся про настоящие серверные поля: квалификации, сертификаты, полки
 * квестов. Поэтому списание и открытие ложатся в базу одной транзакцией с этой строкой (AD-23), а
 * карантин откатывает обе половины (AD-28).
 */
object LessonUnlockOutbox {
    /** `lesson.UNLOCK` — так разблокировку называет и клиент, и приёмник. */
    const val OPERATION: String = OutboxOperations.UNLOCK_LESSON

    /** Ссылка на урок для пометки его карточки (AD-14): `lesson.LESSON:<lessonId>`. */
    const val ENTITY_REF_PREFIX: String = "lesson.LESSON:"

    fun entityRef(lessonId: LessonId): String = ENTITY_REF_PREFIX + lessonId.value

    /** Урок из ссылки, если ссылка наша; чужую ссылку эта фича не разбирает. */
    fun lessonIdOf(entityRef: String?): LessonId? =
        entityRef
            ?.takeIf { it.startsWith(ENTITY_REF_PREFIX) }
            ?.removePrefix(ENTITY_REF_PREFIX)
            ?.takeIf { it.isNotBlank() }
            ?.let(::LessonId)

    /**
     * Ключ идемпотентности намерения.
     *
     * Выводится из тройки «владелец, вид покупки, урок», а не из случайного числа: разблокировать
     * один урок дважды нельзя в принципе — сервер второй раз ничего не спишет, — и два нажатия
     * обязаны дать одну строку, а не две (AD-2).
     *
     * Владелец входит в ключ, потому что уникальность `mutation_id` в таблице сквозная, а не по
     * аккаунту: без него второй аккаунт на том же устройстве, купивший тот же урок, получил бы
     * чужую строку вместо своей, а сервер отверг бы её как ключ, принадлежащий другому аккаунту
     * (AD-8). Прочие писатели этой заботы не знают: у них в ключе лежит идентификатор самого
     * действия, уникальный сам по себе, а у разблокировки такого идентификатора нет.
     *
     * Разделитель — дефис: точку приёмник в ключе не принимает, и двоеточие из
     * `LessonUnlockKind.keyFor` здесь не годится по той же причине.
     */
    fun mutationKey(
        ownerUid: String,
        lessonId: LessonId,
        kindWireName: String,
    ): String = OutboxOperations.mutationKey(OPERATION, "$ownerUid-$kindWireName-${lessonId.value}")

    /**
     * Ключ покупки в наборе `lessonUnlocks` — `"<вид>:<урок>"`.
     *
     * Зеркало `LessonUnlockKind.keyFor` из economy и `unlockDocId` с сервера. Своя копия здесь, а
     * не импорт: домен lesson о типах чужой фичи не знает, а форма ключа — часть контракта с
     * сервером, который этим же ключом отвечает на повтор `charged: 0`.
     */
    fun unlockKey(
        kindWireName: String,
        lessonId: LessonId,
    ): String = "$kindWireName:${lessonId.value}"

    /** Вид покупки из тела записи — его знает только тело: в ссылке стоит один урок. */
    fun kindOf(payload: String): String? = field(payload, "kind")

    /**
     * Сколько ноликов списала локальная половина.
     *
     * Число лежит в теле, потому что откатывать его придётся спустя дни и, возможно, после
     * перезапуска приложения: пересчитать цену заново по вопросам урока нельзя — они успеют
     * измениться, и возврат разошёлся бы со списанием. Сервер это поле не читает и цену считает
     * сам (AD-3): назвать её клиент по-прежнему не может.
     */
    fun chargedNolicsOf(payload: String): Long? = field(payload, CHARGED_NOLICS)?.toLongOrNull()

    private fun field(
        payload: String,
        name: String,
    ): String? =
        runCatching {
            LENIENT.parseToJsonElement(payload).jsonObject[name]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

    /**
     * Запись очереди из намерения.
     *
     * Тело — то, что читает `applyLessonUnlock` на сервере (`lessonId` и `kind`), плюс списанное
     * число ноликов для отката по карантину. Цену сервер по-прежнему считает сам: лишнее поле он
     * не читает вовсе, и клиент, назвавший свою цену, назвал бы ноль (AD-3).
     */
    fun record(
        ownerUid: String,
        lessonId: LessonId,
        kindWireName: String,
        chargedNolics: Long,
        createdAtMs: Long,
    ): OutboxRecord {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        require(kindWireName.isNotBlank()) { "kindWireName must not be blank" }
        require(chargedNolics >= 0L) { "chargedNolics must be non-negative, was: $chargedNolics" }
        return OutboxRecord(
            id = 0L,
            mutationId = mutationKey(ownerUid, lessonId, kindWireName),
            ownerUid = ownerUid,
            operation = OPERATION,
            payload =
                buildJsonObject {
                    put("lessonId", lessonId.value)
                    put("kind", kindWireName)
                    put(CHARGED_NOLICS, chargedNolics)
                }.toString(),
            entityRef = entityRef(lessonId),
            // Разблокировка не версионируется: сервер применяет её к текущему набору купленного,
            // и расхождения версий, которое требовало бы решения игрока, здесь нет (AD-24).
            expectedVersion = null,
            state = OutboxState.WAITING,
            createdAtMs = createdAtMs,
        )
    }

    /** Имя поля с локальным списанием. Серверу неизвестно и им не читается. */
    private const val CHARGED_NOLICS = "chargedNolics"

    private val LENIENT = Json { ignoreUnknownKeys = true }
}
