package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.Core
import com.tpov.common.Core.tpovIdFlow
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.log_api.logger.Logger
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.services.ProfileInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@Logger
@OptIn(FlowPreview::class)
class MainViewModel @Inject constructor(
    private val structureUseCase: StructureUseCase,
    private val questionUseCase: QuestionUseCase,
    private val profileUseCase: ProfileUseCase,
    private val context: Context,
    private val profileInteractor: ProfileInteractor
) : ViewModel() {

    val profileState: StateFlow<ProfileEntity?> get() = _profileState
    private val _profileState = MutableStateFlow<ProfileEntity?>(null)

    val questionData: StateFlow<List<QuestionEntity>?> get() = _questionData
    private val _questionData = MutableStateFlow<List<QuestionEntity>?>(null)
    val structureData: StateFlow<List<StructureDataLocal?>?> get() = _structureData
    private val _structureData = MutableStateFlow<List<StructureDataLocal?>?>(null)

    val taskState = profileInteractor.taskController.taskState
    val livesState = profileInteractor.livesController.livesState
    val addPointsState = profileInteractor.addPointsController.addPointsState
    val premiumState = profileInteractor.premiumController.premiumState
    val daysInGameState = profileInteractor.daysInGameController.daysInGameState

    var firstStartApp = false

    init {
        initControllers()
        runBlocking {
            profileState.collect {

            }
        }
    }

    private fun initControllers() {
        viewModelScope.launch(Dispatchers.IO) {
            profileInteractor.updateShowLife()
            profileInteractor.updateNick()
            profileInteractor.updateAddPoints()
            profileInteractor.updatePoints()
            profileInteractor.updatePremium()
            profileInteractor.updateDaysInGameForBox()
            profileInteractor.updateLoadStatus()
        }
    }

    fun createHeartDrawable(lifePoints: Int, heartIndex: Int, isGold: Boolean) =
        profileInteractor.livesController.createHeartDrawable(lifePoints, heartIndex, isGold)

    fun stopLifesUpdate() {
        profileInteractor.stopLifesUpdate()
    }

    fun initStructureData(event: EventQuiz) = viewModelScope.launch(Dispatchers.IO) {
        _structureData.value = structureUseCase.getStructureEventData(event)?.toList()
    }

    fun initProfile() {
        Log.d("init", "init")
        viewModelScope.launch(Dispatchers.Default) {
            var previousProfile: ProfileEntity? = null

            combine(
                profileUseCase.getProfileFlow() ?: flowOf(null),
                tpovIdFlow
            ) { profile, currentTpovId ->
                profile to currentTpovId
            }
                .debounce(500)
                .collect { (profile, currentTpovId) ->

                    Log.d(
                        "init",
                        "previousProfile: $previousProfile, profile: $profile, currentTpovId: $currentTpovId"
                    )
                    if ((profile != previousProfile) && currentTpovId != 0 || (profile != previousProfile) || profile == null) {
                        Log.d("init", "profile: $profile")
                        _profileState.value = profile
                        if (profile == null && currentTpovId != 0) {
                            createProfile()
                        } else {
                            //profileUseCase.syncProfile()
                        }
                        previousProfile = profile
                    }
                }
        }
    }

    fun updateProfile(profileEntity: ProfileEntity) = viewModelScope.launch(Dispatchers.Default) {
        profileUseCase.updateProfile(profileEntity)
    }

    // Метод для обновления конкретных полей профиля
    fun updateProfile(
        gold: Long? = null,
        skill: Long? = null,
        nolics: Long? = null,
        trophy: String? = null,
        addGold: Long? = null,
        addSkill: Long? = null,
        addNolics: Long? = null,
        addTrophy: String? = null,
        addMassage: String? = null,
        goldHearts: Int? = null,
        countGoldLife: Int? = null,
        goldLife: Int? = null,
        updateTime: Long? = null,
        standardLife: Int? = null,
        standardHearts: Int? = null
    ) = viewModelScope.launch(Dispatchers.Default) {
        val currentProfile = _profileState.value ?: return@launch

        val updatedProfile = currentProfile.copy(
            pointsGold = gold?.toInt() ?: currentProfile.pointsGold,
            pointsSkill = skill?.toInt() ?: currentProfile.pointsSkill,
            pointsNolics = nolics?.toInt() ?: currentProfile.pointsNolics,
            trophy = trophy ?: currentProfile.trophy,
            addPointsGold = addGold?.toInt() ?: currentProfile.addPointsGold,
            addPointsSkill = addSkill?.toInt() ?: currentProfile.addPointsSkill,
            addPointsNolics = addNolics?.toInt() ?: currentProfile.addPointsNolics,
            addTrophy = addTrophy ?: currentProfile.addTrophy,
            addMassage =addMassage ?: currentProfile.addMassage ,
            goldHearts = goldHearts ?: currentProfile.goldHearts,
            goldLife = goldLife ?: currentProfile.goldLife,
            dateCloseApp = updateTime?.toString() ?: currentProfile.dateCloseApp,
            standardLife = standardLife ?: currentProfile.standardLife,
            standardHearts = standardHearts ?: currentProfile.standardHearts
        )

        // Обновляем профиль в базе данных
        profileUseCase.updateProfile(updatedProfile)

        // Обновляем локальное состояние
        _profileState.value = updatedProfile
    }

    fun createProfile() = viewModelScope.launch(Dispatchers.Default) {
        Log.d("createProfile", "createProfile()")
        val currentTimestamp = Instant.now().epochSecond
        val daysSinceEpoch = Instant.now().epochSecond / 86400

        profileUseCase.insertAndPushProfile(
            ProfileEntity(
                dataCreateAcc = currentTimestamp.toString(),
                languages = Locale.getDefault().language,
                timeLastOpenBox = daysSinceEpoch.toString(),
                tpovId = Core.tpovId
            )
        )
    }

    suspend fun pushUserQuestions(
        questionsEntity: ArrayList<QuestionEntity>,
        pathStructure: PathStructure
    ) {

        maybePushQuestionForTranslate(
            questionsEntity,
            getContainsLang(questionsEntity),
            pathStructure
        )

        questionsEntity.forEach {
            pushQuestion(it)
        }
    }

    private fun getContainsLang(questions: List<QuestionEntity>): String {
        return "" //TODO
    }

    fun maybePushQuestionForTranslate(
        questionsEntity: ArrayList<QuestionEntity>,
        mainLanguageQuiz: String,
        structureEditData: PathStructure
    ) {
        Log.e("Translation", "maybePushQuestionForTranslate()")
        Log.e("Translation", "questionsEntity: $questionsEntity")
        Log.e("Translation", "mainLanguageQuiz: $mainLanguageQuiz")
        val mainQuestions = questionsEntity.filter { it.language == mainLanguageQuiz }
        mainQuestions.forEach { question ->
            viewModelScope.launch(Dispatchers.IO) {
                val localLanguagesQuestions: Set<String> = questionsEntity.filter {
                    it.hardQuestion == question.hardQuestion
                            && it.numQuestion == question.numQuestion
                            && it.quiz == question.quiz
                            && it.category == question.category
                            && it.subCategory == question.subCategory
                            && it.event == question.event
                            && it.subsubCategory == question.subsubCategory
                }.map { it.language }.toSet()

                questionUseCase.pushQuestionForTranslate(
                    question, localLanguagesQuestions
                )
            }
        }
    }

    fun syncProfile() = viewModelScope.launch(Dispatchers.Default) {
        profileUseCase.syncProfile()
    }

    private suspend fun pushQuestion(questionEntity: QuestionEntity) {
        questionUseCase.pushQuestion(questionEntity)
    }

    fun addTask(name: String, maxCount: Int = 100) {
        profileInteractor.addLoadingTask(name, maxCount)
    }

    fun updateTaskProgress(name: String, progress: Int, total: Int) {
        profileInteractor.updateTaskProgress(name, progress, total)
    }

    fun completeTask(name: String) {
        profileInteractor.completeTask(name)
    }

    fun resetTasks() {
        profileInteractor.updateLoadStatus()
    }
}
