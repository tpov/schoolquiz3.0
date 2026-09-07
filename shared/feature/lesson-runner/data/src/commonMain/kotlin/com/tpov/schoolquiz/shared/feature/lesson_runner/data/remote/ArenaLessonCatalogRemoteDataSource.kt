package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest

/** Single read only; no owner filter, automatic pagination/retry, anonymous fallback, cache or financial writes. */
fun interface ArenaLessonCatalogRemoteDataSource {
    suspend fun list(request: ArenaLessonCatalogRequest): Result<ArenaLessonCatalogPage>
}
