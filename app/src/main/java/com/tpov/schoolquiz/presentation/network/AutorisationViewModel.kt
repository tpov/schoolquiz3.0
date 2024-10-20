package com.tpov.schoolquiz.presentation.network

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.setTpovId
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject


class AutorisationViewModel @Inject constructor(
    private val profileUseCase: ProfileUseCase
) : ViewModel() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    var someData = MutableLiveData<Int>()

    init {
        log("fun init")
        someData.value = 0
    }

    fun loginAcc(email: String, pass: String, context: Context) {
        log("fun loginAcc")
        auth.signInWithEmailAndPassword(email, pass).addOnSuccessListener {
            log("loginAcc профиль успешно залогинен")
            updateProfile()
        }.addOnFailureListener {

            log("loginAcc ошибка залогина: $it")
            Toast.makeText(context, "error login, try again $it", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateProfile() {
        log("fun updateProfile")
        someData.value = someData.value?.plus(1)
    }

    fun getProfile(): ProfileEntity {
        return profileUseCase.getProfile(getTpovId())
    }

    fun createAcc(
        email: String,
        pass: String,
        context: Context,
        name: String,
        nickname: String,
        date: String,
        city: String,
        languages: String,
        invitation: Int?
    ) {
        log("fun createAcc")
        auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener {
            log("createAcc успешно зареган акк")

            val user = auth.currentUser
            user?.sendEmailVerification()?.addOnCompleteListener { task ->
                log("createAcc отправилось смс на почту")
                if (task.isSuccessful) {

                    val tpovId = getTpovId()

                    var pr = profileUseCase.getProfile(tpovId)
                    log("createAcc try: ${pr.dateSynch}")
                    if ( try {
                        pr.dateSynch!!.isNotEmpty()
                    } catch (e: Exception) {
                            false
                        }
                    ) {
                        log("createAcc текущий профиль уже синхронизирован, создаем новый")
                        val profile = Profile(
                            0.toString(),
                            email,
                            name,
                            nickname,
                            date,
                            Points( 0, 0, 0),
                            "0",
                            Buy( 1, "", "", ""),
                            "",
                            "",
                            city,
                            0,
                            TimeInGames(0, 0, 0, 0, 0, 0),
                            AddPoints( 0, 0, 0, "", ""),
                            Dates(
                                TimeManager.getCurrentTime(),
                                ""
                            ),
                            auth.currentUser?.uid ?: "",
                            languages,
                            Qualification(0, 0, 0, 0, 0, 0),
                            Life(1, 0),
                            Box(0, TimeManager.getCurrentTime(), 0),
                            invitation ?: 0
                        )

                        insertProfile(profile)
                        setProfileTpovId(0)

                        Toast.makeText(
                            context,
                            "Переход на новвый аккаунт, старый сохранен на телефоне",
                            Toast.LENGTH_LONG
                        ).show()

                    } else {

                        log("createAcc текущий профиль еще не был синхронизирован, используем его")
                        val profile = pr.copy(
                            login = email,
                            nickname = nickname,
                            name = name,
                            birthday = date,
                            city = city,
                            languages = languages,
                            dateSynch = TimeManager.getCurrentTime(),
                            commander = invitation ?: 0
                        )
                        Toast.makeText(
                            context,
                            "Ваш аккаунт копируется и создается на сервер.",
                            Toast.LENGTH_LONG
                        ).show()
                        profileUseCase.updateProfile(profile!!)
                    }

                    updateProfile()
                } else {
                    log("createAcc ошибка $task")
                    Toast.makeText(context, "Failed to send verification email.", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, it.toString(), Toast.LENGTH_LONG).show()
        }
    }
    //todo start Activity

    private fun setProfileTpovId(id: Int) {
        setTpovId(id)
        log("setProfileTpovId set tpovId = $id")
    }

    private fun insertProfile(profile: Profile) {
        log("fun insertProfile")
        profileUseCase.insertProfile(profile.toProfileEntity(0, 100))
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "Autorisation", Logcat.LOG_VIEW_MODEL)
    }
}