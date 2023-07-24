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
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
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
        val tpovId = sharedPref?.getInt("tpovId", 0) ?: 0
        log("fun onViewCreated, tpovId: $tpovId")

        lifecycleScope.launch(Dispatchers.IO) {
            eventViewModel.getQuizList()
            eventViewModel.getTranslateList(tpovId)
            eventViewModel.getEventDeveloper()
        }

        recyclerView = view.findViewById(R.id.rv_event)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val eventAdapter = EventAdapter(
            eventViewModel.quiz2List,
            eventViewModel.quiz3List,
            eventViewModel.quiz4List,
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

        eventViewModel.updateEventList.observe(viewLifecycleOwner) {
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

        if (eventViewModel.getProfileCount()!! < 15) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 15% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {

            if (foundQuestionList(quizId, false)) {
                eventViewModel.updateProfileUseCase(
                    eventViewModel.getProfile()
                        .copy(count = eventViewModel.getProfileCount()!! - 15)
                )
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(QuestionActivity.NAME_USER, "user")
                intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
                intent.putExtra(QuestionActivity.HARD_QUESTION, false)
                startActivity(intent)
            } else {
                Toast.makeText(context, "Квест не переведен на ваш язык", Toast.LENGTH_LONG).show()
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun foundQuestionList(idQuiz: Int, hardQuestion: Boolean?): Boolean {

        log("kokol hardQuestion: $hardQuestion")
        val questionThisListAll =
            mainViewModel.getQuestionByIdQuizUseCase(idQuiz)
                .filter { if (hardQuestion != null) it.hardQuestion == hardQuestion else true }

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

        questionList.forEach {

            log("kokol question: ${it.numQuestion}. ${it.nameQuestion}")
        }

        log("kokol foundQuestion: $foundQuestion")
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
        log("fun onQuiz3Clicked")
        log("fun onQuiz2Clicked")

        if (eventViewModel.getProfileCount()!! < 15) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 15% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            if (foundQuestionList(quizId, true)) {
                eventViewModel.updateProfileUseCase(
                    eventViewModel.getProfile()
                        .copy(count = eventViewModel.getProfileCount()!! - 15)
                )
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(QuestionActivity.NAME_USER, "user")
                intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
                intent.putExtra(QuestionActivity.HARD_QUESTION, true)
                startActivity(intent)
            } else {
                Toast.makeText(context, "Квест не переведен на ваш язык", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz4Clicked(quizId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 20) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 20% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            if (foundQuestionList(quizId, null)) {
                eventViewModel.updateProfileUseCase(
                    eventViewModel.getProfile()
                        .copy(count = eventViewModel.getProfileCount()!! - 20)
                )
                log("fun onQuiz4Clicked")
                val intent = Intent(activity, QuestionActivity::class.java)
                intent.putExtra(QuestionActivity.NAME_USER, "user")
                intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
                intent.putExtra(QuestionActivity.HARD_QUESTION, false)
                startActivity(intent)
            } else {
                Toast.makeText(context, "Квест не переведен на ваш язык", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onTranslate1EventClicked(questionId: Int) {

        log("fun onQuiz2Clicked")
        if (eventViewModel.getProfileCount()!! < 10) Toast.makeText(
            activity,
            "Недостаточно жизней. На прохождение квеста тратиться 10% жизни",
            Toast.LENGTH_LONG
        ).show()
        else {
            eventViewModel.updateProfileUseCase(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 10)
            )

            FragmentManager.setFragment(
                TranslateQuestionFragment.newInstance(-1, questionId),
                requireActivity() as MainActivity
            )
            log("fun onTranslate1EventClicked")

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
            eventViewModel.updateProfileUseCase(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 15)
            )
            FragmentManager.setFragment(
                TranslateQuestionFragment.newInstance(-1, questionId),
                requireActivity() as MainActivity
            )
            log("fun onTranslate2EventClicked")
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
            eventViewModel.updateProfileUseCase(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 10)
            )

            FragmentManager.setFragment(
                TranslateQuestionFragment.newInstance(-1, questionId),
                requireActivity() as MainActivity
            )
            log("fun onTranslateEditQuestionClicked")
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
            eventViewModel.updateProfileUseCase(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 50)
            )
            log("fun onModeratorEventClicked")
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
            eventViewModel.updateProfileUseCase(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 50)
            )
            log("fun onAdminEventClicked")
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
            eventViewModel.updateProfileUseCase(
                eventViewModel.getProfile()
                    .copy(count = eventViewModel.getProfileCount()!! - 50)
            )
            log("fun onDeveloperEventClicked")
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