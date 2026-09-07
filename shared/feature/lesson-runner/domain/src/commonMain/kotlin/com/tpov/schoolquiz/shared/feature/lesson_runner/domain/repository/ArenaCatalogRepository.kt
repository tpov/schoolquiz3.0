package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest

/**
 * One explicit main-safe catalog read. No launch consent, caching, automatic pagination or writes.
 * Current owner/generation and structured errors remain the remote boundary's responsibility.
 * Cancellation propagates to the caller; a page is metadata, not a reserved entry or price.
 */
fun interface ArenaCatalogRepository {
    suspend fun list(request: ArenaLessonCatalogRequest): Result<ArenaLessonCatalogPage>
}
