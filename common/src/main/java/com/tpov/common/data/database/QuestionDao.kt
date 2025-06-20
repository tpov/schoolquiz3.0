package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tpov.common.data.model.entity.QuestionEntity

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(name: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionList(name: List<QuestionEntity>)

    @Query("SELECT * FROM question_entity")
    suspend fun getQuestionList(): List<QuestionEntity>

    @Query("""
    SELECT * FROM question_entity
    WHERE event = :event
      AND category = :category
      AND subCategory = :subCategory
      AND subsubCategory = :subsubCategory
      AND quiz = :quiz
""")
    fun getQuestionsByPath(
        event: String,
        category: String,
        subCategory: String,
        subsubCategory: String,
        quiz: String
    ): List<QuestionEntity>
    @Query("DELETE FROM question_entity WHERE quiz IS :id")
    fun deleteQuestionByIdQuiz(id: Int)

    @Query("""
    DELETE FROM question_entity
    WHERE event = :event
      AND category = :category
      AND subCategory = :subCategory
      AND subsubCategory = :subsubCategory
      AND quiz = :quiz
""")
    fun deleteQuestion(event: String,
                       category: String,
                       subCategory: String,
                       subsubCategory: String,
                       quiz: String)

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM question_entity")
    fun getQuestionCount(): Int
}
