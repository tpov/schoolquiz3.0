package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository

/** The main-safe remote already owns its dispatcher and Auth guards; do not duplicate policy here. */
class ArenaCatalogRepositoryImpl(private val remote: ArenaLessonCatalogRemoteDataSource) : ArenaCatalogRepository {
    override suspend fun list(request: ArenaLessonCatalogRequest): Result<ArenaLessonCatalogPage> = remote.list(request)
}
