package com.tpov.schoolquiz.presentation.mainactivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.TitleFragmentBinding
import com.tpov.schoolquiz.databinding.TitleFragmentBinding.inflate
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.dialog.CreateQuestionDialog
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.HARD_QUESTION
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.LIFE
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.ID_QUIZ
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.NAME_USER
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

        val sharedPref = context?.getSharedPreferences("profile", Context.MODE_PRIVATE)
        mainViewModel.tpovId = sharedPref?.getInt("tpovId", -1) ?: -1

    }

    override fun onResume() {
        super.onResume()
        adapter = MainActivityAdapter(this, requireContext())
        binding.rcView.layoutManager = LinearLayoutManager(activity)
        binding.rcView.adapter = adapter

        mainViewModel.getQuizLiveData.observe(viewLifecycleOwner) { quizList ->
            log("quizQuizLiveData.observe $quizList")
            // Фильтруем список, оставляя только элементы с event == 1 или event == 8
            val filteredQuizList = quizList.filter { it.event == 1 || it.event == 8 }
            // Обновление списка
            adapter.submitList(filteredQuizList)
        }

        //  addItemsToRecyclerView(binding.rcView, mainViewModel.quizQuizLiveData.value!!)

        val fabAddItem = binding.fabAddItem
        fabAddItem.setOnClickListener {
            // Добавление нового элемента в список
            val fragmentManager = activity?.supportFragmentManager
            fragmentManager?.let {
                val dialogFragment: CreateQuestionDialog =
                    CreateQuestionDialog.newInstance(CreateQuestionDialog.NAME)
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

        // Set the onClickListeners for the edit and delete buttons

        Log.d("ffsefsf", "deleteItem = $id")
    }

    override fun onClick(id: Int, stars: Int) {
        val intent = Intent(activity, QuestionActivity::class.java)
        intent.putExtra(NAME_USER, "user")
        intent.putExtra(ID_QUIZ, id)
        intent.putExtra(LIFE, 0)
        intent.putExtra(HARD_QUESTION, false)
        startActivity(intent)
    }

    override fun shareItem(id: Int, stars: Int) {

    }

    override fun sendItem(quizEntity: QuizEntity) {

        Log.d("daefdhrt", "tpovId2 ${mainViewModel.tpovId}")
        Log.d("gdrgefs", "1 $quizEntity")
        mainViewModel.insertQuizEvent(quizEntity)
        oldIdQuizEvent1 = quizEntity.id ?: 0
        mainViewModel.getQuizLiveData.observe(this) { list ->
            log("getQuizLiveData.observe")
            list.forEach { quiz ->
                log("getQuizLiveData.observe question by id: ${mainViewModel.getQuestionListByIdQuiz(quiz.id ?: 0).isNullOrEmpty()}")
                log("getQuizLiveData.observe question is empty: ${mainViewModel.getQuestionListByIdQuiz(quiz.id ?: 0)}")
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

    override fun reloadData() {
        activity?.recreate()
    }

    fun onDeleteButtonClick() {
        // Код, который будет выполнен при нажатии на кнопку "Удалить"
    }

    fun onEditButtonClick() {
        Log.d("ffsefsf", "deleteItem = $id")
    }

    companion object {
        @JvmStatic
        fun newInstance() = FragmentMain()

        const val CREATE_QUIZ = ""
        const val DELETE_QUIZ = "deleteQuiz"
        const val SHARE_QUIZ = "shareQuiz"
    }
}