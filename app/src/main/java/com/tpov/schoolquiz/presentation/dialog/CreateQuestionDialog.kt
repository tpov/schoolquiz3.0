package com.tpov.schoolquiz.presentation.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.custom.Errors.errorGetLvlTranslate
import com.tpov.schoolquiz.presentation.custom.LanguageUtils
import com.tpov.schoolquiz.presentation.mainactivity.MainActivityViewModel
import com.tpov.schoolquiz.presentation.question.log
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.android.synthetic.main.create_question_dialog.view.add_question_button
import kotlinx.android.synthetic.main.create_question_dialog.view.quiz_title
import kotlinx.android.synthetic.main.question_create_item.view.hard_question_checkbox
import kotlinx.android.synthetic.main.question_create_item.view.language_selector
import kotlinx.android.synthetic.main.question_create_item.view.question_number
import kotlinx.android.synthetic.main.question_create_item.view.question_title
import kotlinx.android.synthetic.main.question_create_item.view.true_button
import kotlinx.coroutines.InternalCoroutinesApi

class CreateQuestionDialog : DialogFragment() {

    @OptIn(InternalCoroutinesApi::class)
    private val mainActivityViewModel by lazy {
        ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }
    private lateinit var dialogView: View
    private lateinit var questionsContainer: LinearLayout
    private var numQuestion = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        dialogView = inflater.inflate(R.layout.create_question_dialog, null)
        questionsContainer = dialogView.findViewById(R.id.questions_container)

        dialogView.add_question_button.setOnClickListener {
            addQuestionItem()
        }

        return AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Создать вопросы")
            .setView(dialogView)
            .setPositiveButton("Завершить") { _, _ -> createQuestions() }
            .setNegativeButton("Отмена") { _, _ -> }
            .create()
    }

    private fun addQuestionItem() {
        val inflater = LayoutInflater.from(requireContext())
        val questionItemView = inflater.inflate(R.layout.question_create_item, null)

        questionItemView.language_selector.setOnClickListener {
            showLanguageDialog(questionItemView)
        }

        numQuestion++
        questionItemView.question_number.text = numQuestion.toString()
        questionsContainer.addView(questionItemView)
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

        var idQuiz = mainActivityViewModel.getNewIdQuiz()
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
                mainActivityViewModel.getProfile.translater ?: errorGetLvlTranslate(activity)
            )
            questions.add(question)
        }

        // Создание QuizEntity
        val nameQuiz = dialogView.quiz_title.text.toString()
        val tpovId = mainActivityViewModel.tpovId
        val currentTime = TimeManager.getCurrentTime()

        val quizEntity = QuizEntity(
            idQuiz,
            nameQuiz,
            mainActivityViewModel.getProfile.name!!,
            currentTime,
            0,
            0,
            questions.count { !it.hardQuestion },
            questions.count { it.hardQuestion },
            0,
            0,
            0,
            null,
            1,
            0,
            0,
            false,
            tpovId
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

        fun newInstance(name: String): CreateQuestionDialog {
            val fragment = CreateQuestionDialog()
            val args = Bundle()
            args.putString("name", name)
            fragment.arguments = args
            return fragment
        }
    }

}
