package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStatsDao {
    @Query("SELECT * FROM user_stats WHERE uid = :uid")
    fun observeByUid(uid: String): Flow<UserStatsEntity?>

    @Query("SELECT * FROM user_stats WHERE uid = :uid")
    suspend fun findByUid(uid: String): UserStatsEntity?

    @Upsert
    suspend fun upsert(entity: UserStatsEntity)

    @Query("UPDATE user_stats SET developerLevel = :value WHERE uid = :uid")
    suspend fun updateDeveloperLevel(
        uid: String,
        value: Int,
    )

    /**
     * Кладёт строку очереди тем же соединением, что и локальную половину.
     *
     * `IGNORE`, а не `REPLACE`: ключ идемпотентности один на одно намерение (AD-2), и `REPLACE`
     * снёс бы лежащую строку вместе с накопленными попытками, выдав ей новый `id`.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueOutboxRow(entity: OutboxEntity): Long

    /** Состояние записи под ключом — чтобы отличить «уже отложено» от «уже никуда не уедет». */
    @Query("SELECT state FROM outbox WHERE mutation_id = :mutationId")
    suspend fun outboxRowState(mutationId: String): String?

    /** Снимает терминальную запись, занявшую ключ: живой она уже не станет (AD-22). */
    @Query("DELETE FROM outbox WHERE mutation_id = :mutationId")
    suspend fun deleteOutboxRow(mutationId: String)

    /**
     * Ссылки на сущности, чьи записи этой операции стоят в карантине.
     *
     * Один запрос на весь список, а не подписка на каждую строку: экран уроков показывает
     * десятки карточек, и отдельный `observeEntity` на каждую превратил бы одну пометку в
     * десятки живых курсоров (NFR1). Разбирает ссылку владеющая фича — ядро в неё не смотрит
     * (AD-7).
     */
    @Query(
        """
        SELECT entity_ref FROM outbox
        WHERE owner_uid = :ownerUid
          AND operation = :operation
          AND state = 'QUARANTINED'
          AND entity_ref IS NOT NULL
        """,
    )
    fun observeQuarantinedEntityRefs(
        ownerUid: String,
        operation: String,
    ): Flow<List<String>>

    /**
     * Тела записей этой операции, которые ещё не доехали до сервера.
     *
     * Карантинные сюда не входят: их локальная половина уже откачена владеющей фичей (AD-28), и
     * вычитать её второй раз значило бы отнять у игрока то, что ему вернули. Конфликтные входят —
     * эффект не применён, а решение ещё впереди.
     *
     * Отдаются телами, а не числом: ядро в тело не смотрит (AD-7), сумму считает та фича, которая
     * это тело и написала.
     */
    @Query(
        """
        SELECT payload FROM outbox
        WHERE owner_uid = :ownerUid
          AND operation = :operation
          AND state != 'QUARANTINED'
        """,
    )
    suspend fun undeliveredPayloads(
        ownerUid: String,
        operation: String,
    ): List<String>

    /**
     * Списывает нолики, открывает урок и кладёт запись очереди — одной транзакцией (AD-23).
     *
     * Порознь возможны обе беды и обе молчаливые: урок, показанный открытым и никогда не
     * доехавший до сервера, и запись очереди без локального следа, которую нечем откатить по
     * карантину. Владелец решил 2026-09-03, что локальная половина здесь есть: нолики — мягкая
     * валюта, её цену клиент знает заранее (история 5.2), и AD-25 на неё не распространяется.
     *
     * Порядок внутри важен: сначала локальная половина, потом строка очереди. Отказ на строке
     * роняет транзакцию целиком, и списание не переживает его — иначе нолики ушли бы за дверь,
     * которая не откроется.
     *
     * @param unlockKey ключ покупки в наборе `lessonUnlocks` — `"<вид>:<урок>"`.
     * @param priceNolics цена, посчитанная той же функцией, что считает её сервер.
     */
    @Transaction
    suspend fun unlockLessonWithOutbox(
        uid: String,
        unlockKey: String,
        priceNolics: Long,
        outboxRow: OutboxEntity,
    ): LessonUnlockLocalResult {
        require(priceNolics >= 0L) { "priceNolics must be non-negative, was: $priceNolics" }
        // Профиля нет — списывать не с чего. Это не «нет сети»: намерения без баланса не бывает.
        val current = findByUid(uid) ?: return LessonUnlockLocalResult.NoProfile
        // Уже открыт: второй раз за ту же дверь не платят ни здесь, ни на сервере — там повтор
        // отвечает `charged: 0` по тому же набору. Второй записи очереди тоже не нужно.
        if (unlockKey in current.lessonUnlocks) return LessonUnlockLocalResult.AlreadyOpen
        if (current.nolics < priceNolics) {
            return LessonUnlockLocalResult.NotEnoughNolics(
                balanceNolics = current.nolics,
                priceNolics = priceNolics,
            )
        }
        // Карантинная строка под этим ключом держала бы его вечно: ключ выводится из тройки
        // «владелец, вид, урок», другим у нового намерения быть не может, а `IGNORE` на занятый
        // ключ вернул бы −1 и оставил бы списание без своей записи.
        val held = outboxRowState(outboxRow.mutationId)
        if (held != null && OutboxState.entries.firstOrNull { it.name == held }?.isTerminal == true) {
            deleteOutboxRow(outboxRow.mutationId)
        }
        upsert(
            current.copy(
                nolics = current.nolics - priceNolics,
                lessonUnlocks = current.lessonUnlocks + unlockKey,
            ),
        )
        val insertResult = enqueueOutboxRow(outboxRow)
        requireOutboxIntentQueued(insertResult, outboxRow.mutationId, outboxRowState(outboxRow.mutationId))
        return LessonUnlockLocalResult.Opened
    }

    /**
     * Откат локальной половины по карантину (AD-28): нолики возвращаются, урок закрывается.
     *
     * Идемпотентен по ключу покупки: если урока в наборе уже нет — откат состоялся раньше, и
     * второй возврат ноликов был бы выдачей денег из воздуха.
     *
     * @return состоялся ли откат — чтобы фича молчала, когда откатывать было нечего.
     */
    @Transaction
    suspend fun rollbackLessonUnlock(
        uid: String,
        unlockKey: String,
        refundNolics: Long,
    ): Boolean {
        require(refundNolics >= 0L) { "refundNolics must be non-negative, was: $refundNolics" }
        val current = findByUid(uid) ?: return false
        if (unlockKey !in current.lessonUnlocks) return false
        upsert(
            current.copy(
                nolics = current.nolics + refundNolics,
                lessonUnlocks = current.lessonUnlocks - unlockKey,
            ),
        )
        return true
    }
}

/**
 * Чем кончилась локальная половина разблокировки — до всякой отправки.
 *
 * Отдельный тип, а не `Boolean`: «открыл», «уже открыт» и «не хватает ноликов» ведут к трём
 * разным словам игроку, и последнее обязано отличаться от «нет сети» (история 5.3).
 */
sealed interface LessonUnlockLocalResult {
    /** Списано и открыто; запись очереди лежит рядом, в той же транзакции. */
    data object Opened : LessonUnlockLocalResult

    /** Урок уже в наборе купленного: ни второго списания, ни второй записи. */
    data object AlreadyOpen : LessonUnlockLocalResult

    /** Локального баланса не хватает. Записи в очереди нет — отправлять нечего. */
    data class NotEnoughNolics(
        val balanceNolics: Long,
        val priceNolics: Long,
    ) : LessonUnlockLocalResult

    /** Профиля этого аккаунта на устройстве нет: баланса, из которого списывать, не существует. */
    data object NoProfile : LessonUnlockLocalResult
}
