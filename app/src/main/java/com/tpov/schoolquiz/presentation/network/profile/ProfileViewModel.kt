package com.tpov.schoolquiz.presentation.network.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@InternalCoroutinesApi
class ProfileViewModel @Inject constructor(
    private val setProfileFBUseCase: SetProfileFBUseCase,
    private val getProfileFBUseCase: GetProfileFBUseCase,
    private val getTpovIdFBUseCase: GetTpovIdFBUseCase,
    private val getQuiz8UseCase: GetQuiz8UseCase,
    private val getQuizUseCase: GetQuizUseCase,
    private val setQuizUseCase: SetQuizUseCase,
    private val getSynthUseCase: GetSynthUseCase,
    private val deleteAllQuizUseCase: DeleteAllQuizUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    val getPlayersDBUseCase: GetPlayersDBUseCase,
    private val getPlayersListUseCase: GetPlayersListUseCase

) : ViewModel() {

    var addQuestion = MutableLiveData<Int>()
    var addInfoQuestion = MutableLiveData<Int>()
    var addQuiz = MutableLiveData<Int>()
    var synth = getSynthUseCase()

   fun getPlayer() = getPlayersDBUseCase()
       .filter { it.id == SharedPreferencesManager.getTpovId() }[0]

    fun getTpovId() {
        getTpovIdFBUseCase()
    }

    fun setQuizFB() {
        log("fun setQuizFB()")
        viewModelScope.launch(Dispatchers.IO) {
            setQuizUseCase()
        }
    }

    fun setProfile() {
        log("fun setProfile()")
        setProfileFBUseCase()
    }

    fun getProfile() {
        log("fun getProfile()")
        getProfileFBUseCase()
    }

    suspend fun getQuizzFB() {
        log("fun getQuizzFB()")
        getQuizUseCase()
    }

    fun getPlayersList() {
        log("getPlayersList()")
        getPlayersListUseCase()
    }

    fun log(m: String) {
        Logcat.log(m, "Profile", Logcat.LOG_VIEW_MODEL)
    }

    fun getDeleteAllQuiz() {
        deleteAllQuizUseCase()
    }
}