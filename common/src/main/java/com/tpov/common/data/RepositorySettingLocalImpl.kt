package com.tpov.common.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.Core.tpovId
import com.tpov.common.domain.model.LockServerResult
import com.tpov.common.domain.repository.RepositorySettingLocal
import javax.inject.Inject


class RepositorySettingLocalImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : RepositorySettingLocal {

    private val configDoc = firestore.collection("variable").document("serverConfig")
    private val fieldStructureDataName = "isOpenStructureData"

    override fun lockStructureData(): LockServerResult {
        return try {
            val snapshot = Tasks.await(configDoc.get())
            val lockData = snapshot.get(fieldStructureDataName) as? Map<*, *> ?: return LockServerResult.Error(Exception("Нет данных"))

            val isOpen = lockData["isOpen"] as? Boolean ?: true
            val userId = (lockData["tpovIdUser"] as? Long)?.toInt() ?: 0

            if (!isOpen && userId != tpovId) {
                return LockServerResult.AlreadyLocked
            }

            val updatedMap = mapOf(
                "isOpen" to false,
                "tpovIdUser" to tpovId
            )

            Tasks.await(configDoc.update(fieldStructureDataName, updatedMap))
            LockServerResult.Success

        } catch (e: Exception) {
            LockServerResult.Error(e)
        }
    }

    override fun unlockStructureData(): LockServerResult {
        return try {
            val snapshot = Tasks.await(configDoc.get())
            val lockData = snapshot.get(fieldStructureDataName) as? Map<*, *> ?: return LockServerResult.Error(Exception("Нет данных"))

            val isOpen = lockData["isOpen"] as? Boolean ?: true
            val userId = (lockData["tpovIdUser"] as? Long)?.toInt() ?: 0

            if (isOpen || userId != tpovId) {
                return LockServerResult.Success
            }

            val updatedMap = mapOf(
                "isOpen" to true,
                "tpovIdUser" to 0
            )

            Tasks.await(configDoc.update(fieldStructureDataName, updatedMap))
            LockServerResult.Success

        } catch (e: Exception) {
            LockServerResult.Error(e)
        }
    }

    override fun isLockServer(): LockServerResult {
        return try {
            val snapshot = Tasks.await(configDoc.get())
            val lockData = snapshot.get("isOpenServer") as? Map<*, *> ?: return LockServerResult.Error(Exception("Нет данных"))

            val isOpen = lockData["isOpen"] as? Boolean ?: true
            val userId = (lockData["tpovIdUser"] as? Long)?.toInt() ?: 0

            if (isOpen) LockServerResult.Success
            else if (userId == tpovId) LockServerResult.Success
            else LockServerResult.AlreadyLocked

        } catch (e: Exception) {
            LockServerResult.Error(e)
        }
    }
}
