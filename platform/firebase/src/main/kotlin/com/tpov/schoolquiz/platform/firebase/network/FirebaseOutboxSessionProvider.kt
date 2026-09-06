package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider

/** App-lifetime binding. Any auth event invalidates previously captured acknowledgement work. */
class FirebaseOutboxSessionProvider(private val auth: FirebaseAuth) : OutboxSessionProvider {
    private var user: FirebaseUser? = auth.currentUser
    private var generation = 0L
    private val listener = FirebaseAuth.AuthStateListener { authChanged() }

    init {
        auth.addAuthStateListener(listener)
    }

    @Synchronized
    private fun authChanged() {
        // Count events, not just uid transitions: queued A -> B -> A callbacks can all observe
        // the final A when delivered. A conservative extra epoch only defers one queue pass.
        generation++
        user = auth.currentUser
    }

    @Synchronized
    private fun refresh(next: FirebaseUser?): OutboxSession? {
        if (user !== next) {
            generation++
            user = next
        }
        return next?.let { OutboxSession(it.uid, generation) }
    }

    @Synchronized
    override fun current(): OutboxSession? = refresh(auth.currentUser)
}
