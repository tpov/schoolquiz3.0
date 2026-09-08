package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Explicit advisory read only; unknown/stale balance never grants a hint. Settlement remains server-owned. */
internal class ArenaRunHintBudget(
    private val stamp: ArenaSessionStamp?,
    private val wallet: ArenaWalletRepository,
    private val sessions: ArenaSessionProvider,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val changed: () -> Unit,
) {
    private var observation: ArenaWalletObservation? = null
    private var busy = false

    fun clear() {
        observation = null
    }

    fun lives(current: ArenaRunRead.Continuable): Int? {
        val known = observation ?: return null
        return if (known.isUsableFor(sessions.current(), clock.now().toEpochMilliseconds(), MAX_AGE_MS)) {
            val hearts = known.storedLifePoints / UserProfile.LIFE_POINTS_PER_HEART
            val claimed = current.checkpoint.chargeClaims.count(ChargeClaimMask.STANDARD)
            (hearts - claimed).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        } else {
            null
        }
    }

    fun refresh() {
        val original = stamp ?: return
        if (busy || sessions.current() != original) return
        busy = true
        observation = null
        changed()
        scope.launch {
            try {
                val fetched = wallet.read(original).getOrNull()
                if (sessions.current() == original && fetched?.stamp == original) observation = fetched
            } finally {
                busy = false
                changed()
            }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 60_000L
    }
}
