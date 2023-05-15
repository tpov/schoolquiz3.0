package com.tpov.schoolquiz.presentation.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.databinding.TitleFragmentBinding
import com.tpov.schoolquiz.databinding.TitleFragmentBinding.inflate
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.dialog.CreateQuestionDialog
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.event.TranslateQuestionFragment
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.HARD_QUESTION
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.ID_QUIZ
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.NAME_USER
import kotlinx.android.synthetic.main.title_fragment.*
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

@InternalCoroutinesApi
class FragmentMain : BaseFragment(), MainActivityAdapter.Listener {


    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "Main", Logcat.LOG_FRAGMENT)
    }

    private lateinit var mainViewModel: MainActivityViewModel

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    private var oldIdQuizEvent1 = 0

    private lateinit var adapter: MainActivityAdapter

    private lateinit var binding: TitleFragmentBinding
    private var createQuiz = false

    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    override fun onClickNew(name: String, stars: Int) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel = ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]

        adapter = MainActivityAdapter(this, requireContext(), mainViewModel)
        binding.rcView.layoutManager = LinearLayoutManager(activity)
        binding.rcView.adapter = adapter
        rcView.itemAnimator = RotateInItemAnimator()
        // Обработка нажатия на кнопку удаления
        adapter.onDeleteButtonClick = { quizEntity ->
            // Ваш код для удаления или выполнения других действий с элементом
            mainViewModel.deleteQuiz(quizEntity.itemId.toInt())
        }
        val isMyQuiz = arguments?.getInt(ARG_IS_MY_QUIZ, 1)

        if (isMyQuiz == 1) binding.fabAddItem.visibility = View.VISIBLE
        else binding.fabAddItem.visibility = View.GONE

        mainViewModel.getEventLiveDataUseCase().observe(viewLifecycleOwner) { quizList ->
            log("onViewCreated() adapter.submitList: ${quizList.filter { it.event == isMyQuiz }}")
            adapter.submitList(quizList.filter {

                log("event quizz: it.event ${it.event}")
                log("event quizz: isMyQuiz $isMyQuiz")

                it.event == isMyQuiz
            })
        }
    }

    override fun onResume() {
        super.onResume()
        val fabAddItem = binding.fabAddItem
        fabAddItem.setOnClickListener {
            // Добавление нового элемента в список
            val fragmentManager = activity?.supportFragmentManager
            fragmentManager?.let {
                val dialogFragment: CreateQuestionDialog =
                    CreateQuestionDialog.newInstance(CreateQuestionDialog.NAME, -1)
                dialogFragment.show(fragmentManager, "create_question_dialog")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = inflate(inflater, container, false)
        // binding.swipeRefreshLayout.setOnRefreshListener { reloadData() }
        return binding.root
    }

    override fun deleteItem(id: Int) {
        mainViewModel.deleteQuiz(id)
    }

    override fun onClick(id: Int, type: Boolean) {
        if (mainViewModel.getProfileCount()!! < 50) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться пол-жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            mainViewModel.updateProfileUseCase(mainViewModel.getProfile.copy(count = mainViewModel.getProfileCount()!! - 50))
            val intent = Intent(activity, QuestionActivity::class.java)
            intent.putExtra(NAME_USER, "user")
            intent.putExtra(ID_QUIZ, id)
            intent.putExtra(HARD_QUESTION, type)
            startActivityForResult(intent, REQUEST_CODE)
        }
    }

    override fun editItem(id: Int) {
        log("editItem: $id")
        val fragmentManager = activity?.supportFragmentManager
        fragmentManager?.let {
            val dialogFragment: CreateQuestionDialog =
                CreateQuestionDialog.newInstance(CreateQuestionDialog.NAME, id)
            dialogFragment.show(fragmentManager, "create_question_dialog")
        }
    }

    override fun sendItem(id: Int) {
        var quizEntity = mainViewModel.getQuizById(id)

        mainViewModel.updateQuizUseCase(quizEntity.copy(showItemMenu = false))
        mainViewModel.insertQuizEvent(quizEntity)
        oldIdQuizEvent1 = quizEntity.id ?: 0
        mainViewModel.getQuizLiveData.observe(this) { list ->
            log("getQuizLiveData.observe")
            list.forEach { quiz ->
                log(
                    "getQuizLiveData.observe question by id: ${
                        mainViewModel.getQuestionListByIdQuiz(
                            quiz.id ?: 0
                        ).isNullOrEmpty()
                    }"
                )
                log(
                    "getQuizLiveData.observe question is empty: ${
                        mainViewModel.getQuestionListByIdQuiz(
                            quiz.id ?: 0
                        )
                    }"
                )
                log("getQuizLiveData.observe quiz: ${quiz}")
                if (mainViewModel.getQuestionListByIdQuiz(quiz.id ?: 0).isNullOrEmpty()) {
                    mainViewModel.getQuestionListByIdQuiz(oldIdQuizEvent1).forEach {
                        mainViewModel.insertQuestion(
                            it.copy(
                                id = null,
                                idQuiz = quiz.id ?: 0
                            )
                        )
                    }
                }
            }
            var setQuestion = false
            if (list.isEmpty()) setQuestion = true
            list.forEach { item ->
                if (item.id!! < 100) setQuestion = true
            }
            if (!setQuestion) mainViewModel.setQuestionsFB()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let {
                val translate = it.getBooleanExtra("translate", false)
                val idQuiz = it.getIntExtra("idQuiz", 0)

                if (translate) (requireActivity() as MainActivity).replaceFragment(
                    TranslateQuestionFragment.newInstance(idQuiz, null)
                )
            }
        }
    }

    override fun reloadData() {
        activity?.recreate()
    }

    companion object {

        const val ARG_IS_MY_QUIZ = "is_my_quiz"
        const val CREATE_QUIZ = ""
        const val DELETE_QUIZ = "deleteQuiz"
        const val SHARE_QUIZ = "shareQuiz"
        const val REQUEST_CODE = 1

        @JvmStatic
        fun newInstance(idQuiz: Int): FragmentMain {
            val args = Bundle()
            args.putInt(ARG_IS_MY_QUIZ, idQuiz)
            val fragment = FragmentMain()
            fragment.arguments = args
            return fragment
        }
    }
}