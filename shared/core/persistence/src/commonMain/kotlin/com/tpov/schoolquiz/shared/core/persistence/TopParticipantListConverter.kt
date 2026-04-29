package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@ProvidedTypeConverter
class TopParticipantListConverter {

    @TypeConverter
    fun toDb(list: List<TopParticipant>): String = Json.encodeToString(list)

    @TypeConverter
    fun fromDb(json: String): List<TopParticipant> = try {
        Json.decodeFromString(json)
    } catch (e: Exception) {
        emptyList()
    }
}
