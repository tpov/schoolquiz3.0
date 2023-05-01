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
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.android.synthetic.main.create_question_dialog.view.add_question_button
import kotlinx.android.synthetic.main.question_create_item.view.hard_question_checkbox
import kotlinx.android.synthetic.main.question_create_item.view.language_selector
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        dialogView = inflater.inflate(R.layout.create_question_dialog, null)
        questionsContainer = dialogView.findViewById(R.id.questions_container)

        dialogView.add_question_button.setOnClickListener {
            addQuestionItem()
        }

        return AlertDialog.Builder(requireContext())
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

        questionsContainer.addView(questionItemView)
    }

    private fun showLanguageDialog(questionItemView: View) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Выберите язык")
        builder.setItems(LanguageUtils.languagesFullNames) { dialog, which ->
            questionItemView.language_selector.text = LanguageUtils.languagesFullNames[which]
            dialog.dismiss()
        }
        builder.show()
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun createQuestions() {
        val questions = mutableListOf<QuestionEntity>()

        for (i in 0 until questionsContainer.childCount) {
            val questionItemView = questionsContainer.getChildAt(i)

            val questionTitle = questionItemView.question_title.text.toString()
            val questionAnswer = questionItemView.true_button.isChecked
            val questionHard = questionItemView.hard_question_checkbox.isChecked
            val questionLanguage = questionItemView.language_selector.text.toString()
            val language = LanguageUtils.getLanguageShortCode(questionLanguage)
            val question = QuestionEntity(
                null,
                i + 1,
                questionTitle,
                questionAnswer,
                questionHard,
                -1,
                language,
                mainActivityViewModel.getProfile.translater ?: errorGetLvlTranslate(activity)
            )
            questions.add(question)
        }

        // Создание QuizEntity
        val nameQuiz = "Название квеста" // Замените на действительное название квеста
        val tpovId = 0 // Замените на действительный tpovId
        val currentTime = TimeManager.getCurrentTime()

        val quizEntity = QuizEntity(
            id = mainActivityViewModel.getNewIdQuiz(),
            name = nameQuiz,
            creator = mainActivityViewModel.getProfile.name!!,
            created = currentTime,
            questions = questions.size,
            hardQuestions = questions.count { it.hardQuestion },
            totalStars = 0,
            userStars = 0,
            userRating = 0,
            badge = null,
            version = 1,
            timesPlayed = 0,
            isEvent = false,
            tpovId = tpovId
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



}
