package com.tpov.common.data.database

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.domain.model.StructureDataLocal

@Dao
interface StructureDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStructureData(structureData: StructureDataEntity)

    @Update
    suspend fun updateStructureData(
        structureData: StructureDataEntity
    )

    @Transaction
    suspend fun getStructureDataByPath(
        eventId: Int,
        path: List<Int>
    ): List<StructureDataLocal>? {
        var structure = getStructureEventData(eventId)?.toStructureCategoryListLocal()

        if (structure == null) {
            Log.d("initStructureData", "Root structure is null or empty")
            return null
        } else {
            path.forEach { path ->
                if (path != -1) structure = structure!!.find { it.id == path }?.children
            }
        }

        return structure
    }

    @Query("SELECT * FROM structure_data WHERE id = :eventId")
    suspend fun getStructureEventData(eventId: Int): StructureDataEntity?

    @Query("DELETE FROM structure_data")
    suspend fun deleteAllStructureData()

    @Query("SELECT * FROM structure_data")
    suspend fun getAllStructureData(): List<StructureDataEntity>
}