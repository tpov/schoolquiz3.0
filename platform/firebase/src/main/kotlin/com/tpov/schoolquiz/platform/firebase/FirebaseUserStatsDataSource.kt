package com.tpov.schoolquiz.platform.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.shared.core.stats.AuthUidChanged
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseUserStatsDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : UserStatsDataSource {
    private val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Starts a Firestore snapshot listener for the currently authenticated user's document.
     * The UID is captured once at flow collection time.
     *
     * Closes with [AuthUidChanged] when the authenticated user's UID changes, signalling
     * that the consumer should re-subscribe to receive the new user's data.
     */
    override fun observeRaw(): Flow<RawUserStats> =
        callbackFlow {
            val uid = currentUid
            if (uid == null) {
                trySend(RawUserStats())
                close()
                return@callbackFlow
            }
            val authListener =
                FirebaseAuth.AuthStateListener { firebaseAuth ->
                    if (firebaseAuth.currentUser?.uid != uid) close(AuthUidChanged())
                }
            auth.addAuthStateListener(authListener)
            val listener =
                firestore.collection("users").document(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        // trySend result ignored: Firestore emits ~1/s, buffer overflow is unlikely
                        trySend(
                            snapshot?.toRawUserStats() ?: RawUserStats(),
                        )
                    }
            awaitClose {
                listener.remove()
                auth.removeAuthStateListener(authListener)
            }
        }

    override suspend fun fetchRaw(): RawUserStats {
        val uid = currentUid ?: return RawUserStats()
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toRawUserStats() ?: RawUserStats()
    }

}

private fun DocumentSnapshot.toRawUserStats(): RawUserStats? {
    if (!exists()) return null
    return RawUserStats(
        nickname = getString("nickname") ?: "",
        avatarUrl = getString("avatarUrl")?.takeIf { it.startsWith("https://") },
        hasPremium = getBoolean("hasPremium") ?: false,
        streakDays = getLong("streakDays")?.toInt() ?: 0,
        stars = getLong("stars") ?: 0L,
        nolics = getLong("pointsNolics") ?: 0L,
        standardHearts = getLong("standardHearts")?.toInt() ?: 0,
        goldHearts = getLong("goldHearts")?.toInt() ?: 0,
        gold = getLong("gold") ?: 0L,
        currentSkill = getLong("pointsSkill")?.toInt() ?: 0,
        testerLevel = (getLong("tester")?.toInt() ?: 0).coerceAtLeast(0),
        moderatorLevel = (getLong("moderator")?.toInt() ?: 0).coerceAtLeast(0),
        sponsorLevel = (getLong("sponsor")?.toInt() ?: 0).coerceAtLeast(0),
        // "translater" is the actual Firestore field name (intentional typo in production schema)
        translatorLevel = (getLong("translater")?.toInt() ?: 0).coerceAtLeast(0),
        adminLevel = (getLong("admin")?.toInt() ?: 0).coerceAtLeast(0),
        developerLevel = (getLong("developer")?.toInt() ?: 0).coerceAtLeast(0),
    )
}
