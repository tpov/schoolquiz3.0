package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.persistence.GiftClaimIntentDao
import com.tpov.schoolquiz.shared.core.persistence.GiftClaimIntentEntity
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Намерение заявки, лежащее на устройстве, — ровно то, что уедет повтором.
 *
 * Строка создаётся до запроса и живёт до ответа; умерший процесс находит её же и отправляет тот
 * же operationId с тем же телом, поэтому сервер отвечает сохранённым результатом, а не вторым
 * начислением.
 */
data class GiftClaimIntent(
    val ownerUid: String,
    val kind: GiftClaimKind,
    val operationId: String,
    val payloadJson: String,
    val createdAtMs: Long,
)

interface GiftClaimIntentStore {
    suspend fun active(
        ownerUid: String,
        kind: GiftClaimKind,
    ): GiftClaimIntent?

    fun observeActive(
        ownerUid: String,
        kind: GiftClaimKind,
    ): Flow<GiftClaimIntent?>

    /**
     * Кладёт намерение до запроса. Занятое место того же вида не оспаривается: возвращается
     * стоявшее — второй тап не создаёт вторую операцию.
     */
    suspend fun reserve(
        ownerUid: String,
        kind: GiftClaimKind,
        operationId: String,
        payloadJson: String,
        nowMs: Long,
    ): GiftClaimIntent

    /** Разрешает строку любого исхода — и удачного, и DAILY_LIMIT — но только по своему ключу. */
    suspend fun resolve(intent: GiftClaimIntent): Boolean
}

class RoomGiftClaimIntentStore(
    private val dao: GiftClaimIntentDao,
) : GiftClaimIntentStore {
    override suspend fun active(
        ownerUid: String,
        kind: GiftClaimKind,
    ) = dao.find(ownerUid, kind.name)?.intent()

    override fun observeActive(
        ownerUid: String,
        kind: GiftClaimKind,
    ): Flow<GiftClaimIntent?> = dao.observe(ownerUid, kind.name).map { it?.intent() }

    override suspend fun reserve(
        ownerUid: String,
        kind: GiftClaimKind,
        operationId: String,
        payloadJson: String,
        nowMs: Long,
    ): GiftClaimIntent {
        require(ownerUid.isNotBlank() && OPERATION_ID.matches(operationId)) {
            "A gift claim intent needs an owner and a persistent operation id"
        }
        active(ownerUid, kind)?.let { return it }
        dao.insert(GiftClaimIntentEntity(ownerUid, kind.name, operationId, payloadJson, nowMs))
        return checkNotNull(active(ownerUid, kind)) { "The intent row disappeared the moment it was written" }
    }

    override suspend fun resolve(intent: GiftClaimIntent): Boolean =
        dao.resolve(intent.ownerUid, intent.kind.name, intent.operationId) == 1

    private fun GiftClaimIntentEntity.intent(): GiftClaimIntent {
        val kind = kindOf(this.kind)
        check(ownerUid.isNotBlank() && OPERATION_ID.matches(operationId))
        check(payload.isNotBlank() && createdAtMs >= 0)
        return GiftClaimIntent(ownerUid, kind, operationId, payload, createdAtMs)
    }

    companion object {
        /** Ровно тот же алфавит, что требует сервер у `payload.operationId`. */
        val OPERATION_ID = Regex("[A-Za-z0-9_-]{16,128}")

        /** Незнакомое имя вида — испорченная строка, а не новый вид: молча рождать виды нельзя. */
        fun kindOf(name: String): GiftClaimKind =
            GiftClaimKind.entries.firstOrNull { it.name == name }
                ?: throw GiftClaimException(
                    GiftClaimFailureReason.ENVELOPE_INVALID,
                    operationId = null,
                    cause = IllegalStateException("Unknown gift claim kind $name"),
                )
    }
}
