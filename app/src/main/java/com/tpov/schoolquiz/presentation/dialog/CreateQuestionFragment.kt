package com.tpov.schoolquiz.presentation.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.presentation.utils.LanguageAdapter
import com.tpov.schoolquiz.databinding.FragmentCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.main.MainViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuestionFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: MainViewModel

    private var idQuiz = -1

    private var _binding: FragmentCreateQuizBinding? = null
    private val binding get() = _binding!!

    private var questionEntities: List<QuestionEntity>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            idQuiz = it.getInt(ARG_QUIZ)
            viewModel.getQuizByIdQuiz(idQuiz)
            viewModel.getQuestionByIdQuiz(idQuiz)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initSetOnClickListeners()

    }

    private fun initSetOnClickListeners() {
        binding.bSave.setOnClickListener {
            // Логика для сохранения данных
            val newQuestion = QuestionEntity(
                id = questionEntity?.id, // Если редактирование, сохраняем ID
                numQuestion = questionEntity?.numQuestion
                    ?: 0, // Или получаем номер вопроса откуда-то
                nameQuestion = binding.tvQuestionText.text.toString(),
                pathPictureQuestion = questionEntity?.pathPictureQuestion,
                answer = determineCorrectAnswer(), // Ваша логика определения правильного ответа
                nameAnswers = "${binding.bAnswer1.text}|${binding.bAnswer2.text}", // Форматируем ответы
                hardQuestion = binding.checkBox2.isChecked,
                idQuiz = quizEntity!!.id!!, // Используем ID викторины
                language = questionEntity?.language ?: "default",
                lvlTranslate = questionEntity?.lvlTranslate ?: 0
            )

            // Сохранение или обновление вопроса в базе данных
            saveQuestion(newQuestion)
        }
    }

    private fun initView() {
        if (idQuiz != -1) {
            lifecycleScope.launch { viewModel.quizData.collect { it.let { updateUiQuiz(it!!) } } }
            lifecycleScope.launch { viewModel.questionData.collect { it.let {
                questionEntities = it!!
                updateUiQuestion(1) } } }
        }
    }

    private fun updateUiQuiz(quiz: QuizEntity) = with(binding) {
        tvQuizName.setText(quiz.nameQuiz)
        imvQuiz.setImageDrawable(quiz.picture: String)
    }

    private fun updateUiQuestion(numQuestion: Int) = with(binding) {
        val questionEntitiesLanguage = questionEntities?.filter { it.numQuestion == numQuestion }

        questionEntitiesLanguage?.forEach { question ->
            // Обновление текста вопроса
            val questionTextId = resources.getIdentifier("tv_question_text${question.id}", "id", context.packageName)
            val questionTextView = view?.findViewById(questionTextId) ?: EditText(context).apply {
                id = questionTextId
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                llQuestion.addView(this)
            }
            questionTextView.setText(question.nameQuestion)

            // Обновление спиннера языка вопроса и синхронизация с TextView для языка ответа
            val languageSpinnerId = resources.getIdentifier("sp_language_question${question.id}", "id", context.packageName)
            val languageSpinner = view?.findViewById(languageSpinnerId) ?: Spinner(context).apply {
                id = languageSpinnerId
                layoutParams = LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT)
                llQuestion.addView(this)
            }

            // Предполагаем, что адаптер уже создан и содержит нужные данные
            languageSpinner.adapter = LanguageAdapter(LanguageUtils.languagesFullNames)
            languageSpinner.setSelection(getLanguagePosition(question.language))
            languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    // Отображение выбранного языка в TextView для ответов
                    tvLanguageAnswer.text = parent.getItemAtPosition(position).toString()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    tvLanguageAnswer.text = ""
                }
            }

            // Обновление ответов
            question.nameAnswers.split('|').forEachIndexed { index, answer ->
                val answerEditId = resources.getIdentifier("b_answer${question.id}$index", "id", context?.packageName)
                val answerEditText = view?.findViewById(answerEditId) ?: EditText(context).apply {
                    id = answerEditId
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                    llAnswers.addView(this)
                }
                answerEditText.setText(answer)
            }
        }
    }

    private fun getLanguagePosition(language: String): Int {
        // Эта функция возвращает позицию языка в адаптере, предполагаем, что список языков известен и статичен
        return languageList.indexOf(language)
    }

        val correctAnswerIndex = questionEntitiesLanguage?.get(0)?.answer ?: 0
        val correctAnswerButtonId = resources.getIdentifier("bAnswer${questionEntitiesLanguage?.get(0)?.id}$correctAnswerIndex", "id", context?.packageName)
        view?.findViewById<Button>(correctAnswerButtonId)?.setBackgroundResource(R.color.back_main_red)
    }

    private fun determineCorrectAnswer(): Int {
        // Ваша логика определения правильного ответа
        return 0
    }

    private fun saveQuestion(question: QuestionEntity) {
        // Логика сохранения или обновления вопроса в базе данных
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_QUIZ = "arg_quiz"
        private const val ARG_QUESTION = "arg_question"

        fun newInstance(
            idQuiz: Int
        ): CreateQuestionFragment {
            val fragment = CreateQuestionFragment()
            val args = Bundle()
            args.putInt(ARG_QUIZ, idQuiz)
            fragment.arguments = args
            return fragment
        }
    }
}