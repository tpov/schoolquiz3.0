package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentDao
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentWithQuestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReviewAssignmentLocalDataSource {
    fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignmentEntity>>

    suspend fun findAssignments(ownerUid: String): List<ReviewAssignmentEntity>

    fun observeAssignmentDetails(ownerUid: String): Flow<List<ReviewAssignmentWithQuestions>> =
        observeAssignments(ownerUid).map { assignments ->
            assignments.map { ReviewAssignmentWithQuestions(it, emptyList()) }
        }

    suspend fun findAssignmentDetails(ownerUid: String): List<ReviewAssignmentWithQuestions> =
        findAssignments(ownerUid).map { ReviewAssignmentWithQuestions(it, emptyList()) }

    suspend fun replaceAssignments(
        ownerUid: String,
        assignments: List<ReviewAssignmentEntity>,
    )

    suspend fun applyAssignmentChanges(
        ownerUid: String,
        changedIds: Set<String>,
        assignments: List<ReviewAssignmentEntity>,
    )

    suspend fun applyAssignmentDetailChanges(
        ownerUid: String,
        changedIds: Set<String>,
        assignments: List<ReviewAssignmentWithQuestions>,
    ) {
        applyAssignmentChanges(
            ownerUid = ownerUid,
            changedIds = changedIds,
            assignments = assignments.map { it.assignment },
        )
    }
}

class ReviewAssignmentLocalDataSourceImpl(
    private val dao: ReviewAssignmentDao,
) : ReviewAssignmentLocalDataSource {
    override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignmentEntity>> =
        dao.observeByOwner(ownerUid)

    override suspend fun findAssignments(ownerUid: String): List<ReviewAssignmentEntity> =
        dao.findByOwner(ownerUid)

    override fun observeAssignmentDetails(ownerUid: String): Flow<List<ReviewAssignmentWithQuestions>> =
        dao.observeDetailsByOwner(ownerUid)

    override suspend fun findAssignmentDetails(ownerUid: String): List<ReviewAssignmentWithQuestions> =
        dao.findDetailsByOwner(ownerUid)

    override suspend fun replaceAssignments(
        ownerUid: String,
        assignments: List<ReviewAssignmentEntity>,
    ) {
        dao.replaceForOwner(ownerUid, assignments)
    }

    override suspend fun applyAssignmentChanges(
        ownerUid: String,
        changedIds: Set<String>,
        assignments: List<ReviewAssignmentEntity>,
    ) {
        dao.applyChangesForOwner(
            ownerUid = ownerUid,
            changedIds = changedIds.toList(),
            assignments = assignments,
        )
    }

    override suspend fun applyAssignmentDetailChanges(
        ownerUid: String,
        changedIds: Set<String>,
        assignments: List<ReviewAssignmentWithQuestions>,
    ) {
        dao.applyDetailChangesForOwner(
            ownerUid = ownerUid,
            changedIds = changedIds.toList(),
            assignments = assignments.map { it.assignment },
            questions = assignments.flatMap { it.questions },
        )
    }
}
