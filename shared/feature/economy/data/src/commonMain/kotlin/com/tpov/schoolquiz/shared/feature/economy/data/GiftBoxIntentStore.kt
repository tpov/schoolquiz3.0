package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.persistence.OutboxDao
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class GiftBoxIntent(
    val markerId: Long,
    val ownerUid: String,
    val openingId: String,
    val mutationId: String,
    val entityRef: String,
)

interface GiftBoxIntentStore {
    suspend fun active(ownerUid: String): GiftBoxIntent?

    fun observeActive(ownerUid: String): Flow<GiftBoxIntent?>

    suspend fun reserve(
        ownerUid: String,
        openingId: String,
        nowMs: Long,
    ): GiftBoxIntent

    suspend fun acknowledge(intent: GiftBoxIntent): Boolean

    suspend fun requiresAttention(intent: GiftBoxIntent): Boolean
}

/** Two rows, one user intent: background delivery and a durable, never-sent presentation marker. */
class RoomGiftBoxIntentStore(private val dao: OutboxDao) : GiftBoxIntentStore {
    override suspend fun active(ownerUid: String) = dao.findSynchronousIntent(ownerUid, OPERATION)?.intent()

    override fun observeActive(ownerUid: String): Flow<GiftBoxIntent?> =
        dao.observeSynchronousIntent(ownerUid, OPERATION).map { it?.intent() }

    override suspend fun reserve(
        ownerUid: String,
        openingId: String,
        nowMs: Long,
    ): GiftBoxIntent {
        require(ownerUid.isNotBlank() && OPENING_ID.matches(openingId) && nowMs >= 0)
        val key = OutboxOperations.mutationKey(OPERATION, openingId)
        val delivery =
            OutboxEntity(
                mutationId = key,
                ownerUid = ownerUid,
                operation = OPERATION,
                payload =
                    buildJsonObject {
                        put("ownerUid", ownerUid)
                        put("openingId", openingId)
                    }.toString(),
                entityRef = entityRef(openingId),
                expectedVersion = null,
                state = "WAITING",
                attemptCount = 0,
                nextRetryAtMs = 0,
                lastError = null,
                createdAtMs = nowMs,
            )
        val marker = delivery.copy(mutationId = key + MARKER_SUFFIX, state = "SYNCHRONOUS")
        return dao.reserveSynchronousIntent(delivery, marker).intent()
    }

    override suspend fun acknowledge(intent: GiftBoxIntent) =
        dao.acknowledgeSynchronousIntent(intent.ownerUid, intent.markerId, intent.mutationId)

    override suspend fun requiresAttention(intent: GiftBoxIntent): Boolean {
        val delivery = dao.findByMutationId(intent.mutationId) ?: return true
        return delivery.ownerUid != intent.ownerUid || delivery.entityRef != intent.entityRef ||
            delivery.state !in setOf("WAITING", "WAITING_PRECONDITION")
    }

    private fun OutboxEntity.intent(): GiftBoxIntent {
        val body = Json.parseToJsonElement(payload).jsonObject
        val openingId = body.getValue("openingId").jsonPrimitive.content
        check(OPENING_ID.matches(openingId) && body.getValue("ownerUid").jsonPrimitive.content == ownerUid)
        val key = OutboxOperations.mutationKey(OPERATION, openingId)
        check(operation == OPERATION && state == "SYNCHRONOUS" && mutationId == key + MARKER_SUFFIX)
        check(entityRef == entityRef(openingId))
        return GiftBoxIntent(id, ownerUid, openingId, key, entityRef(openingId))
    }

    companion object {
        const val OPERATION = OutboxOperations.OPEN_GIFT_BOX
        private const val MARKER_SUFFIX = "-presentation"
        private val OPENING_ID = Regex("[A-Za-z0-9_-]{16,128}")

        fun entityRef(openingId: String) = "economy.BOX_OPENING:$openingId"
    }
}
