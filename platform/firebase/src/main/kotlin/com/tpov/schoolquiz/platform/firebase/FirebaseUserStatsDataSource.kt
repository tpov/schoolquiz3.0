package com.tpov.schoolquiz.platform.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.schoolquiz.platform.firebase.util.booleanField
import com.tpov.schoolquiz.platform.firebase.util.intField
import com.tpov.schoolquiz.platform.firebase.util.longField
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
        hasPremium = booleanField("hasPremium") ?: false,
        streakDays = intField("streakDays") ?: 0,
        stars = longField("stars") ?: 0L,
        nolics = longField("pointsNolics") ?: 0L,
        standardHearts = intField("standardHearts") ?: 0,
        goldHearts = intField("goldHearts") ?: 0,
        gold = longField("gold") ?: 0L,
        currentSkill = intField("pointsSkill") ?: 0,
        testerLevel = (intField("tester") ?: 0).coerceAtLeast(0),
        moderatorLevel = (intField("moderator") ?: 0).coerceAtLeast(0),
        sponsorLevel = (intField("sponsor") ?: 0).coerceAtLeast(0),
        // "translater" is the actual Firestore field name (intentional typo in production schema)
        translatorLevel = (intField("translater") ?: 0).coerceAtLeast(0),
        adminLevel = (intField("admin") ?: 0).coerceAtLeast(0),
        developerLevel = (intField("developer") ?: 0).coerceAtLeast(0),
        lessonUnlocks =
            (get("lessonUnlocks") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.toSet()
                .orEmpty(),
    )
}
