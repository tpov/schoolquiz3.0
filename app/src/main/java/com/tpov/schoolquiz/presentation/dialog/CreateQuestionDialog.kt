package com.tpov.schoolquiz.presentation.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.custom.Errors.errorGetLvlTranslate
import com.tpov.schoolquiz.presentation.custom.LanguageUtils
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.question.log
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.android.synthetic.main.create_question_dialog.view.*
import kotlinx.android.synthetic.main.question_create_item.view.*
import kotlinx.coroutines.InternalCoroutinesApi

class CreateQuestionDialog : DialogFragment() {

    @OptIn(InternalCoroutinesApi::class)
    private val mainActivityViewModel by lazy {
        ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }
    private lateinit var dialogView: View
    private lateinit var questionsContainer: LinearLayout
    private var numQuestion = 0
    private var id = 0

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val id = arguments?.getInt("id") ?: -1
        this.id = id

        val inflater = LayoutInflater.from(requireContext())
        dialogView = inflater.inflate(R.layout.create_question_dialog, null)
        questionsContainer = dialogView.findViewById(R.id.questions_container)
        mainActivityViewModel.getQuestionListByIdQuiz(id).forEach { questionEntity ->
            addFilledQuestionItem(questionEntity)
        }

        val dialogTitle: String
        val positiveButtonText: String
        val positiveButtonAction: (DialogInterface, Int) -> Unit

        if (id == -1) {
            dialogTitle = "Создать вопросы"
            positiveButtonText = "Завершить"
            positiveButtonAction = { _, _ -> createQuestions() }

            dialogView.add_question_button.setOnClickListener {
                addQuestionItem()
            }
        } else {
            dialogTitle = "Обновить вопросы"
            positiveButtonText = "Сохранить"
            positiveButtonAction = { _, _ -> createQuestions() }

            dialogView.quiz_title.setText(mainActivityViewModel.getQuizById(id).nameQuiz)

            mainActivityViewModel.getQuestionListByIdQuiz(id).forEach { questionEntity ->
                addFilledQuestionItem(questionEntity)
            }
        }

        return AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(positiveButtonText, positiveButtonAction)
            .setNegativeButton("Отмена") { _, _ -> }
            .create()
    }

    private fun addQuestionItem(): View {
        val inflater = LayoutInflater.from(requireContext())
        val questionItemView = inflater.inflate(R.layout.question_create_item, null)

        questionItemView.language_selector.setOnClickListener {
            showLanguageDialog(questionItemView)
        }

        numQuestion++
        questionItemView.question_number.text = numQuestion.toString()
        questionsContainer.addView(questionItemView)

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

    @OptIn(InternalCoroutinesApi::class)
    private fun createQuestions() {
        val questions = mutableListOf<QuestionEntity>()
        var numHQ = 0
        var numLQ = 0

        var idQuiz = if (id == -1) mainActivityViewModel.getNewIdQuiz()
        else id

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

            val question = QuestionEntity(
                null,
                if (questionHard) numHQ
                else numLQ,
                questionTitle,
                questionAnswer,
                questionHard,
                idQuiz,
                language,
                try {
                    mainActivityViewModel.getProfile().translater ?: errorGetLvlTranslate(activity)
                } catch (e: Exception) {
                    0
                }
            )
            questions.add(question)
        }

        // Создание QuizEntity
        val nameQuiz = dialogView.quiz_title.text.toString()
        val currentTime = TimeManager.getCurrentTime()

        val quizEntity = QuizEntity(
            idQuiz,
            nameQuiz,
            mainActivityViewModel.getProfile().name ?: "",
            if (id == -1) currentTime
            else (mainActivityViewModel.getQuizById(id).data),
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).stars),
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).starsPlayer),
            questions.count { !it.hardQuestion },
            questions.count { it.hardQuestion },
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).starsAll),
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).starsAllPlayer),
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).versionQuiz + 1),
            if (id == -1) null
            else (mainActivityViewModel.getQuizById(id).picture),
            if (id == -1) 1
            else (mainActivityViewModel.getQuizById(id).event),
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).rating),
            if (id == -1) 0
            else (mainActivityViewModel.getQuizById(id).ratingPlayer),
            true,
            if (id == -1) SharedPreferencesManager.getTpovId()
            else (mainActivityViewModel.getQuizById(id).tpovId)
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
