package com.tpov.schoolquiz.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.tpov.schoolquiz.data.database.ProfileDao
import com.tpov.schoolquiz.data.database.QuestionDao
import com.tpov.schoolquiz.data.database.QuestionDetailDao
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.fierbase.Quiz
import com.tpov.schoolquiz.data.fierbase.toQuizEntity
import com.tpov.schoolquiz.domain.repository.RepositoryQuiz
import com.tpov.schoolquiz.presentation.BARRIER_QUIZ_ID_LOCAL_AND_REMOVE
import com.tpov.schoolquiz.presentation.DELAY_TIMEOUT_SYNTH_FB
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_ARENA
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_ADMIN
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_TESTER
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_USER
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_HOME
import com.tpov.schoolquiz.presentation.LVL_ADMIN_1_LVL
import com.tpov.schoolquiz.presentation.LVL_DEVELOPER_1_LVL
import com.tpov.schoolquiz.presentation.LVL_MODERATOR_1_LVL
import com.tpov.schoolquiz.presentation.LVL_TESTER_1_LVL
import com.tpov.schoolquiz.presentation.LVL_TRANSLATOR_1_LVL
import com.tpov.schoolquiz.presentation.QUIZ_VERSION_DEFAULT
import com.tpov.schoolquiz.presentation.SPLIT_BETWEEN_LANGUAGES
import com.tpov.schoolquiz.presentation.SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG
import com.tpov.schoolquiz.presentation.core.KEY_RATING
import com.tpov.schoolquiz.presentation.core.KEY_STARS
import com.tpov.schoolquiz.presentation.core.KEY_STARS_ALL
import com.tpov.schoolquiz.presentation.core.KEY_STARS_ALL_PLAYER
import com.tpov.schoolquiz.presentation.core.KEY_STARS_PLAYER
import com.tpov.schoolquiz.presentation.core.KEY_VERSION_QUIZ
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.PATH_ADMIN_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_ARENA_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_HOME_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_ID_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_LIST_READ
import com.tpov.schoolquiz.presentation.core.PATH_MODERATOR_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_PHOTO
import com.tpov.schoolquiz.presentation.core.PATH_PLAYERS_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_TESTER_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_TOURNIRE_LEADER_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_TOURNIRE_QUIZ
import com.tpov.schoolquiz.presentation.core.PATH_USER_QUIZ
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.core.Values
import com.tpov.schoolquiz.presentation.core.Values.context
import com.tpov.schoolquiz.presentation.core.Values.setLoadPB
import com.tpov.schoolquiz.presentation.core.Values.synth
import com.tpov.schoolquiz.presentation.core.Values.synthLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject

class RepositoryQuizImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val questionDao: QuestionDao,
    private val questionDetailDao: QuestionDetailDao,
    private val profileDao: ProfileDao
) : RepositoryQuiz {

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
            callback("$pictureString")
        }.addOnFailureListener {
            callback(null)
        }
    }

    private fun setQuizResults(quiz: QuizEntity) {
        val quizRatingMap = mapOf(
            KEY_RATING to quiz.ratingPlayer,
            KEY_STARS to quiz.stars,
            KEY_STARS_ALL to quiz.starsAll
        )
        if (quiz.ratingPlayer != 0) FirebaseDatabase.getInstance()
            .getReference("$PATH_PLAYERS_QUIZ/${quiz.id}/${SharedPreferencesManager.getTpovId()}")
            .updateChildren(quizRatingMap)
    }


    private fun isInsertQuizAfterSet(quiz: Quiz): Boolean =
        quiz.event == EVENT_QUIZ_FOR_USER || quiz.event in EVENT_QUIZ_ARENA..EVENT_QUIZ_HOME
                || quiz.event in EVENT_QUIZ_FOR_TESTER..EVENT_QUIZ_FOR_ADMIN && quiz.tpovId != SharedPreferencesManager.getTpovId() && quiz.ratingPlayer == 0


    private fun setReadToFalse() {
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference(PATH_LIST_READ)
        myRef.setValue(false)
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
                        } else Values.loadText.value =
                            (context.getString(com.tpov.schoolquiz.R.string.fb_load_text_weit_unlock_server))
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


    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryQuizImpl", Logcat.LOG_FIREBASE)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getQuiz(
        vararg pathQuiz: String? = arrayOf(
            "$PATH_USER_QUIZ/${SharedPreferencesManager.getTpovId()}",
            if (profileDao.getProfile(SharedPreferencesManager.getTpovId()).tester!! >= LVL_TESTER_1_LVL
                || profileDao.getProfile(SharedPreferencesManager.getTpovId()).developer!! >= LVL_DEVELOPER_1_LVL
            ) PATH_TESTER_QUIZ else null,
            if (profileDao.getProfile(SharedPreferencesManager.getTpovId()).moderator!! >= LVL_MODERATOR_1_LVL
                || profileDao.getProfile(SharedPreferencesManager.getTpovId()).developer!! >= LVL_DEVELOPER_1_LVL
            ) PATH_MODERATOR_QUIZ else null,
            if (profileDao.getProfile(SharedPreferencesManager.getTpovId()).admin!! >= LVL_ADMIN_1_LVL
                || profileDao.getProfile(SharedPreferencesManager.getTpovId()).developer!! >= LVL_DEVELOPER_1_LVL
            ) PATH_ADMIN_QUIZ else null,
            PATH_ARENA_QUIZ, PATH_TOURNIRE_QUIZ, PATH_TOURNIRE_LEADER_QUIZ, PATH_HOME_QUIZ
        ), getQuestionDetails: Boolean = true
    ) {
        Values.loadText.postValue("")
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
                                    val newMax =
                                        it.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG)[1].toInt()
                                    if (newMax > max) max = newMax
                                }
                                max
                            } catch (e: Exception) {
                                try {quiz?.languages?.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG)!![1].toInt()
                                } catch (e: Exception) {0}
                            }
                            val quizVersionLocal =
                                SharedPreferencesManager.getVersionQuiz(idQuiz.toString())

                            if (
                                (((quiz?.event in EVENT_QUIZ_ARENA..EVENT_QUIZ_HOME)
                                        && maxLvlTranslate >= LVL_TRANSLATOR_1_LVL || ((quiz?.event in EVENT_QUIZ_FOR_USER..EVENT_QUIZ_FOR_ADMIN)
                                        && quiz?.ratingPlayer != 1))
                                        && (quiz?.versionQuiz!! > quizVersionLocal || quizVersionLocal == -1 || getQuestionDetails)
                                        || quizVersionLocal == QUIZ_VERSION_DEFAULT && quiz?.event == EVENT_QUIZ_FOR_USER)
                            ) savePictureToLocalDirectory(quiz!!.picture) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(Dispatchers.IO) {
                                        deleteQuiz(idQuiz)
                                        quizDao.insertQuiz(
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
                                RepositoryQuestionImpl(questionDao).getQuestions(
                                    quizItem ?: PATH_HOME_QUIZ, idQuiz
                                )
                                if (getQuestionDetails) RepositoryQuestionDetailImpl(
                                    questionDetailDao
                                ).getQuestionDetails(
                                    quizItem ?: PATH_HOME_QUIZ
                                )
                                SharedPreferencesManager.setVersionQuiz(
                                    idQuiz.toString(),
                                    quiz.versionQuiz
                                )
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
        } else Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_server_load))
    }

    override fun getQuiz(id: Int) = quizDao.getQuizById(id)

    override fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int) =
        quizDao.getIdQuizByNameQuizDB(nameQuiz, tpovId) ?: 0

    override suspend fun getQuizListLiveData(id: Int) = quizDao.getQuizLiveDataDB(id)

    override fun getQuizListLiveData() = quizDao.getEventLiveDataDB()

    override suspend fun getQuizList() = quizDao.getQuizEvent()

    override suspend fun getQuizList(tpovId: Int) = quizDao.getQuizList(tpovId)

    override fun updateQuiz(quiz: QuizEntity) = quizDao.updateQuiz(quiz)

    override fun insertQuiz(quiz: QuizEntity) = quizDao.insertQuiz(quiz)

    override fun deleteQuiz(idQuiz: Int) {
        quizDao.deleteQuizById(idQuiz)
        questionDetailDao.deleteQuestionByIdQuiz(idQuiz)
        questionDetailDao.deleteQuestionDetailByIdQuizAndSynth(idQuiz)
    }

    override fun deleteQuiz() {

    }

    override suspend fun downloadQuizHome() {
        getQuiz(PATH_HOME_QUIZ, PATH_ARENA_QUIZ, getQuestionDetails = false)
    }

    override suspend fun unloadQuiz() {
        val lockObject = Object()

        log("setAllQuiz")
        if (waitForReadToBecomeTrue()) {

            log("setAllQuiz waitForReadToBecomeTrue")

            val database = FirebaseDatabase.getInstance()
            Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_send_quizz))

            log("setAllQuiz 22 ${quizDao.getQuizList(SharedPreferencesManager.getTpovId())}")
            var maxIdQuiz = 0
            var i = 0

            val quizEvent = quizDao.getQuizEvent()
            quizEvent.forEach {
                val eventQuiz = it.event
                val quizRef = database.getReference(
                    if (eventQuiz == 1) "$PATH_USER_QUIZ/${SharedPreferencesManager.getTpovId()}" else "$PATH_QUIZ$eventQuiz"
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
                                    log(
                                        "setAllQuiz: $newVersionQuiz = ${
                                            SharedPreferencesManager.getVersionQuiz(
                                                newIdQuiz.toString()
                                            )
                                        }"
                                    )
                                    if (newVersionQuiz <= SharedPreferencesManager.getVersionQuiz(
                                            newIdQuiz.toString()
                                        )
                                    ) {

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
                                                if (isInsertQuizAfterSet(newQuiz)) quizDao.insertQuiz(
                                                    newQuiz.toQuizEntity(
                                                        newIdQuiz!!,
                                                        it.stars,
                                                        it.starsAll,
                                                        it.rating,
                                                        it.picture
                                                    )
                                                )
                                            }.addOnFailureListener { item ->
                                                if (isInsertQuizAfterSet(newQuiz)) quizDao.insertQuiz(
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
                                        quizDao.deleteQuizById(it.id!!)

                                        RepositoryQuestionImpl(questionDao).setQuestions(
                                            newIdQuiz,
                                            eventQuiz,
                                            it.id!!,
                                            newQuiz
                                        )
                                        RepositoryQuestionDetailImpl(questionDetailDao).setQuestionDetails(
                                            newIdQuiz,
                                            eventQuiz,
                                            it.id!!,
                                            newQuiz
                                        )
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
                    quizDao.getQuizList(SharedPreferencesManager.getTpovId()).forEach {
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

    override suspend fun downloadQuizes() {
        getQuiz()
    }

    val database = FirebaseDatabase.getInstance()

}

