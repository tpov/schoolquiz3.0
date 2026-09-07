package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArenaCatalogRepositoryImplTest {
    private val fixtures =
        requireNotNull(javaClass.classLoader.getResourceAsStream("arena-catalog-client-fixtures.json"))
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val request = ArenaLessonCatalogRequest("player", pageSize = PAGE_SIZE)
    private val parser = ArenaLessonCatalogWireParser()
    private val first = parser.parse(fixtures.getValue("first").toString(), request)

    @Test
    fun constructorHasNoReadAndSingleRequestForwardsExactArgumentAndPage() =
        runTest {
            val fake = FakeRemote(Result.success(first))
            val repository = ArenaCatalogRepositoryImpl(fake)
            assertTrue(fake.calls.isEmpty())
            assertSame(first, repository.list(request).getOrThrow())
            assertEquals(1, fake.calls.size)
            assertSame(request, fake.calls.single())
            assertEquals("player", fake.calls.single().ownerUid)
            assertEquals(PAGE_SIZE, fake.calls.single().pageSize)
            // There is a next cursor, but this repository must not auto-fetch it.
            assertTrue(first.nextCursor != null)
            assertEquals(1, fake.calls.size)
        }

    @Test
    fun explicitContinuationAndRepeatedReadsAreNeitherRewrittenNorCached() =
        runTest {
            val next = request.copy(cursor = first.nextCursor)
            val second = parser.parse(fixtures.getValue("second").toString(), next)
            val fake = FakeRemote(Result.success(second))
            val repository = ArenaCatalogRepositoryImpl(fake)
            assertSame(second, repository.list(next).getOrThrow())
            assertSame(second, repository.list(next).getOrThrow())
            assertEquals(2, fake.calls.size)
            fake.calls.forEach { assertSame(next, it) }
            assertSame(first.nextCursor, fake.calls.first().cursor)
        }

    @Test
    fun typedRemoteFailureIsPreservedWithoutRetryOrRemapping() =
        runTest {
            val failure = ArenaLessonCatalogFailure(ArenaLessonCatalogFailureKind.CATALOG_CHANGED)
            val fake = FakeRemote(Result.failure(failure))
            val repository = ArenaCatalogRepositoryImpl(fake)
            assertSame(failure, repository.list(request).exceptionOrNull())
            assertEquals(1, fake.calls.size)
        }

    @Test
    fun thrownCancellationIsPropagatedUnchangedNotConvertedToFailure() =
        runTest {
            val failure = CancellationException("read cancelled")
            val fake = FakeRemote(Result.success(first))
            fake.beforeReturn = { throw failure }
            val repository = ArenaCatalogRepositoryImpl(fake)
            try {
                repository.list(request)
                throw AssertionError("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(failure, actual)
            }
            assertEquals(1, fake.calls.size)
        }

    @Test
    fun suspendedReadRemainsAChildOfItsCallerAndDoesNotContinueAfterCancellation() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val fake = FakeRemote(Result.success(first))
            fake.beforeReturn = {
                entered.complete(Unit)
                awaitCancellation()
            }
            var returned = false
            val repository = ArenaCatalogRepositoryImpl(fake)
            val read =
                launch {
                    repository.list(request)
                    returned = true
                }
            entered.await()
            read.cancelAndJoin()
            assertTrue(read.isCancelled)
            assertFalse(returned)
            assertEquals(1, fake.calls.size)
        }

    @Test
    fun unexpectedThrownRemoteExceptionIsNotHiddenByAnotherBoundary() =
        runTest {
            val failure = IllegalStateException("fake boundary failure")
            val fake = FakeRemote(Result.success(first))
            fake.beforeReturn = { throw failure }
            try {
                ArenaCatalogRepositoryImpl(fake).list(request)
                throw AssertionError("Remote failure must propagate")
            } catch (actual: IllegalStateException) {
                assertSame(failure, actual)
            }
            assertEquals(1, fake.calls.size)
        }

    private class FakeRemote(private val result: Result<ArenaLessonCatalogPage>) : ArenaLessonCatalogRemoteDataSource {
        val calls = mutableListOf<ArenaLessonCatalogRequest>()
        var beforeReturn: suspend () -> Unit = {}

        override suspend fun list(request: ArenaLessonCatalogRequest): Result<ArenaLessonCatalogPage> {
            calls += request
            beforeReturn()
            return result
        }
    }

    private companion object {
        const val PAGE_SIZE = 3
    }
}
