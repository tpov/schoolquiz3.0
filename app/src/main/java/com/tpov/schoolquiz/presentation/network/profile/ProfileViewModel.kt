package com.tpov.schoolquiz.presentation.network.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.LocalUseCase
import com.tpov.schoolquiz.domain.RemoveUseCase
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@InternalCoroutinesApi
class ProfileViewModel @Inject constructor(
    private val localUseCase: LocalUseCase,
    private val removeUseCase: RemoveUseCase
) : ViewModel() {

    var addQuestion = MutableLiveData<Int>()
    var addInfoQuestion = MutableLiveData<Int>()
    var addQuiz = MutableLiveData<Int>()
    var synth = removeUseCase.getSynth()

   fun getPlayer() = localUseCase.getPlayersDB()
       .filter { it.id == SharedPreferencesManager.getTpovId() }[0]

    fun getTpovId() {
        removeUseCase.getTpovIdFB()
    }

    fun setQuizFB() {
        log("fun setQuizFB()")
        viewModelScope.launch(Dispatchers.IO) {
            removeUseCase.setQuiz()
        }
    }

    fun setProfile() {
        log("fun setProfile()")
        removeUseCase.setProfileFB()
    }

    fun getSynthProfile() {
        log("fun getProfile()")
        removeUseCase.getProfileFB()
    }

    fun updateProfile(profile: ProfileEntity) {
        localUseCase.updateProfile(profile)
    }

    fun getProfile() = localUseCase.getProfile(SharedPreferencesManager.getTpovId())

    suspend fun getQuizzFB() {
        log("fun getQuizzFB()")
        removeUseCase.getQuiz()
    }

    fun getPlayersList() {
        log("getPlayersList()")
        removeUseCase.getPlayersList()
    }

    fun log(m: String) {
        Logcat.log(m, "Profile", Logcat.LOG_VIEW_MODEL)
    }

    fun getDeleteAllQuiz() {
        removeUseCase.deleteAllQuiz()
    }
}