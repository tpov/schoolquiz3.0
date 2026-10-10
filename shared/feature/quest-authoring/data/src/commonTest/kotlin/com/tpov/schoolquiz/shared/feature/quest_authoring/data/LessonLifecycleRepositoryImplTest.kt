package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonLifecycleWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonLifecycleRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Репозиторий TZ06B проецирует успех и оборачивает отказ конверта в [SyncFailure]; транспортный
 * отказ ([SyncFailure] от платформы) проходит насквозь без перепревязки.
 */
class LessonLifecycleRepositoryImplTest {
    private class ThrowingRemote : LessonLifecycleRemoteDataSource {
        override suspend fun listOwned(
            afterLessonId: String?,
            pageSize: Int,
        ): Nothing = throw IllegalStateException("malformed envelope")

        override suspend fun listCurator(
            afterLessonId: String?,
            pageSize: Int,
        ): Nothing = throw SyncFailure(SyncError.NoNetwork)

        override suspend fun listHistory(
            lessonId: String,
            cursor: String?,
            pageSize: Int,
        ): Nothing = throw UnsupportedOperationException()

        override suspend fun listRoyalty(
            cursor: String?,
            pageSize: Int,
        ): Nothing = throw UnsupportedOperationException()

        override suspend fun transition(
            command: LessonTransitionCommand,
            mutationId: String,
        ): Nothing = throw UnsupportedOperationException()
    }

    @Test
    fun `decode failure is wrapped as unknown sync failure`() =
        runTest {
            val result = LessonLifecycleRepositoryImpl(ThrowingRemote()).listOwned("uid-a", null, 20)

            assertTrue(result.isFailure)
            assertTrue(result.syncErrorOrNull() is SyncError.Unknown)
        }

    @Test
    fun `transport failure passes through without rewrapping`() =
        runTest {
            val result = LessonLifecycleRepositoryImpl(ThrowingRemote()).listCurator("uid-a", null, 20)

            assertTrue(result.isFailure)
            assertEquals(SyncError.NoNetwork, result.syncErrorOrNull())
        }

    @Test
    fun `transition parses an in-band rejection as a decision, not a failure`() =
        runTest {
            val remote =
                object : LessonLifecycleRemoteDataSource {
                    override suspend fun listOwned(afterLessonId: String?, pageSize: Int) = throw UnsupportedOperationException()

                    override suspend fun listCurator(afterLessonId: String?, pageSize: Int) = throw UnsupportedOperationException()

                    override suspend fun listHistory(lessonId: String, cursor: String?, pageSize: Int) = throw UnsupportedOperationException()

                    override suspend fun listRoyalty(cursor: String?, pageSize: Int) = throw UnsupportedOperationException()

                    override suspend fun transition(
                        command: LessonTransitionCommand,
                        mutationId: String,
                    ) = LessonLifecycleWireParser.transitionDecision(
                        mapOf(
                            "schemaVersion" to 1L,
                            "mutationId" to mutationId,
                            "decidedAtMs" to 1L,
                            "status" to "REJECTED",
                            "result" to mapOf("code" to "REVIEW_APPROVAL_REQUIRED"),
                        ),
                        command,
                    )
                }

            val result =
                LessonLifecycleRepositoryImpl(remote)
                    .transition("uid-a", LessonTransitionCommand("lesson-1", LessonShelf.ARENA, "admit"), "mutation-9")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is LessonTransitionDecision.Rejected)
        }
}
