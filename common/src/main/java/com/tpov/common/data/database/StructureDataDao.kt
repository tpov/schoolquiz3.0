package com.tpov.common.data.database

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
    ): StructureDataLocal? {
        val rootStructure = getStructureDataByEventId(eventId)?.toStructureDataLocal()

        if (rootStructure == null || rootStructure.childes?.isEmpty() == true) {
            return null
        }
        var currentCategory: StructureDataLocal =
            if (path.isNotEmpty() && path[0] != -1) {
                if (path[0] >= rootStructure.childes?.size!!) return null
                rootStructure
            } else {
                return rootStructure.childes?.find { it.id == path.first() }
            }

        for (i in 1 until path.size) {
            val index = path[i]

            if (index == -1) {
                return currentCategory
            }
            if (currentCategory.childes.isNullOrEmpty() || index >= currentCategory.childes!!.size) {
                return null
            }
            currentCategory = currentCategory.childes!!.find { it.id == index }!!
        }
        return currentCategory
    }

    @Query("SELECT * FROM structure_data WHERE id = :eventId")
    suspend fun getStructureDataByEventId(eventId: Int): StructureDataEntity?

    @Query("DELETE FROM structure_data")
    suspend fun deleteAllStructureData()

    @Query("SELECT * FROM structure_data")
    suspend fun getAllStructureData(): List<StructureDataEntity>
}