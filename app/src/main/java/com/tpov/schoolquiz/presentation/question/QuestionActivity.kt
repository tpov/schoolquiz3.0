package com.tpov.schoolquiz.presentation.question

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import com.tpov.schoolquiz.*
import com.tpov.schoolquiz.data.Services.MusicService
import com.tpov.schoolquiz.data.database.log
import com.tpov.schoolquiz.databinding.ActivityQuestionBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.updateProfile
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import kotlinx.android.synthetic.main.activity_question.*
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.floor

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
                cheatButton.visibility = View.GONE
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
                val answerIsTrue = viewModel.questionListThis[viewModel.currentIndex].answerQuestion
                val intent = CheatActivity.newIntent(this@QuestionActivity, answerIsTrue)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val options =
                        ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.width, view.height)
                    startActivityForResult(intent, REQUEST_CODE_CHEAT, options.toBundle())
                } else {
                    startActivityForResult(intent, REQUEST_CODE_CHEAT)
                }
            }


            tv_pref.setOnClickListener {
                setVisibleButtonsPref()
                prefButton()
            }
            tv_next.setOnClickListener {
                setVisibleButtonsNext()
                nextButton()
            }

            log("DSEFSE, currentIndex: ${viewModel.currentIndex}")
            log("DSEFSE, nameQuestion: ${viewModel.questionListThis}")

            try {
                showTextWithDelay( binding.questionTextView, viewModel.questionListThis[viewModel.currentIndex].nameQuestion, DELAY_SHOW_TEXT)
            } catch (e: Exception) {
                Toast.makeText(this@QuestionActivity, "Вопросы не были загружены, возможно произошла ошибка", Toast.LENGTH_LONG).show()
            }
        }
        actionBarSettings()
        startService(Intent(this, MusicService::class.java))
        startTimer()
        visibleCheatButton(viewModel.hardQuestion)
    }

    private fun showTextWithDelay(textView: TextView, text: String, delayInMillis: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            val spannableText = SpannableStringBuilder()

            delay(200)
            for (char in text) {
                val start = spannableText.length
                spannableText.append(char.toString())
                spannableText.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
                delay(delayInMillis)

                // Возвращаем цвет буквы к исходному (черный в данном случае)
                spannableText.setSpan(
                    ForegroundColorSpan(Color.BLACK),
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

        if (viewModel.getQuizByIdUseCase(viewModel.idQuiz).event >= 5) binding.viewBackground.background = getDrawable(R.mipmap.back_question_event5)
        else {
            if (!viewModel.hardQuestion) binding.viewBackground.background =
                getDrawable(R.mipmap.back_question_light)
            else binding.viewBackground.background = getDrawable(R.mipmap.back_question_hard)
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

        try {
            if (nextQuestion) {
                log("setStateTimer currentIndex + 1: ${viewModel.codeAnswer[viewModel.currentIndex + 1]}")

                if (viewModel.codeAnswer[viewModel.currentIndex + 1] == '0') {
                    startTimer()
                } else viewModel.timer?.cancel()

            } else {
                if (viewModel.codeAnswer[viewModel.currentIndex - 1] == '0') {

                    log("setStateTimer currentIndex - 1: ${viewModel.codeAnswer[viewModel.currentIndex - 1]}")
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
                if (viewModel.formatTime(millisUntilFinished)[3] == '0' && viewModel.formatTime(
                        millisUntilFinished
                    )[4] == '3'
                ) anim321(3) //Анимация для цифры 3
                if (viewModel.formatTime(millisUntilFinished)[3] == '0' && viewModel.formatTime(
                        millisUntilFinished
                    )[4] == '2'
                ) anim321(2) //2
                if (viewModel.formatTime(millisUntilFinished)[3] == '0' && viewModel.formatTime(
                        millisUntilFinished
                    )[4] == '1'
                ) anim321(1) //1
            }

            override fun onFinish() {
                if ((Math.random() * 2).toInt() > 1) {
                    setStateTimer(true)
                    setVisibleButtonsNext()
                    viewModel.trueButton()
                } else {
                    setStateTimer(false)
                    setVisibleButtonsNext()
                    viewModel.falseButton()
                }
            }
        }
        viewModel.timer?.start()
    }

    private fun setVisibleButtonsNext() { //Стоит учитывать, что в момент вызова этой функции пользователь находится все еще на том вопросе


    }

    private fun setVisibleButtonsPref() {
        setBlockButton(viewModel.codeAnswer[viewModel.currentIndex - 1] == '0')
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()

            R.id.item_list_answer -> {
                if (!viewModel.hardQuestion) {
                    val questionActivityIntent = Intent(this, QuestionListActivity::class.java)

                    ActivityOptions.makeClipRevealAnimation(
                        View(this),
                        0,
                        0,
                        View(this).width,
                        View(this).height
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
        }
        return true
    }

    private fun insertQuestionsNewEvent() {

        viewModel.getQuizLiveDataUseCase.getQuizUseCase(viewModel.tpovId.toInt())
            .observe(this@QuestionActivity) { list ->

                list.forEach { quiz ->

                    if (viewModel.getQuestionByIdQuizUseCase(quiz.id!!).isNullOrEmpty()) {
                        viewModel.getQuestionByIdQuizUseCase(quiz.id!!).forEach {
                            viewModel.insertQuestionUseCase(it.copy(id = null, idQuiz = quiz.id!!))
                        }
                    }
                }
            }
    }

    private fun springAnim(next: Boolean) = with(binding) {
        val START_VELOCITY = if (next) - 5000f
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
                    ) viewModel.updateProfileUseCase(
                        viewModel.getProfileUseCase(getTpovId()).copy(
                            countLife = viewModel.getProfileUseCase(
                                getTpovId()
                            ).countLife
                        )
                    )
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
        falseButton.isEnabled = state
        falseButton.isClickable = state
        trueButton.isEnabled = state
        trueButton.isClickable = state
    }

    //Тут уже обновленные параметры
    fun updateTextQuestion() {
        updateDataView()

        showTextWithDelay(binding.questionTextView, viewModel.questionListThis[viewModel.currentIndex].nameQuestion, DELAY_SHOW_TEXT)

    }

    private fun updateDataView() {
        binding.tvPercent.text = (viewModel.persent).toString()
        binding.tvPointsLife.text = (floor(
            viewModel.getProfileUseCase(getTpovId()).count?.toDouble()?.div(1000.0) ?: 0.0
        )).toInt().toString()
        binding.tvPointsGoldLife.text = (floor(
            viewModel.getProfileUseCase(getTpovId()).countGold?.toDouble()?.div(1000.0) ?: 0.0
        )).toInt().toString()

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
                setBlockButton(viewModel.codeAnswer[viewModel.currentIndex] == '0')

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
                binding.questionTextView.visibility = View.GONE

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
                setBlockButton(viewModel.codeAnswer[viewModel.currentIndex] == '0')

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

        const val DELAY_SHOW_TEXT = 50L
    }
}
