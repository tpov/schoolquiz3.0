package com.tpov.schoolquiz.presentation.network

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject


class AutorisationViewModel @Inject constructor(
    private val insertProfileUseCase: InsertProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val getProfileUseCase: GetProfileUseCase
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

    fun createAcc(
        email: String,
        pass: String,
        context: Context,
        name: String,
        nickname: String,
        date: String,
        city: String,
        languages: String
    ) {
        log("fun createAcc")
        auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener {
            log("createAcc успешно зареган акк")

            val user = auth.currentUser
            user?.sendEmailVerification()?.addOnCompleteListener { task ->
                log("createAcc отправилось смс на почту")
                if (task.isSuccessful) {
                    Toast.makeText(
                        context,
                        "Verification email sent to ${user.email}",
                        Toast.LENGTH_LONG
                    ).show()
                    val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
                    val tpovId = sharedPref?.getInt("tpovId", 0)

                    var pr = getProfileUseCase(tpovId ?: 0)
                    if (pr != null && !pr.dateSynch.isNullOrEmpty()) {
                        log("createAcc дата в текущем профиле есть")
                        val profile = Profile(
                            0,
                            email,
                            name,
                            nickname,
                            date,
                            Points(0, 0, 0, 0),
                            "0",
                            Buy(1, 0, 1, "0", "0", "0"),
                            "0",
                            "",
                            city,
                            0,
                            TimeInGames("", "", "0", 0),
                            AddPoints(0, 0, 0, 0, ""),
                            Dates(
                                TimeManager.getCurrentTime(),
                                ""
                            ),
                            auth.currentUser?.uid ?: "",
                            languages,
                            Qualification(0,0,0,0,0,0,0)
                        )

                        insertProfile(profile)
                        setProfileTpovId(0, context)

                        Toast.makeText(
                            context,
                            "Переход на новвый аккаунт, старый сохранен на телефоне",
                            Toast.LENGTH_LONG
                        ).show()

                    } else {

                        log("createAcc даты в текущем профиле нет")
                        val profile = pr.copy(
                            login = email,
                            name = name,
                            birthday = date,
                            city = city,
                            languages = languages,
                            dateSynch = TimeManager.getCurrentTime()
                        )
                        Toast.makeText(
                            context,
                            "Ваш аккаунт копируется и создается на сервер.",
                            Toast.LENGTH_LONG
                        ).show()
                        updateProfileUseCase(profile!!)
                    }

                    updateProfile()
                } else {
                    log("createAcc ошибка $task")
                    Toast.makeText(context, "Failed to send verification email.", Toast.LENGTH_LONG)
                        .show()
                }
            }
            //todo start Activity
        }
    }

    private fun setProfileTpovId(id: Int, context: Context) {
        log("fun setProfileTpovId")
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putInt("tpovId", id)
            apply()
        }
        log("setProfileTpovId set tpovId = $id")
    }

    private fun insertProfile(profile: Profile) {
        log("fun insertProfile")
        insertProfileUseCase(profile.toProfileEntity())
    }


    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "Autorisation", Logcat.LOG_VIEW_MODEL)
    }

}