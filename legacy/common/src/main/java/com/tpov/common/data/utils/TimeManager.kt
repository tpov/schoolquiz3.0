package com.tpov.common.data.utils

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.tpov.common.DEFAULT_DATA_IN_GET_CHAT
import com.tpov.common.DEFAULT_DATA_IN_SHOW_AD
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

object TimeManager {

    private const val DEF_TIME_FORMAT = DEFAULT_DATA_IN_GET_CHAT
    fun getCurrentTime(day: Boolean = false): String{
        Log.d("NewNoteActivity", "getCurrentTime")

        val formatter = SimpleDateFormat(if (!day) DEFAULT_DATA_IN_GET_CHAT else DEFAULT_DATA_IN_SHOW_AD, Locale.getDefault())
        return formatter.format(Calendar.getInstance().time)
    }

    fun getTimeFormat(time: String, defPreferences: SharedPreferences): String {
        val defFormatter = SimpleDateFormat(DEF_TIME_FORMAT, Locale.getDefault())
        val defDate = defFormatter.parse(time)
        val newFormat = defPreferences.getString("time_format_key", DEF_TIME_FORMAT)
        val newFormatter = SimpleDateFormat(newFormat, Locale.getDefault())
        return if (defDate != null) {
            newFormatter.format(defDate)
        } else {
            time
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getDaysBetweenDates(dateString1: String, dateString2: String): Long {
        val formatter = SimpleDateFormat(DEF_TIME_FORMAT, Locale.getDefault())

        return try {
            // Преобразуйте строки даты в объекты Date
            val date1 = formatter.parse(dateString1)
            val date2 = formatter.parse(dateString2)

            // Преобразуйте объекты Date в объекты LocalDate
            val localDate1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val localDate2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

            // Вычислите разницу между двумя объектами LocalDate
            val daysBetween = ChronoUnit.DAYS.between(localDate1, localDate2)
            Log.d("daysBetween","daysBetween: $daysBetween")
            // Верните абсолютное значение разницы в днях
            abs(daysBetween)
        } catch (e: ParseException) {
            0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getSecondBetweenDates(dateString1: String, dateString2: String): Long? {
        val formatter = SimpleDateFormat(DEF_TIME_FORMAT, Locale.getDefault())

        return try {
            val date1 = formatter.parse(dateString1)
            val date2 = formatter.parse(dateString2)

            val millisecondsDiff = abs( date2.time - date1.time )
            millisecondsDiff / 1000
        } catch (e: ParseException) {
            0
        }
    }
}