package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-lifetime binding. Any auth event invalidates previously captured acknowledgement work. */
class FirebaseOutboxSessionProvider(private val auth: FirebaseAuth) : OutboxSessionProvider, ArenaSessionObserver {
    private var user: FirebaseUser? = auth.currentUser
    private var generation = 0L
    private val states = MutableStateFlow(stamp())
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
        states.value = stamp()
    }

    @Synchronized
    private fun refresh(next: FirebaseUser?): OutboxSession? {
        if (user !== next) {
            generation++
            user = next
            states.value = stamp()
        }
        return next?.let { OutboxSession(it.uid, generation) }
    }

    @Synchronized
    override fun current(): OutboxSession? = refresh(auth.currentUser)

    override fun observeSessions() = states.asStateFlow()

    private fun stamp(): ArenaSessionStamp? = user?.let { ArenaSessionStamp(it.uid, generation) }
}
