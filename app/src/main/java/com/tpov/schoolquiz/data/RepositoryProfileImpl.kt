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
            baseCollection.document(profileRemote.basic.tpovId.toString()).set(profileRemote.toHashMap()).await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushProfile", e)
        }
    }

    override suspend fun getProfile(): ProfileEntity? {
        return profileDao.getProfile()
    }

    override suspend fun getNewTpovId(): NewProfileIds {
        // Добавляем анонимную аутентификацию перед вызовом функции
        try {
            val authResult = FirebaseAuth.getInstance().signInAnonymously().await()
            Log.d("RepositoryProfileImpl", "Anonymous authentication successful: ${authResult.user?.uid}")
        } catch (e: Exception) {
            Log.e("RepositoryProfileImpl", "Anonymous authentication failed", e)
            // Если анонимная аутентификация не удалась, возможно, стоит выбросить ошибку
            // или вернуть специальный объект, указывающий на сбой.
            // В данном случае, я просто логирую ошибку и продолжу попытку вызвать функцию.
            // Если функция все еще требует аутентификации, она выбросит свою ошибку.
        }

        Log.d("RepositoryProfileImpl", "Calling Firebase Function to get new tpovId and unique hash from server")
        return try {
            // Вызываем вашу Firebase функцию generateNewTpovId
            val result = firebaseFunctions
                .getHttpsCallable("generateNewTpovId") // Укажите здесь точное имя вашей функции
                .call()
                .await()

            // Ожидаем, что функция возвращает Map с ключами "tpovId" (Int) и "authUid" (String)
            val data = result.data as? Map<String, Any>

            if (data != null) {
                val tpovIdAny = data["tpovId"]
                val authUidAny = data["authUid"]

                // Пробуем привести любое число к Long для tpovId
                val newTpovId: Long? = when (tpovIdAny) {
                    is Number -> tpovIdAny.toLong()
                    else -> null
                }
                // authUid ожидаем как String
                val authUid = authUidAny as? String

                if (newTpovId != null && authUid != null) {
                    val newIdInt = newTpovId.toInt()
                    Log.d("RepositoryProfileImpl", "Received new tpovId: $newIdInt, authUid: $authUid")

                    // Now returning both values as NewProfileIds object
                    return NewProfileIds(newIdInt, authUid)
                } else {
                    // Добавляем в лог, что именно не удалось спарсить
                    Log.e("RepositoryProfileImpl", "Firebase Function returned data in unexpected format: $data. Parsed: tpovId=$newTpovId, authUid=$authUid")
                    throw IllegalStateException("Failed to get valid tpovId and authUid from Firebase Function")
                }
            } else {
                Log.e("RepositoryProfileImpl", "Firebase Function did not return a Map")
                throw IllegalStateException("Failed to get data from Firebase Function")
            }
        } catch (e: Exception) {
            Log.e("RepositoryProfileImpl", "Error calling Firebase Function generateNewTpovId", e)
            // Обработка ошибок вызова функции
            throw e // Перебрасываем исключение
        }
    }

    override suspend fun insertProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    override suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfiles(profile)
    }
}
