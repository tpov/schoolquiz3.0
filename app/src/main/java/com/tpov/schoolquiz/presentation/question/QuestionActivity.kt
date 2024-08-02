package com.tpov.schoolquiz.presentation.question

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.Services.MusicService
import com.tpov.schoolquiz.data.database.log
import com.tpov.schoolquiz.databinding.ActivityQuestionBinding
import com.tpov.schoolquiz.presentation.COUNT_LIFE_POINTS_IN_LIFE
import com.tpov.schoolquiz.presentation.DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_TOURNIRE_LEADER
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.UNANSWERED_IN_CODE_ANSWER
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesLife.COAST_LIFE_HOME_QUIZ
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.updateProfile
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

        updateProfile = false
        setContentView(binding.root)
        viewModel = ViewModelProvider(this, viewModelFactory)[QuestionViewModel::class.java]
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        supportActionBar?.hide()
        synthInputData()
        viewModel.synthWithDB(this)
        viewModel.shouldCloseLiveData.observe(this) {
            if (it == RESULT_TRANSLATE || it == RESULT_OK) {
                val resultIntent = Intent()
                resultIntent.putExtra("translate", it == RESULT_TRANSLATE)
                resultIntent.putExtra("idQuiz", viewModel.idQuiz)

                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }

        insertQuestionsNewEvent()
        binding.apply {
            if (viewModel.hardQuestion) {
                imvListQuestion.isClickable = false
                imvListQuestion.isEnabled = false
                imvLife.visibility = View.GONE
                imvLifeGold.visibility = View.VISIBLE
            } else {

            }

            trueButton.setOnClickListener {
                nextButton()
                viewModel.trueButton()
                setStateTimer(true)
                setVisibleButtonsNext()
            }

            falseButton.setOnClickListener {
                nextButton()
                viewModel.falseButton()
                setStateTimer(true)
                setVisibleButtonsNext()
            }

            cheatButton.setOnClickListener { view ->
                log("okokowkdowd 1")
                if (viewModel.getProfile().count!! >= COUNT_LIFE_POINTS_IN_LIFE && !viewModel.hardQuestion
                    || viewModel.getProfile().countGold!! >= COUNT_LIFE_POINTS_IN_LIFE && viewModel.hardQuestion
                ) {
                    val answerIsTrue =
                        viewModel.questionListThis[viewModel.currentIndex].answerQuestion
                    val intent = CheatActivity.newIntent(this@QuestionActivity, answerIsTrue)

                    log("okokowkdowd 2")
                    val options =
                        ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.width, view.height)
                    startActivityForResult(intent, REQUEST_CODE_CHEAT, options.toBundle())
                } else {
                    Toast.makeText(this@QuestionActivity, getString(R.string.question_zero_life), Toast.LENGTH_LONG)
                        .show()
                }
            }

            tvPref.setOnClickListener {
                setVisibleButtonsPref()
                prefButton()
            }
            tvNext.setOnClickListener {
                setVisibleButtonsNext()
                nextButton()
            }

            try {
                showTextWithDelay(
                    binding.questionTextView,
                    viewModel.questionListThis[viewModel.currentIndex].nameQuestion,
                    DELAY_SHOW_TEXT
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@QuestionActivity,
                    getString(R.string.question_error_get_questions),
                    Toast.LENGTH_LONG
                ).show()
                Toast.makeText(
                    this@QuestionActivity,
                    getString(R.string.question_error_get_questions_and_compensation),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.profileUseCase.updateProfile(
                    viewModel.getProfile()
                        .copy(count = viewModel.getProfile().count?.plus(COAST_LIFE_HOME_QUIZ))
                )
                viewModel.timer?.cancel()
                finish()
            }

            viewModel.setPetcentPBLiveData.observe(this@QuestionActivity) {
                pbAnswer.progress = it
            }

            viewModel.setPercentiveData.observe(this@QuestionActivity) {
                tvPercent.text = it.toString()
            }

            viewModel.setLeftAnswerLiveData.observe(this@QuestionActivity) {
                tvLoad.text = it.toString()
            }

            tvLoad.text = viewModel.leftAnswer.toString()
            tvNumQuestion.text = viewModel.numQuestion.toString()

            imvListQuestion.setOnClickListener {
                if (!viewModel.hardQuestion) {
                    val questionActivityIntent =
                        Intent(this@QuestionActivity, QuestionListActivity::class.java)

                    ActivityOptions.makeClipRevealAnimation(
                        View(this@QuestionActivity),
                        0,
                        0,
                        View(this@QuestionActivity).width,
                        View(this@QuestionActivity).height
                    )

                    questionActivityIntent.putExtra(
                        EXTRA_CURRENT_INDEX,
                        viewModel.currentIndex
                    )   //Output
                    questionActivityIntent.putExtra(EXTRA_CODE_ANSWER, viewModel.codeAnswer)
                    questionActivityIntent.putExtra(EXTRA_CODE_ID_USER, viewModel.idQuiz)
                    startActivityForResult(questionActivityIntent, UPDATE_CURRENT_INDEX)
                }
            }

            viewModel.closeActivityEvent.observe(this@QuestionActivity) {
                finish()
            }
        }

        updateDataView()
        actionBarSettings()
        startService(Intent(this, MusicService::class.java))
        startTimer()
        visibleCheatButton(true)
    }

    private fun showTextWithDelay(textView: TextView, text: String, delayInMillis: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            val spannableText = SpannableStringBuilder()

            delay(200)
            for (char in text) {
                val start = spannableText.length
                spannableText.append(char.toString())
                spannableText.setSpan(
                    if (viewModel.hardQuestion) ForegroundColorSpan(Color.RED)
                    else ForegroundColorSpan(Color.GREEN),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
                delay(delayInMillis)

                spannableText.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
            }
        }
    }

    private fun synthInputData() {
        viewModel.userName = intent.getStringExtra(NAME_USER) ?: "user"
        viewModel.idQuiz = intent.getIntExtra(ID_QUIZ, 0)
        viewModel.hardQuestion = intent.getBooleanExtra(HARD_QUESTION, false)
        log("fun synthInputData userName: ${viewModel.userName}, idQuiz: ${viewModel.idQuiz}, hardQuestion: ${viewModel.hardQuestion}")

        if (viewModel.quizUseCase.getQuiz(viewModel.idQuiz).event == EVENT_QUIZ_TOURNIRE_LEADER) binding.viewBackground.background =
            getDrawable(R.drawable.back_arena_question)
        else {
            if (!viewModel.hardQuestion) binding.viewBackground.background =
                getDrawable(R.drawable.back_light_question)
            else binding.viewBackground.background = getDrawable(R.drawable.back_hard_question)
        }
    }

    private fun prefButton() {
        if (viewModel.currentIndex == 0) springAnim(false)
        else moveToPref()
    }

    private fun nextButton() {
        if (viewModel.currentIndex == viewModel.numQuestion - 1) springAnim(true)
        else moveToNext()
    }

    private fun setStateTimer(nextQuestion: Boolean) {

        log("setStateTimer currentIndex: ${viewModel.codeAnswer}")
        log("setStateTimer currentIndex: ${viewModel.currentIndex}")

        try {
            if (nextQuestion) {
                log("setStateTimer currentIndex + 1: ${viewModel.codeAnswer[viewModel.currentIndex + 1]}")

                if (viewModel.codeAnswer[viewModel.currentIndex + 1] == UNANSWERED_IN_CODE_ANSWER) {
                    startTimer()
                } else viewModel.timer?.cancel()

            } else {
                log("setStateTimer currentIndex - 1: ${viewModel.codeAnswer[viewModel.currentIndex - 1]}")

                if (viewModel.codeAnswer[viewModel.currentIndex - 1] == UNANSWERED_IN_CODE_ANSWER) {

                    startTimer()
                } else viewModel.timer?.cancel()
            }
        } catch (e: Exception) {
            viewModel.timer?.cancel()
        }
    }

    private fun startTimer() {
        viewModel.timer?.cancel()
        viewModel.timer = object : CountDownTimer(
            viewModel.getCurrentTimer(viewModel.hardQuestion) * QuestionViewModel.MILLIS_IN_SECONDS,
            QuestionViewModel.MILLIS_IN_SECONDS
        ) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimer.text = viewModel.formatTime(millisUntilFinished)
                if (viewModel.formatTime(millisUntilFinished)[3] == UNANSWERED_IN_CODE_ANSWER && viewModel.formatTime(
                        millisUntilFinished
                    )[4] == '3'
                ) anim321(3) //Анимация для цифры 3
                if (viewModel.formatTime(millisUntilFinished)[3] == UNANSWERED_IN_CODE_ANSWER && viewModel.formatTime(
                        millisUntilFinished
                    )[4] == '2'
                ) anim321(2) //2
                if (viewModel.formatTime(millisUntilFinished)[3] == UNANSWERED_IN_CODE_ANSWER && viewModel.formatTime(
                        millisUntilFinished
                    )[4] == '1'
                ) anim321(1) //1
            }

            override fun onFinish() {
                if ((Math.random() * 2) > 1.0) {
                    nextButton()
                    viewModel.trueButton()
                    setStateTimer(true)
                    setVisibleButtonsNext()
                } else {
                    nextButton()
                    viewModel.falseButton()
                    setStateTimer(true)
                    setVisibleButtonsNext()
                }
            }
        }
        viewModel.timer?.start()
    }

    private fun setVisibleButtonsNext() { //Стоит учитывать, что в момент вызова этой функции пользователь находится все еще на том вопросе


    }

    private fun setVisibleButtonsPref() {

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

    private fun insertQuestionsNewEvent() {
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.quizUseCase.getQuizListLiveData(viewModel.tpovId.toInt())
                .observe(this@QuestionActivity) { list ->
                    list.forEach { quiz ->
                        CoroutineScope(Dispatchers.IO).launch {
                            if (viewModel.questionUseCase.getQuestionsByIdQuiz(quiz.id!!).isNullOrEmpty()) {
                                viewModel.questionUseCase.getQuestionsByIdQuiz(quiz.id!!).forEach {
                                    viewModel.questionUseCase.insertQuestion(
                                        it.copy(
                                            id = null,
                                            idQuiz = quiz.id!!
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
        }
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) {
            return
        }
        if (requestCode == REQUEST_CODE_CHEAT) {

            if (data.getBooleanExtra(
                    EXTRA_ANSWER_SHOW,
                    false
                )
            ) {

                if (!viewModel.hardQuestion) viewModel.profileUseCase.updateProfile(viewModel.profileUseCase.getProfile(getTpovId())
                    .copy(count = viewModel.profileUseCase.getProfile(getTpovId()).count?.minus(COUNT_LIFE_POINTS_IN_LIFE)
                    ))
                else viewModel.profileUseCase.updateProfile(viewModel.profileUseCase.getProfile(getTpovId())
                    .copy(countGold = viewModel.profileUseCase.getProfile(getTpovId()).countGold?.minus(COUNT_LIFE_POINTS_IN_LIFE)
                    ))
            }
        } else if (requestCode == UPDATE_CURRENT_INDEX) {
            viewModel.currentIndex = data.getIntExtra(EXTRA_UPDATE_CURRENT_INDEX, 0)

            //Идея такая, после того как пользователь выбрал любой квест, мы этот выбор пытаемся сделать эквиваленту нажатия на кнопки вперед или назад
            if (data.getIntExtra(EXTRA_UPDATE_CURRENT_INDEX, 0) > viewModel.currentIndex) {
                viewModel.currentIndex = data.getIntExtra(EXTRA_UPDATE_CURRENT_INDEX, 0) - 1
                setVisibleButtonsNext()
                setStateTimer(true)
                nextButton()

            } else {
                viewModel.currentIndex = data.getIntExtra(EXTRA_UPDATE_CURRENT_INDEX, 0) + 1
                setVisibleButtonsPref()
                setStateTimer(false)
                prefButton()
            }
        }
    }

    private fun setBlockButton(state: Boolean) = with(binding) {
        if (state) {
            trueButton.setBackgroundResource(R.drawable.back_item_main_true)
            falseButton.setBackgroundResource(R.drawable.back_item_main_false)
            trueButton.setTextColor(Color.WHITE)
            falseButton.setTextColor(Color.WHITE)
        } else {
            trueButton.setBackgroundResource(R.drawable.back_item_main_unable)
            falseButton.setBackgroundResource(R.drawable.back_item_main_unable)
            trueButton.setTextColor(Color.GRAY)
            falseButton.setTextColor(Color.GRAY)
        }
        falseButton.isEnabled = state
        falseButton.isClickable = state
        trueButton.isEnabled = state
        trueButton.isClickable = state
    }

    //Тут уже обновленные параметры
    fun updateTextQuestion() {
        updateDataView()

        showTextWithDelay(
            binding.questionTextView,
            viewModel.questionListThis[viewModel.currentIndex].nameQuestion,
            DELAY_SHOW_TEXT
        )

    }

    private fun updateDataView() {
        binding.tvPercent.text = (viewModel.persent).toString()
        if (!viewModel.hardQuestion)
            binding.tvPointsLife.text = (viewModel.getProfile().count?.div(COUNT_LIFE_POINTS_IN_LIFE)).toString()
        else binding.tvPointsGoldLife.text = (viewModel.getProfile().countGold?.div(COUNT_LIFE_POINTS_IN_LIFE)).toString()

    }

    private fun moveToPref() = with(binding) {

        var animPref1 =
            AnimationUtils.loadAnimation(this@QuestionActivity, R.anim.pref_question1)
        var animPref2 =
            AnimationUtils.loadAnimation(this@QuestionActivity, R.anim.pref_question2)

        animPref1.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                //todo сделать все кнопки что-бы нельзя их нажать
            }

            override fun onAnimationEnd(p0: Animation?) {
                questionTextView.visibility = View.GONE
                viewModel.currentIndex = (viewModel.currentIndex - 1) % viewModel.numQuestion!!
                updateTextQuestion()

                questionTextView.startAnimation(animPref2)
            }

            override fun onAnimationRepeat(p0: Animation?) {

            }
        })

        animPref2.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                questionTextView.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(p0: Animation?) {
                setBlockButton(viewModel.codeAnswer[viewModel.currentIndex] == UNANSWERED_IN_CODE_ANSWER)
                //todo освободить кнопки
            }

            override fun onAnimationRepeat(p0: Animation?) {

            }
        })
        questionTextView.startAnimation(animPref1)
    }

    private fun moveToNext() {
        var animNext1 = AnimationUtils.loadAnimation(this, R.anim.next_question1)
        var animNext2 = AnimationUtils.loadAnimation(this, R.anim.next_question2)

        animNext1.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                //todo сделать все кнопки что-бы нельзя их нажать
            }

            override fun onAnimationEnd(p0: Animation?) {
                Log.d(
                    "dawdasd",
                    "questionListThis: ${viewModel.currentIndex + 1}, ${viewModel.numQuestion}"
                )
                viewModel.currentIndex++
                updateTextQuestion()

                binding.questionTextView.startAnimation(animNext2)
            }

            override fun onAnimationRepeat(p0: Animation?) {

            }
        })

        animNext2.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                binding.questionTextView.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(p0: Animation?) {
                setBlockButton(viewModel.codeAnswer[viewModel.currentIndex] == UNANSWERED_IN_CODE_ANSWER)

                //todo освободить кнопки
            }

            override fun onAnimationRepeat(p0: Animation?) {

            }
        })
        binding.questionTextView.startAnimation(animNext1)
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
