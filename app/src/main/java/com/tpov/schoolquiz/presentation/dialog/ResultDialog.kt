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
import com.tpov.schoolquiz.presentation.custom.CustomProgressBar


class ResultDialog(
    private var event: Int,
    private var showStars: Int,
    private val stars: Int,
    private val starsPercentAll: Int,
    private val starsPlayersAll: Int,
    private val onDismissListener: ((Int) -> Unit)? = null, // добавлен параметр колбэка для слушания нажатия кнопки OK
    private val onRatingSelected: ((Int) -> Unit)? = null, // определение переменной с значением по умолчанию
    context: Context
) : Dialog(context) {

    private var rating = 0

    init {
        setCanceledOnTouchOutside(false)
    }

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

        tvResult.text = "${starsPercentAll}%"
        // Запускаем анимацию заполнения прогресс-бара
        customBar.progress = starsPercentAll / 100.toFloat()
        customBar.leftMarkerPosition = starsPercentAll / 100.toFloat()
        customBar.rightMarkerPosition = starsPlayersAll / 100.toFloat()
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
            if (showStars == 0) onDismissListener?.invoke(rbEvaluation.progress)
            else onDismissListener?.invoke(showStars)

            dismiss()
        }

    }
}