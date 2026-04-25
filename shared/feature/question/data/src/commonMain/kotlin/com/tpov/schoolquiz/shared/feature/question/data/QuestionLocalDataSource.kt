package com.tpov.schoolquiz.shared.feature.question.data

import com.tpov.schoolquiz.shared.core.persistence.QuestionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionEntity
import kotlinx.coroutines.flow.Flow

interface QuestionLocalDataSource {
    fun observeByLesson(lessonId: String): Flow<List<QuestionEntity>>
    suspend fun upsertByIdIfNewerVersion(entity: QuestionEntity)
    suspend fun deleteById(id: String)
    suspend fun findById(id: String): QuestionEntity?
}

class QuestionLocalDataSourceImpl(
    private val dao: QuestionDao,
) : QuestionLocalDataSource {
    override fun observeByLesson(lessonId: String): Flow<List<QuestionEntity>> =
        dao.observeByLesson(lessonId)

    override suspend fun upsertByIdIfNewerVersion(entity: QuestionEntity) =
        dao.upsertByIdIfNewerVersion(entity)

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun findById(id: String): QuestionEntity? = dao.findById(id)
}
