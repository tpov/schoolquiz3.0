package com.tpov.schoolquiz.presentation.main

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.TitleFragmentBinding
import com.tpov.schoolquiz.databinding.TitleFragmentBinding.inflate
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
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
import java.util.*
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

        mainViewModel.getProfile()
        val isMyQuiz = arguments?.getInt(ARG_IS_MY_QUIZ, 1)

        tv_number_place_user_quiz.text = mainViewModel.getCountPlaceForUserQuiz().toString()
        if (mainViewModel.getCountPlaceForUserQuiz() <= 0) {
            binding.fabAddItem.isClickable = false
            binding.fabAddItem.isEnabled = false
        }

        when (isMyQuiz) {
            1 -> {
                binding.fabAddItem.visibility = View.VISIBLE
                binding.fabSearch.visibility = View.GONE
                binding.fabBox.visibility = View.GONE
            }

            5 -> {
                binding.fabSearch.visibility = View.VISIBLE
                binding.fabAddItem.visibility = View.GONE
                binding.fabBox.visibility = View.GONE
            }

            8 -> {
                binding.fabBox.visibility = View.VISIBLE
                binding.fabAddItem.visibility = View.GONE
                binding.fabSearch.visibility = View.GONE
            }

            else -> {
                binding.fabBox.visibility = View.GONE
                binding.fabAddItem.visibility = View.GONE
                binding.fabSearch.visibility = View.GONE
            }
        }

        val countBox = mainViewModel.getProfileCountBox()
        if (countBox != 0) {
            binding.tvNumberBox.text = countBox.toString()
        } else {
            binding.fabBox.visibility = View.GONE
        }

        binding.fabBox.setOnClickListener {
            Toast.makeText(activity, "Скоро...", Toast.LENGTH_SHORT).show()
        }

        mainViewModel.getEventLiveDataUseCase().observe(viewLifecycleOwner) { quizList ->
            val filteredList =
                quizList.filter { if (it.event == 8) it.event == isMyQuiz else it.event == isMyQuiz && it.tpovId == getTpovId() }
            val sortedList = if (isMyQuiz == 5) {
                filteredList.sortedBy { it.ratingPlayer }
            } else {
                filteredList
            }
            adapter.submitList(sortedList)
        }
        mainViewModel.countPlaceLiveData().observe(viewLifecycleOwner) {

            log("fgjesdriofjeskl observe it: $it")
            tv_number_place_user_quiz.text = it.toString()
            if (it <= 0) {
                binding.fabAddItem.isClickable = false
                binding.fabAddItem.isEnabled = false
            }
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


        binding.fabSearch.setOnClickListener {
            dialogNolics(-1, false, 100)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = inflate(inflater, container, false)

        return binding.root
    }

    override fun deleteItem(id: Int) {
        mainViewModel.deleteQuiz(id)
    }

    override fun onClick(id: Int, type: Boolean) {
        log("onClick mainViewModel.getQuizById(id).event")
        if (mainViewModel.getProfileCount()!! < 33) {
            Toast.makeText(
                activity,
                "Недостаточно жизней. На прохождение квеста тратиться 33% жизни",
                Toast.LENGTH_LONG
            ).show()
        } else {
            if (mainViewModel.getQuizById(id).event == 5) {
                if (containsLangQuizInUser(id)) {
                    dialogNolics(id, type, 500)
                } else {
                    Toast.makeText(context, "Квест не переведен на ваш язык", Toast.LENGTH_LONG)
                        .show()
                }

            } else {
                if (containsLangQuizInUser(id)) {
                mainViewModel.updateProfileUseCase(
                    mainViewModel.getProfile()
                        .copy(count = mainViewModel.getProfileCount()!! - 33)
                )
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(NAME_USER, "user")
                intent.putExtra(ID_QUIZ, id)
                intent.putExtra(HARD_QUESTION, type)
                startActivityForResult(intent, REQUEST_CODE)
                } else {
                    Toast.makeText(context, "Квест не переведен на ваш язык", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
    @OptIn(InternalCoroutinesApi::class)
    fun containsLangQuizInUser(idQuiz: Int) = mainViewModel.getProfile().languages?.split('|')
        ?.any { mainViewModel.getQuizById(idQuiz).languages.contains(it) } ?: false

    private fun dialogNolics(
        id: Int,
        type: Boolean,
        nolics: Int
    ) {
        if (id == -1) {
            if (mainViewModel.getQuizList().isNotEmpty()) {
                val randQuiz = mainViewModel.getQuizList().filter {
                    it.event == 5 && it.languages.contains(Locale.getDefault().language)
                }.random()

                val alertDialog = AlertDialog.Builder(activity)
                    .setTitle("Поиск")
                    .setMessage("Пройти рандомный квест из этого списка")
                    .setPositiveButton("(-) $nolics ноликов") { dialog, which ->

                        mainViewModel.updateProfileUseCase(
                            mainViewModel.getProfile().copy(
                                count = mainViewModel.getProfileCount()!! - 33,
                                pointsNolics = mainViewModel.getProfileNolic()!! - nolics
                            )
                        )

                        val intent = Intent(activity, QuestionActivity::class.java)
                        intent.putExtra(NAME_USER, "user")
                        intent.putExtra(ID_QUIZ, randQuiz.id)
                        intent.putExtra(HARD_QUESTION, randQuiz.stars >= 100)
                        startActivityForResult(intent, REQUEST_CODE)
                    }
                    .setNegativeButton("Отмена", null)
                    .create()

                alertDialog.setOnShowListener { dialog ->
                    val positiveButton =
                        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                    val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                    positiveButton.setTextColor(Color.WHITE)
                    negativeButton.setTextColor(Color.YELLOW)

                    dialog.window?.setBackgroundDrawableResource(R.color.design3_top_start)
                }

                alertDialog.show()
            }
        } else {
            val alertDialog = AlertDialog.Builder(activity)
                .setTitle("Попытка платная")
                .setMessage("В целях честной игры снимается плата.")
                .setPositiveButton("(-) $nolics ноликов") { dialog, which ->
                    mainViewModel.updateProfileUseCase(
                        mainViewModel.getProfile().copy(
                            count = mainViewModel.getProfileCount()!! - 33,
                            pointsNolics = mainViewModel.getProfileNolic()!! - nolics
                        )
                    )
                    val intent = Intent(activity, QuestionActivity::class.java)
                    intent.putExtra(NAME_USER, "user")
                    intent.putExtra(ID_QUIZ, id)
                    intent.putExtra(HARD_QUESTION, type)
                    startActivityForResult(intent, REQUEST_CODE)
                }
                .setNegativeButton("Отмена", null)
                .create()

            alertDialog.setOnShowListener { dialog ->
                val positiveButton =
                    (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                positiveButton.setTextColor(Color.WHITE)
                negativeButton.setTextColor(Color.YELLOW)

                dialog.window?.setBackgroundDrawableResource(R.color.design3_top_start)
            }

            alertDialog.show()
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
        mainViewModel.getQuizLiveData().observe(this) { list ->
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
                    TranslateQuestionFragment.newInstance(idQuiz, -1)
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