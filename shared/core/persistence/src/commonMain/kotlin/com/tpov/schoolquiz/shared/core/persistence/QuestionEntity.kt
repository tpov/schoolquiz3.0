package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tpov.schoolquiz.shared.core.question_schema.QuestionLanguageLevel

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["lessonId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val text: String,
    val payload: String,
    val language: String,
    val order: Int,
    val version: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
    @ColumnInfo(defaultValue = "1") val languageLevel: Int = QuestionLanguageLevel.DEFAULT,
)
