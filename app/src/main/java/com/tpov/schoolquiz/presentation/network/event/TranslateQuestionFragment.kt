package com.tpov.schoolquiz.presentation.network.event

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.FragmentTranslateQuestionBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.mainactivity.MainActivityViewModel
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class TranslateQuestionFragment : Fragment() {

    private lateinit var sourceQuestionEditText: EditText
    private lateinit var translatedQuestionEditText: EditText
    private lateinit var saveTranslationButton: Button
    private lateinit var binding: FragmentTranslateQuestionBinding
    private lateinit var translationAdapter: TranslationQuestionAdapter

    private lateinit var languageSpinner: Spinner
    private lateinit var addLanguageButton: Button
    private val translatedQuestions = mutableMapOf<String, EditText>()

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    companion object {

        private const val ARG_ID_QUIZ = "idQuiz"
        private const val ARG_ID_QUESTION = "idQuestion"

        fun newInstance(idQuiz: Int?, idQuestion: Int?): TranslateQuestionFragment {
            val args = Bundle()

            args.putInt(ARG_ID_QUIZ, idQuiz ?: -1)
            args.putInt(ARG_ID_QUESTION, idQuestion ?: -1)

            val fragment = TranslateQuestionFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTranslateQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val idQuiz = arguments?.getInt(ARG_ID_QUIZ, -1)
        val idQuestion = arguments?.getInt(ARG_ID_QUESTION, -1)

        val viewModel = ViewModelProvider(this, viewModelFactory)[EventViewModel::class.java]

        translationAdapter = TranslationQuestionAdapter(emptyList())
        setupLanguageSpinner()
        if (idQuiz != null && idQuiz != -1) {
            viewModel.loadQuests(idQuiz)
            viewModel.questionLiveData.observe(viewLifecycleOwner) { question ->
                // Здесь обрабатывайте список квестов
                question.forEach {
                    val translatedQuestions =
                        processQuestion(it) // Получите список TranslatedQuestion
                    translationAdapter.translatedQuestions = translatedQuestions
                    translationAdapter.notifyDataSetChanged()
                }
            }
        } else if (idQuestion != null && idQuestion != -1) {
            viewModel.loadQuestion(idQuestion)
            viewModel.questionLiveData.observe(viewLifecycleOwner) { question ->
                question.forEach {
                    val translatedQuestions =
                        processQuestion(it) // Получите список TranslatedQuestion
                    translationAdapter.translatedQuestions = translatedQuestions
                    translationAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun processQuestion(question: QuestionEntity): List<TranslatedQuestion> {
        val translatedQuestions = mutableListOf<TranslatedQuestion>()

        val questionEntities = listOf(question).filter { it.lvlTranslate > 0 }
        translatedQuestions.addAll(questionEntities.map { entity ->
            TranslatedQuestion(
                entity.id,
                entity.numQuestion,
                entity.nameQuestion,
                entity.answerQuestion,
                entity.hardQuestion,
                entity.idQuiz,
                entity.language,
                entity.lvlTranslate
            )
        })

        return translatedQuestions
    }


    private fun addTranslatedQuestionField() {
        val selectedLanguage = languageSpinner.selectedItem.toString()
        if (translatedQuestions.containsKey(selectedLanguage)) {
            Toast.makeText(
                requireContext(),
                "Перевод на этот язык уже добавлен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val translatedQuestionEditText = EditText(requireContext())
        translatedQuestionEditText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        translatedQuestionEditText.hint = selectedLanguage
        translatedQuestionEditText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        translatedQuestionEditText.setLines(4)

        (view as? LinearLayout)?.addView(translatedQuestionEditText)
        translatedQuestions[selectedLanguage] = translatedQuestionEditText
    }

    private fun saveTranslatedQuestion(sourceQuestion: String, translatedQuestion: String) {
        val translations = translatedQuestions.mapValues { it.value.text.toString() }

        // TODO: Реализуйте логику сохранения переведенного вопроса с использованием translations
    }

    private fun setupLanguageSpinner() {
        // Получите список поддерживаемых языков
        val languages = listOf("English", "Spanish", "French", "German") // Замените на список поддерживаемых языков

        // Создайте адаптер для Spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Привяжите адаптер к Spinner
        binding.languageSpinner.adapter = adapter
    }

}

data class TranslatedQuestion(
    val id: Int?,
    val numQuestion: Int,
    val nameQuestion: String,
    val answerQuestion: Boolean,
    val hardQuestion: Boolean,
    val idQuiz: Int,
    val language: String,
    val lvlTranslate: Int
)
