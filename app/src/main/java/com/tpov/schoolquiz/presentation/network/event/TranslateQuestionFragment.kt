package com.tpov.schoolquiz.presentation.network.event

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.databinding.FragmentTranslateQuestionBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

class TranslateQuestionFragment : Fragment() {

    private lateinit var binding: FragmentTranslateQuestionBinding
    private lateinit var translationAdapter: TranslationQuestionAdapter

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

    private var questionIndex = 0
    private var questions: List<QuestionEntity>? = null

    companion object {

        private const val ARG_ID_QUIZ = "idQuiz"

        fun newInstance(idQuiz: Int?): TranslateQuestionFragment {
            val args = Bundle()

            args.putInt(ARG_ID_QUIZ, idQuiz ?: -1)

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

        val viewModel = ViewModelProvider(this, viewModelFactory)[EventViewModel::class.java]

        val languages = listOf("EN", "RU", "FR") // Замените на список доступных языков
        translationAdapter = TranslationQuestionAdapter(mutableListOf(), languages)
        binding.recyclerViewQuestions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewQuestions.adapter = translationAdapter

        if (idQuiz != -1) {
            log("getQuestionListUseCase() idQuiz != -1")
            viewModel.questionLiveData.observe(viewLifecycleOwner) { receivedQuestions ->
                questions = receivedQuestions

                if (!receivedQuestions.isNullOrEmpty()) {
                    loadNextQuestion()
                } else {
                    Toast.makeText(
                        activity,
                        "Не удалось найти вопросы, которые можно перевести",
                        Toast.LENGTH_LONG
                    ).show()
                }

                log("getQuestionListUseCase(): $receivedQuestions")
            }

            viewModel.loadQuests()
        }

        binding.buttonAddTranslation.setOnClickListener {
            log("Add Translation button clicked")
            translationAdapter.addNewQuestion()
        }

        binding.buttonSave.setOnClickListener {
            val currentQuestion = questions?.get(questionIndex)
            val updatedQuestions = translationAdapter.getUpdatedQuestions()
            viewModel.saveQuestions(updatedQuestions)
            translationAdapter.questions.clear()
            currentQuestion?.let {
                translationAdapter.questions.add(it)
                translationAdapter.notifyDataSetChanged()
            }
            loadNextQuestion()
        }

        // binding.buttonCancel.setOnClickListener {
        //     requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        // }
    }

    private fun loadNextQuestion() {
        if (questionIndex < (questions?.size ?: 0)) {
            val nextQuestion = questions?.get(questionIndex)
            nextQuestion?.let {
                translationAdapter.questions.add(it)
                translationAdapter.notifyDataSetChanged()
                questionIndex++
            }
        } else {
            Toast.makeText(
                activity,
                "Все доступные вопросы загружены",
                Toast.LENGTH_SHORT
            ).show()
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
    }
}
