package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.fake.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Contract tests for [com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository]
 * — verified via [FakeAuthRepository].
 *
 * Spec: home-and-my-quests `0-spec.md` Decision #42 + Domain Contract scenarios 34a-34e.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryContractTest {

    @Test
    fun `given no initial uid when currentUid called then returns null`() = runTest {
        val auth = FakeAuthRepository()

        assertNull(auth.currentUid())
    }

    @Test
    fun `given initial uid when currentUid called then returns that uid`() = runTest {
        val auth = FakeAuthRepository(initialUid = "user-A")

        assertEquals("user-A", auth.currentUid())
    }

    @Test
    fun `given guest then signIn when observeUid collected then emits null then uid`() = runTest {
        val auth = FakeAuthRepository()

        val collected = mutableListOf<String?>()
        val job = launch { auth.observeUid().take(2).toList(collected) }
        runCurrent() // Allow collector to subscribe and receive initial null
        auth.signIn("user-A")
        job.join()

        assertEquals(listOf(null, "user-A"), collected)
    }

    @Test
    fun `given authenticated then signOut when observeUid collected then emits uid then null`() = runTest {
        val auth = FakeAuthRepository(initialUid = "user-A")

        val collected = mutableListOf<String?>()
        val job = launch { auth.observeUid().take(2).toList(collected) }
        runCurrent() // Allow collector to subscribe and receive initial uid
        auth.signOut()
        job.join()

        assertEquals(listOf("user-A", null), collected)
    }

    @Test
    fun `given fake when signIn with blank uid then throws`() {
        val auth = FakeAuthRepository()

        assertFailsWith<IllegalArgumentException> { auth.signIn("") }
        assertFailsWith<IllegalArgumentException> { auth.signIn("   ") }
    }

    @Test
    fun `given initial uid when observeUid first then emits that uid`() = runTest {
        val auth = FakeAuthRepository(initialUid = "user-A")

        val first = auth.observeUid().first()

        assertEquals("user-A", first)
    }
}
