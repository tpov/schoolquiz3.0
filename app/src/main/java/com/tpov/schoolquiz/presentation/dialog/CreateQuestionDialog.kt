package com.tpov.schoolquiz.presentation.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.custom.Errors.errorGetLvlTranslate
import com.tpov.schoolquiz.presentation.custom.LanguageUtils
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.question.log
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.android.synthetic.main.create_question_dialog.view.*
import kotlinx.android.synthetic.main.question_create_item.view.*
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.*

class CreateQuestionDialog : DialogFragment() {

    @OptIn(InternalCoroutinesApi::class)
    private val mainActivityViewModel by lazy {
        ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }
    private lateinit var dialogView: View
    private lateinit var questionsContainer: LinearLayout
    private var numQuestion = 0
    private var id1 = 0

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val id = arguments?.getInt("id") ?: -1
        this.id1 = id
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        val inflater = LayoutInflater.from(requireContext())
        dialogView = inflater.inflate(R.layout.create_question_dialog, null)
        questionsContainer = dialogView.findViewById(R.id.questions_container)
        mainActivityViewModel.getQuestionListByIdQuiz(id).forEach { questionEntity ->
            addFilledQuestionItem(questionEntity)
        }

        if (id == -1) {

            dialogView.add_question_button.setOnClickListener {
                addQuestionItem()
            }

        } else {
            dialogView.quiz_title.setText(mainActivityViewModel.getQuizById(id).nameQuiz)

            mainActivityViewModel.getQuestionListByIdQuiz(id).forEach { questionEntity ->
                addFilledQuestionItem(questionEntity)
            }
        }

        val alertDialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        // Установка анимации входа
        alertDialog.window?.attributes?.windowAnimations = R.style.DialogAnimationCreateQuestion
        alertDialog.show()

        dialogView.save_question_button.setOnClickListener {
            createQuestions()
            dismiss()
        }

        dialogView.cancel_question_button.setOnClickListener {
            dismiss()
        }

        return alertDialog

    }

    private fun addQuestionItem(): View {
        val inflater = LayoutInflater.from(requireContext())
        val questionItemView = inflater.inflate(R.layout.question_create_item, null)

        val questionTitle = questionItemView.findViewById<EditText>(R.id.question_title)

        questionItemView.language_selector.setOnClickListener {
            showLanguageDialog(questionItemView)
        }

        dialogView.save_question_button.isClickable = true
        dialogView.save_question_button.isEnabled = true

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
                                questionItemView.language_selector.text = languageFullName
                            } else {
                                val languageFullName = LanguageUtils.getLanguageFullName(language)
                                questionItemView.language_selector.text = languageFullName
                            }
                        }
                        .addOnFailureListener {
                            val userLocale: Locale = Locale.getDefault()
                            val userLanguageCode: String = userLocale.language
                            lang = userLanguageCode

                            val languageFullName = LanguageUtils.getLanguageFullName(lang)
                            questionItemView.language_selector.text = languageFullName
                        }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        numQuestion++
        questionItemView.question_number.text = numQuestion.toString()
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
            questionItemView.language_selector.text = LanguageUtils.languagesFullNames[which]
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

        var idQuiz = if (id1 == -1) mainActivityViewModel.getNewIdQuiz()
        else id1

        log("getNewIdQuiz: ${mainActivityViewModel.getNewIdQuiz()}")
        for (i in 0 until questionsContainer.childCount) {
            val questionItemView = questionsContainer.getChildAt(i)

            val questionTitle = questionItemView.question_title.text.toString()
            val questionAnswer = questionItemView.true_button.isChecked
            val questionHard = questionItemView.hard_question_checkbox.isChecked

            if (questionHard) numHQ++
            else numLQ++

            val questionLanguage = questionItemView.language_selector.text.toString()
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
                    getTpovId().toString()
                )
            )
        }

        // Создание QuizEntity
        val nameQuiz = dialogView.quiz_title.text.toString()
        val currentTime = TimeManager.getCurrentTime()

        val quizEntity = QuizEntity(
            idQuiz,
            nameQuiz,
            mainActivityViewModel.getProfile().name ?: "",
            if (id1 == -1) currentTime
            else (mainActivityViewModel.getQuizById(id1).data),
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).stars),
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).starsPlayer),
            questions.count { !it.hardQuestion },
            questions.count { it.hardQuestion },
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).starsAll),
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).starsAllPlayer),
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).versionQuiz + 1),
            if (id1 == -1) null
            else (mainActivityViewModel.getQuizById(id1).picture),
            if (id1 == -1) 1
            else (mainActivityViewModel.getQuizById(id1).event),
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).rating),
            if (id1 == -1) 0
            else (mainActivityViewModel.getQuizById(id1).ratingPlayer),
            true,
            if (id1 == -1) getTpovId()
            else (mainActivityViewModel.getQuizById(id1).tpovId),
            if (id1 == -1) findLanguageWithMinLevel(mainActivityViewModel.getQuestionListByIdQuiz(idQuiz))
            else findLanguageWithMinLevel(mainActivityViewModel.getQuestionListByIdQuiz(idQuiz))
        )

        // Сохранение quizEntity и questions в базу данных
        mainActivityViewModel.insertQuiz(quizEntity)

        questions.forEach {
            mainActivityViewModel.insertQuestion(
                it.copy(
                    idQuiz = mainActivityViewModel.getIdQuizByNameQuiz(
                        nameQuiz
                    )
                )
            )
        }

        mainActivityViewModel.removePlaceInUserQuiz()
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
