package com.tpov.schoolquiz.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tpov.schoolquiz.data.database.QuestionDetailDao
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.data.fierbase.QuestionDetail
import com.tpov.schoolquiz.data.fierbase.Quiz
import com.tpov.schoolquiz.data.fierbase.toQuestionDetailEntity
import com.tpov.schoolquiz.domain.repository.RepositoryQuestionDetail
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.core.*
import com.tpov.schoolquiz.presentation.core.Values.synth
import com.tpov.schoolquiz.presentation.core.Values.synthLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class RepositoryQuestionDetailImpl @Inject constructor(
    private val questionDetailDao: QuestionDetailDao
) : RepositoryQuestionDetail {

    private fun isQuizForEvent(quiz: Quiz?): Boolean =
        this.isQuizPrivate(quiz) && quiz?.ratingPlayer != 1

    private fun isQuizPrivate(quiz: Quiz?): Boolean =
        quiz?.event in EVENT_QUIZ_FOR_USER..EVENT_QUIZ_FOR_ADMIN

    private fun isInsertQuizAfterSet(quiz: Quiz): Boolean =
        quiz.event == EVENT_QUIZ_FOR_USER || quiz.event in EVENT_QUIZ_ARENA..EVENT_QUIZ_HOME
                || quiz.event in EVENT_QUIZ_FOR_TESTER..EVENT_QUIZ_FOR_ADMIN && quiz.tpovId != SharedPreferencesManager.getTpovId() && quiz.ratingPlayer == 0

    fun setQuestionDetails(newIdQuiz: Int?, eventQuiz: Int?, id: Int, quiz: Quiz) {
        val questionDetailRef = FirebaseDatabase.getInstance().getReference(
            "$PATH_QUESTION_DETAIL$eventQuiz/${SharedPreferencesManager.getTpovId()}/$newIdQuiz"
        )


        log("setQuestionDetails $newIdQuiz $eventQuiz $id $quiz")
        if (isQuizForEvent(quiz)) FirebaseDatabase.getInstance()
            .getReference("$PATH_QUESTION_DETAIL${eventQuiz?.minus(1)}")
            .child(SharedPreferencesManager.getTpovId().toString())
            .child(newIdQuiz.toString()).setValue(null)
        CoroutineScope(Dispatchers.IO).launch {
            log("setQuestionDetails 2")
            val listQuestionDetail = questionDetailDao.getQuestionDetailByIdQuiz(id)
            listQuestionDetail.forEach { qd ->
                log("setQuestionDetails 3 $qd")
                if (!qd.synthFB) {
                    log("setQuestionDetails 4")
                    questionDetailDao.deleteQuestionDetail(qd.id!!)
                    log("setQuestionDetails 1 ${questionDetailDao.getQuestionDetailByIdQuiz(id)}")



                    questionDetailRef.push().setValue(qd).addOnSuccessListener {
                        if (isInsertQuizAfterSet(quiz)) questionDetailDao.insertQuestionDetail(
                            qd.copy(
                                idQuiz = newIdQuiz!!,
                                synthFB = true
                            )
                        )
                        log("setQuestionDetails addOnSuccessListener")
                    }.addOnFailureListener {
                        if (isInsertQuizAfterSet(quiz)) questionDetailDao.insertQuestionDetail(
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

    fun downloadQuestionDetails(pathQuiz: String) {
        val pathQuestionDetail = when (pathQuiz) {
            PATH_TESTER_QUIZ -> "$PATH_TESTER_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_MODERATOR_QUIZ -> "$PATH_MODERATOR_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_ADMIN_QUIZ -> "$PATH_ADMIN_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_ARENA_QUIZ -> "$PATH_ARENA_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_TOURNIRE_QUIZ -> "$PATH_TOURNIRE_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_TOURNIRE_LEADER_QUIZ -> "$PATH_TOURNIRE_LEADER_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_HOME_QUIZ -> "$PATH_HOME_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            else -> "$PATH_USER_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
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

                        questionDetailDao.insertQuestionDetail(
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
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryFB", Logcat.LOG_FIREBASE)
    }


    override fun insertQuestionDetail(questionDetail: QuestionDetailEntity) {
        questionDetailDao.insertQuestionDetail(questionDetail)
    }

    override fun getQuestionDetailList() = questionDetailDao.getQuestionDetailList()

    override fun updateQuestionDetail(questionDetail: QuestionDetailEntity) {
        questionDetailDao.updateQuizDetail(questionDetail)
    }

    override fun deleteQuestionDetailByIdQuiz(idQuiz: Int) {
        questionDetailDao.deleteQuestionDetailByIdQuiz(idQuiz)
    }

    fun getQuestionDetails(pathQuiz: String) {
        val pathQuestionDetail = when (pathQuiz) {
            PATH_TESTER_QUIZ -> "$PATH_TESTER_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_MODERATOR_QUIZ -> "$PATH_MODERATOR_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_ADMIN_QUIZ -> "$PATH_ADMIN_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_ARENA_QUIZ -> "$PATH_ARENA_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_TOURNIRE_QUIZ -> "$PATH_TOURNIRE_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_TOURNIRE_LEADER_QUIZ -> "$PATH_TOURNIRE_LEADER_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            PATH_HOME_QUIZ -> "$PATH_HOME_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
            else -> "$PATH_USER_QUESTION_DETAIL/${SharedPreferencesManager.getTpovId()}"
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

                        questionDetailDao.insertQuestionDetail(
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
}