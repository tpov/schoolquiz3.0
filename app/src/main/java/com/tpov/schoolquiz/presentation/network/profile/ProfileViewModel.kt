package com.tpov.schoolquiz.presentation.network.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.DataUseCase
import com.tpov.schoolquiz.domain.PlayersUseCase
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.domain.QuizUseCase
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@InternalCoroutinesApi
class ProfileViewModel @Inject constructor(
    private val dataUseCase: DataUseCase,
    private val playersUseCase: PlayersUseCase,
    private val profileUseCase: ProfileUseCase,
    private val quizUseCase: QuizUseCase
) : ViewModel() {

    var addQuestion = MutableLiveData<Int>()
    var addInfoQuestion = MutableLiveData<Int>()
    var addQuiz = MutableLiveData<Int>()

   fun getPlayer() = playersUseCase.getPlayers()
       .filter { it.id == SharedPreferencesManager.getTpovId() }[0]

    fun getTpovId() {
        dataUseCase.downloadTpovId()
    }

    fun setQuizFB() {
        log("fun setQuizFB()")
        viewModelScope.launch(Dispatchers.IO) {
            quizUseCase.unloadQuizUseCase()
        }
    }

    fun setProfile() {
        log("fun setProfile()")
        profileUseCase.unloadProfile()
    }

    fun getSynthProfile() {
        log("fun getProfile()")
        profileUseCase.downloadProfile()
    }

    fun updateProfile(profile: ProfileEntity) {
        profileUseCase.updateProfile(profile)
    }

    fun getProfile() = profileUseCase.getProfile(SharedPreferencesManager.getTpovId())

    suspend fun getQuizzFB() {
        log("fun getQuizzFB()")
        quizUseCase.downloadQuizes()
    }

    fun getPlayersList() {
        log("getPlayersList()")
        playersUseCase.downloadPlayers()
    }

    fun log(m: String) {
        Logcat.log(m, "Profile", Logcat.LOG_VIEW_MODEL)
    }

    fun getDeleteAllQuiz() {
        quizUseCase.deleteQuiz()
    }
}