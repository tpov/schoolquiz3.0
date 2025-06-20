package com.tpov.schoolquiz.presentation.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.log_api.logger.Logger
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.services.ProfileInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val nicknameState = profileInteractor.nicknameController.nicknameState
    val daysInGameState = profileInteractor.daysInGameController.daysInGameState

    var firstStartApp = false

    init {
        initControllers()
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

    fun initProfile() = viewModelScope.launch(Dispatchers.Default) {
            _profileState.value = profileUseCase.getProfileFlow()?.first()
    }

    fun updateProfile(profileEntity: ProfileEntity) = viewModelScope.launch(Dispatchers.Default) {
        profileUseCase.updateProfile(profileEntity)
    }

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

    suspend fun pushUserQuestions(
        questionLocalList: ArrayList<QuestionLocal>,
        pathStructure: PathStructure
    ) {

        maybePushQuestionForTranslate(
            questionLocalList,
            getContainsLang(questionLocalList),
            pathStructure
        )

        questionLocalList.forEach {
            pushQuestion(it)
        }
    }

    private fun getContainsLang(questions: List<QuestionLocal>): LanguageUtils {
        return LanguageUtils.ENGLISH
    }

    fun maybePushQuestionForTranslate(
        questionLocalList: ArrayList<QuestionLocal>,
        mainLanguageQuiz: LanguageUtils,
        structureEditData: PathStructure
    ) {
        val mainQuestions = questionLocalList.filter { it.language == mainLanguageQuiz }
        mainQuestions.forEach { question ->
            viewModelScope.launch(Dispatchers.IO) {
                val localLanguagesQuestions: Set<LanguageUtils> = questionLocalList.filter {
                    it.hardQuestion == question.hardQuestion
                            && it.numQuestion == question.numQuestion
                            && it.pathStructure.nameQuiz == question.pathStructure.nameQuiz
                            && it.pathStructure.nameCategory == question.pathStructure.nameCategory
                            && it.pathStructure.nameSubCategory == question.pathStructure.nameSubCategory
                            && it.pathStructure.nameEvent == question.pathStructure.nameEvent
                            && it.pathStructure.nameSubsubCategory == question.pathStructure.nameSubsubCategory
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

    private suspend fun pushQuestion(questionLocal: QuestionLocal) {
        questionUseCase.pushQuestion(questionLocal)
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

    /**
     * Добавляет премиум дни к профилю пользователя
     * @param days количество дней премиума для добавления
     */
    fun addPremiumDays(days: Int) = viewModelScope.launch(Dispatchers.Default) {
        val currentProfile = _profileState.value ?: return@launch
        val currentPremiumDays = currentProfile.datePremium.toIntOrNull() ?: 0
        val newPremiumDays = currentPremiumDays + days

        val updatedProfile = currentProfile.copy(
            datePremium = newPremiumDays.toString()
        )

        // Обновляем профиль в базе данных
        profileUseCase.updateProfile(updatedProfile)

        // Обновляем локальное состояние
        _profileState.value = updatedProfile
        
        // Обновляем состояние премиума в контроллере
        profileInteractor.updatePremium()
    }

    /**
     * Добавляет новый логотип в коллекцию пользователя
     * @param logoName название логотипа для добавления
     */
    fun addLogo(logoName: String) = viewModelScope.launch(Dispatchers.Default) {
        val currentProfile = _profileState.value ?: return@launch
        val currentLogos = currentProfile.logo ?: ""
        val updatedLogos = if (currentLogos == "") logoName else "$currentLogos,$logoName"

        val updatedProfile = currentProfile.copy(
            logo = updatedLogos
        )

        // Обновляем профиль в базе данных
        profileUseCase.updateProfile(updatedProfile)

        // Обновляем локальное состояние
        _profileState.value = updatedProfile
    }
}
