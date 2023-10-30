package com.tpov.schoolquiz.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tpov.schoolquiz.data.database.QuestionDao
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.log
import com.tpov.schoolquiz.data.fierbase.Question
import com.tpov.schoolquiz.data.fierbase.Quiz
import com.tpov.schoolquiz.data.fierbase.toQuestion
import com.tpov.schoolquiz.domain.repository.RepositoryQuestion
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.core.*
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.abs

class RepositoryQuestionImpl @Inject constructor(
    private val questionDao: QuestionDao
) : RepositoryQuestion {
    override suspend fun getQuestionList() = questionDao.getQuestionList()

    override fun getQuestionsByIdQuiz(idQuiz: Int) = questionDao.getQuestionByIdQuiz(idQuiz)

    private fun isQuizForEvent(quiz: Quiz?): Boolean =
        this.isQuizPrivate(quiz) && quiz?.ratingPlayer != 1

    private fun isQuizPrivate(quiz: Quiz?): Boolean =
        quiz?.event in EVENT_QUIZ_FOR_USER..EVENT_QUIZ_FOR_ADMIN

    fun setQuestions(newIdQuiz: Int?, eventQuiz: Int?, id: Int, quiz: Quiz) {
        val questionRef = FirebaseDatabase.getInstance().getReference(
            if (eventQuiz != EVENT_QUIZ_FOR_USER) "$PATH_QUESTION$eventQuiz/$newIdQuiz"
            else "$PATH_USER_QUESTION/${SharedPreferencesManager.getTpovId()}/$newIdQuiz"
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
                questionDao.getQuestionByIdQuiz(id).forEach {
                    log("setQuestions 2: $it")
                    questionRef
                        .child(if (it.hardQuestion) "-${it.numQuestion}" else "${it.numQuestion}")
                        .child(if (it.lvlTranslate < BARRIER_QUIZ_ID_LOCAL_AND_REMOVE) "$SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG${it.language}" else it.language)
                        .setValue(it.toQuestion())
                    questionDao.deleteQuestion(id)
                    delay(100)
                    if (isInsertQuizAfterSet(quiz)) questionDao.insertQuestion(it.copy(idQuiz = newIdQuiz!!))
                }
            }
            deferred.await()
        }
    }


    private fun isInsertQuizAfterSet(quiz: Quiz): Boolean =
        quiz.event == EVENT_QUIZ_FOR_USER || quiz.event in EVENT_QUIZ_ARENA..EVENT_QUIZ_HOME
                || quiz.event in EVENT_QUIZ_FOR_TESTER..EVENT_QUIZ_FOR_ADMIN && quiz.tpovId != SharedPreferencesManager.getTpovId() && quiz.ratingPlayer == 0


    override suspend fun insertQuestion(question: QuestionEntity) {
        questionDao.insertQuestion(question)
    }

    override fun deleteQuestionByIdQuiz(idQuiz: Int) {
        questionDao.deleteQuestionByIdQuiz(idQuiz)
    }

    override fun deleteQuestion(id: Int) {
        questionDao.deleteQuestion(id)
    }

    fun getQuestions(eventQuiz: String, idQuiz: Int) {
        val pathQuestion = when (eventQuiz) {
            PATH_TESTER_QUIZ -> "$PATH_TESTER_QUESTION/$idQuiz"
            PATH_MODERATOR_QUIZ -> "$PATH_MODERATOR_QUESTION/$idQuiz"
            PATH_ADMIN_QUIZ -> "$PATH_ADMIN_QUESTION/$idQuiz"
            PATH_ARENA_QUIZ -> "$PATH_ARENA_QUESTION/$idQuiz"
            PATH_TOURNIRE_QUIZ -> "$PATH_TOURNIRE_QUESTION/$idQuiz"
            PATH_TOURNIRE_LEADER_QUIZ -> "$PATH_TOURNIRE_LEADER_QUESTION/$idQuiz"
            PATH_HOME_QUIZ -> "$PATH_HOME_QUESTION/$idQuiz"
            else -> "$PATH_USER_QUESTION/${SharedPreferencesManager.getTpovId()}/$idQuiz"
        }

        val database = FirebaseDatabase.getInstance()
        val questionRef = database.getReference(pathQuestion)

        questionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (numQuestionSnapshot in dataSnapshot.children) {
                    for (languageSnapshot in numQuestionSnapshot.children) {
                        val question = languageSnapshot.getValue(Question::class.java)

                        CoroutineScope(Dispatchers.IO).launch {
                            questionDao.insertQuestion(
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
                            Values.synthLiveData.postValue(++Values.synth)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun getTypeQuestion(toInt: Int) = toInt <= 0
}