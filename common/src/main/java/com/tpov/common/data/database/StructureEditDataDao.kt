package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tpov.common.data.model.remote.StructureEditData

@Dao
interface StructureEditDataDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertStructureEditData(structureData: StructureEditData)

        @Query("SELECT * FROM StructureEditData") // Corrected table name based on schema
        suspend fun getAllStructureEditData(): List<StructureEditData>

        @Query("DELETE FROM StructureEditData") // Added method to clear the table
        suspend fun clearAllStructureEditData()
}