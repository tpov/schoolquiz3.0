package com.tpov.schoolquiz.presentation.services

import android.content.Context
import android.util.Log
import com.tpov.schoolquiz.data.fierbase.AddPoints
import com.tpov.schoolquiz.data.fierbase.Box
import com.tpov.schoolquiz.data.fierbase.Points
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.main.profile_state.AddPointsController
import com.tpov.schoolquiz.presentation.main.profile_state.DaysInGameController
import com.tpov.schoolquiz.presentation.main.profile_state.LivesController
import com.tpov.schoolquiz.presentation.main.profile_state.NicknameController
import com.tpov.schoolquiz.presentation.main.profile_state.PointsController
import com.tpov.schoolquiz.presentation.main.profile_state.PremiumController
import com.tpov.schoolquiz.presentation.main.profile_state.TaskController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileInteractor @Inject constructor(
    private val context: Context? = null,
    private val profileUseCase: ProfileUseCase
) {
    val livesController = LivesController(context!!)
    val daysInGameController = DaysInGameController(context!!)
    val pointsController = PointsController(context!!)
    val addPointsController = AddPointsController(context!!)
    val nicknameController = NicknameController()
    val premiumController = PremiumController(context!!)
    val taskController = TaskController(context!!)

    suspend fun updateShowLife() {
        profileUseCase.getProfileFlow()?.first()?.let {
            livesController.stopTimer()
            livesController.startTimer(
                standardLife = it.standardLife,
                standardHearts = it.standardHearts,
                goldLife = it.goldLife,
                goldHearts = it.goldHearts,
                lastUpdateTime = it.dateCloseApp.toLongOrNull() ?: 0L
            )
        } ?: run {
            delay(1000)
            updateShowLife()
        }
    }

    suspend fun updateNick() {
        profileUseCase.getProfileFlow()?.first()?.let {
            Log.d("fsdrfsf", it.toString())
            nicknameController.setNickname(it.nickname ?: "Profile offline status", it.trophy)
        }
    }

    suspend fun updateAddPoints() {
        profileUseCase.getProfileFlow()?.first()?.let {
            addPointsController.setCoins(
                AddPoints(
                    it.addPointsGold.toLong(),
                    it.addPointsSkill.toLong(),
                    it.addPointsNolics.toLong(),
                    it.addTrophy,
                    it.addMassage
                )
            )
        }
    }

    suspend fun updatePoints() {
        profileUseCase.getProfileFlow()?.first()?.let {
            pointsController.setCoins(
                Points(
                    it.pointsGold.toLong(),
                    it.pointsSkill.toLong(),
                    it.pointsNolics.toLong(),
                    it.trophy,
                    it.friends
                )
            )
        }
    }

    suspend fun updatePremium() {
        profileUseCase.getProfileFlow()?.first()?.let {
            premiumController.setPremium(it.datePremium)
        }
    }

    suspend fun updateDaysInGameForBox() {
        profileUseCase.getProfileFlow()?.first()?.let {
            daysInGameController.setDaysInGame(Box(it.countBox.toLong(), it.dateCloseApp, it.countDayBox.toLong()))
        }
    }

    fun updateLoadStatus() {
        taskController.reset()
    }

    fun addLoadingTask(taskName: String, maxCount: Int = 100) {
        taskController.addTask(taskName, maxCount)
    }

    fun updateTaskProgress(taskName: String, progress: Int, total: Int) {
        taskController.updateTaskProgress(taskName, progress, total)
    }

    fun completeTask(taskName: String) {
        taskController.completeTask(taskName)
    }

    fun stopLifesUpdate() {
        livesController.stopTimer()
    }
}
