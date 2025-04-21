package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tpov.common.data.model.local.QuestionEntity

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestion(name: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionList(name: List<QuestionEntity>)

    @Query("SELECT * FROM question_entity")
    suspend fun getQuestionList(): List<QuestionEntity>

    @Query("""
    SELECT * FROM question_entity 
    WHERE idEvent = :idEvent
      AND idCategory = :idCategory
      AND idSubCategory = :idSubCategory
      AND idSubsubCategory = :idSubsubCategory
      AND idQuiz = :idQuiz
""")
    fun getQuestionsByPath(
        idEvent: Int,
        idCategory: Int,
        idSubCategory: Int,
        idSubsubCategory: Int,
        idQuiz: Int
    ): List<QuestionEntity>
    @Query("DELETE FROM question_entity WHERE idQuiz IS :id")
    fun deleteQuestionByIdQuiz(id: Int)

    @Query("""
    DELETE FROM question_entity 
    WHERE idEvent = :idEvent
      AND idCategory = :idCategory
      AND idSubCategory = :idSubCategory
      AND idSubsubCategory = :idSubsubCategory
      AND idQuiz = :idQuiz
""")
    fun deleteQuestion(idEvent: Int,
                       idCategory: Int,
                       idSubCategory: Int,
                       idSubsubCategory: Int,
                       idQuiz: Int)

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM question_entity")
    fun getQuestionCount(): Int
}