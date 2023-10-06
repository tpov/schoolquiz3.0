package com.tpov.schoolquiz.data

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.custom.*
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getVersionQuiz
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.setTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.setVersionQuiz
import com.tpov.schoolquiz.presentation.custom.Values.loadProgress
import com.tpov.schoolquiz.presentation.custom.Values.loadText
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class RepositoryFBImpl @Inject constructor(
    private val dao: QuizDao, private val application: Application
) : RepositoryFB {

    private lateinit var chatValueEventListener: ValueEventListener
    private val context = application.baseContext
    var synthLiveData = MutableLiveData<Int>()
    var synth = 0
    var synthGetData = 0
    var synthSetData = 0
    override fun deleteAllQuiz() {
//        FirebaseDatabase.getInstance().getReference("question1").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question2").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question3").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question4").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question5").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question6").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question7").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question8").setValue(null)
//
//        FirebaseDatabase.getInstance().getReference("quiz1").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz2").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz3").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz4").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz5").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz6").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz7").setValue(null)
//        FirebaseDatabase.getInstance().getReference("quiz8").setValue(null)
//
//        FirebaseDatabase.getInstance().getReference("question_detail1").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail2").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail3").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail4").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail5").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail6").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail7").setValue(null)
//        FirebaseDatabase.getInstance().getReference("question_detail8").setValue(null)

    }

    @Synchronized
    fun setLoadPB(value: Int, max: Int) {
        log("ioioioio fun ${(value * 100) / max}%")
        loadProgress.value = ((value * 100) / max)
    }

    override fun getValSynth(): MutableLiveData<Int> = synthLiveData

    override fun getPlayersList() {
        loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_leaders))
        val playersListRef = FirebaseDatabase.getInstance().getReference(PATH_LIST_PLAYERS)
        playersListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playersList = mutableListOf<PlayersEntity>()
                log("getPlayersList snapshot: $snapshot")
                var i = 0
                for (playerSnapshot in snapshot.children) {
                    i++
                    log("getPlayersList playerSnapshot: $playerSnapshot")
                    val player = playerSnapshot.getValue(PlayersEntity::class.java)
                    if (player != null) {
                        log("getPlayersList player: $player")
                        playersList.add(
                            player.copy(id = playerSnapshot.key?.toInt())
                        )

                        setLoadPB(i, snapshot.childrenCount.toInt())
                    }
                }
                dao.deletePlayersList()
                dao.insertPlayersList(playersList)
                loadText.postValue("")
            }

            override fun onCancelled(error: DatabaseError) {
                // Обработка ошибок
            }

        })
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun getChatData(): Flow<List<ChatEntity>> {
        getPersonalMassage()
        val chatRef = FirebaseDatabase.getInstance().getReference(PATH_CHAT)
        val dateFormatKiev = SimpleDateFormat(DEFAULT_DATA_IN_GET_CHAT)
        dateFormatKiev.timeZone = TimeZone.getTimeZone(DEFAULT_LOCAL_IN_GET_CHAT)

        var countSmsDoNotWatch = 0
        loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_chat))
        chatValueEventListener =
            chatRef.limitToLast(20).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    log("getChatData snapshot: $snapshot")

                    var lastTime = SharedPreferencesManager.getTimeMassage()
                    // Получаем данные из snapshot и сохраняем их в локальную базу данных
                    for (dateSnapshot in snapshot.children) {
                        for (data in dateSnapshot.children) {
                            try {
                                val chat = data.getValue(Chat::class.java)
                                val date1 = dateFormatKiev.parse(chat?.time.toString())

                                if (chat != null) {

                                    if (lastTime == "0") {
                                        lastTime = System.currentTimeMillis().toString()
                                        SharedPreferencesManager.setTimeMassage(lastTime)
                                    }
                                    if (chat != null) {
                                        if (lastTime == "0") lastTime =
                                            System.currentTimeMillis().toString()
                                        val date2 =
                                            if (lastTime != null) Date(lastTime.toLong()) else null

                                        if (date2 != null && date1.after(date2)) {
                                            SharedPreferencesManager.setTimeMassage(date1.time.toString())
                                            dao.insertChat(chat.toChatEntity())
                                            countSmsDoNotWatch++
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                log("Error: ${e.message}")
                            }
                        }
                    }
                    loadText.value = context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_new_massage, countSmsDoNotWatch)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Обработка ошибок
                }
            })

        return dao.getChat()
    }

    override fun removeChatListener() {
        val chatRef = FirebaseDatabase.getInstance().getReference(PATH_CHAT)
        chatRef.removeEventListener(chatValueEventListener)
    }

    fun savePictureToLocalDirectory(
        pictureString: String, callback: (path: String?) -> Unit
    ) {
        if (!context.cacheDir.exists()) context.cacheDir.mkdir()
        val directory = File(context.cacheDir, "")
        FirebaseAuth.getInstance().currentUser?.uid

        val storage: FirebaseStorage = FirebaseStorage.getInstance()
        val storageRef: StorageReference = storage.reference
        val pathReference: StorageReference = storageRef.child("$PATH_PHOTO/$pictureString")

        val file = File(directory, "$pictureString")

        pathReference.getFile(file).addOnSuccessListener {
            // Обработка успешного скачивания картинки

            callback("$pictureString")
        }.addOnFailureListener {
            callback(null)
        }
    }

    private fun getPersonalMassage() {
        val chatRef =
            FirebaseDatabase.getInstance().getReference("$PATH_TRANSLATE/${getTpovId()}")
        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Получаем данные из snapshot и сохраняем их в локальную базу данных

                for (dateSnapshot in snapshot.children) {
                    for (idQuizSnapshot in dateSnapshot.children) {
                        for (numQuestion in idQuizSnapshot.children) {
                            for (languageSnap in numQuestion.children) {
                                for (chatSnap in languageSnap.children) {
                                    val chat = dateSnapshot.getValue(Chat::class.java)
                                    dao.insertChat(chat!!.toChatEntity())
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Обработка ошибок
            }
        })
    }

    override fun getProfile() {
        log("fun getProfile()")
        val profileRef = FirebaseDatabase.getInstance().getReference(PATH_PROFILES)

        loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_profile))
        profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getProfile() snapshot: ${snapshot.key}")
                val tpovId = getTpovId()
                val profile = snapshot.child("$tpovId").getValue(Profile::class.java)

                log("getProfile() tpovId: $tpovId")

                if (profile != null) {
                    log("getProfile() профиль не пустой")
                    if (dao.getProfileByTpovId(tpovId) == null) {
                        log("getProfile() профиль по tpovid пустой, создаем новый")
                        dao.insertProfile(profile.toProfileEntity(COUNT_LIFE_POINTS_IN_LIFE * COUNT_MAX_LIFE_GOLD,
                            COUNT_LIFE_POINTS_IN_LIFE * COUNT_MAX_LIFE))
                        synthLiveData.value = ++synth
                    } else {
                        log("getProfile() профиль по tpovid найден $profile")
                        val updatedProfile = dao.getProfileByFirebaseId(
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
                        log("getProfile ${dao.updateProfiles(updatedProfile)}")
                        if (dao.updateProfiles(updatedProfile) > 0) synthLiveData.value = ++synth
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {
                log("getProfile() ошибка: $error")
            }
        })
    }

    override fun setTpovIdFB() {

        log("fun setTpovIdFB()")
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("players")
        val uid = FirebaseAuth.getInstance().uid
        log("setTpovIdFB() tpovId = ${getTpovId()}")

        ref.child("$PATH_LIST_TPOV_ID/$uid").setValue(getTpovId().toString()).addOnSuccessListener {
            log("setTpovIdFB() успех загрузки на сервер")
            synthLiveData.value = ++synth
        }.addOnFailureListener {

            log("setTpovIdFB() ошибка: $it")
        }
    }

    override fun getTpovIdFB() {
        synth = 0
        synthLiveData.value = 0
        log("fun getTpovIdFB()")
        val database = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().uid
        val ref = database.getReference(PATH_PLAYERS)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                try {
                    val tpovId: String =
                        snapshot.child("$PATH_LIST_TPOV_ID/$uid").getValue(String::class.java)!!
                    setTpovId(tpovId.toInt())
                } catch (e: Exception) {
                    log("getTpovIdFB error get")
                } finally {
                    log("getTpovIdFB finally")
                    synthLiveData.value = ++synth
                }
            }

            override fun onCancelled(error: DatabaseError) {
                log("getTpovIdFB() ошибка $error")
            }
        })
    }

    private fun errorTpovId(): Int {
        getTpovIdFB()
        return getTpovId()
    }

    override fun setProfile() {
        log("fun setProfile()")
        val database = FirebaseDatabase.getInstance()
        val profileRef = database.getReference(PATH_PROFILES)
        val profilesRef = database.getReference(PATH_PLAYERS)
        var idUsers = 0
        var oldIdUser = 0

        loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_profile))
        val tpovId = getTpovId()
        val profile = dao.getProfileByTpovId(tpovId)

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
                            dao.updateProfiles(
                                profile.copy(
                                    tpovId = idUsers,
                                    idFirebase = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                    dateSynch = TimeManager.getCurrentTime()
                                )
                            )

                            dao.getQuizList(oldIdUser).forEach {
                                dao.updateQuiz(it.copy(tpovId = idUsers))
                            }

                            setTpovId(idUsers)
                            setTpovIdFB()

                            log("setProfile() tpovId: $tpovId")

                            loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_profile_error))
                        }
                    }.addOnFailureListener {
                        log("setProfile() error1: $it")
                        loadText.postValue("")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    log("setProfile() error2: $error")
                    loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_profile_error))
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
                                    if (profile.addTrophy == "") profileSnapshot.child(KEY_ADD_POINTS)
                                        .child(KEY_ADD_TROPHY).getValue(String::class.java) else ""
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_TROPHY)
                                        .getValue(String::class.java) ?: ""
                                },

                                addMassage = try {
                                    if (profile.addMassage == "") profileSnapshot.child(KEY_ADD_MASSAGE)
                                        .child(KEY_ADD_MASSAGE).getValue(String::class.java) else ""
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_MASSAGE)
                                        .getValue(String::class.java) ?: ""
                                },

                                addPointsNolics = try {
                                    if (profile.addPointsNolics == 0) profileSnapshot.child(KEY_ADD_POINTS)
                                        .child(KEY_ADD_NOLICS).getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_NOLICS)
                                        .getValue(Int::class.java) ?: 0
                                },

                                addPointsGold = try {
                                    if (profile.addPointsGold == 0) profileSnapshot.child(KEY_ADD_POINTS)
                                        .child(KEY_ADD_GOLD).getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_GOLD)
                                        .getValue(Int::class.java) ?: 0
                                },

                                addPointsSkill = try {
                                    if (profile.addPointsSkill == 0) profileSnapshot.child(KEY_ADD_POINTS)
                                        .child(KEY_ADD_SKILL).getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child(KEY_ADD_POINTS).child(KEY_ADD_SKILL)
                                        .getValue(Int::class.java) ?: 0
                                }
                            ).toProfile()
                        ).addOnSuccessListener {
                            loadText.value = ("")
                            synthLiveData.value = ++synth
                        }.addOnFailureListener {
                            loadText.value = (context.getString(com.tpov.schoolquiz.R.string.fb_load_text_get_profile_error))
                            log("setProfile() $it")
                            synthLiveData.value = ++synth
                        }
                    } catch (e: Exception) {
                        loadText.value = (context.getString(com.tpov.schoolquiz.R.string.fb_load_text_get_profile_error))
                        synthLiveData.value = ++synth
                    }

                }

                override fun onCancelled(error: DatabaseError) {

                    loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_get_profile_error))
                    log("setProfile() error24 $error")
                }
            })
        }

    }

    override fun getUserName(): Profile {
        val tpovId = getTpovId()
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

    override suspend fun getQuiz8FB() {
        getQuiz(PATH_HOME_QUIZ, getQuestionDetails = false)
    }

    override suspend fun getAllQuiz() {
        getQuiz()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun waitForReadToBecomeTrue(): Boolean = try {
        withTimeout(DELAY_TIMEOUT_SYNTH_FB) {
            suspendCancellableCoroutine { continuation ->
                val database = FirebaseDatabase.getInstance()
                val myRef = database.getReference(PATH_LIST_READ)

                val listener = object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val value = dataSnapshot.getValue(Boolean::class.java)
                        if (value == true) {
                            setRead(false)
                            myRef.removeEventListener(this)
                            continuation.resume(true) {}
                        } else loadText.value = (context.getString(com.tpov.schoolquiz.R.string.fb_load_text_weit_unlock_server))
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                    }
                }

                myRef.addValueEventListener(listener)

                continuation.invokeOnCancellation {
                    myRef.removeEventListener(listener)
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        setRead(true)
        true
    }

    private fun setRead(value: Boolean) {
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference(PATH_LIST_READ)
        myRef.setValue(value)
    }

    override suspend fun setAllQuiz() {
        val lockObject = Object()

        log("setAllQuiz")
        if (waitForReadToBecomeTrue()) {

            log("setAllQuiz waitForReadToBecomeTrue")

            val database = FirebaseDatabase.getInstance()
            loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_quizz))

            log("setAllQuiz 22 ${dao.getQuizList(getTpovId())}")
            var maxIdQuiz = 0
            var i = 0

            val quizEvent = dao.getQuizEvent()
            quizEvent.forEach {
                val eventQuiz = it.event
                val quizRef = database.getReference(
                    if (eventQuiz == 1) "$PATH_USER_QUIZ/${getTpovId()}" else "$PATH_QUIZ$eventQuiz"
                )

                log("setAllQuiz 3")
                val profileRef = FirebaseDatabase.getInstance().getReference(PATH_ID_QUIZ)
                profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {

                        log("setAllQuiz 4")
                        val newIdQuiz = if (it.id!! < BARRIER_QUIZ_ID_LOCAL_AND_REMOVE) {
                            val id = if (maxIdQuiz == 0) snapshot.getValue(Int::class.java)?.plus(1)
                            else maxIdQuiz + 1
                            profileRef.setValue(id)
                            maxIdQuiz = id ?: 0
                            maxIdQuiz
                        } else it.id

                        quizRef.child(newIdQuiz.toString())
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {

                                    val newVersionQuiz =
                                        snapshot.child(KEY_VERSION_QUIZ).getValue(Int::class.java)
                                            ?: -1

                                    log("setAllQuiz: $it")
                                    log("setAllQuiz: $newVersionQuiz = ${getVersionQuiz(newIdQuiz.toString())}")
                                    if (newVersionQuiz <= getVersionQuiz(newIdQuiz.toString())) {

                                        log("setAllQuiz setQuiz ")
                                        val newQuiz = Quiz(
                                            nameQuiz = it.nameQuiz,
                                            tpovId = it.tpovId,
                                            data = it.data,
                                            versionQuiz = newVersionQuiz,
                                            picture = it.picture ?: "",
                                            event = eventQuiz,
                                            numQ = it.numQ,
                                            numHQ = it.numHQ,
                                            starsAllPlayer = snapshot.child(KEY_STARS_ALL_PLAYER)
                                                .getValue(Int::class.java) ?: 0,
                                            starsPlayer = snapshot.child(KEY_STARS_PLAYER)
                                                .getValue(Int::class.java) ?: 0,
                                            ratingPlayer = it.ratingPlayer,
                                            userName = it.userName,
                                            languages = it.languages

                                        )
                                        quizRef.child(newIdQuiz.toString()).setValue(newQuiz)
                                        database.getReference("$PATH_QUIZ${eventQuiz - 1}")
                                            .child(newIdQuiz.toString()).setValue(null)
                                            .addOnSuccessListener { item ->
                                                if (isInsertQuizAfterSet(newQuiz)) dao.insertQuiz(
                                                    newQuiz.toQuizEntity(
                                                        newIdQuiz!!,
                                                        it.stars,
                                                        it.starsAll,
                                                        it.rating,
                                                        it.picture
                                                    )
                                                )
                                            }.addOnFailureListener { item ->
                                                if (isInsertQuizAfterSet(newQuiz)) dao.insertQuiz(
                                                    newQuiz.toQuizEntity(
                                                        newIdQuiz!!,
                                                        it.stars,
                                                        it.starsAll,
                                                        it.rating,
                                                        it.picture
                                                    )
                                                )
                                            }
                                        quizRef.child(newIdQuiz.toString()).setValue(newQuiz)
                                        dao.deleteQuizById(it.id!!)

                                        setQuestions(newIdQuiz, eventQuiz, it.id!!, newQuiz)
                                        setQuestionDetails(newIdQuiz, eventQuiz, it.id!!, newQuiz)
                                        setQuizResults(it)

                                        i++
                                        setLoadPB(i, quizEvent.size)
                                        if (i == quizEvent.size) synthLiveData.postValue(++synth)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }
                })
            }

            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    var exit = true
                    dao.getQuizList(getTpovId()).forEach {
                        if (it.id!! < BARRIER_QUIZ_ID_LOCAL_AND_REMOVE) exit = false
                    }

                    if (exit) {
                        setRead(true)

                        break
                    }
                    delay(300)
                }
            }
        }
    }

    private fun setQuizResults(quiz: QuizEntity) {
        val quizRatingMap = mapOf(
            KEY_RATING to quiz.ratingPlayer,
            KEY_STARS to quiz.stars,
            KEY_STARS_ALL to quiz.starsAll
        )
        if (quiz.ratingPlayer != 0) FirebaseDatabase.getInstance()
            .getReference("$PATH_PLAYERS_QUIZ/${quiz.id}/${getTpovId()}")
            .updateChildren(quizRatingMap)
    }

    private fun isInsertQuizAfterSet(quiz: Quiz): Boolean = quiz.event == EVENT_QUIZ_FOR_USER || quiz.event in EVENT_QUIZ_ARENA..EVENT_QUIZ_HOME
            || quiz.event in EVENT_QUIZ_FOR_TESTER..EVENT_QUIZ_FOR_ADMIN && quiz.tpovId != getTpovId() && quiz.ratingPlayer == 0

    private fun setQuestions(newIdQuiz: Int?, eventQuiz: Int?, id: Int, quiz: Quiz) {
        val questionRef = FirebaseDatabase.getInstance().getReference(
            if (eventQuiz != EVENT_QUIZ_FOR_USER) "$PATH_QUESTION$eventQuiz/$newIdQuiz"
            else "$PATH_USER_QUESTION/${getTpovId()}/$newIdQuiz"
        )
        log("setQuestions 1")

        if (isQuizForEvent(quiz)) {
            FirebaseDatabase.getInstance()
                .getReference("question${quiz.event - 1}")
                .child(newIdQuiz.toString())
                .setValue(null)
        }

        CoroutineScope(Dispatchers.Main).launch {
            val deferred = async(Dispatchers.IO) {
                // Весь ваш код, который вы хотите выполнить асинхронно
                dao.getQuestionByIdQuiz(id).forEach {
                    log("setQuestions 2: $it")
                    questionRef
                        .child(if (it.hardQuestion) "-${it.numQuestion}" else "${it.numQuestion}")
                        .child(if (it.lvlTranslate < BARRIER_QUIZ_ID_LOCAL_AND_REMOVE) "$SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG${it.language}" else it.language)
                        .setValue(it.toQuestion())
                    dao.deleteQuestion(id)
                    delay(100)
                    if (isInsertQuizAfterSet(quiz)) dao.insertQuestion(it.copy(idQuiz = newIdQuiz!!))
                }
            }

            // Этот код не будет выполнен, пока не завершится deferred
            deferred.await()
            // Теперь вы можете продолжить выполнение других задач
        }
    }

    private fun setQuestionDetails(newIdQuiz: Int?, eventQuiz: Int?, id: Int, quiz: Quiz) {
        val questionDetailRef = FirebaseDatabase.getInstance().getReference(
            "$PATH_QUESTION_DETAIL$eventQuiz/${getTpovId()}/$newIdQuiz"
        )

        log("setQuestionDetails $newIdQuiz $eventQuiz $id $quiz")
        if (isQuizForEvent(quiz)) FirebaseDatabase.getInstance()
            .getReference("$PATH_QUESTION_DETAIL${eventQuiz?.minus(1)}")
            .child(getTpovId().toString())
            .child(newIdQuiz.toString()).setValue(null)
        CoroutineScope(Dispatchers.IO).launch {
            log("setQuestionDetails 2")
            val listQuestionDetail = dao.getQuestionDetailByIdQuiz(id)
            listQuestionDetail.forEach { qd ->
                log("setQuestionDetails 3 $qd")
                if (!qd.synthFB) {
                    log("setQuestionDetails 4")
                    dao.deleteQuestionDetail(qd.id!!)
                    log("setQuestionDetails 1 ${dao.getQuestionDetailByIdQuiz(id)}")

                    questionDetailRef.push().setValue(qd).addOnSuccessListener {
                        if (isInsertQuizAfterSet(quiz)) dao.insertQuizDetail(
                            qd.copy(
                                idQuiz = newIdQuiz!!,
                                synthFB = true
                            )
                        )
                        log("setQuestionDetails addOnSuccessListener")
                    }.addOnFailureListener {
                        if (isInsertQuizAfterSet(quiz)) dao.insertQuizDetail(
                            qd.copy(
                                idQuiz = newIdQuiz!!,
                                synthFB = true
                            )
                        )
                        log("setQuestionDetails addOnFailureListener")
                    }
                }
            }
        }
    }

    private fun setReadToFalse() {
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference(PATH_LIST_READ)
        myRef.setValue(false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getQuiz(
        vararg pathQuiz: String? = arrayOf(
            "$PATH_USER_QUIZ/${getTpovId()}",
            if (dao.getProfile(getTpovId()).tester!! >= LVL_TESTER_1_LVL
                || dao.getProfile(getTpovId()).developer!! >= LVL_DEVELOPER_1_LVL
            ) PATH_TESTER_QUIZ else null,
            if (dao.getProfile(getTpovId()).moderator!! >= LVL_MODERATOR_1_LVL
                || dao.getProfile(getTpovId()).developer!! >= LVL_DEVELOPER_1_LVL
            ) PATH_MODERATOR_QUIZ else null,
            if (dao.getProfile(getTpovId()).admin!! >= LVL_ADMIN_1_LVL
                || dao.getProfile(getTpovId()).developer!! >= LVL_DEVELOPER_1_LVL
            ) PATH_ADMIN_QUIZ else null,
            PATH_ARENA_QUIZ, PATH_TOURNIRE_QUIZ, PATH_TOURNIRE_LEADER_QUIZ, PATH_HOME_QUIZ
        ), getQuestionDetails: Boolean = true
    ) {
        loadText.postValue("")
        log("wdwdwdw 1")
        if (waitForReadToBecomeTrue()) { // Мы дождались, пока read станет true
            log("wdwdwdw 2, pathQuiz: $pathQuiz")
            pathQuiz.forEach { quizItem ->
                val database = FirebaseDatabase.getInstance()
                val quizRef = database.getReference(quizItem ?: PATH_HOME_QUIZ)

                var i = 0
                log("wdwdwdw 3, quizItem: $quizItem")
                quizRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        for (quizSnapshot in dataSnapshot.children) {
                            i++
                            val idQuiz = quizSnapshot.key?.toInt() ?: 0
                            val quiz = quizSnapshot.getValue(Quiz::class.java)

                            log("wdwdwdw 6 quiz${quiz?.event}: $quiz")

                            val maxLvlTranslate = try {
                                var max = 0
                                quiz?.languages?.split(SPLIT_BETWEEN_LANGUAGES)?.forEach {
                                    val newMax = it.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG)[1].toInt()
                                    if (newMax > max) max = newMax
                                }
                                max
                            } catch (e: Exception) {
                                try {
                                    quiz?.languages?.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG)!![1].toInt()
                                } catch (e: Exception) {
                                    0
                                }
                            }

                            val quizVersionLocal = getVersionQuiz(idQuiz.toString())

                            log("wdwdwdw 6 1: ${this@RepositoryFBImpl.isQuizPublic(quiz) && maxLvlTranslate >= 100 && (quiz?.versionQuiz!! > quizVersionLocal || quizVersionLocal == -1)}")
                            log("wdwdwdw 6 2: ${(isQuizForEvent(quiz)) && (quiz?.versionQuiz!! > quizVersionLocal || quizVersionLocal == -1)}")
                            log("wdwdwdw 6 3: ${quizVersionLocal == QUIZ_VERSION_DEFAULT && quiz?.event == 1}")
                            log("wdwdwdw 6 4: ${maxLvlTranslate >= 100}")
                            log("wdwdwdw 6 5: ${getQuestionDetails}")
                            log("wdwdwdw 6 6: ${this@RepositoryFBImpl.isQuizPublic(quiz)}")

                            if (
                                ((isQuizPublic(quiz) && maxLvlTranslate >= LVL_TRANSLATOR_1_LVL || isQuizForEvent(
                                    quiz
                                ))
                                        && (quiz?.versionQuiz!! > quizVersionLocal || quizVersionLocal == -1 || getQuestionDetails)
                                        || quizVersionLocal == QUIZ_VERSION_DEFAULT && quiz?.event == EVENT_QUIZ_FOR_USER)
                            ) savePictureToLocalDirectory(quiz!!.picture) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(Dispatchers.IO) {
                                        deleteQuiz(idQuiz)
                                        dao.insertQuiz(
                                            quiz.toQuizEntity(
                                                idQuiz,
                                                0,
                                                0,
                                                0,
                                                it
                                            )
                                        )
                                    }
                                }

                                log("wdwdwdw INSERT")
                                getQuestions(quizItem ?: PATH_HOME_QUIZ, idQuiz)
                                if (getQuestionDetails) getQuestionDetails(quizItem ?: PATH_HOME_QUIZ)
                                setVersionQuiz(idQuiz.toString(), quiz.versionQuiz)
                            }
                            log("ioioioio $i ${dataSnapshot.childrenCount.toInt()}")
                            setLoadPB(i, dataSnapshot.childrenCount.toInt())
                            if (i == dataSnapshot.childrenCount.toInt()) synthLiveData.value =
                                ++synth
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Ошибка чтения данных
                    }
                })
            }
            setRead(true)
        } else loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_server_load))
    }

    private fun deleteQuiz(idQuiz: Int) {
        dao.deleteQuizById(idQuiz)
        dao.deleteQuestionByIdQuiz(idQuiz)
        dao.deleteQuestionDetailByIdQuizAndSynthFB(idQuiz)
    }

    private fun isQuizPublic(quiz: Quiz?): Boolean = quiz?.event in EVENT_QUIZ_ARENA..EVENT_QUIZ_HOME

    private fun isQuizForEvent(quiz: Quiz?): Boolean =
        this.isQuizPrivate(quiz) && quiz?.ratingPlayer != 1

    private fun isQuizPrivate(quiz: Quiz?): Boolean = quiz?.event in EVENT_QUIZ_FOR_USER..EVENT_QUIZ_FOR_ADMIN

    fun getQuestions(eventQuiz: String, idQuiz: Int) {
        val pathQuestion = when (eventQuiz) {
            PATH_TESTER_QUIZ -> "$PATH_TESTER_QUESTION/$idQuiz"
            PATH_MODERATOR_QUIZ -> "$PATH_MODERATOR_QUESTION/$idQuiz"
            PATH_ADMIN_QUIZ -> "$PATH_ADMIN_QUESTION/$idQuiz"
            PATH_ARENA_QUIZ -> "$PATH_ARENA_QUESTION/$idQuiz"
            PATH_TOURNIRE_QUIZ -> "$PATH_TOURNIRE_QUESTION/$idQuiz"
            PATH_TOURNIRE_LEADER_QUIZ -> "$PATH_TOURNIRE_LEADER_QUESTION/$idQuiz"
            PATH_HOME_QUIZ -> "$PATH_HOME_QUESTION/$idQuiz"
            else -> "$PATH_USER_QUESTION/${getTpovId()}/$idQuiz"
        }

        val database = FirebaseDatabase.getInstance()
        val questionRef = database.getReference(pathQuestion)

        questionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (numQuestionSnapshot in dataSnapshot.children) {
                    for (languageSnapshot in numQuestionSnapshot.children) {
                        val question = languageSnapshot.getValue(Question::class.java)

                        CoroutineScope(Dispatchers.IO).launch {
                            dao.insertQuestion(
                                QuestionEntity(
                                    null,
                                    abs(numQuestionSnapshot.key?.toInt()!!),
                                    question!!.nameQuestion,
                                    question.answerQuestion,
                                    getTypeQuestion(numQuestionSnapshot.key?.toInt()!!),
                                    idQuiz,
                                    languageSnapshot.key!!.replace("-", "", true),
                                    question.lvlTranslate,
                                    question.infoTranslater
                                )
                            )
                            synthLiveData.postValue(++synth)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    fun getTypeQuestion(toInt: Int) = toInt <= 0

    fun getQuestionDetails(pathQuiz: String) {
        val pathQuestionDetail = when (pathQuiz) {
            PATH_TESTER_QUIZ -> "$PATH_TESTER_QUESTION_DETAIL/${getTpovId()}"
            PATH_MODERATOR_QUIZ -> "$PATH_MODERATOR_QUESTION_DETAIL/${getTpovId()}"
            PATH_ADMIN_QUIZ -> "$PATH_ADMIN_QUESTION_DETAIL/${getTpovId()}"
            PATH_ARENA_QUIZ -> "$PATH_ARENA_QUESTION_DETAIL/${getTpovId()}"
            PATH_TOURNIRE_QUIZ -> "$PATH_TOURNIRE_QUESTION_DETAIL/${getTpovId()}"
            PATH_TOURNIRE_LEADER_QUIZ -> "$PATH_TOURNIRE_LEADER_QUESTION_DETAIL/${getTpovId()}"
            PATH_HOME_QUIZ -> "$PATH_HOME_QUESTION_DETAIL/${getTpovId()}"
            else -> "$PATH_USER_QUESTION_DETAIL/${getTpovId()}"
        }

        log("24545 1 $pathQuestionDetail")
        val database = FirebaseDatabase.getInstance()
        val questionDetailRef = database.getReference(pathQuestionDetail)
        questionDetailRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(tpovIdSnapshot: DataSnapshot) {
                log("24545 2 ${tpovIdSnapshot.key}")
                for (idQuizSnapshot in tpovIdSnapshot.children) {
                    log("24545 3 ${idQuizSnapshot.key}")
                    for (idQuizNumSnapshot in idQuizSnapshot.children) {
                        log("24545 4 ${idQuizNumSnapshot.key}")

                        val questionDetail =
                            idQuizNumSnapshot.getValue(QuestionDetail::class.java)

                        dao.insertQuizDetail(
                            questionDetail?.toQuestionDetailEntity(
                                id = null,
                                idQuizSnapshot.key?.toInt()!!,
                                true
                            )!!
                        )
                        synthLiveData.value = ++synth
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {

                log("24545 error: $error $pathQuestionDetail")
            }
        })
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryFB", Logcat.LOG_FIREBASE)
    }
}