package com.tpov.common.data.manager

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tpov.common.data.model.entity.StructureDataEntity

class Converters {
    @TypeConverter
    fun fromChildesList(value: List<StructureDataEntity?>?): String {
        return if (value == null) {
            "null"
        } else {
            Gson().toJson(value)
        }
    }

    @TypeConverter
    fun toChildesList(value: String): List<StructureDataEntity>? {
        return if (value == "null" || value.isBlank()) {
            null
        } else {
            try {
                val listType = object : TypeToken<List<StructureDataEntity>>() {}.type
                val result: List<StructureDataEntity>? = Gson().fromJson(value, listType)
                result
            } catch (e: JsonSyntaxException) {
                android.util.Log.e("Converters", "JSON Syntax Error parsing: ${value.take(200)}...", e)
                null
            } catch (e: Exception) {
                android.util.Log.e("Converters", "Error parsing JSON: ${value.take(200)}...", e)
                null
            }
        }
    }
    
    // 🔍 Конвертеры для векторов семантического поиска
    @TypeConverter
    fun fromFloatList(value: List<Float>?): String? {
        return if (value == null) {
            null
        } else {
            value.joinToString(",")
        }
    }

    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        return if (value == null || value.isBlank()) {
            null
        } else {
            try {
                value.split(",").map { it.toFloat() }
            } catch (e: NumberFormatException) {
                android.util.Log.e("Converters", "Error parsing float list: $value", e)
                null
            }
        }
    }
}
