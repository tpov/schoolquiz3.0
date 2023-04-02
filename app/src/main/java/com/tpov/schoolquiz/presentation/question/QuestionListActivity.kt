package com.tpov.schoolquiz.presentation.question

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.Quiz
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

const val EXTRA_UPDATE_CURRENT_INDEX = "com.tpov.geoquiz.update_current_index"
const val EXTRA_CURRENT_INDEX = "com.tpov.geoquiz.current_index"
const val EXTRA_CODE_ANSWER = "com.tpov.geoquiz.code_answer"
const val EXTRA_CODE_ID_USER = "com.tpov.geoquiz.code_question_bank"

@InternalCoroutinesApi
class QuestionListActivity : AppCompatActivity(), QuestionListRecyclerAdapter.UpdateData {

    private lateinit var questionViewModel: QuestionViewModel
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val component by lazy {
        (application as MainApp).component
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        component.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_question)
        questionViewModel = ViewModelProvider(this, viewModelFactory)[QuestionViewModel::class.java]

        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        val questionBankAdapter = mutableListOf<Quiz>()
        val currentIndex: Int = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
        val codeAnswer: String = intent.getStringExtra(EXTRA_CODE_ANSWER) ?: questionViewModel.codeAnswer
        val idUser: String = intent.getStringExtra(EXTRA_CODE_ID_USER) ?: questionViewModel.userName
        val codeAnswerArray = codeAnswer.toMutableList()
        Log.d("QuestionListActivity", "map1 = ${questionBankAdapter.map { (it.textResId) }}")

        val questionThis = questionViewModel.questionListThis
        questionBankAdapter.addAll(questionThis.filterNot { it.hardQuestion }.map { Quiz(it.nameQuestion, it.answerQuestion) })

        recyclerView.adapter = QuestionListRecyclerAdapter(
            questionBankAdapter.map { (it.textResId) },
            this,
            codeAnswerArray,
            currentIndex,
            questionBankAdapter
        )
    }

    override fun closeList(position: Int) {
        val intent1 = Intent(this, MainActivity::class.java)
        intent1.putExtra(EXTRA_UPDATE_CURRENT_INDEX, position)
        setResult(position, intent1)
        finish()
    }
}