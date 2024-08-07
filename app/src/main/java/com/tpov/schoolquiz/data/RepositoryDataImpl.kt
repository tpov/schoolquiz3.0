package com.tpov.schoolquiz.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tpov.common.data.dao.QuizDao
import com.tpov.schoolquiz.data.database.DataDao
import com.tpov.schoolquiz.data.fierbase.Profile
import com.tpov.schoolquiz.domain.repository.RepositoryData
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.PATH_LIST_TPOV_ID
import com.tpov.schoolquiz.presentation.core.PATH_PLAYERS
import com.tpov.schoolquiz.presentation.core.PATH_PROFILES
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.core.Values
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class RepositoryDataImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val dataDao: DataDao
) : RepositoryData {

    fun getUserName(): Profile {
        val tpovId = SharedPreferencesManager.getTpovId()
        log("fun getUserName()")
        val profileRef = FirebaseDatabase.getInstance().getReference(PATH_PROFILES)
        var profile = Profile()

        profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getUserName() snapshot: ${snapshot.key}")
                profile = snapshot.child("$tpovId").getValue(Profile::class.java)!!
            }

            override fun onCancelled(error: DatabaseError) {
                log("getUserName() ошибка ")
            }

        })
        return profile
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryDataImpl", Logcat.LOG_FIREBASE)
    }

    override fun getTpovIdByEmail(email: String) = dataDao.getTpovIdByEmailDB(email)

    override fun downloadTpovId() {
            Values.synth = 0
            Values.synthLiveData.value = 0
            log("fun getTpovIdFB()")
            val database = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().uid
            val ref = database.getReference(PATH_PLAYERS)

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    try {
                        val tpovId: String =
                            snapshot.child("$PATH_LIST_TPOV_ID/$uid").getValue(String::class.java)!!
                        SharedPreferencesManager.setTpovId(tpovId.toInt())
                    } catch (e: Exception) {
                        log("getTpovIdFB error get")
                    } finally {
                        log("getTpovIdFB finally")
                        Values.synthLiveData.value = ++Values.synth
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    log("getTpovIdFB() ошибка $error")
                }
            })
    }
}