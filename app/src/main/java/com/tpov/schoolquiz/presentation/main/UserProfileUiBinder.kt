package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.fierbase.AddPoints
import com.tpov.schoolquiz.data.fierbase.Box
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.model.LivesState // Уточненный импорт
import com.tpov.schoolquiz.presentation.main.profile_state.TaskState
import com.tpov.userguide.presentation.UserGuide
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserProfileUiBinder(
    private val lifecycleOwner: LifecycleOwner,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel,
    private val context: Context,
    private val livesStateFlow: StateFlow<LivesState>,
    private val addPointsStateFlow: StateFlow<AddPoints>,
    private val premiumStateFlow: StateFlow<String>,
    private val nicknameStateFlow: StateFlow<String>,
    private val daysInGameStateFlow: StateFlow<Box>,
    private val taskStateFlow: StateFlow<TaskState>
) {
    private val listLives by lazy {
        listOf(binding.pbLife1, binding.pbLife2, binding.pbLife3, binding.pbLife4, binding.pbLife5)
    }
    private val listGoldLives by lazy {
        listOf(binding.pbLifeGold1)
    }
    private val boxDays by lazy {
        listOf(
            binding.boxDay1, binding.boxDay2, binding.boxDay3, binding.boxDay4, binding.boxDay5,
            binding.boxDay6, binding.boxDay7, binding.boxDay8, binding.boxDay9, binding.boxDay10
        )
    }

    fun startObserving() {
        observeLife()
        observeAddPoints()
        observePremium()
        observeNickname()
        observeDayInGameAndBox()
        observeTaskStatus()
    }

    private fun observeLife() {
        lifecycleOwner.lifecycleScope.launch {
            livesStateFlow.collect { state ->
                listLives.forEachIndexed { index, imageView ->
                    if (index < state.standardHearts) {
                        imageView.visibility = View.VISIBLE
                        imageView.setImageDrawable(
                            viewModel.createHeartDrawable( // Используем viewModel из конструктора
                                lifePoints = state.standardLife,
                                heartIndex = index,
                                isGold = false
                            )
                        )
                    } else imageView.visibility = View.GONE
                }

                listGoldLives[0].apply {
                    visibility = if (state.goldHearts > 0) View.VISIBLE else View.GONE
                    if (state.goldHearts > 0) {
                        setImageDrawable(
                            viewModel.createHeartDrawable( // Используем viewModel из конструктора
                                lifePoints = state.goldLife,
                                heartIndex = 0,
                                isGold = true
                            )
                        )
                    }
                }

                viewModel.updateProfile( // Используем viewModel из конструктора
                    goldHearts = state.goldHearts,
                    // countGoldLife = state.goldHearts, // В MainViewModel.updateProfile нет countGoldLife, возможно, это было специфично для ProfileInteractor
                    goldLife = state.goldLife,
                    updateTime = state.updateTime,
                    standardLife = state.standardLife,
                    standardHearts = state.standardHearts
                )
            }
        }
    }

    private fun observeAddPoints() {
        lifecycleOwner.lifecycleScope.launch {
            addPointsStateFlow.collect { state ->
                if (state.addGold > 0L) {
                    showUserGuide(context.getString(R.string.coins_added_gold, state.addGold)) // Используем context для getString
                    viewModel.updateProfile( // Используем viewModel из конструктора
                        gold = viewModel.profileState.value?.pointsGold?.toLong()?.plus(state.addGold),
                        addGold = 0
                    )
                }
                if (state.addSkill > 0L) {
                    showUserGuide(context.getString(R.string.coins_added_skill, state.addSkill)) // Используем context для getString
                    viewModel.updateProfile( // Используем viewModel из конструктора
                        skill = viewModel.profileState.value?.pointsSkill?.toLong()?.plus(state.addSkill), addSkill = 0
                    )
                }
                if (state.addNolics > 0L) {
                    showUserGuide(context.getString(R.string.coins_added_nolics, state.addNolics)) // Используем context для getString
                    viewModel.updateProfile( // Используем viewModel из конструктора
                        nolics = viewModel.profileState.value?.pointsNolics?.toLong()?.plus(state.addNolics), addNolics = 0
                    )
                }
                if (state.addTrophy.isNotEmpty()) {
                    showUserGuide(context.getString(R.string.coins_added_trophy, state.addTrophy)) // Используем context для getString
                    viewModel.updateProfile(trophy = viewModel.profileState.value?.trophy + state.addTrophy, addTrophy = "")
                }
                if (state.addMassage.isNotEmpty()) {
                    showUserGuide(context.getString(R.string.message_from_developer, state.addMassage)) // Используем context для getString
                    // Сообщение от разработчика не требует обновления профиля, если оно только для отображения
                }
            }
        }
    }

    private fun observePremium() {
        lifecycleOwner.lifecycleScope.launch {
            premiumStateFlow.collect { premiumValue ->
                binding.tvCountPremiun.text = premiumValue ?: "0" // premiumValue это StateFlow<String>, но может быть nullable из viewModel
            }
        }
    }

    private fun observeNickname() {
        lifecycleOwner.lifecycleScope.launch {
            nicknameStateFlow.collect { nicknameValue ->
                binding.tvName.text = nicknameValue ?: "" // nicknameValue это StateFlow<String>, но может быть nullable из viewModel
            }
        }
    }

    private fun observeDayInGameAndBox() {
        lifecycleOwner.lifecycleScope.launch {
            daysInGameStateFlow.collect { state ->
                boxDays.take(state.countDayBox.toInt()).forEach { view -> // Используем локальный boxDays
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.green)) // Используем context
                }

                binding.tvNumberBox.text = state.countBox.toString()
                binding.fabBox.visibility = if (state.countBox > 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeTaskStatus() {
        lifecycleOwner.lifecycleScope.launch {
            taskStateFlow.collect { state ->
                binding.tvPbLoad.text = state.currentTaskName.ifEmpty { context.getString(R.string.loading_completed) } // Используем context
                binding.progressBar2.progress = (state.progressPercentage * 100).toInt()

                val visibility = if (state.isRunning && state.tasks.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvPbLoad.visibility = visibility
                binding.progressBar2.visibility = visibility
            }
        }
    }

    private fun showUserGuide(text: String) {
        UserGuide(context).guideBuilder()
            .setText(text)
            .build()
    }
}
