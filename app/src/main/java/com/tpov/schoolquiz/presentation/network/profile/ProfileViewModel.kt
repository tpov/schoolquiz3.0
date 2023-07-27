package com.tpov.schoolquiz.presentation.network.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.domain.DeleteAllQuizUseCase
import com.tpov.schoolquiz.domain.GetPlayersDBUseCase
import com.tpov.schoolquiz.domain.GetPlayersListUseCase
import com.tpov.schoolquiz.domain.GetProfileFBUseCase
import com.tpov.schoolquiz.domain.GetProfileUseCase
import com.tpov.schoolquiz.domain.GetQuiz1FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz2FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz3FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz4FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz5FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz6FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz7FBUseCase
import com.tpov.schoolquiz.domain.GetQuiz8FBUseCase
import com.tpov.schoolquiz.domain.GetSynthUseCase
import com.tpov.schoolquiz.domain.GetTpovIdFBUseCase
import com.tpov.schoolquiz.domain.GetTranslateUseCase
import com.tpov.schoolquiz.domain.SetProfileFBUseCase
import com.tpov.schoolquiz.domain.SetQuestionDetailFBUseCase
import com.tpov.schoolquiz.domain.SetQuestionFBUseCase
import com.tpov.schoolquiz.domain.SetQuizDataFBUseCase
import com.tpov.schoolquiz.domain.SetQuizEventUseCase
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@InternalCoroutinesApi
class ProfileViewModel @Inject constructor(
    private val setProfileFBUseCase: SetProfileFBUseCase,
    private val setQuestionFBUseCase: SetQuestionFBUseCase,
    private val setQuizDataFBUseCase: SetQuizDataFBUseCase,
    private val setQuestionDetailFBUseCase: SetQuestionDetailFBUseCase,
    private val getProfileFBUseCase: GetProfileFBUseCase,
    private val getTpovIdFBUseCase: GetTpovIdFBUseCase,

    private val getQuiz1FBUseCase: GetQuiz1FBUseCase,
    private val getQuiz2FBUseCase: GetQuiz2FBUseCase,
    private val getQuiz3FBUseCase: GetQuiz3FBUseCase,

    private val getQuiz4FBUseCase: GetQuiz4FBUseCase,
    private val getQuiz5FBUseCase: GetQuiz5FBUseCase,
    private val getQuiz6FBUseCase: GetQuiz6FBUseCase,
    private val getQuiz7FBUseCase: GetQuiz7FBUseCase,
    private val getQuiz8FBUseCase: GetQuiz8FBUseCase,
    private val getSynthUseCase: GetSynthUseCase,
    private val setQuizEventUseCase: SetQuizEventUseCase,
    private val deleteAllQuizUseCase: DeleteAllQuizUseCase,
    private val getTranslateUseCase: GetTranslateUseCase,
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
            setQuizDataFBUseCase()
        }
    }

    fun setEventQuiz() {
        log("fun setEventQuiz()")
        viewModelScope.launch {
            setQuizEventUseCase()
        }
    }

    fun setQuestionsFB() {
        log("fun setQuestionsFB()")
        setQuestionFBUseCase()
        setQuestionDetailFBUseCase()
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
        getQuiz1FBUseCase()
        getQuiz2FBUseCase()
        getQuiz3FBUseCase()
        getQuiz4FBUseCase()
        getQuiz5FBUseCase()
        getQuiz6FBUseCase()
        getQuiz7FBUseCase()
        getQuiz8FBUseCase()
    }

    suspend fun getTranslate() {
        getTranslateUseCase()
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