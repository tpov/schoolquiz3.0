package com.tpov.schoolquiz.platform.firebase.util

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

internal fun DocumentSnapshot.longField(field: String): Long? =
    when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

internal fun DocumentSnapshot.intField(field: String): Int? =
    longField(field)?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()

internal fun DocumentSnapshot.doubleField(field: String): Double? =
    when (val value = get(field)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

internal fun DocumentSnapshot.booleanField(field: String): Boolean? =
    when (val value = get(field)) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        is Number -> value.toInt() != 0
        else -> null
    }

internal fun DocumentSnapshot.millisField(field: String): Long? =
    longField(field)
