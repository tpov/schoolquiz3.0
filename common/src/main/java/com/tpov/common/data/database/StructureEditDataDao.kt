package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.tpov.common.data.model.remote.StructureEditData

@Dao
interface StructureEditDataDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertStructureEditData(structureData: StructureEditData)
}