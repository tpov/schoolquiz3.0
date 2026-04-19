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
    WHERE eventName = :eventName
      AND categoryName = :categoryName
      AND subCategoryName = :subCategoryName
      AND subsubCategoryName = :subsubCategoryName
      AND quizName = :quizName
""")
    fun getQuestionsByPath(
        eventName: String,
        categoryName: String,
        subCategoryName: String,
        subsubCategoryName: String,
        quizName: String
    ): List<QuestionEntity>
    @Query("DELETE FROM question_entity WHERE quizName IS :id")
    fun deleteQuestionByIdQuiz(id: Int)

    @Query("""
    DELETE FROM question_entity
    WHERE eventName = :eventName
      AND categoryName = :categoryName
      AND subCategoryName = :subCategoryName
      AND subsubCategoryName = :subsubCategoryName
      AND quizName = :quizName
""")
    fun deleteQuestion(eventName: String,
                       categoryName: String,
                       subCategoryName: String,
                       subsubCategoryName: String,
                       quizName: String)

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM question_entity")
    fun getQuestionCount(): Int
}
