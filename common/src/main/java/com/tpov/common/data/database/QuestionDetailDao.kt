package com.tpov.common.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tpov.common.data.model.local.QuestionDetailEntity

@Dao
interface QuestionDetailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertQuestionDetail(note: QuestionDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestionDetailList(note: List<QuestionDetailEntity>)

    @Query("SELECT * FROM question_detail_entity")
    fun getQuestionDetailList(): List<QuestionDetailEntity>

    @Query("SELECT * FROM question_detail_entity WHERE quiz LIKE :nameQuiz")
    fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity>

    @Query("""
    SELECT * FROM question_detail_entity
    WHERE event = :event
      AND category = :category
      AND subCategory = :subCategory
      AND subsubCategory = :subsubCategory
      AND quiz = :quiz
""")
    suspend fun getQuestionDetailByPath(
        event: String,
        category: String,
        subCategory: String,
        subsubCategory: String,
        quiz: String
    ): List<QuestionDetailEntity>

    @Query("SELECT * FROM question_detail_entity WHERE id IS :id")
    fun getQuestionDetail(id: Int): QuestionDetailEntity

    @Query("""
    DELETE FROM question_detail_entity
    WHERE event = :event
      AND category = :category
      AND subCategory = :subCategory
      AND subsubCategory = :subsubCategory
      AND quiz = :quiz
""")
    fun deleteQuestionDetailByPath(
        event:String,
        category:String,
        subCategory:String,
        subsubCategory:String,
        quiz:String
    )

    @Query("DELETE FROM question_detail_entity WHERE quiz = :id AND synth = :synth")
    fun deleteQuestionDetailByPathAndSynth(id: Int, synth: Boolean = true)

    @Query("DELETE FROM question_detail_entity WHERE id IS :id")
    fun deleteQuestionDetail(id: Int)

    @Update
    fun updateQuizDetail(questionDetailEntity: QuestionDetailEntity)

    @Query("SELECT COUNT(*) FROM question_detail_entity")
    fun getQuestionDetailCount(): Int
}
