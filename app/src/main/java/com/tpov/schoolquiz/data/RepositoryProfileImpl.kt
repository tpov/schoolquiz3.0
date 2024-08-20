package com.tpov.schoolquiz.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tpov.common.COUNT_LIFE_POINTS_IN_LIFE
import com.tpov.common.COUNT_MAX_LIFE
import com.tpov.common.COUNT_MAX_LIFE_GOLD
import com.tpov.common.DEFAULT_TPOVID
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.utils.TimeManager
import com.tpov.common.presentation.utils.Values
import com.tpov.common.presentation.utils.Values.context
import com.tpov.common.presentation.utils.Values.synth
import com.tpov.common.presentation.utils.Values.synthLiveData
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import com.tpov.schoolquiz.data.fierbase.toProfile
import com.tpov.schoolquiz.data.fierbase.toProfileEntity
import com.tpov.schoolquiz.domain.repository.RepositoryProfile
import com.tpov.schoolquiz.presentation.core.KEY_ADD_GOLD
import com.tpov.schoolquiz.presentation.core.KEY_ADD_MASSAGE
import com.tpov.schoolquiz.presentation.core.KEY_ADD_NOLICS
import com.tpov.schoolquiz.presentation.core.KEY_ADD_POINTS
import com.tpov.schoolquiz.presentation.core.KEY_ADD_SKILL
import com.tpov.schoolquiz.presentation.core.KEY_ADD_TROPHY
import com.tpov.schoolquiz.presentation.core.KEY_ADMIN
import com.tpov.schoolquiz.presentation.core.KEY_ID_USER
import com.tpov.schoolquiz.presentation.core.KEY_QUALIFICATION
import com.tpov.schoolquiz.presentation.core.KEY_SPONSOR
import com.tpov.schoolquiz.presentation.core.KEY_TESTER
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.PATH_LIST_TPOV_ID
import com.tpov.schoolquiz.presentation.core.PATH_PLAYERS
import com.tpov.schoolquiz.presentation.core.PATH_PROFILES
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class RepositoryProfileImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val quizDao: QuizDao
) : RepositoryProfile {

    fun setTpovIdFB() {

        log("fun setTpovIdFB()")
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("players")
        val uid = FirebaseAuth.getInstance().uid
        log("setTpovIdFB() tpovId = ${SharedPreferencesManager.getTpovId()}")

        ref.child("$PATH_LIST_TPOV_ID/$uid").setValue(SharedPreferencesManager.getTpovId().toString()).addOnSuccessListener {
            log("setTpovIdFB() успех загрузки на сервер")
            synthLiveData.value = ++synth
        }.addOnFailureListener {
            log("setTpovIdFB() ошибка: $it")
        }
    }

    override fun getProfileFlow(tpovId: Int) = profileDao.getProfileFlow(tpovId)

    override fun getProfile(tpovId: Int) = profileDao.getProfile(tpovId)

    override fun getProfileList() = profileDao.getAllProfiles()

    override fun insertProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    override fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfiles(profile)
    }

    override fun unloadProfile() {
        log("fun setProfile()")
        val database = FirebaseDatabase.getInstance()
        val profileRef = database.getReference(PATH_PROFILES)
        val profilesRef = database.getReference(PATH_PLAYERS)
        var idUsers = 0
        var oldIdUser = 0

        Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_profile))
        val tpovId = SharedPreferencesManager.getTpovId()
        val profile = profileDao.getProfileByTpovId(tpovId)

        log("setProfile() tpovId: $tpovId")
        if (tpovId == DEFAULT_TPOVID) {

            profilesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    log("setProfile() snapshot: ${snapshot.key}")
                    idUsers =
                        ((snapshot.value as Map<*, *>)[KEY_ID_USER] as Long).toInt() // Получение значения переменной allQuiz
                    oldIdUser = tpovId
                    idUsers++

                    log("setProfile() idUsers + 1: $idUsers")
                    profilesRef.updateChildren(
                        hashMapOf<String, Any>(
                            KEY_ID_USER to idUsers
                        )
                    )

                    profileRef.child(idUsers.toString()).setValue(
                        profile.copy(
                            tpovId = idUsers,
                            idFirebase = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                            dateSynch = TimeManager.getCurrentTime()
                        ).toProfile()

                    ).addOnSuccessListener {

                        CoroutineScope(Dispatchers.IO).launch {
                            profileDao.updateProfiles(
                                profile.copy(
                                    tpovId = idUsers,
                                    idFirebase = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                    dateSynch = TimeManager.getCurrentTime()
                                )
                            )

                            quizDao.getQuizList(oldIdUser).forEach {
                                quizDao.updateQuiz(it.copy(tpovId = idUsers))
                            }

                            SharedPreferencesManager.setTpovId(idUsers)
                            setTpovIdFB()

                            log("setProfile() tpovId: $tpovId")

                            Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_profile_error))
                        }
                    }.addOnFailureListener {
                        log("setProfile() error1: $it")
                        Values.loadText.postValue("")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    log("setProfile() error2: $error")
                    Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_profile_error))
                }
            })

        } else {
            log("setProfile() id != 0 просто сохраняем на сервер profile: $profile, tpovId: $tpovId")
            profileRef.child("$tpovId").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(profileSnapshot: DataSnapshot) {

                    log(
                        "setProfile() 255: ${
                            profileSnapshot.child("addPoints").child("addNolics")
                                .getValue(Int::class.java)
                        }"
                    )
                    try {
                        profileRef.child(tpovId.toString()).setValue(
                            profile.copy(

                                translater = profileSnapshot.child(KEY_QUALIFICATION)
                                    .child("translater").getValue(Int::class.java),

                                admin = profileSnapshot.child(KEY_QUALIFICATION).child(KEY_ADMIN)
                                    .getValue(Int::class.java),

                                developer = profileSnapshot.child(KEY_QUALIFICATION)
                                    .child("developer").getValue(Int::class.java),

                                moderator = profileSnapshot.child(KEY_QUALIFICATION)
                                    .child("moderator").getValue(Int::class.java),

                                sponsor = profileSnapshot.child(KEY_QUALIFICATION).child(KEY_SPONSOR)
                                    .getValue(Int::class.java),

                                tester = profileSnapshot.child(KEY_QUALIFICATION).child(KEY_TESTER)
                                    .getValue(Int::class.java),

                                addTrophy = try {
                                    if (profile.addTrophy == "") profileSnapshot.child(
                                        KEY_ADD_POINTS
                                    )
                                        .child(KEY_ADD_TROPHY).getValue(String::class.java) else ""
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_TROPHY)
                                        .getValue(String::class.java) ?: ""
                                },

                                addMassage = try {
                                    if (profile.addMassage == "") profileSnapshot.child(
                                        KEY_ADD_MASSAGE
                                    )
                                        .child(KEY_ADD_MASSAGE).getValue(String::class.java) else ""
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_MASSAGE)
                                        .getValue(String::class.java) ?: ""
                                },

                                addPointsNolics = try {
                                    if (profile.addPointsNolics == 0) profileSnapshot.child(
                                        KEY_ADD_POINTS
                                    )
                                        .child(KEY_ADD_NOLICS).getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_NOLICS)
                                        .getValue(Int::class.java) ?: 0
                                },

                                addPointsGold = try {
                                    if (profile.addPointsGold == 0) profileSnapshot.child(
                                        KEY_ADD_POINTS
                                    )
                                        .child(KEY_ADD_GOLD).getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_GOLD)
                                        .getValue(Int::class.java) ?: 0
                                },

                                addPointsSkill = try {
                                    if (profile.addPointsSkill == 0) profileSnapshot.child(
                                        KEY_ADD_POINTS
                                    )
                                        .child(KEY_ADD_SKILL).getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_SKILL)
                                        .getValue(Int::class.java) ?: 0
                                }
                            ).toProfile()
                        ).addOnSuccessListener {
                            Values.loadText.value = ("")
                            synthLiveData.value = ++synth
                        }.addOnFailureListener {
                            Values.loadText.value = (context.getString(com.tpov.schoolquiz.R.string.fb_load_text_get_profile_error))
                            log("setProfile() $it")
                            synthLiveData.value = ++synth
                        }
                    } catch (e: Exception) {
                        Values.loadText.value = (context.getString(com.tpov.schoolquiz.R.string.fb_load_text_get_profile_error))
                        synthLiveData.value = ++synth
                    }

                }

                override fun onCancelled(error: DatabaseError) {

                    Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_get_profile_error))
                    log("setProfile() error24 $error")
                }
            })
        }
    }

    override fun downloadProfile() {
        log("fun getProfile()")
        val profileRef = FirebaseDatabase.getInstance().getReference(PATH_PROFILES)

        Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_profile))
        profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getProfile() snapshot: ${snapshot.key}")
                val tpovId = SharedPreferencesManager.getTpovId()
                val profile = snapshot.child("$tpovId").getValue(Profile::class.java)

                log("getProfile() tpovId: $tpovId")

                if (profile != null) {
                    log("getProfile() профиль не пустой")
                    if (profileDao.getProfileByTpovId(tpovId) == null) {
                        log("getProfile() профиль по tpovid пустой, создаем новый")
                        profileDao.insertProfile(profile.toProfileEntity(
                            COUNT_LIFE_POINTS_IN_LIFE * COUNT_MAX_LIFE_GOLD,
                            COUNT_LIFE_POINTS_IN_LIFE * COUNT_MAX_LIFE
                        ))
                        synthLiveData.value = ++synth
                    } else {
                        log("getProfile() профиль по tpovid найден $profile")
                        val updatedProfile = profileDao.getProfileByFirebaseId(
                            FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        ).copy(
                            addPointsGold = profile.addPoints.addGold,
                            addPointsNolics = profile.addPoints.addNolics,
                            addTrophy = profile.addPoints.addTrophy,
                            addMassage = profile.addPoints.addMassage,
                            addPointsSkill = profile.addPoints.addSkill,
                            sponsor = profile.qualification.sponsor,
                            tester = profile.qualification.tester,
                            translater = profile.qualification.translater,
                            moderator = profile.qualification.moderator,
                            admin = profile.qualification.admin,
                            developer = profile.qualification.developer,
                            dateSynch = TimeManager.getCurrentTime()
                        )
                        log("getProfile ${profileDao.updateProfiles(updatedProfile)}")
                        if (profileDao.updateProfiles(updatedProfile) > 0) synthLiveData.value = ++synth
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {
                log("getProfile() ошибка: $error")
            }
        })
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryProfile", Logcat.LOG_FIREBASE)
    }

}