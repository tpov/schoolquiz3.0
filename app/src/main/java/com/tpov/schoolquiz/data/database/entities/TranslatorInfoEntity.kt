package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translator_info")
data class TranslatorInfoEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int?,

    @ColumnInfo(name = "oldText")
    val oldText: String,
    
    @ColumnInfo(name = "newText")
    val newText: String,

    @ColumnInfo(name = "idQuiz")
    val idQuiz: Int,

    @ColumnInfo(name = "numQuestion")
    val numQuestion: Int,

    @ColumnInfo(name = "idThisTranslator")
    val idThisTranslator: Int,

    @ColumnInfo(name = "idLastTranslator")
    val idLastTranslator: Int,

    @ColumnInfo(name = "language")
    val language: Int,

    @ColumnInfo(name = "lvlTranslator")
    val lvlTranslator: Int,

    @ColumnInfo(name = "rating")
    val rating: Int,
) {
    constructor() : this(
        0, "", "", 0, 0, 0, 0, "", 0, 0
    )
}
