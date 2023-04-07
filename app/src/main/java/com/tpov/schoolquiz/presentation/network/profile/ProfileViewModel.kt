package com.tpov.schoolquiz.presentation.network.profile

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
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

    private val getQuestion1FBUseCase: GetQuestion1FBUseCase,
    private val getQuiz1FBUseCase: GetQuiz1FBUseCase,
    private val getQuestionDetail1FBUseCase: GetQuestionDetail1FBUseCase,

    private val getQuestion2FBUseCase: GetQuestion2FBUseCase,
    private val getQuiz2FBUseCase: GetQuiz2FBUseCase,
    private val getQuestionDetail2FBUseCase: GetQuestionDetail2FBUseCase,
    private val getSynthUseCase: GetSynthUseCase

) : ViewModel() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    var addQuestion = MutableLiveData<Int>()
    var addInfoQuestion = MutableLiveData<Int>()
    var addQuiz = MutableLiveData<Int>()
    var synth = getSynthUseCase()

    var tpovId = 0


    fun getTpovId() {
        getTpovIdFBUseCase()
    }



    fun setQuizFB() {
        log("fun setQuizFB()")
        viewModelScope.launch {
            setQuizDataFBUseCase()
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

    fun getQuizzFB() {
        log("fun getQuizzFB()")
        getQuiz1FBUseCase()
        getQuiz2FBUseCase()
    }

    fun getQuestions2FB() {
        log("fun getQuestions2FB()")
        getQuestion2FBUseCase()
        getQuestionDetail2FBUseCase()
    }

    fun getQuestions1FB() {
        log("fun getQuestions1FB()")
        getQuestion1FBUseCase()
        getQuestionDetail1FBUseCase()
    }

    fun log(m: String) {
        Logcat.log(m, "Profile", Logcat.LOG_VIEW_MODEL)
    }
}