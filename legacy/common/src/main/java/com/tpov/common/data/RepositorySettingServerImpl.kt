package com.tpov.common.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.LockServerResult
import com.tpov.common.domain.repository.RepositorySettingServer
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import javax.inject.Inject

class RepositorySettingServerImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : RepositorySettingServer {

    // Ссылка на корень serverConfig
    private val serverConfigDoc = firestore.collection("variable").document("serverConfig")

    // Функция для получения ссылки на документ конкретного события в subcollection
    private fun getEventDocRef(event: EventQuiz): DocumentReference {
        return serverConfigDoc
            .collection("eventLocks")
            .document(event.name)
    }

    override fun lockStructureData(event: EventQuiz): LockServerResult {
        return try {
            // Выполняем транзакцию только над документом конкретного события
            val result = Tasks.await(
                firestore.runTransaction { transaction ->
                    val eventDocRef = getEventDocRef(event)
                    val snapshot = transaction.get(eventDocRef)

                    // Если документа нет, создаём его с дефолтными значениями (isOpen = true)
                    val isOpen = if (snapshot.exists()) {
                        snapshot.getBoolean("isOpen") ?: true
                    } else {
                        true
                    }
                    val ownerId = if (snapshot.exists()) {
                        (snapshot.getLong("tpovIdThis") ?: 0L).toInt()
                    } else {
                        0
                    }

                    // Если уже заблокировано кем-то другим — возвращаем AlreadyLocked
                    if (!isOpen && ownerId != settingsConfig.tpovId) {
                        return@runTransaction LockServerResult.AlreadyLocked
                    }

                    // Обновляем (или создаём) документ с флагом lock
                    val updatedData = mapOf(
                        "isOpen" to false,
                        "tpovIdThis" to settingsConfig.tpovId
                    )
                    transaction.set(eventDocRef, updatedData)

                    LockServerResult.Success
                }
            )
            result
        } catch (e: Exception) {
            LockServerResult.Error(e)
        }
    }

    override fun unlockStructureData(event: EventQuiz): LockServerResult {
        return try {
            val result = Tasks.await(
                firestore.runTransaction { transaction ->
                    val eventDocRef = getEventDocRef(event)
                    val snapshot = transaction.get(eventDocRef)

                    // Если документ не существует или уже открыт — ничего не делаем
                    if (!snapshot.exists()) {
                        return@runTransaction LockServerResult.Success
                    }
                    val isOpen = snapshot.getBoolean("isOpen") ?: true
                    val ownerId = (snapshot.getLong("tpovIdThis") ?: 0L).toInt()

                    // Если уже открыт или вы не владелец блокировки — возвращаем Success
                    if (isOpen || ownerId != settingsConfig.tpovId) {
                        return@runTransaction LockServerResult.Success
                    }

                    // Сброс флагов: открываем и обнуляем tpovIdThis
                    val updatedData = mapOf(
                        "isOpen" to true,
                        "tpovIdThis" to 0L
                    )
                    transaction.update(eventDocRef, updatedData)

                    LockServerResult.Success
                }
            )
            result
        } catch (e: Exception) {
            LockServerResult.Error(e)
        }
    }

    override fun isLockServer(event: EventQuiz): LockServerResult {
        return try {
            val eventDocRef = getEventDocRef(event)
            val snapshot = Tasks.await(eventDocRef.get())

            if (!snapshot.exists()) {
                return LockServerResult.Success
            }
            val isOpen = snapshot.getBoolean("isOpen") ?: true
            val ownerId = (snapshot.getLong("tpovIdThis") ?: 0L).toInt()

            return if (isOpen || ownerId == settingsConfig.tpovId) {
                LockServerResult.Success
            } else {
                LockServerResult.AlreadyLocked
            }
        } catch (e: Exception) {
            LockServerResult.Error(e)
        }
    }
}
