package com.tpov.schoolquiz.presentation.network.event

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.presentation.DEFAULT_RATING_QUIZ
import com.tpov.schoolquiz.presentation.DEFAULT_TPOVID
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_QUIZ2
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_QUIZ3
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_QUIZ4
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_QUIZ_TRANSLATE1
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.fragment.FragmentManager
import com.tpov.schoolquiz.presentation.main.MainActivity
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class EventFragment : BaseFragment(), EventAdapter.ListenerEvent {

    private lateinit var recyclerView: RecyclerView
    private lateinit var eventViewModel: EventViewModel

    @OptIn(InternalCoroutinesApi::class)
    private lateinit var mainViewModel: MainActivityViewModel

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventViewModel = ViewModelProvider(this, viewModelFactory)[EventViewModel::class.java]
        mainViewModel = ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]

        val sharedPref = context?.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val tpovId = sharedPref?.getInt("tpovId", DEFAULT_TPOVID) ?: DEFAULT_TPOVID
        log("fun onViewCreated, tpovId: $tpovId")

        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(tpovId)
            eventViewModel.getEventDeveloper()
        }

        recyclerView = view.findViewById(R.id.rv_event)
        recyclerView.layoutManager = LinearLayoutManager(context)

        eventViewModel.updateEventList.observe(viewLifecycleOwner) {

            val eventAdapter = EventAdapter(
                eventViewModel.quiz2List.filter { it.ratingPlayer == DEFAULT_RATING_QUIZ },
                eventViewModel.quiz3List.filter { it.ratingPlayer == DEFAULT_RATING_QUIZ },
                eventViewModel.quiz4List.filter { it.ratingPlayer == DEFAULT_RATING_QUIZ },
                emptyList(),// eventViewModel.translate1Question,
                emptyList(),//eventViewModel.translate2Question,
                emptyList(),//eventViewModel.translateEditQuestion,
                emptyList(),//eventViewModel.moderator,
                emptyList(),//eventViewModel.admin,
                emptyList(),//eventViewModel.develop,
                this,
                mainViewModel
            )
           eventAdapter.setDataObserver(eventAdapter)
            recyclerView.adapter = eventAdapter
            com.tpov.schoolquiz.presentation.network.event.log("fun eventList()3 $it")
            eventAdapter.onDataUpdated()
            recyclerView.post {
                eventAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // раздуваем макет фрагмента
        return inflater.inflate(R.layout.event_fragment, container, false)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz2Clicked(quizId: Int) {
        log("fun onQuiz2Clicked")

        if (eventViewModel.getProfileCount()!! < COAST_LIFE_QUIZ2) Toast.makeText(
            activity,
            context?.getString(R.string.not_enough_lives, COAST_LIFE_QUIZ2),
            Toast.LENGTH_LONG
        ).show()
        else {
            log("foundQuestionList(quizId, false) ${foundQuestionList(quizId, false)}")
            if (foundQuestionList(quizId, false)) {
                eventViewModel.profileUseCase.updateProfile(
                    eventViewModel.getProfile()
                        .copy(count = eventViewModel.getProfileCount()!! - COAST_LIFE_QUIZ2)
                )
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(QuestionActivity.NAME_USER, "user")
                intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
                intent.putExtra(QuestionActivity.HARD_QUESTION, false)
                startActivity(intent)
            } else Toast.makeText(context, context?.getString(R.string.quiz_not_translated), Toast.LENGTH_LONG).show()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun foundQuestionList(idQuiz: Int, hardQuestion: Boolean?): Boolean {

    val questionThisListAll = mainViewModel.questionUseCase.getQuestionsByIdQuiz(idQuiz)
            .filter { if (hardQuestion != null) it.hardQuestion == hardQuestion else true }
            .sortedBy { it.numQuestion }

        var listMap = mutableMapOf<Int, Boolean>()
        listMap = getMap(questionThisListAll, listMap)
        val questionByLocal = getListQuestionListByLocal(listMap, questionThisListAll)

        val questionListThis =
            if (didFoundAllQuestion(questionByLocal, listMap)) questionByLocal
            else getListQuestionByProfileLang(
                questionThisListAll,
                listMap
            )

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

    @OptIn(InternalCoroutinesApi::class)
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

        listMap.forEach {
            try {
                if (questionList[it.key - 1].id == null) foundQuestion = false
            } catch (e: Exception) {
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

    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz3Clicked(quizId: Int) {

        if (eventViewModel.getProfileCount()!! < COAST_LIFE_QUIZ3) Toast.makeText(
            activity,
            context?.getString(R.string.not_enough_lives, COAST_LIFE_QUIZ3),
            Toast.LENGTH_LONG
        ).show()
        else {
            if (foundQuestionList(quizId, true)) {
                eventViewModel.profileUseCase.updateProfile(
                    eventViewModel.getProfile()
                        .copy(count = eventViewModel.getProfileCount()!! - COAST_LIFE_QUIZ3)
                )
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(QuestionActivity.NAME_USER, "user")
                intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
                intent.putExtra(QuestionActivity.HARD_QUESTION, true)
                startActivity(intent)
            } else {
                Toast.makeText(context, context?.getString(R.string.quiz_not_translated), Toast.LENGTH_LONG)
                    .show()
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz4Clicked(quizId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < COAST_LIFE_QUIZ4) Toast.makeText(
            activity,
            context?.getString(R.string.not_enough_lives, COAST_LIFE_QUIZ4),
            Toast.LENGTH_LONG
        ).show()
        else {
            if (foundQuestionList(quizId, null)) {
                eventViewModel.profileUseCase.updateProfile(
                    eventViewModel.getProfile()
                        .copy(count = eventViewModel.getProfileCount()!! - COAST_LIFE_QUIZ4)
                )
                log("fun onQuiz4Clicked")
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(QuestionActivity.NAME_USER, "user")
                intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
                intent.putExtra(QuestionActivity.HARD_QUESTION, false)
                startActivity(intent)
            } else Toast.makeText(context, context?.getString(R.string.quiz_not_translated), Toast.LENGTH_LONG)
                    .show()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onTranslate1EventClicked(questionId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < COAST_LIFE_QUIZ_TRANSLATE1) Toast.makeText(
            activity,
            context?.getString(R.string.not_enough_lives, COAST_LIFE_QUIZ_TRANSLATE1),
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.profileUseCase.updateProfile(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - COAST_LIFE_QUIZ_TRANSLATE1)
            )

            FragmentManager.setFragment(
                TranslateQuestionFragment.newInstance(-1, questionId),
                requireActivity() as MainActivity
            )
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onTranslate2EventClicked(questionId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 15) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 15% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.profileUseCase.updateProfile(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 15)
            )
            FragmentManager.setFragment(
                TranslateQuestionFragment.newInstance(-1, questionId),
                requireActivity() as MainActivity
            )
            log("fun onTranslate2EventClicked")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onTranslateEditQuestionClicked(questionId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 10) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 15% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.profileUseCase.updateProfile(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 10)
            )

            FragmentManager.setFragment(
                TranslateQuestionFragment.newInstance(-1, questionId),
                requireActivity() as MainActivity
            )
            log("fun onTranslateEditQuestionClicked")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    override fun onModeratorEventClicked(quizId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 50) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 30% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.profileUseCase.updateProfile(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 50)
            )
            log("fun onModeratorEventClicked")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    override fun onAdminEventClicked(quizId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 50) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 30% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.profileUseCase.updateProfile(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 50)
            )
            log("fun onAdminEventClicked")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    override fun onDeveloperEventClicked(quizId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 50) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 30% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.profileUseCase.updateProfile(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 50)
            )
            log("fun onDeveloperEventClicked")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(getTpovId())
            eventViewModel.getEventDeveloper()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = EventFragment()
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "Event", Logcat.LOG_FRAGMENT)
    }
}