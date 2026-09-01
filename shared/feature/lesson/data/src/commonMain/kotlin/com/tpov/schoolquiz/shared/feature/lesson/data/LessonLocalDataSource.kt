package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import kotlinx.coroutines.flow.Flow

interface LessonLocalDataSource {
    fun observeByTheme(themeId: String): Flow<List<LessonEntity>>
    suspend fun upsertByIdIfNewerVersion(entity: LessonEntity)
    suspend fun upsertFromSyncList(entity: LessonEntity) = upsertByIdIfNewerVersion(entity)
    suspend fun deleteById(id: String)
    suspend fun findById(id: String): LessonEntity?
}

class LessonLocalDataSourceImpl(
    private val dao: LessonDao,
) : LessonLocalDataSource {
    override fun observeByTheme(themeId: String): Flow<List<LessonEntity>> =
        dao.observeByTheme(themeId)

    override suspend fun upsertByIdIfNewerVersion(entity: LessonEntity) =
        dao.upsertByIdIfNewerVersion(entity)

    override suspend fun upsertFromSyncList(entity: LessonEntity) =
        dao.upsertFromSyncList(entity)

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun findById(id: String): LessonEntity? = dao.findById(id)
}
