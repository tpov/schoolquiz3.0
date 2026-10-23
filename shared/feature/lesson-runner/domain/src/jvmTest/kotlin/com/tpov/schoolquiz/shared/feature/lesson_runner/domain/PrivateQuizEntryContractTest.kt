package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRun
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizRunQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.PrivateQuizRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartPrivateQuizAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Вход в приватный квиз держит два правила (AD-6/AD-29, история 8.4):
 *  - повтор намерения едет с тем же `startId` — повтор после оборванной сети не тратит лимит;
 *  - отказов входа ровно именованный набор, и «продолжить позже» среди них нет (AD-31:
 *    приватная попытка — один заход; отложенное продолжение не вводится).
 */
class PrivateQuizEntryContractTest {

    private class RecordingRepository : PrivateQuizRunRepository {
        val startIds = mutableListOf<String>()
        var reply: PrivateQuizEntry =
            PrivateQuizEntry.Ready(
                PrivateQuizRun(
                    startId = "start-000000001",
                    startedAtMs = 1_000L,
                    deadlineMs = 2_000L,
                    questions = listOf(PrivateQuizRunQuestion("q-1", 0, "{}")),
                ),
            )

        override suspend fun start(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ): PrivateQuizEntry {
            startIds += startId
            return reply
        }

        override suspend fun readOwnResult(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
        ) = throw IllegalStateException("not reached")

        override suspend fun submitAttempt(
            ownerUid: String,
            catalogId: String,
            questId: String,
            startId: String,
            submission: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizSubmission,
        ) = throw IllegalStateException("not reached")
    }

    private class FixedAuth(
        private val uid: String?,
    ) : AuthRepository {
        override fun observeUid() = kotlinx.coroutines.flow.MutableStateFlow(uid)
        override suspend fun currentUid(): String? = uid
    }

    @Test
    fun aNewIntentMintsANewStartId_andARetryReusesIt() = runTest {
        val repository = RecordingRepository()
        val minted = mutableListOf<String>()
        val useCase =
            StartPrivateQuizAttemptUseCase(
                repository = repository,
                authRepository = FixedAuth("uid-1"),
                startIdProvider = {
                    "start-minted-${minted.size + 1}".also(minted::add)
                },
            )

        val first = useCase("owner", "catalog", "quest")
        assertEquals(1, minted.size, "новое намерение mint'ит один ключ")
        assertTrue(first is PrivateQuizEntry.Ready)
        val firstStartId = minted.single()

        // Повтор ТОГО ЖЕ намерения (тот же ключ) — не тратит новый: дверь позвали тем же id.
        val retry = useCase("owner", "catalog", "quest", existingStartId = firstStartId)
        assertEquals(1, minted.size, "повтор входа не mint'ит новый ключ (AD-6)")
        assertTrue(retry is PrivateQuizEntry.Ready)
        assertEquals(listOf(firstStartId, firstStartId), repository.startIds, "повтор едет тем же startId")
    }

    @Test
    fun everyRefusalIsNamed_andNoneOfThemIsAResume() = runTest {
        val refusals = PrivateQuizEntryRefusal.entries
        assertEquals(
            setOf(
                "LINK_DISABLED", "LINK_EXPIRED", "ATTEMPT_LIMIT_REACHED",
                "NOT_FOUND", "RUN_CONTENT_CHANGED", "ATTEMPT_TERMINAL", "AUTH_REQUIRED", "UNAVAILABLE",
            ),
            refusals.map { it.name }.toSet(),
            "набор отказов входа изменён — проверь, что среди них не появилось «продолжить позже» (AD-31)",
        )
        assertEquals(
            false,
            refusals.any { it.name.contains("RESUME") || it.name.contains("DEFER") || it.name.contains("LATER") },
            "отложенное продолжение у приватного квиза не существует",
        )
    }

    @Test
    fun withoutAuth_theEntryRefusesWithoutTouchingTheDoor() = runTest {
        val repository = RecordingRepository()
        val useCase =
            StartPrivateQuizAttemptUseCase(
                repository = repository,
                authRepository = FixedAuth(null),
                startIdProvider = { "start-minted-1" },
            )
        assertEquals(
            PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.AUTH_REQUIRED),
            useCase("owner", "catalog", "quest"),
        )
        assertEquals(0, repository.startIds.size, "без сессии дверь не трогается")
    }

    private fun ready(): PrivateQuizEntry =
        PrivateQuizEntry.Ready(
            PrivateQuizRun(
                startId = "start-000000001",
                startedAtMs = 1_000L,
                deadlineMs = 2_000L,
                questions = listOf(PrivateQuizRunQuestion("q-1", 0, "{}")),
            ),
        )
}
