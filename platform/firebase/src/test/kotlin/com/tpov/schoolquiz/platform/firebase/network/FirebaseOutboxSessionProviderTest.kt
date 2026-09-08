package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tpov.schoolquiz.platform.firebase.di.firebaseModule
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.module

class FirebaseOutboxSessionProviderTest {
    @Test
    fun productionEpochObserverAndOutboxAliasOneLazyConcreteProvider() {
        val provider = mockk<FirebaseOutboxSessionProvider>()
        val app = KoinApplication.init().modules(firebaseModule, module { single { provider } })
        try {
            assertSame(provider, app.koin.get<OutboxSessionProvider>())
            assertSame(provider, app.koin.get<ArenaSessionObserver>())
            verify { provider wasNot Called }
        } finally {
            app.close()
        }
    }

    @Test
    fun capturesCurrentOwnerEvenBeforeTheAuthCallbackIsDispatched() {
        val auth = mockk<FirebaseAuth>()
        val first = mockk<FirebaseUser>()
        val second = mockk<FirebaseUser>()
        every { first.uid } returns "A"
        every { second.uid } returns "B"
        var user: FirebaseUser? = first
        every { auth.currentUser } answers { user }
        every { auth.addAuthStateListener(any()) } just Runs
        val sessions = FirebaseOutboxSessionProvider(auth)
        val initial = sessions.current()!!
        user = second
        val next = sessions.current()!!
        assertEquals("B", next.ownerUid)
        assertNotEquals(initial.generation, next.generation)
        assertEquals(next.ownerUid, sessions.observeSessions().value?.ownerUid)
        assertEquals(next.generation, sessions.observeSessions().value?.generation)
        user = null
        assertNull(sessions.current())
        assertNull(sessions.observeSessions().value)
    }

    @Test
    fun sameUidAuthEventsInvalidateTheEpochEvenWhenIntermediateUserWasNotObserved() {
        val auth = mockk<FirebaseAuth>()
        val first = mockk<FirebaseUser>()
        val second = mockk<FirebaseUser>()
        every { first.uid } returns "A"
        every { second.uid } returns "B"
        var user: FirebaseUser? = first
        val callback = slot<FirebaseAuth.AuthStateListener>()
        every { auth.currentUser } answers { user }
        every { auth.addAuthStateListener(capture(callback)) } just Runs
        val sessions = FirebaseOutboxSessionProvider(auth)
        val initial = sessions.current()!!
        assertEquals(initial, sessions.current())
        // A -> B -> A may finish before queued callbacks read auth.currentUser. Counting
        // callback events still invalidates work captured under the original A session.
        callback.captured.onAuthStateChanged(auth)
        callback.captured.onAuthStateChanged(auth)
        assertEquals(initial.ownerUid, sessions.current()!!.ownerUid)
        assertNotEquals(initial.generation, sessions.current()!!.generation)
        assertEquals(sessions.current()!!.generation, sessions.observeSessions().value?.generation)
        assertEquals("A", sessions.observeSessions().value?.ownerUid)
    }
}
