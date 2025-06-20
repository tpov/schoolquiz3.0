package com.tpov.schoolquiz.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tpov.common.data.model.NewProfileIds
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.data.fierbase.fromHashMap
import com.tpov.schoolquiz.data.fierbase.toHashMap
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryProfileImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val firestore: FirebaseFirestore,
    private val firebaseFunctions: FirebaseFunctions
) : RepositoryProfile {
    private val baseCollection = firestore.collection("profiles")

    override suspend fun getProfileFlow(): Flow<ProfileEntity?>? {
        return profileDao.getProfileFlow()
    }

    override suspend fun fetchProfile(tpovId: Int): ProfileRemote? {
        Log.d("FirebaseStorage", "fetchProfile $tpovId")
        return try {
            val task = baseCollection.document(tpovId.toString()).get().await()

            if (task.exists()) {
                val profileData = task.data
                fromHashMap(profileData!!)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetchProfile", e)
            null
        }
    }

    override suspend fun pushProfile(profileRemote: ProfileRemote) {
        Log.d("FirebaseStorage", "pushProfile")
        try {
            // Call the validateProfileUpdate function instead of directly updating
            val result = firebaseFunctions
                .getHttpsCallable("validateProfileUpdate")
                .call(mapOf(
                    "tpovId" to profileRemote.basic.tpovId,
                    "profileData" to profileRemote.toHashMap(),
                    "isServerUpdate" to false // По умолчанию считаем, что это клиентское обновление
                ))
                .await()

            val data = result.data as? Map<*, *>
            if (data != null && data["success"] == true) {
                val suspiciousChanges = data["suspiciousChanges"] as? List<*>
                if (suspiciousChanges?.isNotEmpty() == true) {
                    Log.w("Firestore", "Suspicious changes detected: $suspiciousChanges")
                }
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushProfile", e)
        }
    }

    override suspend fun getProfile(): ProfileEntity? {
        return profileDao.getProfile()
    }

    override suspend fun getNewTpovId(): NewProfileIds? {
        Log.d("RepositoryProfileImpl", "Starting getNewTpovId")
        // Добавляем анонимную аутентификацию перед вызовом функции
        try {
            Log.d("RepositoryProfileImpl", "Attempting anonymous authentication")
            val authResult = FirebaseAuth.getInstance().signInAnonymously().await()
            Log.d("RepositoryProfileImpl", "Anonymous authentication successful: ${authResult.user?.uid}")
        } catch (e: Exception) {
            Log.e("RepositoryProfileImpl", "Anonymous authentication failed", e)
            return null // Возвращаем null при ошибке аутентификации
        }

        Log.d("RepositoryProfileImpl", "Calling Firebase Function to get new tpovId and unique hash from server")
        return try {
            // Вызываем вашу Firebase функцию generateNewTpovId
            Log.d("RepositoryProfileImpl", "Getting HttpsCallable for generateNewTpovId")
            val callable = firebaseFunctions.getHttpsCallable("generateNewTpovId")
            Log.d("RepositoryProfileImpl", "Calling generateNewTpovId function")
            val result = callable.call().await()
            Log.d("RepositoryProfileImpl", "Function call completed, result: $result")

            // Ожидаем, что функция возвращает Map с ключами "tpovId" (Int) и "authUid" (String)
            val data = result.data as? Map<String, Any>
            Log.d("RepositoryProfileImpl", "Parsed result data: $data")

            if (data != null) {
                val tpovIdAny = data["tpovId"]
                val authUidAny = data["authUid"]
                Log.d("RepositoryProfileImpl", "Raw values - tpovId: $tpovIdAny, authUid: $authUidAny")

                // Пробуем привести любое число к Long для tpovId
                val newTpovId: Long? = when (tpovIdAny) {
                    is Number -> tpovIdAny.toLong()
                    else -> null
                }
                // authUid ожидаем как String
                val authUid = authUidAny as? String

                if (newTpovId != null && authUid != null) {
                    val newIdInt = newTpovId.toInt()
                    Log.d("RepositoryProfileImpl", "Successfully parsed - new tpovId: $newIdInt, authUid: $authUid")
                    return NewProfileIds(newIdInt, authUid)
                } else {
                    Log.e("RepositoryProfileImpl", "Failed to parse values - tpovId: $newTpovId, authUid: $authUid")
                    return null
                }
            } else {
                Log.e("RepositoryProfileImpl", "Firebase Function did not return a Map")
                return null
            }
        } catch (e: Exception) {
            Log.e("RepositoryProfileImpl", "Error calling Firebase Function generateNewTpovId", e)
            return null // Возвращаем null при любой ошибке
        }
    }

    override suspend fun insertProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    override suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfiles(profile)
    }
}
