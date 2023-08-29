package com.tpov.schoolquiz.presentation.network.event

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.databinding.FragmentTranslateQuestionBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class TranslateQuestionFragment : BaseFragment() {

    private lateinit var binding: FragmentTranslateQuestionBinding
    private lateinit var translationAdapter: TranslationQuestionAdapter
    private var numQuestion = 0

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    private lateinit var viewModel: EventViewModel
    private var listSize = 0
    private var idQuiz = 0
    private var idQuestion = 0

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    private var questionIndex = 0
    private var questions: MutableList<QuestionEntity>? = null
    private var hardQuestions: MutableList<QuestionEntity>? = null

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

        idQuiz = arguments?.getInt(ARG_ID_QUIZ, -1) ?: -1
        idQuestion = arguments?.getInt(ARG_ID_QUESTION, -1) ?: -1

        if (idQuiz == -1) {
            viewModel = ViewModelProvider(this, viewModelFactory)[EventViewModel::class.java]

            translationAdapter = TranslationQuestionAdapter(mutableListOf())
            binding.recyclerViewQuestions.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerViewQuestions.adapter = translationAdapter

            CoroutineScope(Dispatchers.IO).launch {
                var question = viewModel.getQuestionItem(idQuestion)[0]
                questions =
                    viewModel.getQuestionList(question.numQuestion, question.idQuiz).toMutableList()
            }
            binding.buttonAddTranslation.setOnClickListener {
                log("Add Translation button clicked")
                translationAdapter.addNewQuestion()
            }

            binding.buttonSave.setOnClickListener {
                val updatedQuestions = translationAdapter.getUpdatedQuestions()
                viewModel.saveQuestions(updatedQuestions, activity)
                requireActivity().supportFragmentManager.beginTransaction().remove(this)
                    .commit()
            }

            binding.bCancel.setOnClickListener {
                requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
            }

            if (!questions.isNullOrEmpty()) {
                loadNextQuestion()
            } else {
                Toast.makeText(
                    activity,
                    "Не удалось найти вопросы, которые можно перевести",
                    Toast.LENGTH_LONG
                ).show()
            }

        } else {
            viewModel = ViewModelProvider(this, viewModelFactory)[EventViewModel::class.java]

            translationAdapter = TranslationQuestionAdapter(mutableListOf())
            binding.recyclerViewQuestions.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerViewQuestions.adapter = translationAdapter

            log("getQuestionListUseCase() idQuiz != -1")
            viewModel.questionLiveData.observe(viewLifecycleOwner) { receivedQuestions ->
                questions =
                    receivedQuestions?.sortedWith(compareByDescending<QuestionEntity> { false }
                        .thenBy { it.numQuestion })
                        ?.filter { it.idQuiz == idQuiz }?.toMutableList()
                log("questions: $questions")
                if (!questions.isNullOrEmpty()) loadNextQuestion()
                else {
                    Toast.makeText(
                        activity,
                        "Не удалось найти вопросы, которые можно перевести",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                viewModel.loadQuests()
            }
            binding.buttonAddTranslation.setOnClickListener {
                log("Add Translation button clicked")
                translationAdapter.addNewQuestion()
            }

            binding.buttonSave.setOnClickListener {
                val updatedQuestions = translationAdapter.getUpdatedQuestions()
                viewModel.saveQuestions(updatedQuestions, activity)
                loadNextQuestion()
            }

            binding.bCancel.setOnClickListener {
                requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
            }
        }
    }

    private fun loadNextQuestion() {
        log("receivedQuestions?.filter questions:$questions")
        if (questions?.isNotEmpty() == true && listSize <= (viewModel.getProfile().translater
                ?: 0) || questions?.isNotEmpty() == true &&
            try {
                viewModel.getTpovIdQuiz(
                    questions!![0].idQuiz
                ) == getTpovId()
            } catch (e: Exception) {
                false
            }
        ) {
            listSize++
            numQuestion = questions!![0].numQuestion
            var hardQuestion = questions!![0].hardQuestion
            translationAdapter.questions.clear()

            questions!!.removeIf { question ->
                log("receivedQuestions?.filter, allQuestions:$question")
                if (question.numQuestion == numQuestion && question.hardQuestion == hardQuestion) {
                    log("receivedQuestions?.filter, addQuestion:$question")
                    translationAdapter.questions.add(question)
                    true
                } else {
                    false
                }
            }

            translationAdapter.notifyDataSetChanged()

        } else {
            Toast.makeText(
                activity,
                "Все доступные вопросы загружены",
                Toast.LENGTH_SHORT
            ).show()
            requireActivity().supportFragmentManager.beginTransaction().remove(this)
                .commit()
        }
    }
}