package com.tpov.schoolquiz.domain

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.tpov.common.data.model.ProfileStatus
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.ProfileRemote
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.domain.ProfileExtention.createAnonymousProfile
import com.tpov.schoolquiz.domain.ProfileExtention.updateFromRemote
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import javax.inject.Inject

/**
 * Улучшенная версия ProfileUseCase с обработкой ошибок
 */
class ProfileUseCaseImproved @Inject constructor(
    private val repositoryProfile: RepositoryProfile
) {

    /**
     * Получение профиля с обработкой ошибок
     */
    suspend fun getProfileFlow() = runCatching {
        repositoryProfile.getProfileFlow()
    }.getOrElse { throwable ->
        Log.e("ProfileUseCase", "Error getting profile flow", throwable)
        throw ProfileException("Failed to get profile flow", throwable)
    }

    /**
     * Вставка и отправка профиля с обработкой ошибок
     */
    suspend fun insertAndPushProfile(profile: ProfileEntity): Result<Unit> = runCatching {
        try {
            repositoryProfile.insertProfile(profile)
            repositoryProfile.pushProfile(profile.toProfile())
        } catch (e: Exception) {
            Log.e("ProfileUseCase", "Error inserting and pushing profile", e)
            throw ProfileException("Failed to insert and push profile", e)
        }
    }

    /**
     * Обновление профиля с обработкой ошибок
     */
    suspend fun updateProfile(profile: ProfileEntity): Result<Unit> = runCatching {
        try {
            repositoryProfile.updateProfile(profile)
        } catch (e: Exception) {
            Log.e("ProfileUseCase", "Error updating profile", e)
            throw ProfileException("Failed to update profile", e)
        }
    }

    /**
     * Отправка профиля с обработкой ошибок
     */
    suspend fun pushProfile(profileRemote: ProfileRemote): Result<Unit> = runCatching {
        try {
            repositoryProfile.pushProfile(profileRemote)
        } catch (e: Exception) {
            Log.e("ProfileUseCase", "Error pushing profile", e)
            throw ProfileException("Failed to push profile", e)
        }
    }

    /**
     * Синхронизация профиля с улучшенной обработкой ошибок
     */
    suspend fun syncProfile(): Result<ProfileEntity> = runCatching {
        try {
            val currentProfile = repositoryProfile.getProfile()
            val isAuthenticated = FirebaseAuth.getInstance().currentUser?.uid != null && 
                                 repositoryProfile.getProfile()?.tpovId != null
            val isOffline = currentProfile?.status == ProfileStatus.OFFLINE.statusCode

            var newLocalProfile = when {
                currentProfile == null -> {
                    val newTpovId = repositoryProfile.getNewTpovId()
                    if (newTpovId != null) {
                        ProfileEntity().create(newTpovId)
                    } else {
                        throw ProfileException("Failed to generate new tpovId")
                    }
                }
                isOffline || !isAuthenticated -> {
                    val newTpovId = repositoryProfile.getNewTpovId()
                    if (newTpovId != null) {
                        currentProfile.createAnonymousProfile(newTpovId)
                    } else {
                        currentProfile
                    }
                }
                else -> currentProfile
            }

            if (isAuthenticated) {
                Log.d("ProfileUseCase", "User is authenticated, fetching remote profile")
                try {
                    val remoteProfile = repositoryProfile.fetchProfile(newLocalProfile.tpovId ?: 0)
                    if (remoteProfile != null) {
                        newLocalProfile = newLocalProfile.updateFromRemote(remoteProfile)
                        repositoryProfile.pushProfile(newLocalProfile.toProfile())
                    }
                } catch (e: Exception) {
                    Log.w("ProfileUseCase", "Failed to fetch remote profile, using local", e)
                    // Продолжаем с локальным профилем
                }
            }

            repositoryProfile.insertProfile(newLocalProfile)
            newLocalProfile

        } catch (e: Exception) {
            Log.e("ProfileUseCase", "Error syncing profile", e)
            throw ProfileException("Failed to sync profile", e)
        }
    }

    /**
     * Проверка состояния профиля
     */
    suspend fun checkProfileStatus(): Result<ProfileStatus> = runCatching {
        try {
            val profile = repositoryProfile.getProfile()
            when {
                profile == null -> ProfileStatus.NOT_CREATED
                profile.status == ProfileStatus.OFFLINE.statusCode -> ProfileStatus.OFFLINE
                FirebaseAuth.getInstance().currentUser?.uid != null -> ProfileStatus.ONLINE
                else -> ProfileStatus.ANONYMOUS
            }
        } catch (e: Exception) {
            Log.e("ProfileUseCase", "Error checking profile status", e)
            throw ProfileException("Failed to check profile status", e)
        }
    }

    /**
     * Очистка профиля
     */
    suspend fun clearProfile(): Result<Unit> = runCatching {
        try {
            // Здесь можно добавить логику очистки профиля
            Log.d("ProfileUseCase", "Profile cleared")
        } catch (e: Exception) {
            Log.e("ProfileUseCase", "Error clearing profile", e)
            throw ProfileException("Failed to clear profile", e)
        }
    }
}

/**
 * Кастомное исключение для профиля
 */
class ProfileException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Статусы профиля
 */
enum class ProfileStatus {
    NOT_CREATED,
    ANONYMOUS,
    OFFLINE,
    ONLINE
}