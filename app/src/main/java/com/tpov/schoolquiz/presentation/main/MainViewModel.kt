package com.tpov.schoolquiz.presentation.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

class MainViewModel @Inject constructor(
    private val structureUseCase: StructureUseCase,
    private val quizUseCase: QuizUseCase,
    private val questionUseCase: QuestionUseCase,
    private val profileUseCase: ProfileUseCase
) : ViewModel() {

    val categoryData: LiveData<List<CategoryData>> get() = _categoryData
    private val _categoryData = MutableLiveData<List<CategoryData>>()
    val profileState: StateFlow<ProfileEntity?> get() = _profileState
    private val _profileState = MutableStateFlow<ProfileEntity?>(null)

    var firstStartApp = false

    init {
        viewModelScope.launch {
            profileUseCase.getProfileFlow(tpovId).collect { profile ->
                _profileState.value = profile
            }
        }
    }

    fun updateProfile(profileEntity: ProfileEntity) {
        profileUseCase.updateProfile(profileEntity)
    }

    suspend fun loadHomeCategory(): List<String> {
        var listNewQuiz: List<String> = listOf()

        var structureDataList = structureUseCase.getStructureData()
        if (structureDataList == null) {
            firstStartApp = true
            listNewQuiz = structureUseCase.syncData()
            structureDataList = structureUseCase.getStructureData()
        }
        withContext(Dispatchers.Main) {
            _categoryData.value = prepareData(structureDataList ?: retryFromDelay())
        }
        return listNewQuiz
    }

    //This fun started only first run
    suspend fun loadQuizByStructure(listNewQuiz: List<String>) {
        listNewQuiz.forEach { quizStructure ->
            val categories = quizStructure.split("|")

            if (categories.size >= 5) {
                val typeId = categories[0].toIntOrNull() ?: errorGetDataFromStructure()
                val categoryId = categories[1].toIntOrNull() ?: errorGetDataFromStructure()
                val subcategoryId = categories[2].toIntOrNull() ?: errorGetDataFromStructure()
                val subsubcategoryId = categories[3].toIntOrNull() ?: errorGetDataFromStructure()
                val idQuiz = categories[4].toIntOrNull() ?: errorGetDataFromStructure()

                val starsMaxLocal = 0
                val starsAverageLocal = 0
                val ratingLocal = 0

                quizUseCase.fetchQuiz(
                    typeId,
                    categoryId,
                    subcategoryId,
                    subsubcategoryId,
                    starsMaxLocal,
                    starsAverageLocal,
                    ratingLocal
                )

                questionUseCase.fetchQuestion(
                    8,
                    categoryId,
                    subcategoryId,
                    Locale.getDefault().language, idQuiz
                )
            }
        }
    }

    suspend fun createProfile() {
        val currentTimestamp = Instant.now().epochSecond
        val userLocalDate = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        profileUseCase.insertProfile(
            ProfileEntity(
                dataCreateAcc = currentTimestamp.toString(),
                dateSynch = currentTimestamp.toString(),
                languages = Locale.getDefault().language,
                timeLastOpenBox = userLocalDate
            )
        )
    }

    private suspend fun retryFromDelay(maxAttempts: Int = 100): StructureData {
        var attempt = 0
        while (attempt < maxAttempts) {
            delay(1000L * (attempt + 1))
            val data = structureUseCase.getStructureData()
            if (data != null) {
                return data
            }
            attempt++
        }
        return null!!                   //Best practices
    }

    private fun prepareData(structureDataList: StructureData): List<CategoryData> {
        return structureDataList.event.filter { it.id == 8 }.flatMap { eventData ->
            eventData.category.filter { it.isShowDownload }
        }
    }

    private fun errorGetDataFromStructure(): Int {
        return 0
    }

}

class ViewModelFactory @Inject constructor(
    private val creators: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass] ?: creators.entries.first {
            modelClass.isAssignableFrom(
                it.key
            )
        }.value
        return creator.get() as T
    }
}
