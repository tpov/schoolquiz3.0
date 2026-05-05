package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Dao
interface ReviewAssignmentDao {
    @Query("SELECT * FROM review_assignments WHERE ownerUid = :ownerUid ORDER BY createdAtMs ASC")
    fun observeByOwner(ownerUid: String): Flow<List<ReviewAssignmentEntity>>

    @Query("SELECT * FROM review_assignments WHERE ownerUid = :ownerUid ORDER BY createdAtMs ASC")
    suspend fun findByOwner(ownerUid: String): List<ReviewAssignmentEntity>

    @Query("SELECT * FROM review_assignment_questions WHERE ownerUid = :ownerUid ORDER BY language ASC, `order` ASC")
    fun observeQuestionsByOwner(ownerUid: String): Flow<List<ReviewAssignmentQuestionEntity>>

    @Query("SELECT * FROM review_assignment_questions WHERE ownerUid = :ownerUid ORDER BY language ASC, `order` ASC")
    suspend fun findQuestionsByOwner(ownerUid: String): List<ReviewAssignmentQuestionEntity>

    fun observeDetailsByOwner(ownerUid: String): Flow<List<ReviewAssignmentWithQuestions>> =
        combine(
            observeByOwner(ownerUid),
            observeQuestionsByOwner(ownerUid),
        ) { assignments, questions ->
            assignments.withQuestions(questions)
        }

    suspend fun findDetailsByOwner(ownerUid: String): List<ReviewAssignmentWithQuestions> =
        findByOwner(ownerUid).withQuestions(findQuestionsByOwner(ownerUid))

    @Query("DELETE FROM review_assignments WHERE ownerUid = :ownerUid")
    suspend fun deleteByOwner(ownerUid: String)

    @Query("DELETE FROM review_assignment_questions WHERE ownerUid = :ownerUid")
    suspend fun deleteQuestionsByOwner(ownerUid: String)

    @Query("DELETE FROM review_assignments WHERE ownerUid = :ownerUid AND id IN (:ids)")
    suspend fun deleteByOwnerAndIds(
        ownerUid: String,
        ids: List<String>,
    )

    @Query("DELETE FROM review_assignment_questions WHERE ownerUid = :ownerUid AND assignmentId IN (:ids)")
    suspend fun deleteQuestionsByOwnerAndAssignmentIds(
        ownerUid: String,
        ids: List<String>,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignments: List<ReviewAssignmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<ReviewAssignmentQuestionEntity>)

    @Transaction
    suspend fun replaceForOwner(
        ownerUid: String,
        assignments: List<ReviewAssignmentEntity>,
    ) {
        deleteByOwner(ownerUid)
        deleteQuestionsByOwner(ownerUid)
        if (assignments.isNotEmpty()) insertAll(assignments)
    }

    @Transaction
    suspend fun applyChangesForOwner(
        ownerUid: String,
        changedIds: List<String>,
        assignments: List<ReviewAssignmentEntity>,
    ) {
        if (changedIds.isNotEmpty()) deleteByOwnerAndIds(ownerUid, changedIds)
        if (assignments.isNotEmpty()) insertAll(assignments)
    }

    @Transaction
    suspend fun applyDetailChangesForOwner(
        ownerUid: String,
        changedIds: List<String>,
        assignments: List<ReviewAssignmentEntity>,
        questions: List<ReviewAssignmentQuestionEntity>,
    ) {
        if (changedIds.isNotEmpty()) {
            deleteByOwnerAndIds(ownerUid, changedIds)
            deleteQuestionsByOwnerAndAssignmentIds(ownerUid, changedIds)
        }
        if (assignments.isNotEmpty()) insertAll(assignments)
        if (questions.isNotEmpty()) insertQuestions(questions)
    }

    private fun List<ReviewAssignmentEntity>.withQuestions(
        questions: List<ReviewAssignmentQuestionEntity>,
    ): List<ReviewAssignmentWithQuestions> {
        val questionsByAssignment = questions.groupBy { it.assignmentId }
        return map { assignment ->
            ReviewAssignmentWithQuestions(
                assignment = assignment,
                questions = questionsByAssignment[assignment.id].orEmpty(),
            )
        }
    }
}
