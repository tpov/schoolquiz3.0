package com.tpov.schoolquiz.presentation.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.presentation.custom.CalcValues
import com.tpov.schoolquiz.presentation.custom.CustomProgressBar
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import kotlinx.coroutines.InternalCoroutinesApi


class ResultDialog(
    private var hardQuestion: Boolean,
    private var event: Int,
    private var showStars: Int,
    private val stars: Int,
    private val starsPercentAll: Int,
    private val starsPlayersAll: Int,
    private val firstQuestionDetail: Boolean,
    private val onDismissListener: ((Int, Int) -> Unit)? = null, // определение переменной с значением по умолчанию
    private val onRatingSelected: ((Int) -> Unit)? = null,
    context: Context,
    private val profile: ProfileEntity
) : Dialog(context) {

    private var rating = 0

    init {
        setCanceledOnTouchOutside(false)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.result_dialog)

        val customBar = findViewById<CustomProgressBar>(R.id.customProgressBar)
        val tvResult = findViewById<TextView>(R.id.tv_result)
        val progressBar = findViewById<ProgressBar>(R.id.pb_result)
        val tvEvaluation = findViewById<TextView>(R.id.tv_evaluation)
        val rbEvaluation = findViewById<RatingBar>(R.id.rb_evaluation)
        val bOk = findViewById<Button>(R.id.b_ok)
        val bHelpTranslate = findViewById<Button>(R.id.b_help_translate)
        val tvNolic = findViewById<TextView>(R.id.tv_nolics)

        tvResult.text = "${starsPercentAll}%"
        // Запускаем анимацию заполнения прогресс-бара
        customBar.progress = starsPercentAll / 100.toFloat()
        customBar.leftMarkerPosition = starsPercentAll / 100.toFloat()
        customBar.rightMarkerPosition = starsPlayersAll / 100.toFloat()
        val coins = CalcValues.getValueNolicForGame(hardQuestion, stars, event, firstQuestionDetail, profile)
        tvNolic.text = coins.toString()
        Handler(Looper.getMainLooper()).postDelayed({
            customBar.setProgressWithAnimation(
                stars / 100.toFloat(), 2000,
                tvResult,
                progressBar,
                tvEvaluation,
                rbEvaluation,
                bOk,
                bHelpTranslate,
                showStars == 0,
                event
            )
        }, 1000)

        bOk.setOnClickListener {
            if (showStars == 0) onDismissListener?.invoke(rbEvaluation.progress, QuestionActivity.RESULT_OK)
            else onDismissListener?.invoke(showStars, QuestionActivity.RESULT_OK)

            dismiss()
        }
        bHelpTranslate.setOnClickListener {
            if (showStars == 0) onDismissListener?.invoke(rbEvaluation.progress, QuestionActivity.RESULT_TRANSLATE)
            else onDismissListener?.invoke(showStars, QuestionActivity.RESULT_TRANSLATE)
        }

    }
}