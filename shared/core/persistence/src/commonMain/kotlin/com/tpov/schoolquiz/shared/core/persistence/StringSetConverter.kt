package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter

@ProvidedTypeConverter
class StringSetConverter {

    @TypeConverter
    fun fromSet(value: Set<String>?): String? {
        require(value?.none { '\u001F' in it } != false) {
            "Set elements must not contain the unit separator (U+001F)"
        }
        return value?.joinToString(separator = "\u001F")
    }

    @TypeConverter
    fun toSet(value: String?): Set<String>? =
        value?.split("\u001F")?.filter { it.isNotEmpty() && '\u001F' !in it }?.toSet()

    @TypeConverter
    fun fromList(value: List<String>): String {
        require(value.none { '\u001F' in it }) {
            "List elements must not contain the unit separator (U+001F)"
        }
        return value.joinToString(separator = "\u001F")
    }

    @TypeConverter
    fun toList(value: String?): List<String> =
        value?.split("\u001F")?.filter { it.isNotEmpty() && '\u001F' !in it } ?: emptyList()
}
