package com.tpov.schoolquiz.presentation.network.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
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
    //private val getPlayersListUseCase: GetPlayersListUseCase

) : ViewModel() {

    var addQuestion = MutableLiveData<Int>()
    var addInfoQuestion = MutableLiveData<Int>()
    var addQuiz = MutableLiveData<Int>()
    var synth = getSynthUseCase()


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
        //getPlayersListUseCase()
    }

    fun log(m: String) {
        Logcat.log(m, "Profile", Logcat.LOG_VIEW_MODEL)
    }

    fun getDeleteAllQuiz() {
        deleteAllQuizUseCase()
    }
}