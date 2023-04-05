package com.tpov.schoolquiz.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositoryFBImpl @Inject constructor(
    private val dao: QuizDao,
    private val application: Application
) : RepositoryFB {

    val context = application.baseContext
    var synthLiveData = MutableLiveData<Int>()
    var synth = 0
    var newVersionQuiz = ArrayList<Int>()
    var newVersionQuizDetail = ArrayList<Int>()

    override fun getValSynth(): MutableLiveData<Int> {

        log("getValSynth()  ${synthLiveData.value}")
        log("getValSynth()s  $synth")
        return synthLiveData
    }

    init {
        val referenceValue = Integer.toHexString(System.identityHashCode(getValSynth()))

        log("fun init referenceValue :$referenceValue")
    }


    override fun getChatData(): Flow<List<ChatEntity>> {
        val chatRef = FirebaseDatabase.getInstance().getReference("chat")
        chatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Получаем данные из snapshot и сохраняем их в локальную базу данных
                for (data in snapshot.children) {
                    val chatEntity = data.getValue(ChatEntity::class.java)
                    if (chatEntity != null) {
                        dao.insertChat(chatEntity)
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {
                // Обработка ошибок
            }
        })
        return dao.getChat()
    }

    fun savePictureToLocalDirectory(
        pictureString: String,
        context: Context,
        callback: (path: String?) -> Unit
    ) {
        log("fun savePictureToLocalDirectory()")
        if (!context.cacheDir.exists()) context.cacheDir.mkdir()
        val directory = File(context.cacheDir, "")
        var uid = FirebaseAuth.getInstance().currentUser?.uid

        val storage: FirebaseStorage = FirebaseStorage.getInstance()
        val storageRef: StorageReference = storage.reference
        val pathReference: StorageReference = storageRef.child("picture/$pictureString")

        val file = File(directory, "$pictureString")

        log("savePictureToLocalDirectory() путь сохранения картинки: $pictureString")
        pathReference.getFile(file).addOnSuccessListener {
            // Обработка успешного скачивания картинки
            log("savePictureToLocalDirectory() картинка получена успешно")

            callback("$pictureString")
        }.addOnFailureListener {

            log("savePictureToLocalDirectory() ошибка получение картинки: $it")
            // Обработка ошибок
            callback(null)
        }
    }

    override fun getQuiz8Data(context: Context) {
        log("fun getQuiz8Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz8"))
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun getQuiz7Data(context: Context) {
        log("fun getQuiz7Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz7"))
    }

    private fun getQuiz(
        quizRef: DatabaseReference
    ) {
        quizRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (data in snapshot.children) {
                    val versionQuiz = SharedPreferencesManager.getVersionQuiz(data.key ?: "-1")

                    val quizEntity = data.getValue(Quiz::class.java)
                    if (quizEntity != null && versionQuiz < quizEntity.versionQuiz) {
                        savePictureToLocalDirectory(
                            quizEntity.picture,
                            context
                        ) { path ->
                            if (path != null) {
                                if (versionQuiz == -1) dao.insertQuiz(
                                    quizEntity.toQuizEntity(
                                        0,
                                        path,
                                        data.key!!.toInt()
                                    )
                                )
                                else dao.updateQuiz(quizEntity.toQuizEntity(0, path, data.key!!.toInt()))
                                val refQuestion = when (quizEntity.event) {
                                    2 -> FirebaseDatabase.getInstance().getReference("question2")
                                    3 -> FirebaseDatabase.getInstance().getReference("question3")
                                    4 -> FirebaseDatabase.getInstance().getReference("question3")
                                    5 -> FirebaseDatabase.getInstance().getReference("question3")
                                    6 -> FirebaseDatabase.getInstance().getReference("question3")
                                    7 -> FirebaseDatabase.getInstance().getReference("question3")
                                    8 -> FirebaseDatabase.getInstance().getReference("question3")
                                    else -> FirebaseDatabase.getInstance()
                                        .getReference("question1/${quizEntity.tpovId}")
                                }
                                getQuestion(refQuestion, data.key!!)

                            } else {
                                // Обрабатывайте ошибки здесь
                            }
                            SharedPreferencesManager.setVersionQuiz(
                                data.key!!,
                                quizEntity.versionQuiz
                            )
                        }

                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    override fun getQuiz6Data() {
        log("fun getQuiz6Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz6"))
    }

    override fun getQuiz5Data() {
        log("fun getQuiz5Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz5"))
    }

    override fun getQuiz4Data() {
        log("fun getQuiz4Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz4"))
    }

    override fun getQuiz3Data() {
        log("fun getQuiz3Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz3"))
    }

    override fun getQuiz2Data() {
        log("fun getQuiz2Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz2"))
    }

    override fun getQuiz1Data(tpovId: Int) {
        log("fun getQuiz1Data")
        getQuiz(FirebaseDatabase.getInstance().getReference("quiz1/$tpovId"))
    }

    override fun getQuestion8() {
        log("fun getQuestion8Data")
        val questionRef = FirebaseDatabase.getInstance().getReference("question8")
        getQuestion(questionRef, "-1")
    }

    private fun getQuestion(questionRef: DatabaseReference, idQuiz: String) {
        questionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getQuestion8Data snapshot: ${snapshot.key}")
                for (idQuizSnap in snapshot.children) { // перебор всех папок idQuiz внутри uid
                    if (idQuizSnap.key == idQuiz) {
                        log("getQuestion8Data idQuizSnap: ${idQuizSnap.key}")
                        for (idQuestionSnap in idQuizSnap.children) { // перебор всех папок idQuiz внутри uid
                            log("getQuestion8Data idQuestionSnap: ${idQuestionSnap.key}")
                            for (languageSnap in idQuestionSnap.children) { // перебор всех папок language внутри idQuiz
                                log("getQuestion8Data languageSnap: ${languageSnap.key}")
                                for (questionSnap in languageSnap.children) { // перебор всех вопросов внутри language
                                    log("getQuestion8Data questionSnap: ${questionSnap.key}")
                                    val question = questionSnap.getValue(Question::class.java)
                                    if (question != null) {
                                        dao.deleteQuestionByIdQuiz(idQuizSnap.key?.toInt() ?: -1)
                                        dao.insertQuestion(
                                            QuestionEntity(
                                                null,
                                                idQuestionSnap.key?.toInt() ?: 0,
                                                question.nameQuestion,
                                                question.answerQuestion,
                                                question.typeQuestion,
                                                idQuizSnap.key?.toInt() ?: -1,
                                                languageSnap.key ?: "eu",
                                                question.lvlTranslate
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                log("getQuestion8Data ошибка: $error")
            }
        })
    }

    override fun getQuestion7() {
        getQuestion(FirebaseDatabase.getInstance().getReference("question7"), "-1")
    }

    override fun getQuestion6() {
        getQuestion(FirebaseDatabase.getInstance().getReference("question6"), "-1")
    }

    override fun getQuestion5() {
        getQuestion(FirebaseDatabase.getInstance().getReference("question5"), "-1")
    }

    override fun getQuestion4Data() {
        getQuestion(FirebaseDatabase.getInstance().getReference("question4"), "-1")
    }

    override fun getQuestion3() {
        getQuestion(FirebaseDatabase.getInstance().getReference("question3"), "-1")
    }

    override fun getQuestion2() {
        getQuestion(FirebaseDatabase.getInstance().getReference("question2"), "-1")
    }

    override fun getQuestion1(tpovId: Int) {
        getQuestion(FirebaseDatabase.getInstance().getReference("question1/$tpovId"), "-1")
    }

    override fun getQuestionDetail1(tpovId: Int) {
        log("fun getQuestionDetail1()")
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail1/$tpovId"), "-1")
    }

    override fun getQuestionDetail2() {
        log("fun getQuestionDetail2()")
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail2"), "-1")
    }

    private fun getQuestionDetail(questionRef: DatabaseReference, idQuiz: String) {
        questionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getQuestionDetail2() snapshot: ${snapshot.key}")
                for (user in snapshot.children) {
                    log("getQuestionDetail2() user: ${user.key}")
                    if (user.key == idQuiz && dao.getQuestionDetailList().size != user.childrenCount.toInt()) {
                        for (idQuizSnap in user.children) {                                     //
                            log("getQuestionDetail2() idQuizSnap: ${idQuizSnap.key}")
                            val questionDetailEntity =
                                idQuizSnap.getValue(QuestionDetailEntity::class.java)
                            if (questionDetailEntity != null) {
                                log("getQuestionDetail2() квест не пустой, добавляем в список")
                                dao.deleteQuestionDetailByIdQuiz(questionDetailEntity.idQuiz)
                                dao.insertQuizDetail(questionDetailEntity)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                log("getQuestionDetail2() ошибка: $error")

            }
        })
    }

    override fun getQuestionDetail3() {
        log("fun getQuestionDetail3()")
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail3"), "-1")
    }

    override fun getQuestionDetail4() {
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail4"), "-1")
    }

    override fun getQuestionDetail5() {
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail5"), "-1")
    }

    override fun getQuestionDetail6() {
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail6"), "-1")
    }

    override fun getQuestionDetail7() {
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail7"), "-1")
    }

    override fun getQuestionDetail8() {
        getQuestionDetail(FirebaseDatabase.getInstance().getReference("question_detail8"), "-1")
    }

    override fun getProfile(context: Context) {
        log("fun getProfile()")
        val profileRef = FirebaseDatabase.getInstance().getReference("Profiles")
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

        profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getProfile() snapshot: ${snapshot.key}")
                var tpovId = sharedPref?.getInt("tpovId", 0) ?: 0
                val profile = snapshot.child("$tpovId").getValue(Profile::class.java)

                log("getProfile() tpovId: $tpovId")

                if (profile != null) {
                    log("getProfile() профиль не пустой")
                    if (dao.getProfileByTpovId(tpovId) == null) {
                        log("getProfile() профиль по tpovid пустой, создаем новый")
                        dao.insertProfile(profile.toProfileEntity())

                    } else {
                        log("getProfile() профиль по tpovid найден")
                        dao.updateProfiles(
                            dao.getProfileByFirebaseId(
                                FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            )
                                .copy(
                                    addPointsGold = profile.addPoints.addGold,
                                    addPointsNolics = profile.addPoints.addNolics,
                                    addTrophy = profile.addPoints.addTrophy,
                                    addPointsSkill = profile.addPoints.addSkill,
                                    addPointsSkillInSeason = profile.addPoints.addSkillInSesone,
                                    gamer = profile.qualification.gamer,
                                    sponsor = profile.qualification.sponsor,
                                    tester = profile.qualification.tester,
                                    translater = profile.qualification.translater,
                                    moderator = profile.qualification.moderator,
                                    admin = profile.qualification.admin,
                                    developer = profile.qualification.developer
                                )
                        )
                    }
                    synthLiveData.value = ++synth
                    log("getProfile() synth: ${synthLiveData.value}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                log("getProfile() ошибка ")
            }
        })
    }

    override suspend fun setQuizData(tpovId: Int) {
        log("fun setQuizData()")
        var quizDB = dao.getQuizList(tpovId)
        var idQuiz = 0

        val database = FirebaseDatabase.getInstance()
        val quizRef1 = database.getReference("quiz1")
        val quizRef2 = database.getReference("quiz2")
        val quizRef3 = database.getReference("quiz3")
        val quizRef4 = database.getReference("quiz4")
        val quizRef5 = database.getReference("quiz5")
        val quizRef6 = database.getReference("quiz6")
        val quizRef7 = database.getReference("quiz7")
        val quizRef8 = database.getReference("quiz8")

        val playersRef = FirebaseDatabase.getInstance().getReference("players")
        // создаем скоуп для запуска корутин
        val coroutineScope = CoroutineScope(Dispatchers.Default)

// запускаем корутину
        coroutineScope.launch {
            log("setQuizData() launch")
            var readValue = true
            var blockServer = false
            quizDB.forEach {
                if (it.id!! < 100) {
                    log("setQuizData() найден квест который не был синхронизирован с сервером")
                    blockServer = true
                }
            }

            val databaseReference = FirebaseDatabase.getInstance().getReference("players")
            databaseReference.child("read").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (blockServer) {
                        readValue = dataSnapshot.value as Boolean
                        log("setQuizData() databaseReference readValue: $readValue")
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    log("setQuizData() databaseReference error read fb: $databaseError")
                }
            })

            playersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    log("setQuizData() playersRef snapshot: $snapshot")

                    coroutineScope.launch {
                        if (blockServer) {
                            while (!readValue) {
                                log("setQuizData() playersRef сервер занят, ждем")
                                delay(100) // заменяем Thread.sleep() на delay()
                            }

                            val players =
                                snapshot.value as Map<*, *> // Преобразование значений в Map
                            idQuiz =
                                (players["idQuiz"] as Long).toInt() // Получение значения переменной allQuiz
                            log("setQuizData() playersRef idQuiz: $idQuiz")
                            val updates = hashMapOf<String, Any>("read" to false)
                            playersRef.updateChildren(updates)
                        }

                        quizDB.forEach {
                            log("setQuizData() playersRef quizDB перебираем")
                            if (it.event == 1) {

                                log("setQuizData() playersRef quizDB event1")
                                if (it.id!! >= 100) {
                                    log("setQuizData() playersRef quizDB event1 id >= 100 просто созраняем на сервер")
                                    quizRef1.child("${tpovId}/${it.id.toString()}")
                                        .setValue(it)
                                } else {
                                    log("setQuizData() playersRef quizDB event1 id < 100 синхронизируем с сервером")

                                    idQuiz++
                                    val oldId = it.id
                                    it.id = idQuiz
                                    quizRef1.child("${tpovId}/$idQuiz").setValue(it)

                                    dao.getQuestionByIdQuiz(oldId!!).forEach { item ->
                                        dao.insertQuestion(item.copy(idQuiz = it.id!!))
                                    }
                                    dao.getQuestionDetailByIdQuiz(oldId).forEach { item ->
                                        dao.insertQuizDetail(item.copy(idQuiz = it.id!!))
                                    }
                                    dao.deleteQuestionDetailByIdQuiz(oldId)
                                    dao.deleteQuestionByIdQuiz(oldId)
                                    dao.insertQuiz(it)
                                    dao.deleteQuizById(oldId)
                                    SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                                }

                            } else if (it.event == 2) {
                                log("setQuizData() playersRef quizDB event2")
                                if (it.id!! >= 100) {
                                    log("setQuizData() playersRef quizDB id >= 100  event2 просто созраняем на сервер")
                                    quizRef1.child("${tpovId}/${it.id.toString()}")
                                        .setValue(it)
                                } else {

                                    log("setQuizData() playersRef quizDB id < 100 event2 синхронизируем с сервером")
                                    idQuiz++
                                    var oldId = it.id!!
                                    it.id = idQuiz
                                    quizRef2.child("$idQuiz").setValue(it)

                                    dao.getQuestionByIdQuiz(oldId).forEach { item ->
                                        dao.insertQuestion(item.copy(idQuiz = it.id!!))
                                    }
                                    dao.getQuestionDetailByIdQuiz(oldId)
                                        .forEach { item ->
                                            dao.insertQuizDetail(item.copy(idQuiz = it.id!!))
                                        }
                                    dao.deleteQuestionDetailByIdQuiz(oldId)
                                    dao.deleteQuestionByIdQuiz(oldId)
                                    dao.insertQuiz(it)
                                    dao.deleteQuizById(oldId!!)
                                    SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)

                                }
                            } else if (it.event == 3) {
                                quizRef3.child(it.id.toString())
                                    .setValue(it)

                                SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                            }
                            else if (it.event == 4) {
                                quizRef4.child(it.id.toString())
                                    .setValue(it)

                                SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                            }
                            else if (it.event == 5) {
                                quizRef5.child(it.id.toString())
                                    .setValue(it)

                                SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                            }
                            else if (it.event == 6) {
                                quizRef6.child(it.id.toString())
                                    .setValue(it)

                                SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                            }
                            else if (it.event == 7) {
                                quizRef7.child(it.id.toString())
                                    .setValue(it)

                                SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                            }
                            else if (it.event == 8) {
                                log("setQuizData() event8 просто сохраняем на сервер")
                                quizRef8.child(it.id.toString()).setValue(it)
                                SharedPreferencesManager.setVersionQuiz(idQuiz.toString(), it.versionQuiz)
                            }

                        }

                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    log("setQuizData() error: $error")
                }
            })

            var synth2 = true
            log("setQuizData() dao.getQuizList(tpovId): ${dao.getQuizList(tpovId)}")
            while (synth2) {
                synth2 = false
                dao.getQuizList(tpovId).forEach {
                    log("setQuizData() it: $it")
                    if (it.id!! < 100) synth2 = true
                }
            }
            synthLiveData.postValue(++synth)
            if (blockServer) {

                log("setQuizData() blockServer = true")
                coroutineScope.launch {
                    while (true) {
                        var openServer = true
                        quizDB.forEach {
                            if (it.id!! < 100) openServer = false
                        }
                        log("setQuizData() сервер не завершился, ждем..")
                        if (openServer) break
                        delay(100)
                    }

                    log("setQuizData() открываем доступ к серверу")

                    val databaseReference = FirebaseDatabase.getInstance().reference
                    val updates = hashMapOf<String, Any>(
                        "players/read" to true,
                        "players/idQuiz" to idQuiz
                    )
                    databaseReference.updateChildren(updates).addOnFailureListener {
                        log("setQuizData() ошибка : $it")
                    }
                }
            }
        }
    }

    override fun setQuestionData(tpovId: Int) {

        log("fun setQuestionData()")
        var question = dao.getQuestionList()

        val database = FirebaseDatabase.getInstance()
        val questionRef1 = database.getReference("question1")
        val questionRef2 = database.getReference("question2")
        val questionRef3 = database.getReference("question3")
        val questionRef4 = database.getReference("question4")
        val questionRef5 = database.getReference("question5")
        val questionRef6 = database.getReference("question6")
        val questionRef7 = database.getReference("question7")
        val questionRef8 = database.getReference("question8")

        var i = 0
        question.forEach {
            log(
                "setQuestionData() перебираем квесты size: ${question.size}, dao.getQuizTpovIdById(it.idQuiz): ${
                    dao.getQuizTpovIdById(
                        it.idQuiz
                    )
                }, = tpovid: $tpovId"
            )
            if (dao.getQuizTpovIdById(it.idQuiz) == tpovId) {
                synthLiveData.value = --synth
                log("setQuestionData() найдет квест который совпадает с tpovId, idQuiz: ${it.idQuiz}")
                if (dao.getEventByIdQuiz(it.idQuiz) == 1) questionRef1.child("${tpovId}/${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener {
                        synthLiveData.value = ++synth
                    }
                if (dao.getEventByIdQuiz(it.idQuiz) == 2) questionRef2.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 3) questionRef3.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 4) questionRef4.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 5) questionRef5.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 6) questionRef6.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 7) questionRef7.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 8) questionRef8.child("${it.idQuiz}/${it.id}/${it.language}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
            }
        }
        synthLiveData.value = ++synth


    }

    override fun setTpovIdFB(context: Context) {

        log("fun setTpovIdFB()")
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("players")
        var uid = FirebaseAuth.getInstance().uid
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        var tpovId = sharedPref?.getInt("tpovId", 0) ?: 0
        log("setTpovIdFB() tpovId = $tpovId")

        ref.child("listTpovId/$uid").setValue(tpovId).addOnSuccessListener {
            log("setTpovIdFB() успех загрузки на сервер")
            synthLiveData.value = ++synth
        }.addOnFailureListener {

            log("setTpovIdFB() ошибка: $it")
        }
    }

    override fun getTpovIdFB(context: Context) {
        synth = 0
        synthLiveData.value = 0
        log("fun getTpovIdFB()")
        val database = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().uid
        val ref = database.getReference("players")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                log("getTpovIdFB() snapshot: $snapshot")

                val tpovId: Long =
                    snapshot.child("listTpovId/$uid").getValue(Long::class.java) ?: 0
                log("getTpovIdFB() tpovId: $tpovId")
                val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putInt("tpovId", tpovId.toInt())
                    apply()
                }

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

    override fun setQuestionDetail(tpovId: Int) {

        log("fun setQuestionDetail()")

        var questionDetail = dao.getQuestionDetailList()

        val database = FirebaseDatabase.getInstance()
        val questionDetailRef1 = database.getReference("questionDetail1")
        val questionDetailRef2 = database.getReference("questionDetail2")
        val questionDetailRef3 = database.getReference("questionDetail3")
        val questionDetailRef4 = database.getReference("questionDetail4")
        val questionDetailRef5 = database.getReference("questionDetail5")
        val questionDetailRef6 = database.getReference("questionDetail6")
        val questionDetailRef7 = database.getReference("questionDetail7")
        val questionDetailRef8 = database.getReference("questionDetail8")

        questionDetail.forEach {
            if (dao.getQuizTpovIdById(it.idQuiz) == tpovId) {
                synthLiveData.value = --synth
                log("setQuestionDetail() найден квест с таким же tpovId, idQuiz: ${it.idQuiz}")
                if (dao.getEventByIdQuiz(it.idQuiz) == 1) questionDetailRef1.child("${tpovId}/${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 2) questionDetailRef2.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 3) questionDetailRef3.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 4) questionDetailRef4.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 5) questionDetailRef5.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 6) questionDetailRef6.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 7) questionDetailRef7.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
                if (dao.getEventByIdQuiz(it.idQuiz) == 8) questionDetailRef8.child("${it.idQuiz}/${it.id}")
                    .setValue(it).addOnSuccessListener { synthLiveData.value = ++synth }
            }
        }
        synthLiveData.value = ++synth

    }

    override fun setProfile(context: Context) {
        log("fun setProfile()")
        val database = FirebaseDatabase.getInstance()
        val profileRef = database.getReference("Profiles")
        val profilesRef = database.getReference("players")
        var idUsers = 0
        var oldIdUser = 0
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        var tpovId = sharedPref?.getInt("tpovId", 0) ?: 0
        var profile = dao.getProfileByTpovId(tpovId)

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

                        with(sharedPref.edit()) {
                            putInt("tpovId", idUsers)
                            apply()
                        }
                        setTpovIdFB(context)

                        log("setProfile() tpovId: $tpovId")
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
            try {
                log("setProfile() id != 0 просто сохраняем на сервер profile: $profile, tpovId: $tpovId")
                profileRef.child(tpovId.toString()).setValue(profile.toProfile())
                    .addOnSuccessListener {
                        synthLiveData.value = ++synth
                    }

                log("setProfile() id != 0 просто сохраняем на сервер")
            } catch (e: java.lang.Exception) {
                synthLiveData.value = ++synth
                log("setProfile() id != 0 и в бд пусто, ничего не отправляем")
            }

        }
    }

    override fun setEvent(position: Int) {
    }

    override fun getUserName(tpovId: Int): Profile {
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

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryFB", Logcat.LOG_FIREBASE)
    }
}