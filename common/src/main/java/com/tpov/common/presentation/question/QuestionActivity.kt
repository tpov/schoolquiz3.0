package com.tpov.common.presentation.question

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import com.tpov.common.data.utils.ShowText
import com.tpov.schoolquiz.*
import com.tpov.schoolquiz.data.Services.MusicService
import com.tpov.schoolquiz.databinding.ActivityQuestionBinding
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import kotlinx.android.synthetic.main.activity_question.*
import kotlinx.coroutines.*
import javax.inject.Inject

private const val REQUEST_CODE_CHEAT = 0

/**
 * This activity contains many variables that are needed to restore the session and the information processing logic.
 * High WTF/min
 * Refractoring incomplete
 * First, there is a check to see if the quest that the player wants to complete is not completed, if not,
 * then he goes through it from the beginning.
 * To save the session, many variables and encodings are used that are made from objects in one line,
 * this allows you to have the entire progress of the passage, save it and restore it.
 */
@InternalCoroutinesApi
class QuestionActivity : AppCompatActivity() {

    lateinit var viewModel: QuestionViewModel
    private var languageUser: String? = null

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val binding by lazy {
        ActivityQuestionBinding.inflate(layoutInflater)
    }
    private val component by lazy {
        (application as MainApp).component
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        component.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        viewModel = ViewModelProvider(this, viewModelFactory)[QuestionViewModel::class.java]
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()

        synthInputData()
        loadQuizData()
        showQuestion()
    }


    private fun loadQuizData() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.getQuizById()
            viewModel.quiz.collect {it?.let {
                    viewModel.initQuizValues()
                    viewModel.getQuestionList(languageUser ?: viewModel.notFoundInputData().toString())
                    viewModel.questionList.collect { questionList -> questionList?.let {
                            viewModel.getQuestionDetailByIdQuiz()
                            viewModel.questionDetailList.collect { questionDetailList ->
                                questionDetailList?.let {
                                    viewModel.initQuestionDetail()
                                    viewModel.questionDetail.collect { questionDetail ->
                                        questionDetail?.let {
                                            viewModel.initQuestionValues()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showQuestion() {
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.currentQuestion.collect { currentQuestion ->
                if (currentQuestion != null && currentQuestion != viewModel.unknownCurrentQuestion) {
                    val questionText = viewModel.questionList.value?.get(currentQuestion)?.nameQuestion
                    val answer = viewModel.questionList.value?.get(currentQuestion)?.answer
                    val answersName = viewModel.questionList.value?.get(currentQuestion)?.nameAnswers
                    if (questionText?.let { Regex("\\(.{7}\\)").containsMatchIn(it) } == true) show4Answers(answer, answersName)
                    else show8Answers(answer, answersName)
                    binding.tvQuestionText.setText(questionText)
                    binding.imvQuestionImage.setDarawable(viewModel.questionList.value?.get(currentQuestion)?.pathPictureQuestion)

                }
            }
        }
    }

    private fun show8Answers(answer: Int?, answersName: String?) {


    }

    private fun show4Answers(answer: Int?, answersName: String?) {


    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun synthInputData() {
        viewModel.idQuiz = intent.getIntExtra(ID_QUIZ, 0)
        viewModel.hardQuiz = intent.getBooleanExtra(HARD_QUESTION, false)
        languageUser = intent.getStringExtra(LANGUAGE_USER)
        viewModel.life = intent.getIntExtra(LIFE, 0)

    }

    private fun visibleCheatButton(it: Boolean) {
        binding.cheatButton.isClickable = it
        binding.cheatButton.isEnabled = it
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, MusicService::class.java))
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }


    private fun springAnim(next: Boolean) = with(binding) {
        val START_VELOCITY = if (next) -5000f
        else 5000f

        var springAnimation = SpringAnimation(questionTextView, DynamicAnimation.X)
        var springForce = SpringForce()
        springForce.finalPosition = questionTextView.x
        springForce.dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
        springForce.stiffness = SpringForce.STIFFNESS_HIGH

        springAnimation.spring = springForce
        springAnimation.setStartVelocity(START_VELOCITY)
        springAnimation.start()
    }

    private fun actionBarSettings() {       //Кнопка назад в баре
        val ab = supportActionBar
        ab?.setDisplayHomeAsUpEnabled(true)
    }

    @SuppressLint("ResourceType")
    private fun anim321(num: Int) = with(binding) {
        tv321.text = num.toString()

        var anim = AnimationUtils.loadAnimation(this@QuestionActivity, R.anim.time_3_2_1)
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                tv321.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(p0: Animation?) {
                tv321.visibility = View.GONE
            }

            override fun onAnimationRepeat(p0: Animation?) {
            }
        })
        tv321.startAnimation(anim)
    }

    private fun setBlockButton(state: Boolean) = with(binding) {
        falseButton.isEnabled = state
        falseButton.isClickable = state
        trueButton.isEnabled = state
        trueButton.isClickable = state
    }

    //Тут уже обновленные параметры
    fun updateTextQuestion() {
        updateDataView()

        ShowText().showTextWithDelay(
            binding.questionTextView,
            viewModel.questionListThis[viewModel.currentIndex].nameQuestion,
            DELAY_SHOW_TEXT
        )

    }


    companion object {
        const val ID_QUIZ = "name_question"
        const val NAME_USER = "name_user"
        const val HARD_QUESTION = "hard_question"
        const val STARS = "stars"
        const val UPDATE_CURRENT_INDEX = 1
        const val RESULT_TRANSLATE = 2
        const val RESULT_OK = 1

        const val DELAY_SHOW_TEXT = DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY
    }
}
