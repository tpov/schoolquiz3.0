package com.tpov.schoolquiz.presentation.main

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.databinding.FragmentTitleBinding
import com.tpov.schoolquiz.databinding.FragmentTitleBinding.inflate
import com.tpov.schoolquiz.presentation.BARRIER_QUIZ_ID_LOCAL_AND_REMOVE
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_ARENA
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_HOME
import com.tpov.schoolquiz.presentation.MAX_PERCENT_LIGHT_QUIZ_FULL
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_ARENA_QUIZ
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_HOME_QUIZ
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_QUIZ8
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_RANDOM_QUIZ8
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.dialog.CreateQuestionDialog
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.event.TranslateQuestionFragment
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.HARD_QUESTION
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.ID_QUIZ
import com.tpov.schoolquiz.presentation.question.QuestionActivity.Companion.NAME_USER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.Locale
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

    private lateinit var binding: FragmentTitleBinding
    private var createQuiz = false

    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    override fun onClickNew(name: String, stars: Int) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel =
            ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]

        adapter = MainActivityAdapter(this, requireContext(), mainViewModel)
        binding.rcView.layoutManager = LinearLayoutManager(activity)
        binding.rcView.adapter = adapter
        binding.rcView.itemAnimator = RotateInItemAnimator()
        // Обработка нажатия на кнопку удаления
        adapter.onDeleteButtonClick = { quizEntity ->
            // Ваш код для удаления или выполнения других действий с элементом
            mainViewModel.deleteQuiz(quizEntity.itemId.toInt())
        }

        mainViewModel.getProfile()
        val isMyQuiz = arguments?.getInt(ARG_IS_MY_QUIZ, 1)

        lifecycleScope.launchWhenStarted {
            val countPlace = mainViewModel.getCountPlaceForUserQuiz()
                view.findViewById<TextView>(R.id.tv_number_place_user_quiz).text = countPlace.toString()

            if (mainViewModel.getCountPlaceForUserQuiz() <= 0) {
                binding.fabAddItem.isClickable = false
                binding.fabAddItem.isEnabled = false
            }
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
            Toast.makeText(activity, R.string.soon___, Toast.LENGTH_SHORT).show()
        }

        mainViewModel.quizUseCase.getQuizListLiveData().observe(viewLifecycleOwner) { quizList ->
            val filteredList = quizList.filter {
                when (isMyQuiz) {
                    EVENT_QUIZ_HOME -> it.event == isMyQuiz
                    EVENT_QUIZ_ARENA -> it.event == isMyQuiz
                    else -> it.event == isMyQuiz && it.tpovId == getTpovId()
                }
            }

            val sortedList = if (isMyQuiz == EVENT_QUIZ_ARENA) {
                filteredList.sortedBy { -it.ratingPlayer }
            } else {
                filteredList
            }
            adapter.submitList(sortedList)
        }

        mainViewModel.countPlaceLiveData().observe(viewLifecycleOwner) {

            log("fgjesdriofjeskl observe it: $it")
                view.findViewById<TextView>(R.id.tv_number_place_user_quiz).text = it.toString()
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
            if (mainViewModel.getProfileCount()!! < COAST_LIFE_HOME_QUIZ) {
                val toastMessage = getString(R.string.not_enough_lives, COAST_LIFE_ARENA_QUIZ)
                Toast.makeText(
                    activity,
                    toastMessage,
                    Toast.LENGTH_LONG
                ).show()

            } else dialogNolics(-1, false, COAST_RANDOM_QUIZ8)
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

    override fun onClick(id: Int, typeQuestion: Boolean) {
        log("onClick mainViewModel.getQuizById(id).event")
        if (mainViewModel.getProfileCount()!! < COAST_LIFE_HOME_QUIZ) {
            val toastMessage = getString(R.string.not_enough_lives, COAST_LIFE_ARENA_QUIZ)
            Toast.makeText(
                activity,
                toastMessage,
                Toast.LENGTH_LONG
            ).show()

        } else {
            if (foundQuestionList(id, typeQuestion)) {

                if (mainViewModel.getQuizById(id).event == EVENT_QUIZ_ARENA) {
                    dialogNolics(id, typeQuestion, COAST_QUIZ8)
                } else {
                    mainViewModel.profileUseCase.updateProfile(
                        mainViewModel.getProfile()
                            .copy(count = mainViewModel.getProfileCount()!! - COAST_LIFE_HOME_QUIZ)
                    )
                    val intent = Intent(activity, QuestionActivity::class.java)
                    intent.putExtra(NAME_USER, "user")
                    intent.putExtra(ID_QUIZ, id)
                    intent.putExtra(HARD_QUESTION, typeQuestion)
                    startActivityForResult(intent, REQUEST_CODE)
                }

            } else Toast.makeText(context, R.string.quiz_not_translated, Toast.LENGTH_LONG).show()
        }
    }

    private fun foundQuestionList(idQuiz: Int, hardQuestion: Boolean): Boolean {

        val questionThisListAll =
            mainViewModel.questionUseCase.getQuestionsByIdQuiz(idQuiz)
                .filter { it.hardQuestion == hardQuestion }

        log("kokol size questionThisListAll: ${questionThisListAll.size}")
        var listMap = mutableMapOf<Int, Boolean>()

        listMap = getMap(questionThisListAll, listMap)

        log("kokol size listMap: ${listMap.size}")
        val questionByLocal = getListQuestionListByLocal(listMap, questionThisListAll)

        log("kokol size questionByLocal: ${questionByLocal.size}")
        val questionListThis =
            if (didFoundAllQuestion(questionByLocal, listMap)) questionByLocal
            else getListQuestionByProfileLang(
                questionThisListAll,
                listMap
            )

        log("kokol size questionListThis: ${questionListThis.size}")
        return didFoundAllQuestion(questionListThis, listMap)

    }


    private fun getMap(
        listQuestionEntity: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): MutableMap<Int, Boolean> {

        listQuestionEntity.forEach {
            listMap[it.numQuestion] = false
        }

        return listMap
    }

    private fun getUserLocalization(context: Context): String {
        val config: Configuration = context.resources.configuration
        return config.locale.language
    }

    private fun getListQuestionByProfileLang(
        questionThisListAll: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(requireContext())
        val availableLanguages = mainViewModel.getProfile().languages

        val questionList = ArrayList<QuestionEntity>()

        listMap.forEach { map ->
            var filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) {
                questionList.add(filteredList[0])
            } else {
                filteredList = questionThisListAll
                    .filter { it.numQuestion == map.key }
                    .filter { availableLanguages?.contains(it.language) ?: false }

                if (filteredList.isNotEmpty()) {
                    questionList.add(filteredList[0])
                }
            }
        }
        return questionList
    }

    private fun didFoundAllQuestion(
        questionList: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): Boolean {
        var foundQuestion = listMap.isNotEmpty()

        questionList.forEach {
            log("kokol question: ${it.numQuestion}. ${it.nameQuestion} - ${it.language}")
        }

        log("kokol foundQuestion: $foundQuestion")
        listMap.forEach {

            try {
                if (questionList[it.key - 1].id == null) foundQuestion = false
            } catch (e: Exception) {
                log("kokol catch: $it")
                foundQuestion = false
            }
        }

        return foundQuestion
    }

    private fun getListQuestionListByLocal(
        listMap: MutableMap<Int, Boolean>,
        questionThisListAll: List<QuestionEntity>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(requireContext())

        val questionList = ArrayList<QuestionEntity>()
        listMap.forEach { map ->
            val filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) questionList.add(filteredList[0])
        }

        return questionList
    }

    private fun dialogNolics(
        id: Int,
        type: Boolean,
        nolics: Int
    ) {
        if (id == -1) {
            lifecycleScope.launchWhenStarted {
                if (mainViewModel.getQuizList().isNotEmpty()) {
                    try {

                        val randQuiz = mainViewModel.getQuizList().filter {
                            it.event == EVENT_QUIZ_ARENA && it.languages.contains(Locale.getDefault().language)
                        }.random()

                        val alertDialog = AlertDialog.Builder(activity)
                            .setTitle(Html.fromHtml("<font color='#FFFF00'>" + getString(R.string.search_title) + "</font>"))
                            .setMessage(R.string.search_message)
                            .setPositiveButton("(-) $nolics nolics") { dialog, which ->

                                mainViewModel.profileUseCase.updateProfile(
                                    mainViewModel.getProfile().copy(
                                        count = mainViewModel.getProfileCount()!! - COAST_LIFE_HOME_QUIZ,
                                        pointsNolics = mainViewModel.getProfileNolic()!! - nolics
                                    )
                                )

                                val intent = Intent(activity, QuestionActivity::class.java)
                                intent.putExtra(NAME_USER, "user")
                                intent.putExtra(ID_QUIZ, randQuiz.id)
                                intent.putExtra(
                                    HARD_QUESTION,
                                    randQuiz.stars >= MAX_PERCENT_LIGHT_QUIZ_FULL
                                )
                                startActivityForResult(intent, REQUEST_CODE)
                            }

                            .setNegativeButton(R.string.cancel_button, null)
                            .create()

                        alertDialog.setOnShowListener { dialog ->
                            val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            val messageTextView = dialog.findViewById<TextView>(android.R.id.message)

                            positiveButton.setTextColor(Color.WHITE)
                            negativeButton.setTextColor(Color.WHITE)

                            messageTextView?.setTextColor(Color.WHITE)

                            dialog.window?.setBackgroundDrawableResource(R.drawable.back_item_main)
                        }

                        alertDialog.show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            R.string.no_translated_quest_found,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            val alertDialog = AlertDialog.Builder(activity)
                .setTitle(Html.fromHtml("<font color='#FFFF00'>" + getString(R.string.paid_attempt_title) + "</font>"))
                .setMessage(R.string.paid_attempt_message)
                .setPositiveButton("(-) $nolics nolics") { dialog, which ->
                    mainViewModel.profileUseCase.updateProfile(
                        mainViewModel.getProfile().copy(
                            count = mainViewModel.getProfileCount()!! - COAST_LIFE_ARENA_QUIZ,
                            pointsNolics = mainViewModel.getProfileNolic()!! - nolics
                        )
                    )
                    val intent = Intent(activity, QuestionActivity::class.java)
                    intent.putExtra(NAME_USER, "user")
                    intent.putExtra(ID_QUIZ, id)
                    intent.putExtra(HARD_QUESTION, type)
                    startActivityForResult(intent, REQUEST_CODE)
                }
                .setNegativeButton(R.string.cancel_button, null)
                .create()

            alertDialog.setOnShowListener { dialog ->
                val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val messageTextView = dialog.findViewById<TextView>(android.R.id.message)

                positiveButton.setTextColor(Color.WHITE)
                negativeButton.setTextColor(Color.WHITE)

                messageTextView?.setTextColor(Color.WHITE)

                dialog.window?.setBackgroundDrawableResource(R.color.back_main_top)
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

        mainViewModel.quizUseCase.updateQuiz(quizEntity.copy(showItemMenu = false))
        mainViewModel.insertQuizEvent(quizEntity)
        oldIdQuizEvent1 = quizEntity.id ?: 0
        lifecycleScope.launchWhenStarted {
            mainViewModel.getQuizLiveData().observe(this@FragmentMain) { list ->
                log("getQuizLiveData.observe")
                CoroutineScope(Dispatchers.IO).launch {
                    list.forEach { quiz ->
                        if (mainViewModel.getQuestionListByIdQuiz(quiz.id ?: 0)
                                .isNullOrEmpty()
                        ) mainViewModel.getQuestionListByIdQuiz(oldIdQuizEvent1).forEach {
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
                    if (item.id!! < BARRIER_QUIZ_ID_LOCAL_AND_REMOVE) setQuestion = true
                }
                if (!setQuestion) mainViewModel.setQuestionsFB()
            }
        }
    }

    @Deprecated("Reason for deprecation")
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