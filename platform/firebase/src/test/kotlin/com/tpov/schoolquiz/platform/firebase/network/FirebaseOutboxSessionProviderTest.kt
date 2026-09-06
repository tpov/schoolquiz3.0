package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseOutboxSessionProviderTest {
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
        user = null
        assertNull(sessions.current())
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
    }
}
