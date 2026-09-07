package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.arenaRatingHundredths
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogBounds
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPlacement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaCatalogRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultArenaLessonCatalogComponentTest {
    @Test
    fun creationAndResumeDoNotLoadOrPretendTheCatalogIsEmpty() =
        runTest {
            withFixture {
                runCurrent()
                assertTrue(repository.requests.isEmpty())
                assertEquals(ArenaCatalogNotice.NOT_LOADED, component.state.value.notice)
                assertNull(component.state.value.totalLessons)
                lifecycle.stop()
                lifecycle.resume()
                runCurrent()
                assertTrue(repository.requests.isEmpty())
            }
        }

    @Test
    fun unauthenticatedActionNeverCallsTheRepository() =
        runTest {
            withFixture {
                sessions.value = null
                auth.signOut()
                runCurrent()
                component.onRefresh()
                component.onLoadMore()
                runCurrent()
                assertFalse(component.state.value.sessionAvailable)
                assertEquals(ArenaCatalogNotice.ACCOUNT_UNAVAILABLE, component.state.value.notice)
                assertTrue(repository.requests.isEmpty())
            }
        }

    @Test
    fun refreshAndNextAreSingleFlightAndAlwaysRequestTwenty() =
        runTest {
            withFixture {
                component.onRefresh()
                component.onRefresh()
                component.onLoadMore()
                assertTrue(component.state.value.isLoading)
                runCurrent()
                assertEquals(1, repository.requests.size)
                assertEquals(20, repository.requests.single().pageSize)
                assertEquals("owner-a", repository.requests.single().ownerUid)
                assertNull(repository.requests.single().cursor)
                repository.reply(0, firstPage())
                runCurrent()
                assertEquals(20, component.state.value.lessons.size)
                assertFalse(component.state.value.isLoading)
                assertTrue(component.state.value.canLoadMore)
                // Completing a page never initiates the next page.
                assertEquals(1, repository.requests.size)
            }
        }

    @Test
    fun manualNextForwardsTheExactOpaqueCursorAndAppendsServerOrder() =
        runTest {
            withFixture {
                val first = firstPage()
                loadFirst(first)
                runCurrent()
                component.onLoadMore()
                component.onLoadMore()
                runCurrent()
                assertEquals(2, repository.requests.size)
                assertSame(first.nextCursor, repository.requests[1].cursor)
                assertEquals(20, repository.requests[1].pageSize)
                repository.reply(1, lastPage())
                runCurrent()
                assertEquals((0..20).map(::lessonId), component.state.value.lessons.map { it.lessonId })
                assertEquals(21, component.state.value.totalLessons)
                assertFalse(component.state.value.canLoadMore)
                component.onLoadMore()
                runCurrent()
                assertEquals(2, repository.requests.size)
            }
        }

    @Test
    fun explicitRefreshDiscardsContinuationInsteadOfReusingAnOldCursor() =
        runTest {
            withFixture {
                loadFirst(firstPage())
                runCurrent()
                component.onRefresh()
                runCurrent()
                assertNull(repository.requests[1].cursor)
                repository.reply(1, page(emptyList(), total = 0))
                runCurrent()
                assertTrue(component.state.value.lessons.isEmpty())
                assertEquals(0, component.state.value.totalLessons)
                assertNull(component.state.value.notice)
            }
        }

    @Test
    fun changedCatalogClearsOldCardsAndContinuationAndNeedsManualRefresh() =
        runTest {
            withFixture {
                loadFirst(firstPage())
                runCurrent()
                component.onLoadMore()
                runCurrent()
                repository.fail(1, ArenaLessonCatalogFailureKind.CATALOG_CHANGED)
                runCurrent()
                assertTrue(component.state.value.lessons.isEmpty())
                assertNull(component.state.value.totalLessons)
                assertFalse(component.state.value.canLoadMore)
                assertEquals(ArenaCatalogNotice.CATALOG_CHANGED, component.state.value.notice)
                component.onLoadMore()
                runCurrent()
                assertEquals(2, repository.requests.size)
                component.onRefresh()
                runCurrent()
                assertNull(repository.requests[2].cursor)
            }
        }

    @Test
    fun transientFailurePreservesTheSnapshotAndRetriesOnlyOnManualNext() =
        runTest {
            withFixture {
                val first = firstPage()
                loadFirst(first)
                runCurrent()
                component.onLoadMore()
                runCurrent()
                repository.pending[1].complete(Result.failure(IllegalStateException("private SDK content")))
                runCurrent()
                assertEquals(first.lessons, component.state.value.lessons)
                assertEquals(ArenaCatalogNotice.UNAVAILABLE, component.state.value.notice)
                assertTrue(component.state.value.canLoadMore)
                assertEquals(2, repository.requests.size)
                component.onLoadMore()
                runCurrent()
                assertSame(first.nextCursor, repository.requests[2].cursor)
            }
        }

    @Test
    fun closedRefusalsNeverBecomeAnEmptySuccessfulCatalog() =
        runTest {
            withFixture {
                val expected =
                    mapOf(
                        ArenaLessonCatalogFailureKind.CATALOG_TOO_LARGE to ArenaCatalogNotice.TOO_LARGE,
                        ArenaLessonCatalogFailureKind.CATALOG_INVALID to ArenaCatalogNotice.INVALID_CATALOG,
                        ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE to ArenaCatalogNotice.INVALID_CATALOG,
                        ArenaLessonCatalogFailureKind.INVALID_REQUEST to ArenaCatalogNotice.INVALID_CATALOG,
                        ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE to ArenaCatalogNotice.ACCOUNT_UNAVAILABLE,
                        ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED to ArenaCatalogNotice.ACCOUNT_CHANGED,
                    )
                expected.forEach { (failure, notice) ->
                    component.onRefresh()
                    runCurrent()
                    repository.fail(repository.requests.lastIndex, failure)
                    runCurrent()
                    assertEquals(notice, component.state.value.notice)
                    assertNull(component.state.value.totalLessons)
                    assertFalse(component.state.value.canLoadMore)
                }
            }
        }

    @Test
    fun switchedOwnerReplyCannotPublishOldCards() =
        runTest {
            withFixture {
                component.onRefresh()
                runCurrent()
                sessions.value = ArenaSessionStamp("owner-b", 2)
                repository.reply(0, firstPage())
                runCurrent()
                assertTrue(component.state.value.lessons.isEmpty())
                assertFalse(component.state.value.isLoading)
                assertEquals(ArenaCatalogNotice.ACCOUNT_CHANGED, component.state.value.notice)
            }
        }

    @Test
    fun sameUidNewEpochReplyIsRejectedEvenWithoutUidFlowEmission() =
        runTest {
            withFixture {
                component.onRefresh()
                runCurrent()
                // A -> B -> A may be conflated by a uid-only Flow.
                sessions.value = ArenaSessionStamp("owner-a", 3)
                repository.reply(0, firstPage())
                runCurrent()
                assertTrue(component.state.value.lessons.isEmpty())
                assertEquals(ArenaCatalogNotice.ACCOUNT_CHANGED, component.state.value.notice)
            }
        }

    @Test
    fun sameUidNewEpochBetweenPagesCannotContinueTheOldSnapshot() =
        runTest {
            withFixture {
                loadFirst(firstPage())
                runCurrent()
                sessions.value = ArenaSessionStamp("owner-a", 3)
                component.onLoadMore()
                runCurrent()
                assertEquals(1, repository.requests.size)
                assertTrue(component.state.value.lessons.isEmpty())
                assertFalse(component.state.value.canLoadMore)
                component.onRefresh()
                runCurrent()
                assertNull(repository.requests[1].cursor)
            }
        }

    @Test
    fun authSignalClearsDisplayedCardsWithoutAnAutomaticReplacementRequest() =
        runTest {
            withFixture {
                loadFirst(firstPage())
                runCurrent()
                sessions.value = ArenaSessionStamp("owner-b", 2)
                auth.signIn("owner-b")
                runCurrent()
                assertTrue(component.state.value.lessons.isEmpty())
                assertEquals(1, repository.requests.size)
            }
        }

    @Test
    fun resumeRechecksTheActualEpochWithoutMakingARequest() =
        runTest {
            withFixture {
                loadFirst(firstPage())
                runCurrent()
                lifecycle.stop()
                sessions.value = ArenaSessionStamp("owner-a", 9)
                lifecycle.resume()
                runCurrent()
                assertTrue(component.state.value.lessons.isEmpty())
                assertEquals(1, repository.requests.size)
            }
        }

    @Test
    fun oldCancelledFinallyCannotClearNewOwnersBusyFlag() =
        runTest {
            withFixture {
                repository.nonCancellableNext = true
                component.onRefresh()
                runCurrent()
                sessions.value = ArenaSessionStamp("owner-b", 2)
                auth.signIn("owner-b")
                runCurrent()
                component.onRefresh()
                runCurrent()
                assertEquals(2, repository.requests.size)
                repository.reply(0, firstPage())
                runCurrent()
                assertTrue(component.state.value.isLoading)
                assertTrue(component.state.value.lessons.isEmpty())
                repository.reply(1, firstPage(owner = "owner-b"))
                runCurrent()
                assertFalse(component.state.value.isLoading)
                assertEquals(20, component.state.value.lessons.size)
            }
        }

    @Test
    fun cancellationPropagatesAndDoesNotBecomeAServerFailure() =
        runTest {
            withFixture {
                component.onRefresh()
                runCurrent()
                repository.pending[0].completeExceptionally(CancellationException("cancelled"))
                runCurrent()
                assertEquals(1, repository.cancellations)
                assertFalse(component.state.value.isLoading)
                assertEquals(ArenaCatalogNotice.NOT_LOADED, component.state.value.notice)
                assertNull(component.state.value.totalLessons)
            }
        }

    @Test
    fun finalDestructionCancelsOwnedRequestAndIgnoresLaterActions() =
        runTest {
            withFixture {
                component.onRefresh()
                runCurrent()
                destroy()
                runCurrent()
                assertEquals(1, repository.cancellations)
                component.onRefresh()
                component.onLoadMore()
                runCurrent()
                assertEquals(1, repository.requests.size)
                assertFalse(component.state.value.isLoading)
            }
        }

    @Test
    fun retainedHolderKeepsOneInflightReadAcrossComponentRecreation() =
        runTest {
            withFixture {
                component.onRefresh()
                runCurrent()
                val restored = createComponent()
                assertSame(component.state, restored.state)
                restored.onRefresh()
                runCurrent()
                assertEquals(1, repository.requests.size)
                repository.reply(0, firstPage())
                runCurrent()
                assertEquals(20, restored.state.value.lessons.size)
            }
        }

    @Test
    fun newProcessHolderHasNoCardsCursorOrAutomaticRead() =
        runTest {
            withFixture {
                loadFirst(firstPage())
                runCurrent()
                val anotherKeeper = InstanceKeeperDispatcher()
                try {
                    val restored = createComponent(anotherKeeper)
                    runCurrent()
                    assertNull(restored.state.value.totalLessons)
                    assertTrue(restored.state.value.lessons.isEmpty())
                    assertEquals(1, repository.requests.size)
                    restored.onLoadMore()
                    runCurrent()
                    assertEquals(1, repository.requests.size)
                } finally {
                    anotherKeeper.destroy()
                }
            }
        }

    @Test
    fun typedPageForAnotherOwnerOrSnapshotIsStillRefused() =
        runTest {
            withFixture {
                component.onRefresh()
                runCurrent()
                repository.reply(0, firstPage(owner = "other-owner"))
                runCurrent()
                assertEquals(ArenaCatalogNotice.INVALID_CATALOG, component.state.value.notice)
                loadFirst(firstPage())
                runCurrent()
                component.onLoadMore()
                runCurrent()
                repository.reply(2, lastPage().copy(snapshotFingerprint = "b".repeat(64)))
                runCurrent()
                assertEquals(ArenaCatalogNotice.INVALID_CATALOG, component.state.value.notice)
                assertTrue(component.state.value.lessons.isEmpty())
            }
        }

    @Test
    fun displayRoundingIsSafeAtTheMaximumAndDoesNotChangeRationalComparison() {
        val maximum = ArenaLessonCatalogBounds.MAX_SAFE_INTEGER
        assertEquals(100L, arenaRatingHundredths(ArenaLessonCatalogScore(maximum, maximum)))
        assertEquals(205L, arenaRatingHundredths(ArenaLessonCatalogScore(43, 21)))
        val lower = ArenaLessonCatalogScore(maximum - 1, maximum)
        val upper = ArenaLessonCatalogScore(maximum, maximum)
        assertEquals(arenaRatingHundredths(lower), arenaRatingHundredths(upper))
        assertTrue(lower < upper)
    }

    @Test
    fun immediateReentrantResetCancelsTheRegisteredOldJob() =
        runTest {
            withFixture(immediate = true) {
                var reentered = false
                repository.onEnter = {
                    if (!reentered) {
                        reentered = true
                        sessions.value = ArenaSessionStamp("owner-b", 2)
                        component.onRefresh()
                    }
                }
                component.onRefresh()
                assertEquals(2, repository.requests.size)
                assertEquals(1, repository.cancellations)
                assertTrue(component.state.value.isLoading)
                repository.reply(1, firstPage(owner = "owner-b"))
                assertFalse(component.state.value.isLoading)
                assertEquals(20, component.state.value.lessons.size)
            }
        }

    private suspend fun TestScope.withFixture(
        immediate: Boolean = false,
        block: suspend Fixture.() -> Unit,
    ) {
        val dispatcher =
            if (immediate) {
                UnconfinedTestDispatcher(
                    testScheduler,
                )
            } else {
                StandardTestDispatcher(testScheduler)
            }
        val fixture = Fixture(dispatcher)
        try {
            fixture.block()
        } finally {
            fixture.close()
            fixture.lifecycles.forEach {
                it.stop()
                it.destroy()
            }
        }
    }

    private class Fixture(private val dispatcher: TestDispatcher) {
        val repository = CatalogRepositoryFake()
        val sessions = Sessions()
        val auth = FakeAuthRepository("owner-a")
        val keeper = InstanceKeeperDispatcher()
        val lifecycles = mutableListOf<LifecycleRegistry>()
        val component = createComponent()
        val lifecycle get() = lifecycles.first()
        private var destroyed = false

        fun destroy() {
            if (!destroyed) {
                destroyed = true
                keeper.destroy()
            }
        }

        fun close() {
            destroy()
            // Even a failed assertion must release deliberately non-cooperative fake reads.
            repository.pending.forEach { it.cancel() }
        }

        fun createComponent(instanceKeeper: InstanceKeeperDispatcher = keeper): DefaultArenaLessonCatalogComponent {
            val lifecycle = LifecycleRegistry().also { lifecycles += it }
            return DefaultArenaLessonCatalogComponent(
                DefaultComponentContext(lifecycle, instanceKeeper = instanceKeeper),
                repository,
                sessions,
                auth,
                dispatcher,
            ).also { lifecycle.resume() }
        }

        fun loadFirst(page: ArenaLessonCatalogPage) {
            component.onRefresh()
            dispatcher.scheduler.runCurrent()
            repository.reply(repository.requests.lastIndex, page)
        }
    }

    private class Sessions : ArenaSessionProvider {
        var value: ArenaSessionStamp? = ArenaSessionStamp("owner-a", 1)

        override fun current(): ArenaSessionStamp? = value
    }

    private class CatalogRepositoryFake : ArenaCatalogRepository {
        val requests = mutableListOf<ArenaLessonCatalogRequest>()
        val pending = mutableListOf<CompletableDeferred<Result<ArenaLessonCatalogPage>>>()
        var nonCancellableNext = false
        var cancellations = 0
        var onEnter: (() -> Unit)? = null

        override suspend fun list(request: ArenaLessonCatalogRequest): Result<ArenaLessonCatalogPage> {
            requests += request
            val reply = CompletableDeferred<Result<ArenaLessonCatalogPage>>().also { pending += it }
            val ignoreCancellation = nonCancellableNext.also { nonCancellableNext = false }
            return try {
                onEnter?.invoke()
                if (ignoreCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
            } catch (error: CancellationException) {
                cancellations++
                throw error
            }
        }

        fun reply(
            index: Int,
            page: ArenaLessonCatalogPage,
        ) {
            pending[index].complete(Result.success(page))
        }

        fun fail(
            index: Int,
            kind: ArenaLessonCatalogFailureKind,
        ) {
            pending[index].complete(Result.failure(ArenaLessonCatalogFailure(kind)))
        }
    }

    private companion object {
        fun lessonId(index: Int) = "lesson-" + index.toString().padStart(3, '0')

        fun lesson(index: Int) =
            ArenaLessonCatalogCard(
                lessonId(index),
                1,
                "Lesson " + index,
                "public-author",
                ArenaLessonCatalogRating(2, 1, ArenaLessonCatalogScore(42, 21), 1),
                1,
                ArenaLessonCatalogPlacement(null, null, null),
            )

        fun firstPage(owner: String = "owner-a"): ArenaLessonCatalogPage {
            val lessons = (0 until 20).map(::lesson)
            val cursor =
                ArenaLessonCatalogCursor(
                    "opaque_token",
                    owner,
                    "a".repeat(64),
                    lessons.last(),
                    21,
                    lessons.map { it.lessonId }.toSet(),
                    1,
                )
            return page(lessons, 21, owner).copy(nextCursor = cursor)
        }

        fun lastPage() = page(listOf(lesson(20)), 21)

        fun page(
            lessons: List<ArenaLessonCatalogCard>,
            total: Int,
            owner: String = "owner-a",
        ) = ArenaLessonCatalogPage(owner, "a".repeat(64), 123, total, lessons, null)
    }
}
