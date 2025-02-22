package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.Core
import com.tpov.common.Core.tpovIdFlow
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.log_api.logger.Logger
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@Logger
@OptIn(FlowPreview::class)
class MainViewModel @Inject constructor(
    private val structureUseCase: StructureUseCase,
    private val questionUseCase: QuestionUseCase,
    private val profileUseCase: ProfileUseCase,
    private val context: Context
) : ViewModel() {

    val profileState: StateFlow<ProfileEntity?> get() = _profileState
    private val _profileState = MutableStateFlow<ProfileEntity?>(null)

    val questionData: StateFlow<List<QuestionEntity>?> get() = _questionData
    private val _questionData = MutableStateFlow<List<QuestionEntity>?>(null)
    val structureData: StateFlow<List<StructureDataLocal?>?> get() = _structureData
    private val _structureData = MutableStateFlow<List<StructureDataLocal?>?>(null)

    var firstStartApp = false

    fun initStructureData(eventId: Int) = viewModelScope.launch(Dispatchers.IO) {
        _structureData.value = structureUseCase.getStructureCategoryList(eventId)?.children?.toList()
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
                            && it.idQuiz == question.idQuiz
                            && it.idCategory == question.idCategory
                            && it.idSubCategory == question.idSubCategory
                            && it.idEvent == question.idEvent
                            && it.idSubsubCategory == question.idSubsubCategory
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
        Log.d("pushTheQuiz", "pushingQuestion")
        questionUseCase.pushQuestion(questionEntity)
    }
}
