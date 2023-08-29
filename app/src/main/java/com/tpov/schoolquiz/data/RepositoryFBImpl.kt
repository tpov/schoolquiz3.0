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
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getVersionQuiz
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.setTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.setVersionQuiz
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

    override fun getValSynth(): MutableLiveData<Int> = synthLiveData

    override fun getPlayersList() {
        val playersListRef = FirebaseDatabase.getInstance().getReference("players/listPlayers")
        playersListRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loadText.postValue("Загрузка лидеров")
                val playersList = mutableListOf<PlayersEntity>()
                log("getPlayersList snapshot: $snapshot")
                for (playerSnapshot in snapshot.children) {

                    log("getPlayersList playerSnapshot: $playerSnapshot")
                    val player = playerSnapshot.getValue(PlayersEntity::class.java)
                    if (player != null) {
                        log("getPlayersList player: $player")
                        playersList.add(
                            player.copy(id = playerSnapshot.key?.toInt())
                        )
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
        val chatRef = FirebaseDatabase.getInstance().getReference("chat")
        val dateFormatKiev = SimpleDateFormat("HH:mm:ss - dd/MM/yy")
        dateFormatKiev.timeZone = TimeZone.getTimeZone("Europe/Kiev")

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
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                log("Error: ${e.message}")
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Обработка ошибок
                }
            })

        return dao.getChat()
    }

    override fun removeChatListener() {
        val chatRef = FirebaseDatabase.getInstance().getReference("chat")
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
        val pathReference: StorageReference = storageRef.child("picture/$pictureString")

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
            FirebaseDatabase.getInstance().getReference("PersonalMassage/translate/${getTpovId()}")
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
        val profileRef = FirebaseDatabase.getInstance().getReference("Profiles")

        loadText.postValue("Загрузка профилей")
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
                        dao.insertProfile(profile.toProfileEntity(100, 500))
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

                loadText.postValue("")
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

        ref.child("listTpovId/$uid").setValue(getTpovId().toString()).addOnSuccessListener {
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
        val ref = database.getReference("players")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getTpovIdFB() snapshot: $snapshot")

                val tpovId: String = snapshot.child("listTpovId/$uid").getValue(String::class.java)
                    ?: errorTpovId().toString()
                log("getTpovIdFB() tpovId: $tpovId")
                setTpovId(tpovId.toInt())

                log("getTpovIdFB()/ set tpovId: $tpovId")
                synthLiveData.value = ++synth

                log("getTpovIdFB()/ set synth: ${synthLiveData.value}")
                log("getTpovIdFB()/ set synth: ${synth}")
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
        val profileRef = database.getReference("Profiles")
        val profilesRef = database.getReference("players")
        var idUsers = 0
        var oldIdUser = 0

        loadText.postValue("Отправка профиля")
        val tpovId = getTpovId()
        val profile = dao.getProfileByTpovId(tpovId)

        log("setProfile() tpovId: $tpovId")
        if (tpovId == 0) {

            profilesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    log("setProfile() snapshot: ${snapshot.key}")
                    idUsers =
                        ((snapshot.value as Map<*, *>)["idUser"] as Long).toInt() // Получение значения переменной allQuiz
                    oldIdUser = tpovId
                    idUsers++

                    profilesRef.updateChildren(
                        hashMapOf<String, Any>(
                            "idUser" to idUsers
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

                        }
                    }.addOnFailureListener {
                        log("setProfile() error1: $it")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    log("setProfile() error2: $error")
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

                                translater = profileSnapshot.child("qualification")
                                    .child("translater").getValue(Int::class.java),

                                admin = profileSnapshot.child("qualification").child("admin")
                                    .getValue(Int::class.java),

                                developer = profileSnapshot.child("qualification")
                                    .child("developer").getValue(Int::class.java),

                                moderator = profileSnapshot.child("qualification")
                                    .child("moderator").getValue(Int::class.java),

                                sponsor = profileSnapshot.child("qualification").child("sponsor")
                                    .getValue(Int::class.java),

                                tester = profileSnapshot.child("qualification").child("tester")
                                    .getValue(Int::class.java),

                                addTrophy = try {
                                    if (profile.addTrophy == "") profileSnapshot.child("addPoints")
                                        .child("addTrophy").getValue(String::class.java) else ""
                                } catch (e: Exception) {
                                    profileSnapshot.child("addPoints").child("addTrophy")
                                        .getValue(String::class.java)
                                },

                                addMassage = try {
                                    if (profile.addMassage == "") profileSnapshot.child("addMassage")
                                        .child("addMassage").getValue(String::class.java) else ""
                                } catch (e: Exception) {
                                    profileSnapshot.child("addPoints").child("addMassage")
                                        .getValue(String::class.java)
                                },

                                addPointsNolics = try {
                                    if (profile.addPointsNolics == 0) profileSnapshot.child("addPoints")
                                        .child("addNolics").getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child("addPoints").child("addNolics")
                                        .getValue(Int::class.java)
                                },

                                addPointsGold = try {
                                    if (profile.addPointsGold == 0) profileSnapshot.child("addPoints")
                                        .child("addGold").getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child("addPoints").child("addGold")
                                        .getValue(Int::class.java)
                                },

                                addPointsSkill = try {
                                    if (profile.addPointsSkill == 0) profileSnapshot.child("addPoints")
                                        .child("addSkill").getValue(Int::class.java) else 0
                                } catch (e: Exception) {
                                    profileSnapshot.child("addPoints").child("addSkill")
                                        .getValue(Int::class.java)
                                }

                            ).toProfile()
                        ).addOnSuccessListener {
                            synthLiveData.value = ++synth
                            loadText.postValue("")
                        }.addOnFailureListener {
                            log("setProfile() $it")
                            synthLiveData.value = ++synth
                        }
                    } catch (e: Exception) {
                        synthLiveData.value = ++synth
                    }

                }

                override fun onCancelled(error: DatabaseError) {

                    log("setProfile() error24 $error")
                }
            })
        }

    }

    override fun getUserName(): Profile {
        val tpovId = getTpovId()
        log("fun getUserName()")
        val profileRef = FirebaseDatabase.getInstance().getReference("Profiles")
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
        getQuiz("quiz8", getQuestionDetails = false)
    }

    override suspend fun getAllQuiz() {
        getQuiz()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun waitForReadToBecomeTrue(): Boolean = try {
        withTimeout(60000L) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                val database = FirebaseDatabase.getInstance()
                val myRef = database.getReference("players/read")

                val listener = object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val value = dataSnapshot.getValue(Boolean::class.java)
                        if (value == true) {
                            myRef.removeEventListener(this)
                            continuation.resume(true) {
                                // Обработка отмены после возобновления корутины
                            }
                        }
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
        val myRef = database.getReference("players/read")
        myRef.setValue(value)
    }

    override suspend fun setAllQuiz() {

        if (waitForReadToBecomeTrue()) {

            val database = FirebaseDatabase.getInstance()
            loadText.postValue("Отправка квестов на сервер")

            dao.getQuizList(getTpovId()).forEach {
                val eventQuiz = it.event
                val quizRef = database.getReference(
                    if (eventQuiz == 1) "quiz1/${getTpovId()}" else "quiz$eventQuiz"
                )

                val profileRef = FirebaseDatabase.getInstance().getReference("players/idQuiz")
                profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {

                        val newIdQuiz = if (it.id!! < 100) {
                            val id = snapshot.getValue(Int::class.java)?.plus(1)
                            profileRef.setValue(id)
                            id
                        } else it.id

                        quizRef.child(newIdQuiz.toString())
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val newVersionQuiz =
                                        snapshot.child("versionQuiz").getValue(Int::class.java)

                                    try {

                                    if (newVersionQuiz!! < getVersionQuiz(newIdQuiz.toString())) {
                                        val newQuiz = Quiz(
                                            nameQuiz = it.nameQuiz,
                                            tpovId = it.tpovId,
                                            data = it.data,
                                            versionQuiz = newVersionQuiz,
                                            picture = it.picture!!,
                                            event = eventQuiz,
                                            numQ = it.numQ,
                                            numHQ = it.numHQ,
                                            starsAllPlayer = snapshot.child("starsAllPlayer")
                                                .getValue(Int::class.java)!!,
                                            starsPlayer = snapshot.child("starsPlayer")
                                                .getValue(Int::class.java)!!,
                                            ratingPlayer = it.ratingPlayer,
                                            userName = it.userName,
                                            languages = it.languages

                                        )
                                        quizRef.child(newIdQuiz.toString()).setValue(newQuiz)
                                        dao.deleteQuizById(it.id!!)
                                        dao.insertQuiz(
                                            newQuiz.toQuizEntity(
                                                newIdQuiz!!,
                                                it.stars,
                                                it.starsAll,
                                                it.rating,
                                                it.picture
                                            )
                                        )
                                        setQuestions(newIdQuiz, eventQuiz, it.id!!)
                                        setQuestionDetails(newIdQuiz, eventQuiz, it.id!!)
                                    }
                                    } catch (e: Exception) {
                                        log("ошибка квеста ${snapshot.value}")
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
                        if (it.id!! < 100) exit = false
                    }

                    if (exit) {
                        setRead(true)
                        loadText.postValue("")
                        synthLiveData.postValue(++synth)

                        break
                    }
                    delay(300)
                }
            }
        }
    }

    private fun setQuestions(newIdQuiz: Int?, eventQuiz: Int?, id: Int) {
        val questionRef = FirebaseDatabase.getInstance().getReference(
            if (eventQuiz != 1) "question$eventQuiz/$newIdQuiz"
            else "question1/${getTpovId()}/$newIdQuiz"
        )

        dao.getQuestionByIdQuiz(id).forEach {
            questionRef.child(newIdQuiz.toString())
                .child(if (it.hardQuestion) "-${it.numQuestion}" else "${it.numQuestion}")
                .child(if (it.lvlTranslate <= 100) "-${it.language}" else "${it.language}")
                .setValue(it.toQuestion())

            dao.deleteQuestion(id)
            CoroutineScope(Dispatchers.IO).launch {
                dao.insertQuestion(it.copy(id = newIdQuiz))
            }
        }
    }

    private fun setQuestionDetails(newIdQuiz: Int?, eventQuiz: Int?, id: Int) {
        val questionDetailRef = FirebaseDatabase.getInstance().getReference(
            if (eventQuiz != 1) "questionDetail$eventQuiz/$newIdQuiz"
            else "question1/${getTpovId()}/$newIdQuiz"
        )

        CoroutineScope(Dispatchers.Main).launch {
            dao.getQuestionDetailByIdQuiz(id).forEach {
                questionDetailRef.setValue(it)

                dao.deleteQuestionDetailByIdQuiz(id)
                dao.insertQuizDetail(it.copy(id = newIdQuiz))
            }
        }
    }

    private fun setReadToFalse() {
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("players/read")
        myRef.setValue(false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getQuiz(
        vararg pathQuiz: String? = arrayOf(
            "quiz1/${getTpovId()}",
            if (dao.getProfile(getTpovId()).tester!! >= 100
                || dao.getProfile(getTpovId()).developer!! >= 100
            ) "quiz2" else null,
            if (dao.getProfile(getTpovId()).moderator!! >= 100
                || dao.getProfile(getTpovId()).developer!! >= 100
            ) "quiz3" else null,
            if (dao.getProfile(getTpovId()).admin!! >= 100
                || dao.getProfile(getTpovId()).developer!! >= 100
            ) "quiz4" else null,
            "quiz5", "quiz6", "quiz7", "quiz8"
        ), getQuestionDetails: Boolean = true
    ) {
        loadText.postValue("Загрузка квестов")
        log("wdwdwdw 1")

        if (waitForReadToBecomeTrue()) { // Мы дождались, пока read станет true
            log("wdwdwdw 2")
            pathQuiz.forEach { quizItem ->
                log("wdwdwdw 3")
                val database = FirebaseDatabase.getInstance()
                val quizRef = database.getReference(quizItem!!)

                quizRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        for (quizSnapshot in dataSnapshot.children) {
                            log("wdwdwdw 4")
                            val idQuiz = quizSnapshot.key?.toInt() ?: 0
                            val quiz =
                                quizSnapshot.getValue(Quiz::class.java) // Замените Quiz на класс вашего объекта
                            if (quiz?.versionQuiz!! > getVersionQuiz(idQuiz.toString()))
                                savePictureToLocalDirectory(quiz.picture) {
                                    dao.insertQuiz(quiz.toQuizEntity(idQuiz, 0, 0, 0, it))
                                    getQuestions(quizItem)
                                    if (getQuestionDetails) getQuestionDetails(quizItem)
                                    setVersionQuiz(idQuiz.toString(), quiz.versionQuiz)
                                    synthLiveData.value = ++synth
                                    loadText.postValue("")
                                }
                            // Теперь вы можете работать с объектом quiz
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Ошибка чтения данных
                    }
                })
            }
            setRead(true) // Устанавливаем read обратно в false
            loadText.postValue("")
        } else {
            loadText.postValue("Сервер занят, ждем..")
        }
    }


    fun getQuestions(pathQuiz: String) {
        val pathQuestion = when (pathQuiz) {
            "quiz2" -> "question2"
            "quiz3" -> "question3"
            "quiz4" -> "question4"
            "quiz5" -> "question5"
            "quiz6" -> "question6"
            "quiz7" -> "question7"
            "quiz8" -> "question8"
            else -> "question1/${getTpovId()}"
        }

        val database = FirebaseDatabase.getInstance()
        val questionRef = database.getReference(pathQuestion)
        questionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (idQuizSnapshot in dataSnapshot.children) {
                    for (numQuestionSnapshot in idQuizSnapshot.children) {
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
                                        idQuizSnapshot.key!!.toInt(),
                                        languageSnapshot.key!!,
                                        question.lvlTranslate,
                                        question.infoTranslater
                                    )
                                )
                                synthLiveData.postValue(++synth)
                            }
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
            "quiz2" -> "questionDetail2"
            "quiz3" -> "questionDetail3"
            "quiz4" -> "questionDetail4"
            "quiz5" -> "questionDetail5"
            "quiz6" -> "questionDetail6"
            "quiz7" -> "questionDetail7"
            "quiz8" -> "questionDetail8"
            else -> "questionDetail1/${getTpovId()}"
        }

        val database = FirebaseDatabase.getInstance()
        val questionDetailRef = database.getReference(pathQuestionDetail)
        questionDetailRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (idQuizSnapshot in dataSnapshot.children) {
                    for ((index, numQuestionDetailSnapshot) in idQuizSnapshot.children.withIndex()) {
                        val questionDetail =
                            numQuestionDetailSnapshot.getValue(QuestionDetail::class.java)

                        dao.insertQuizDetail(
                            questionDetail?.toQuestionDetailEntity(
                                index,
                                idQuizSnapshot.key?.toInt()!!,
                                true
                            )!!
                        )
                        synthLiveData.value = ++synth
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryFB", Logcat.LOG_FIREBASE)
    }
}