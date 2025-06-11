package com.tpov.common.data.database

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tpov.common.data.model.entity.StructureDataEntity
import com.tpov.common.data.model.local.StructureDataLocal

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
        event:String,
        path: List<String>
    ): List<StructureDataLocal>? {
        var structure = getStructureEventData(event)?.toStructureCategoryListLocal()

        if (structure == null) {
            Log.d("initStructureData", "Root structure is null or empty")
            return null
        } else {
            path.forEach { path ->
                if (path != "") structure = structure!!.find { it.nameItem == path }?.children
            }
        }

        return structure
    }

    @Query("SELECT * FROM structure_data WHERE nameItem = :event")
    suspend fun getStructureEventData(event: String): StructureDataEntity?

    @Query("DELETE FROM structure_data")
    suspend fun deleteAllStructureData()

    @Query("SELECT * FROM structure_data")
    suspend fun getAllStructureData(): List<StructureDataEntity>
}
