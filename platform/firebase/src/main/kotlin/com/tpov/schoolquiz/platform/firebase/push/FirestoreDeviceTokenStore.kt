package com.tpov.schoolquiz.platform.firebase.push

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * `users/{uid}/devices/{token}` в Firestore (AD-20).
 *
 * Идентификатор документа — сам токен, поэтому повторная выдача того же токена — тот же документ.
 * Время приходит снаружи: своих часов у адаптера нет (AD-9).
 */
class FirestoreDeviceTokenStore(
    private val firestore: FirebaseFirestore,
) : DeviceTokenStore {
    override suspend fun register(
        uid: String,
        token: String,
        updatedAtMs: Long,
    ) {
        document(uid, token)
            .set(mapOf(FIELD_UPDATED_AT_MS to updatedAtMs, FIELD_PLATFORM to PLATFORM_ANDROID))
            .await()
    }

    override suspend fun detach(
        uid: String,
        token: String,
    ) {
        document(uid, token).delete().await()
    }

    private fun document(
        uid: String,
        token: String,
    ): DocumentReference = firestore.collection(USERS).document(uid).collection(DEVICES).document(token)

    companion object {
        const val USERS = "users"
        const val DEVICES = "devices"
        const val FIELD_UPDATED_AT_MS = "updatedAtMs"
        const val FIELD_PLATFORM = "platform"
        const val PLATFORM_ANDROID = "android"
    }
}
