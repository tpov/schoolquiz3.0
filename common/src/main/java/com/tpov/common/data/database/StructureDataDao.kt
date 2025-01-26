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
    ): StructureDataLocal? {
        Log.d("initStructureData", "getStructureDataByPath: eventId=$eventId, path=$path")

        // Получаем корневую структуру
        val rootStructure = getStructureDataByEventId(eventId)?.toStructureDataLocal()
        Log.d("initStructureData", "rootStructure: $rootStructure")

        // Проверяем корневую структуру
        if (rootStructure == null || rootStructure.childes.isNullOrEmpty()) {
            Log.d("initStructureData", "Root structure is null or empty")
            return null
        }

        // Проверяем путь
        if (path.isEmpty()) {
            Log.d("initStructureData", "Path is empty, returning root")
            return rootStructure
        }

        // Получаем текущую категорию
        var currentCategory = if (path[0] != -1) {
            if (path[0] >= rootStructure.childes?.size!!) {
                Log.d("initStructureData", "Path[0] out of bounds")
                return null
            }
            rootStructure
        } else {
            // Безопасно ищем первый элемент по ID
            rootStructure.childes?.find { it?.id == path.firstOrNull() } ?: run {
                Log.d("initStructureData", "Could not find first element")
                return null
            }
        }

        // Проходим по пути
        for (i in 1 until path.size) {
            val index = path[i]
            Log.d("initStructureData", "Processing index $i: $index")

            if (index == -1) {
                return currentCategory
            }

            if (currentCategory.childes.isNullOrEmpty()) {
                Log.d("initStructureData", "Current category has no children")
                return null
            }

            // Безопасный поиск следующей категории
            currentCategory = currentCategory.childes?.find { it?.id == index } ?: run {
                Log.d("initStructureData", "Could not find category with id $index")
                return null
            }
        }

        Log.d("initStructureData", "Final category: $currentCategory")
        return currentCategory
    }

    @Query("SELECT * FROM structure_data WHERE id = :eventId")
    suspend fun getStructureDataByEventId(eventId: Int): StructureDataEntity?

    @Query("DELETE FROM structure_data")
    suspend fun deleteAllStructureData()

    @Query("SELECT * FROM structure_data")
    suspend fun getAllStructureData(): List<StructureDataEntity>
}