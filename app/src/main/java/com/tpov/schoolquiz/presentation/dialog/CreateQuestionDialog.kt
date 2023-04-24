package com.tpov.schoolquiz.presentation.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.CreateQuestionDialogBinding
import com.tpov.schoolquiz.presentation.mainactivity.MainActivityViewModel
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.*


class CreateQuestionDialog() : DialogFragment() {

    @OptIn(InternalCoroutinesApi::class)
    private val mainActivityViewModel by lazy {
        ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }

    @OptIn(InternalCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val binding = CreateQuestionDialogBinding.inflate(LayoutInflater.from(activity))
        var question = ArrayList<QuestionEntity>()
        val sharedPref = context?.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val tpovId = sharedPref?.getInt("tpovId", 0)
        var nameQuestion = ""
        var intvQuestion2 = TextInputEditText(requireContext())
        var questionLayout2 = LinearLayout(context)
        var tvQuestion2 = TextView(context)
        var sumbolQuestion2 = TextView(context)

        val languageIdentifier = LanguageIdentification.getClient()
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        builder.setView(binding.root)

        var numQuestion = 0
        var nameQuiz = ""


        binding.tvNext.setOnClickListener {

            tvQuestion2.text =
                "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|$numQuestion"

            intvQuestion2 = TextInputEditText(requireContext())
            tvQuestion2 = TextView(context)

            intvQuestion2.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    nameQuestion = ""
                    nameQuestion = s.toString()
                    tvQuestion2.text =
                        "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|$numQuestion"
                }

                override fun afterTextChanged(s: Editable?) {}
            })
            if (numQuestion != 0) {
                getLanguage(languageIdentifier, nameQuestion) { lang ->
                    question.add(
                        QuestionEntity(
                            null,
                            numQuestion - 1,
                            nameQuestion,
                            getTextTrue(binding.rbTrue).toBoolean(),
                            getTypeText(binding.rbLightQuestion).toBoolean(),
                            -1,
                            lang,
                            mainActivityViewModel.getProfileFBLiveData.value?.translater ?: -1
                        )
                    )
                }

            } else {
                nameQuiz = binding.intvQuiz.text.toString()
            }

            Log.d("adasfgdrh", "===question ${question}")
            // Создание нового question_layout


            questionLayout2.id = View.generateViewId()
            questionLayout2.orientation = LinearLayout.HORIZONTAL

            tvQuestion2.layoutParams =
                LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT)
            questionLayout2.addView(tvQuestion2)

            // Добавление TextView для символа
            sumbolQuestion2.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            sumbolQuestion2.text = " -$ "
            questionLayout2.addView(sumbolQuestion2)
            questionLayout2.background = null
            // Добавление TextInputEditText для ввода текста вопроса
            intvQuestion2.layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2F)
            intvQuestion2.hint = "your question"
            intvQuestion2.requestFocus()
            val cursorDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.cursor)
            intvQuestion2.textCursorDrawable = cursorDrawable
            questionLayout2.addView(intvQuestion2)

            sumbolQuestion2.setTextAppearance(requireContext(), R.style.TerminalText)
            tvQuestion2.setTextAppearance(requireContext(), R.style.TerminalText)
            intvQuestion2.setTextAppearance(requireContext(), R.style.TerminalText)

            tvQuestion2.maxLines = 1
            intvQuestion2.background = null

            intvQuestion2.width = 30
            // Добавление нового question_layout в layout
            binding.layout.addView(questionLayout2)
            questionLayout2 = LinearLayout(context)
            sumbolQuestion2 = TextView(context)
            intvQuestion2 = TextInputEditText(requireContext())

            binding.rbTrue.setOnCheckedChangeListener { _, _ ->
                tvQuestion2.text =
                    "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|$numQuestion"

            }

            binding.rbLightQuestion.setOnCheckedChangeListener { _, _ ->
                tvQuestion2.text =
                    "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|$numQuestion"
            }

            numQuestion++
        }

        binding.tvEnd.setOnClickListener {
            intvQuestion2 = TextInputEditText(requireContext())
            tvQuestion2 = TextView(context)
            intvQuestion2.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    // вызывается перед изменением текста в поле
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    nameQuestion = ""
                    nameQuestion = s.toString()
                    Log.d("adasfgdrh", "Текст изменился: $s")
                }

                override fun afterTextChanged(s: Editable?) {
                    // вызывается после изменения текста в поле
                }
            })

            if (numQuestion != 0) {
                getLanguage(languageIdentifier, nameQuestion) { lang ->

                    question.add(
                        QuestionEntity(
                            null,
                            numQuestion,
                            nameQuestion,
                            getTextTrue(binding.rbTrue).toBoolean(),
                            getTypeText(binding.rbLightQuestion).toBoolean(),
                            -1,
                            lang,
                            mainActivityViewModel.getProfile.translater
                        )
                    )
                }
            } else {
                nameQuiz = binding.intvQuiz.text.toString()
            }

            Log.d("adasfgdrh", "===question $question")
            // Создание нового question_layout


            questionLayout2.id = View.generateViewId()
            questionLayout2.orientation = LinearLayout.HORIZONTAL

            tvQuestion2.layoutParams =
                LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT)
            tvQuestion2.text =
                "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|${++numQuestion}"

            binding.rbLightQuestion.setOnCheckedChangeListener { _, _ ->
                // Установка значения tvQuestion2 в зависимости от состояния кнопок
                tvQuestion2.text =
                    "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|$numQuestion"
            }

            binding.rbTrue.setOnCheckedChangeListener { _, _ ->
                // Установка значения tvQuestion2 в зависимости от состояния кнопок
                tvQuestion2.text =
                    "${getTextTrue(binding.rbTrue)}|${getTypeText(binding.rbLightQuestion)}|$numQuestion"
            }
            questionLayout2.addView(tvQuestion2)

            // Добавление TextView для символа
            sumbolQuestion2.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            sumbolQuestion2.text = " -$ "
            questionLayout2.addView(sumbolQuestion2)
            questionLayout2.background = null
            // Добавление TextInputEditText для ввода текста вопроса
            intvQuestion2.layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2F)
            intvQuestion2.hint = "your question"
            intvQuestion2.requestFocus()
            val cursorDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.cursor)
            intvQuestion2.textCursorDrawable = cursorDrawable
            questionLayout2.addView(intvQuestion2)

            sumbolQuestion2.setTextAppearance(requireContext(), R.style.TerminalText)
            tvQuestion2.setTextAppearance(requireContext(), R.style.TerminalText)
            intvQuestion2.setTextAppearance(requireContext(), R.style.TerminalText)

            tvQuestion2.maxLines = 1
            intvQuestion2.background = null

            intvQuestion2.width = 30

            // Добавление нового question_layout в layout
            binding.layout.addView(questionLayout2)
            questionLayout2 = LinearLayout(context)
            tvQuestion2 = TextView(context)
            sumbolQuestion2 = TextView(context)
            intvQuestion2 = TextInputEditText(requireContext())
                mainActivityViewModel.insertQuiz(
                    QuizEntity(
                        null,
                        nameQuiz,
                        mainActivityViewModel.getProfile.name,
                        TimeManager.getCurrentTime(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null,
                        1,
                        0,
                        0,
                        false,
                        tpovId ?: 0
                    )
                )

                question.forEach {
                    mainActivityViewModel.insertQuestion(
                        it.copy(
                            idQuiz = mainActivityViewModel.getIdQuizByNameQuiz(
                                nameQuiz
                            )
                        )
                    )
            }
        }

        return builder.create()
    }

    private fun getLanguage(
        languageIdentifier: LanguageIdentifier,
        nameQuestion: String,
        callback: (String) -> Unit
    ) {
        var lang = ""

        languageIdentifier.identifyLanguage(nameQuestion)
            .addOnSuccessListener { language ->
                lang = language ?: "und"
                if (lang == "und") {
                    val userLocale: Locale = Locale.getDefault()
                    val userLanguageCode: String = userLocale.language
                    lang = userLanguageCode
                }
                callback(lang)
            }
            .addOnFailureListener {
                val userLocale: Locale = Locale.getDefault()
                val userLanguageCode: String = userLocale.language
                lang = userLanguageCode
                callback(lang)
            }
    }

    private fun getTextTrue(rbTrue: RadioButton): String {
        return if (rbTrue.isChecked) "true"
        else "false"
    }

    private fun getTypeText(rbLightQuestion: RadioButton): String {
        return if (rbLightQuestion.isChecked) "false"
        else "true"
    }

    companion object {
        const val NAME = "name"

        fun newInstance(name: String): CreateQuestionDialog {
            val fragment = CreateQuestionDialog()
            val args = Bundle()
            args.putString("name", name)
            fragment.arguments = args
            return fragment
        }
    }
}