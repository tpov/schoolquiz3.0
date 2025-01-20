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

    @Query("SELECT * FROM question_detail_entity WHERE idQuiz LIKE :nameQuiz")
    fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity>

    @Query("""
    SELECT * FROM question_detail_entity 
    WHERE idEvent = :idEvent 
      AND idCategory = :idCategory 
      AND idSubCategory = :idSubCategory 
      AND idSubsubCategory = :idSubsubCategory 
      AND idQuiz = :idQuiz
""")
    suspend fun getQuestionDetailByPath(
        idEvent: Int,
        idCategory: Int,
        idSubCategory: Int,
        idSubsubCategory: Int,
        idQuiz: Int
    ): List<QuestionDetailEntity>

    @Query("SELECT * FROM question_detail_entity WHERE id IS :id")
    fun getQuestionDetail(id: Int): QuestionDetailEntity

    @Query("""
    DELETE FROM question_detail_entity 
    WHERE idEvent = :idEvent 
      AND idCategory = :idCategory 
      AND idSubCategory = :idSubCategory 
      AND idSubsubCategory = :idSubsubCategory 
      AND idQuiz = :idQuiz
""")
    fun deleteQuestionDetailByPath(
        idEvent: Int,
        idCategory: Int,
        idSubCategory: Int,
        idSubsubCategory: Int,
        idQuiz: Int
    )

    @Query("DELETE FROM question_detail_entity WHERE idQuiz = :id AND synth = :synth")
    fun deleteQuestionDetailByPathAndSynth(id: Int, synth: Boolean = true)

    @Query("DELETE FROM question_detail_entity WHERE id IS :id")
    fun deleteQuestionDetail(id: Int)

    @Update
    fun updateQuizDetail(questionDetailEntity: QuestionDetailEntity)

    @Query("SELECT COUNT(*) FROM question_detail_entity")
    fun getQuestionDetailCount(): Int
}