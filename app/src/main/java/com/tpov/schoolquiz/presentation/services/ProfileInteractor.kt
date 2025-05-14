package com.tpov.schoolquiz.presentation.services

import android.content.Context
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
import com.tpov.schoolquiz.presentation.setting.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileInteractor @Inject constructor(
    private val context: Context,
    private val profileUseCase: ProfileUseCase
) {
    val livesController = LivesController(context)
    val daysInGameController = DaysInGameController(context)
    val pointsController = PointsController(context)
    val addPointsController = AddPointsController(context)
    val nicknameController = NicknameController(context)
    val premiumController = PremiumController(context)

    fun updateShowLife() {
        runBlocking {
            profileUseCase.getProfileFlow()?.first()?.let {
                livesController.stopTimer()
                livesController.startTimer(
                    standardLife = it.standardLife,
                    standardHearts = it.standardHearts,
                    goldLife = it.goldLife,
                    goldHearts = it.goldHearts,
                    lastUpdateTime = it.dateCloseApp.toLong()
                )
            } ?: {
                runBlocking {
                    delay(1000)
                    updateShowLife()
                }
            }
        }
    }

    fun updateNick() {
        runBlocking {
            profileUseCase.getProfileFlow()?.first()?.let {
                nicknameController.setNickname(it.nickname ?: "...", it.trophy)
            }
        }
    }

    fun updateAddPoints() {
        runBlocking {
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
    }

    fun updatePoints() {
        runBlocking {
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
    }

    fun updatePremium() {
        runBlocking {
            profileUseCase.getProfileFlow()?.first()?.let {
                premiumController.setPremium(it.datePremium)
            }
        }
    }
    fun updateDaysInGameForBox() {
        runBlocking {
            profileUseCase.getProfileFlow()?.first()?.let {
                daysInGameController.setDaysInGame(Box(it.countBox.toLong(), it.timeLastOpenBox, it.countDayBox.toLong()))
            }
        }
    }

    fun updateLoadStatus() {
        Task
    }

    fun stopLifesUpdate() {
        livesController.stopTimer()
    }

}
