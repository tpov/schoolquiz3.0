package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MutationReceiptDao {
    @Query("SELECT * FROM mutation_receipts WHERE owner_uid = :ownerUid AND mutation_id = :mutationId")
    suspend fun find(
        ownerUid: String,
        mutationId: String,
    ): MutationReceiptEntity?

    @Query(
        "SELECT * FROM mutation_receipts WHERE owner_uid = :ownerUid AND entity_ref = :entityRef " +
            "ORDER BY received_at_ms, mutation_id",
    )
    fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<List<MutationReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(receipt: MutationReceiptEntity): Long

    @Transaction
    suspend fun persist(receipt: MutationReceiptEntity) {
        insertIfAbsent(receipt)
        val stored = checkNotNull(find(receipt.ownerUid, receipt.mutationId))
        // A retry gets a later local arrival time, but cannot replace the server's first result.
        check(
            stored.operation == receipt.operation && stored.entityRef == receipt.entityRef &&
                stored.payloadJson == receipt.payloadJson,
        ) { "Conflicting permanent mutation receipt" }
    }
}
