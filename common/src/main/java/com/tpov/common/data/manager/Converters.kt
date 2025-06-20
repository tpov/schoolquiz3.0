package com.tpov.common.data.manager

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tpov.common.data.model.entity.StructureDataEntity

class Converters {
    @TypeConverter
    fun fromChildesList(value: List<StructureDataEntity?>?): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toChildesList(value: String): List<StructureDataEntity>? {
        val listType = object : TypeToken<List<StructureDataEntity>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
