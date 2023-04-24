package com.tpov.schoolquiz.presentation.network.event

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
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

        val languages = listOf("EN", "RU", "FR") // Замените на список доступных языков
        translationAdapter = TranslationQuestionAdapter(mutableListOf(), languages)
        binding.recyclerViewQuestions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewQuestions.adapter = translationAdapter

        if (idQuiz != -1) {
            log("getQuestionListUseCase() idQuiz != -1")
            viewModel.questionLiveData.observe(viewLifecycleOwner) { questions ->

                log("getQuestionListUseCase() :${questions}")
                translationAdapter.questions.addAll(questions)
                translationAdapter.notifyDataSetChanged()
            }
            viewModel.loadQuests()
        } else if (idQuestion != -1) {
            log("getQuestionListUseCase() idQuestion != -1")
            viewModel.questionLiveData.observe(viewLifecycleOwner) { question ->
                log("getQuestionListUseCase() :${question}")
                translationAdapter.questions.addAll(question)
                translationAdapter.notifyDataSetChanged()
            }
            viewModel.loadQuestion(idQuestion!!)
        }

        binding.buttonAddTranslation.setOnClickListener {
            log("Add Translation button clicked")
            translationAdapter.addNewQuestion()
        }

        binding.buttonSave.setOnClickListener {
            val updatedQuestions = translationAdapter.getUpdatedQuestions()
            viewModel.saveQuestions(updatedQuestions)
        }
    }
}
