package com.tpov.schoolquiz.presentation.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.core.Errors.errorGetLvlTranslate
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.question.log
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CreateQuestionDialog : DialogFragment() {

    @OptIn(InternalCoroutinesApi::class)
    private val mainActivityViewModel by lazy {
        ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }
    private lateinit var dialogView: View
    private lateinit var questionsContainer: LinearLayout
    private var numQuestion = 0
    private var idQuiz = 0

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val id = arguments?.getInt("id") ?: -1
        this.idQuiz = id
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        val inflater = LayoutInflater.from(requireContext())
        dialogView = inflater.inflate(R.layout.create_question_dialog, null)
        questionsContainer = dialogView.findViewById(R.id.questions_container)

        if (id != -1) {

            dialogView.findViewById<EditText>(R.id.quiz_title).setText(mainActivityViewModel.getQuizById(id).nameQuiz)
            dialogView.findViewById<Button>(R.id.add_question_button).setOnClickListener {
                addQuestionItem()
            }

            CoroutineScope(Dispatchers.IO).launch {
                mainActivityViewModel.getQuestionListByIdQuiz(id).forEach { questionEntity ->
                    withContext(Dispatchers.Main) {
                        addFilledQuestionItem(questionEntity)
                    }
                }
            }
        }

        val alertDialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        alertDialog.window?.attributes?.windowAnimations = R.style.DialogAnimationCreateQuestion
        alertDialog.show()

        dialogView.findViewById<Button>(R.id.add_question_button).setOnClickListener {
            addQuestionItem()
        }

        dialogView.findViewById<Button>(R.id.save_question_button).setOnClickListener {
            createQuestions()
            dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_question_button).setOnClickListener {
            dismiss()
        }

        return alertDialog

    }

    private fun addQuestionItem(): View {
        val inflater = LayoutInflater.from(requireContext())
        val questionItemView = inflater.inflate(R.layout.question_create_item, null)

        val questionTitle = questionItemView.findViewById<EditText>(R.id.question_title)

        questionItemView.findViewById<TextView>(R.id.language_selector).setOnClickListener {
            showLanguageDialog(questionItemView)
        }

            dialogView.findViewById<Button>(R.id.save_question_button).isClickable = true
            dialogView.findViewById<Button>(R.id.save_question_button).isEnabled = true

        // Добавляем TextWatcher к questionTitle
        questionTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                log("onTextChanged $s")
                if (count > 0) {
                    log("onTextChanged count > 0")

                    var lang = ""
                    val languageIdentifier = LanguageIdentification.getClient()
                    languageIdentifier.identifyLanguage(s.toString())
                        .addOnSuccessListener { language ->
                            log("onTextChanged, $language")
                            lang = language ?: "und"
                            if (lang == "und") {
                                val userLocale: Locale = Locale.getDefault()
                                val userLanguageCode: String = userLocale.language

                                val languageFullName =
                                    LanguageUtils.getLanguageFullName(userLanguageCode)
                                questionItemView.findViewById<TextView>(R.id.language_selector).text = languageFullName
                            } else {
                                val languageFullName = LanguageUtils.getLanguageFullName(language)
                                questionItemView.findViewById<TextView>(R.id.language_selector).text = languageFullName
                            }
                        }
                        .addOnFailureListener {
                            val userLocale: Locale = Locale.getDefault()
                            val userLanguageCode: String = userLocale.language
                            lang = userLanguageCode

                            val languageFullName = LanguageUtils.getLanguageFullName(lang)
                            questionItemView.findViewById<TextView>(R.id.language_selector).text = languageFullName
                        }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        numQuestion++
        questionItemView.findViewById<TextView>(R.id.question_number).text = numQuestion.toString()
        questionsContainer.addView(questionItemView)
        questionTitle.requestFocus()
        return questionItemView
    }

    private fun addFilledQuestionItem(questionEntity: QuestionEntity): View {
        val questionItemView = addQuestionItem()

        val questionTitle = questionItemView.findViewById<EditText>(R.id.question_title)
        val trueButton = questionItemView.findViewById<RadioButton>(R.id.true_button)
        val hardQuestionCheckbox =
            questionItemView.findViewById<CheckBox>(R.id.hard_question_checkbox)
        val languageSelector =
            questionItemView.findViewById<AppCompatTextView>(R.id.language_selector)

        questionTitle.setText(questionEntity.nameQuestion)
        trueButton.isChecked = questionEntity.answerQuestion
        hardQuestionCheckbox.isChecked = questionEntity.hardQuestion
        languageSelector.text = LanguageUtils.getLanguageFullName(questionEntity.language)

        return questionItemView
    }

    private fun showLanguageDialog(questionItemView: View) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Язык вопроса")
        builder.setItems(LanguageUtils.languagesFullNames) { dialog, which ->
            questionItemView.findViewById<Button>(R.id.language_selector).text = LanguageUtils.languagesFullNames[which]
            dialog.dismiss()
        }
        builder.show()
    }

    fun findLanguageWithMinLevel(elements: List<QuestionEntity>): String {
        var commonLanguage: String? = null
        var minLevel = Int.MAX_VALUE

        for (element in elements) {
            if (element.language == elements[0].language) {
                if (element.lvlTranslate < minLevel) {
                    minLevel = element.lvlTranslate
                    commonLanguage = element.language
                }
            } else {
                commonLanguage = null
                break
            }
        }

        return if (commonLanguage != null) {
            "$commonLanguage-$minLevel"
        } else {
            ""
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun createQuestions() {
        val questions = mutableListOf<QuestionEntity>()
        var numHQ = 0
        var numLQ = 0

        var newIdQuiz = if (idQuiz == -1) mainActivityViewModel.getNewIdQuiz()
        else idQuiz

        log("getNewIdQuiz: ${mainActivityViewModel.getNewIdQuiz()}")
        for (i in 0 until questionsContainer.childCount) {
            val questionItemView = questionsContainer.getChildAt(i)

            val questionTitle = questionItemView.findViewById<TextView>(R.id.question_title).text.toString()
            val questionAnswer = questionItemView.findViewById<RadioButton>(R.id.true_button).isChecked
            val questionHard = questionItemView.findViewById<CheckBox>(R.id.hard_question_checkbox).isChecked

            if (questionHard) numHQ++
            else numLQ++

            val questionLanguage = questionItemView.findViewById<TextView>(R.id.language_selector).text.toString()
            val language = LanguageUtils.getLanguageShortCode(questionLanguage)

            questions.add(
                QuestionEntity(
                    null,
                    if (questionHard) numHQ
                    else numLQ,
                    questionTitle,
                    questionAnswer,
                    questionHard,
                    idQuiz,
                    language,
                    try {
                        mainActivityViewModel.getProfile().translater ?: errorGetLvlTranslate(
                            activity
                        )
                    } catch (e: Exception) {
                        0
                    },
                    getTpovId().toString() + "|"
                )
            )
        }

        // Создание QuizEntity
        val nameQuiz = dialogView.findViewById<TextView>(R.id.quiz_title).text.toString()
        val currentTime = TimeManager.getCurrentTime()

        val questionMap: MutableMap<Int, MutableSet<String>> = mutableMapOf()

        // Перебираем вопросы и заполняем карту, в которой ключ - номер вопроса, значение - множество языков
        for (question in questions) {
            if (question.nameQuestion != "") {
                val num = question.numQuestion
                val language = question.language

                log("wd23 num:$num, language:$language")
                if (num != null && language != null) {

                    log("wd23 оба значение != null идем дальше")
                    val languages = questionMap.getOrDefault(num, mutableSetOf())
                    languages.add(language)
                    log("wd23 непонятная переменна languages:$languages")
                    questionMap[num] = languages
                    log("wd23 теперь questionMap:$questionMap")
                } else
                    log("wd23 эти переменные содержат null")
            }
        }

        // Находим пересечение множеств языков для всех номеров вопросов
        val commonLanguages =
            questionMap.values.reduce { acc, set -> acc.intersect(set).toMutableSet() }
        log("wd23 commonLanguages: $commonLanguages")
        val profileLvlTranslate = mainActivityViewModel.getProfile().translater
        // Создаем строку в необходимом формате
        val questionLang = commonLanguages.joinToString("|") { "$it-$profileLvlTranslate" }

        log("wd23 questionLang: $questionLang")

        if (idQuiz != -1) mainActivityViewModel.deleteQuestion(idQuiz)

        val quizEntity = QuizEntity(
            newIdQuiz,
            nameQuiz,
            mainActivityViewModel.getProfile().nickname ?: "",
            if (this.idQuiz == -1) currentTime
            else (mainActivityViewModel.getQuizById(this.idQuiz).data),
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).stars),
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).starsPlayer),
            questions.count { !it.hardQuestion && it.nameQuestion != "" },
            questions.count { it.hardQuestion && it.nameQuestion != "" },
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).starsAll),
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).starsAllPlayer),
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).versionQuiz + 1),
            if (this.idQuiz == -1) null
            else (mainActivityViewModel.getQuizById(this.idQuiz).picture),
            if (this.idQuiz == -1) 1
            else (mainActivityViewModel.getQuizById(this.idQuiz).event),
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).rating),
            if (this.idQuiz == -1) 0
            else (mainActivityViewModel.getQuizById(this.idQuiz).ratingPlayer),
            true,
            if (this.idQuiz == -1) getTpovId()
            else (mainActivityViewModel.getQuizById(this.idQuiz).tpovId),
            if (this.idQuiz == -1) questionLang
            else (mainActivityViewModel.getQuizById(this.idQuiz).languages)
        )

        CoroutineScope(Dispatchers.IO).launch {
            mainActivityViewModel.removePlaceInUserQuiz()
        }

        // Сохранение quizEntity и questions в базу данных
        mainActivityViewModel.insertQuiz(quizEntity)

        questions.forEach {
            //if (it.nameQuestion != "") {
                mainActivityViewModel.insertQuestion(
                    it.copy(idQuiz = newIdQuiz)
                )
           // }
        }
    }

    companion object {
        const val NAME = "name"

        fun newInstance(name: String, id: Int): CreateQuestionDialog {
            val fragment = CreateQuestionDialog()
            val args = Bundle()
            args.putString(NAME, name)
            args.putInt("id", id)
            fragment.arguments = args
            return fragment
        }
    }

}
