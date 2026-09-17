package com.tpov.schoolquiz.platform.firebase.push

import com.tpov.schoolquiz.platform.firebase.push.fake.FakeAuthRepository
import com.tpov.schoolquiz.platform.firebase.push.fake.FakeDeviceTokenSource
import com.tpov.schoolquiz.platform.firebase.push.fake.FakeDeviceTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Токен устройства держится под текущим аккаунтом (Story 10.2, 10.5; инвариант 8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTokenRegistrarTest {
    private val auth = FakeAuthRepository()
    private val tokens = FakeDeviceTokenSource()
    private val store = FakeDeviceTokenStore()
    private var now = 1_000L

    private fun registrar(scope: CoroutineScope) =
        DeviceTokenRegistrar(
            authRepository = auth,
            tokenSource = tokens,
            store = store,
            nowMs = { now },
            scope = scope,
        )

    @Test
    fun `guest writes nothing and the flow says so instead of staying empty`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registrar = registrar(scope)
            tokens.issue("t-1")

            assertEquals(TokenRecipient.Nobody, registrar.recipients().first())
            val job = registrar.start()
            assertEquals(emptyList<Triple<String, String, Long>>(), store.registerCalls)
            job.cancel()
        }

    @Test
    fun `token is written when the uid appears in the same process`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val job = registrar(scope).start()
            tokens.issue("t-1")
            assertEquals(0, store.registerCalls.size)

            auth.signIn("uid-A")

            assertEquals(listOf(Triple("uid-A", "t-1", 1_000L)), store.registerCalls)
            assertEquals(listOf("users/uid-A/devices/t-1"), store.documentsOf("uid-A"))
            job.cancel()
        }

    @Test
    fun `time comes from the injected source`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            now = 42L
            val job = registrar(scope).start()
            tokens.issue("t-1")

            assertEquals(42L, store.documents["users/uid-A/devices/t-1"])
            job.cancel()
        }

    @Test
    fun `the same token three times is still one document`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            val job = registrar(scope).start()

            repeat(3) { tokens.issue("t-1") }

            assertEquals(listOf("users/uid-A/devices/t-1"), store.documentsOf("uid-A"))
            job.cancel()
        }

    @Test
    fun `switching accounts re-creates the flow and writes under the new uid`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            val job = registrar(scope).start()
            tokens.issue("t-1")

            auth.signIn("uid-B")

            assertEquals(listOf("users/uid-B/devices/t-1"), store.documentsOf("uid-B"))
            assertEquals(
                listOf(Triple("uid-A", "t-1", 1_000L), Triple("uid-B", "t-1", 1_000L)),
                store.registerCalls,
            )
            job.cancel()
        }

    @Test
    fun `sign-out emits nobody and a later sign-in writes again`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registrar = registrar(scope)
            auth.signIn("uid-A")
            tokens.issue("t-1")
            val seen = mutableListOf<TokenRecipient>()
            val collector = scope.launchCollecting(registrar, seen)

            auth.signOut()
            auth.signIn("uid-B")

            assertEquals(
                listOf(
                    TokenRecipient.Account("uid-A", "t-1"),
                    TokenRecipient.Nobody,
                    TokenRecipient.Account("uid-B", "t-1"),
                ),
                seen,
            )
            collector.cancel()
        }

    @Test
    fun `a failed write does not kill the registration`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            store.failRegister = true
            val job = registrar(scope).start()
            tokens.issue("t-1")
            store.failRegister = false

            tokens.issue("t-2")

            assertEquals(listOf("users/uid-A/devices/t-2"), store.documentsOf("uid-A"))
            job.cancel()
        }

    @Test
    fun `reclaim after a failed sign-in writes the token back under the previous account`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            val registrar = registrar(scope)
            val job = registrar.start()
            tokens.issue("t-1")
            registrar.release("uid-A")
            assertEquals(emptyList<String>(), store.documentsOf("uid-A"))

            // Вход как B не удался: A остаётся активным, а поток регистрации молчит — адресат для
            // него не менялся. Без возврата устройство не получит push для A до перезапуска.
            registrar.reclaim("uid-A")

            assertEquals(listOf("users/uid-A/devices/t-1"), store.documentsOf("uid-A"))
            job.cancel()
        }

    @Test
    fun `release removes the current token from the previous account`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            val registrar = registrar(scope)
            val job = registrar.start()
            tokens.issue("t-1")

            registrar.release("uid-A")

            assertEquals(listOf("uid-A" to "t-1"), store.detachCalls)
            assertEquals(emptyList<String>(), store.documentsOf("uid-A"))
            job.cancel()
        }

    @Test
    fun `release failure is swallowed so the sign-in is not blocked`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            auth.signIn("uid-A")
            val registrar = registrar(scope)
            tokens.issue("t-1")
            store.failDetach = true

            registrar.release("uid-A")

            assertEquals(listOf("uid-A" to "t-1"), store.detachCalls)
        }

    @Test
    fun `release without a token or uid does nothing`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registrar = registrar(scope)

            registrar.release("uid-A")
            tokens.issue("t-1")
            registrar.release("")

            assertEquals(emptyList<Pair<String, String>>(), store.detachCalls)
        }

    private fun CoroutineScope.launchCollecting(
        registrar: DeviceTokenRegistrar,
        into: MutableList<TokenRecipient>,
    ) = launch { registrar.recipients().collect { into += it } }
}
