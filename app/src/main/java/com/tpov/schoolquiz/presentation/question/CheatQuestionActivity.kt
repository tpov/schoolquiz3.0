package com.tpov.schoolquiz.presentation.question

import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.content.Intent
import android.widget.TextView
import android.content.Context
import android.view.MenuItem
import com.tpov.schoolquiz.R
import kotlinx.coroutines.InternalCoroutinesApi

const val EXTRA_ANSWER_SHOW = "com.tpov.geoquiz.answer_show"
private const val EXTRA_ANSWER_IS_TRUE = "com.tpov.geoquiz.answer_is_true"

// TODO: 29.07.2022 Activity -> Fragment
@InternalCoroutinesApi
class CheatActivity : AppCompatActivity() {
    private lateinit var answerTextView: TextView
    private lateinit var showAnswerButton: Button

    private var answerIsTrue = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cheat)

        actionBarSettings()

        answerIsTrue = intent.getBooleanExtra(EXTRA_ANSWER_IS_TRUE, false)

        answerTextView = findViewById(R.id.answer_text_view)
        showAnswerButton = findViewById(R.id.show_answer_button)

        showAnswerButton.setOnClickListener {
            val answerText = when {
                answerIsTrue -> R.string.true_button
                else -> R.string.false_button
            }
            answerTextView.setText(answerText)
            setAnswerShowResult(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    private fun setAnswerShowResult(isAnswerShow: Boolean) {
        val data = Intent().apply {
            putExtra(EXTRA_ANSWER_SHOW, isAnswerShow)
        }
        setResult(Activity.RESULT_OK, data)
    }

    private fun actionBarSettings() {       //Кнопка назад в баре
        val ab = supportActionBar
        ab?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        fun newIntent(packageContext: Context, answerIsTrue: Boolean): Intent {
            return Intent(packageContext, CheatActivity::class.java).apply {
                putExtra(EXTRA_ANSWER_IS_TRUE, answerIsTrue)
            }
        }
    }
}