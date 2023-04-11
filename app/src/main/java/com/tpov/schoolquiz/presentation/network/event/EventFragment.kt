package com.tpov.schoolquiz.presentation.network.event

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import com.tpov.schoolquiz.presentation.question.QuestionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class EventFragment : BaseFragment(), EventAdapter.ListenerEvent {

    private lateinit var recyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter
    private lateinit var eventViewModel: EventViewModel

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        eventViewModel = ViewModelProvider(this, viewModelFactory)[EventViewModel::class.java]

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

        val quiz2List = eventViewModel.quiz2List
        val quiz3List = eventViewModel.quiz3List
        val quiz4List = eventViewModel.quiz4List
        val translate1EventList = eventViewModel.translate1Question
        val translate2EventList = eventViewModel.translate2Question
        val translateEditQuestionList = eventViewModel.translateEditQuestion
        val moderatorEventList = eventViewModel.moderator
        val adminEventList = eventViewModel.admin
        val developerEventList = eventViewModel.develop

        val eventAdapter = EventAdapter(
            quiz2List,
            quiz3List,
            quiz4List,
            translate1EventList,
            translate2EventList,
            translateEditQuestionList,
            moderatorEventList,
            adminEventList,
            developerEventList,
            this // ваш ListenerEvent
        )

        recyclerView.adapter = eventAdapter

    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // раздуваем макет фрагмента
        val view = inflater.inflate(R.layout.event_fragment, container, false)


        return view
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }


    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz2Clicked(quizId: Int) {
        log("fun onQuiz2Clicked")
            val intent = Intent(activity, QuestionActivity::class.java)
            intent.putExtra(QuestionActivity.NAME_USER, "user")
            intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
            intent.putExtra(QuestionActivity.LIFE, 0)
            intent.putExtra(QuestionActivity.HARD_QUESTION, false)
            startActivity(intent)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz3Clicked(quizId: Int) {
        log("fun onQuiz3Clicked")
        val intent = Intent(activity, QuestionActivity::class.java)
        intent.putExtra(QuestionActivity.NAME_USER, "user")
        intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
        intent.putExtra(QuestionActivity.LIFE, 0)
        intent.putExtra(QuestionActivity.HARD_QUESTION, true)
        startActivity(intent)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onQuiz4Clicked(quizId: Int) {

        log("fun onQuiz4Clicked")
        val intent = Intent(activity, QuestionActivity::class.java)
        intent.putExtra(QuestionActivity.NAME_USER, "user")
        intent.putExtra(QuestionActivity.ID_QUIZ, quizId)
        intent.putExtra(QuestionActivity.LIFE, 0)
        intent.putExtra(QuestionActivity.HARD_QUESTION, false)
        startActivity(intent)
    }

    override fun onTranslate1EventClicked(questionId: Int) {

        log("fun onTranslate1EventClicked")
    }

    override fun onTranslate2EventClicked(quizId: Int) {

        log("fun onTranslate2EventClicked")
    }

    override fun onTranslateEditQuestionClicked(questionId: Int) {

        log("fun onTranslateEditQuestionClicked")
    }

    override fun onModeratorEventClicked(quizId: Int) {

        log("fun onModeratorEventClicked")
    }

    override fun onAdminEventClicked(quizId: Int) {

        log("fun onAdminEventClicked")
    }

    override fun onDeveloperEventClicked(quizId: Int) {

        log("fun onDeveloperEventClicked")
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