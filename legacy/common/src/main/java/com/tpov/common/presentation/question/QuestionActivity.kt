package com.tpov.common.presentation.question

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.tpov.common.ANIM_SPRING_VELOCITY_LEFT
import com.tpov.common.ANIM_SPRING_VELOCITY_RIGHT
import com.tpov.common.DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY
import com.tpov.common.MAX_CLASSIC_ANSWER
import com.tpov.common.R
import com.tpov.common.REGEX_DROP_TEXT
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.TIME_QUESTION
import com.tpov.common.databinding.ActivityQuestionBinding
import com.tpov.common.di.DaggerCommonComponent
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.quiz.QuizFragment.Companion.KEY_ID_CATEGORY
import com.tpov.common.presentation.quiz.QuizFragment.Companion.KEY_ID_EVENT
import com.tpov.common.presentation.quiz.QuizFragment.Companion.KEY_ID_SUB_CATEGORY
import com.tpov.common.presentation.quiz.QuizFragment.Companion.KEY_ID_SUB_SUB_CATEGORY
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.Values.context
import com.tpov.log_api.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private var languageUser: List<LanguageUtils>? = null

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private val binding: ActivityQuestionBinding by lazy {
        ActivityQuestionBinding.inflate(layoutInflater)
    }

    private var eventId = 0
    private var pathCategory: MutableList<Int> = mutableListOf()

    val doter = REGEX_DROP_TEXT
    private var originalText: String = ""
    private val insertedOrder = mutableListOf<Int>()

    private lateinit var buttons8: List<Button>
    private lateinit var buttons4: List<EditText>

    override fun onCreate(savedInstanceState: Bundle?) {
        DaggerCommonComponent.factory()
            .create(application)
            .inject(this)
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        viewModel = ViewModelProvider(this, viewModelFactory)[QuestionViewModel::class.java]
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()

        actionBarSettings()
        hideSystemUI()
        initView()
        setOnClickListener()
        synthInputData()
        filledView()
        loadQuizData()
        showQuestion()
        makeButtonsDraggable()
        initTranslateDialog()
        viewModel.initQuizValue()
    }

    private fun filledView() {
        binding.tvPercent.text = viewModel.calculatePercentByCodeAnswer().toString()
        if (viewModel.hardQuiz == true) binding.tvPointsGoldLife.text = viewModel.life.toString()
        else binding.tvPointsLife.text = viewModel.life.toString()
        binding.tvNumQuestion.text = viewModel.questionList.value?.size.toString()
        binding.tvLoad.text = viewModel.countNonEmptyAnswers().toString()
    }

    private fun setOnClickListener() {
        buttons4.forEach { button ->
            button.setOnClickListener {
                viewModel.checkAnswer(listOf(button.tag as Int), true)
            }
        }

        binding.bCheat.setOnClickListener {

        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private fun initView() {
        buttons8 = listOf(
            binding.b1, binding.b2, binding.b3, binding.b4,
            binding.b5, binding.b6, binding.b7, binding.b8
        )

        buttons4 = listOf(
            binding.button1, binding.button2, binding.button3, binding.button4
        )

        CoroutineScope(Dispatchers.IO).launch {
            viewModel.closeActivity.collect { shouldClose ->
                if (shouldClose) {
                    finish()
                }
            }
        }
    }

    private fun loadQuizData() {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("jfgksdjefkse", "1")
            viewModel.quiz.collect {
                Log.d("jfgksdjefkse", "2")
                it?.let {
                    Log.d("jfgksdjefkse", "3")
                    viewModel.initQuizValues()
                    viewModel.getQuestionList(settingsConfig.languages)
                    viewModel.questionList.collect { questionList ->
                        questionList?.let {questionList ->

                            Log.d("jfgksdjefkse", "4")
                            viewModel.getQuestionDetailByPath()
                            viewModel.questionDetailList.collect { questionDetailList ->
                                questionDetailList?.let {
                                    viewModel.initQuestionDetail()
                                    viewModel.questionDetail.collect { questionDetail ->
                                        questionDetail?.let {
                                            viewModel.initQuestionValues()
                                            withContext(Dispatchers.Main) {
                                                initDefaultView()
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
    }

    private fun initTranslateDialog() {
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.showTranslateDialog.collect {
                if (it == true) {
                    TranslateDialog().show(supportFragmentManager, "translate_dialog")
                }
            }
        }
    }

    private fun initDefaultView() {
        visibleCheatButton(
            viewModel.hardQuiz ?: viewModel.errorHandler.notFoundInputData()
        )
    }

    @SuppressLint("DiscouragedApi")
    private fun showQuestion() {
        CoroutineScope(Dispatchers.Main).launch {
            Log.d("jfgksdjefkse", "currentQuestion")
            viewModel.currentQuestion.collect { currentQuestion ->
                currentQuestion?.let { question ->
                    Log.d("jfgksdjefkse", "question: $question")
                    val questionText = question.nameQuestion
                    val answersName = question.nameAnswers
                    val is8Button = questionText.let { doter.containsMatchIn(it) }

                    showViewByTypeQuestion(is8Button, answersName)
                    startTimer(is8Button)

                    if (viewModel.hardQuiz == true) {
                        binding.llLifeGold.visibility = View.VISIBLE
                        binding.llLife.visibility = View.GONE
                    } else {
                        binding.llLife.visibility = View.VISIBLE
                        binding.llLifeGold.visibility = View.GONE
                    }

                    binding.tvQuestionText.text = questionText

                    // Обработка изображения
                    question.pathPictureQuestion?.let { photoPath ->
                        if (photoPath.isNotBlank()) {
                            try {
                                val photoDir = File(context.filesDir, "questionPhoto")
                                val localFile = File(photoDir, File(photoPath).name)

                                if (localFile.exists()) {
                                    binding.imvPhotoQuestion.visibility = View.VISIBLE
                                    Glide.with(this@QuestionActivity)
                                        .load(localFile)
                                        .into(binding.imvPhotoQuestion)
                                } else {
                                    binding.imvPhotoQuestion.visibility = View.GONE
                                    Log.d("Question", "Image file not found: $photoPath")
                                }
                            } catch (e: Exception) {
                                binding.imvPhotoQuestion.visibility = View.GONE
                                Log.e("Question", "Error loading image", e)
                            }
                        } else {
                            binding.imvPhotoQuestion.visibility = View.GONE
                        }
                    } ?: run {
                        binding.imvPhotoQuestion.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showViewByTypeQuestion(is8Button: Boolean, answersName: String) {
        if (is8Button) {
            binding.ll8Answer.visibility = View.VISIBLE
            binding.ll4Answer.visibility = View.GONE
            show8Answers(answersName)
        } else {
            binding.ll8Answer.visibility = View.GONE
            binding.ll4Answer.visibility = View.VISIBLE
            show4Answers(answersName)
        }
    }

    private fun makeButtonsDraggable() {

        for (button in buttons8) {
            button.setOnLongClickListener { view ->
                val clipText = (view as Button).text.toString()
                val item = ClipData.Item(clipText)
                val dragData =
                    ClipData(clipText, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                val myShadow = View.DragShadowBuilder(view)

                view.startDragAndDrop(dragData, myShadow, null, 0)
                true
            }
        }
    }

    private fun setupTextViewForDrop() {
        originalText = binding.tvQuestionText.text.toString()

        binding.tvQuestionText.setOnDragListener { view, dragEvent ->
            when (dragEvent.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    dragEvent.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.invalidate()
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    view.invalidate()
                    true
                }

                DragEvent.ACTION_DROP -> {
                    val item = dragEvent.clipData.getItemAt(0)
                    val dragData = item.text.toString()

                    val textView = view as TextView
                    val text = textView.text.toString()

                    val dropX = dragEvent.x.toInt()
                    val dropY = dragEvent.y.toInt()

                    val layout = textView.layout

                    val line = layout.getLineForVertical(dropY)
                    val offset = layout.getOffsetForHorizontal(line, dropX.toFloat())

                    val matchBefore = doter.find(text.substring(0, offset))
                    val matchAfter = doter.find(text.substring(offset))

                    when {
                        matchBefore != null && matchAfter != null -> {
                            val nearestMatch =
                                if (offset - matchBefore.range.last <= matchAfter.range.first) {
                                    matchBefore
                                } else {
                                    matchAfter
                                }
                            val start = nearestMatch.range.first
                            val newText =
                                text.replaceRange(start, start + 8, dragData) //todo What is 8?
                            textView.text = newText
                        }

                        matchBefore != null -> {
                            val start = matchBefore.range.first
                            val newText = text.replaceRange(start, start + 8, dragData)
                            textView.text = newText
                        }

                        matchAfter != null -> {
                            val start = matchAfter.range.first
                            val newText = text.replaceRange(start, start + 8, dragData)
                            textView.text = newText
                        }
                    }

                    insertedOrder.add(buttons8.indexOfFirst { button: Button -> button.text == dragData } + 1)

                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    view.invalidate()
                    true
                }
                else -> false
            }
        }
        setupUndoFunctionality()
    }

    private fun setupUndoFunctionality() {
        binding.tvQuestionText.setOnClickListener {
            binding.tvQuestionText.text = originalText
        }
    }

    private fun show8Answers(answersName: String?) {
        val answers = answersName?.split(SPLIT_BETWEEN_ANSWERS) ?: return
        val maxAnswers = 8

        val indexedAnswers = answers.withIndex().toList().shuffled()
        val newAnswerOrder =
            indexedAnswers.take(maxAnswers).map { it.index + 1 }.joinToString("").toInt()

        for (i in buttons8.indices) {
            if (i < indexedAnswers.size && i < maxAnswers) {
                buttons8[i].text = indexedAnswers[i].value
                buttons8[i].visibility = View.VISIBLE
                buttons8[i].tag = indexedAnswers[i].index + 1
            } else {
                buttons8[i].visibility = View.GONE
            }
        }

        setupTextViewForDrop()

        viewModel.originalAnswerOrder = answersName
        viewModel.newAnswerOrder = newAnswerOrder
    }

    private fun show4Answers(answersName: String?) {
        val answers = answersName?.split(SPLIT_BETWEEN_ANSWERS) ?: return
        val maxAnswers = MAX_CLASSIC_ANSWER

        val indexedAnswers = answers.withIndex().toList().shuffled()
        val newAnswerOrder =
            indexedAnswers.take(maxAnswers).map { it.index + 1 }.joinToString("").toInt()

        for (i in buttons4.indices) {
            if (i < indexedAnswers.size && i < maxAnswers) {
                buttons4[i].setText(indexedAnswers[i].value)
                buttons4[i].visibility = View.VISIBLE
                buttons4[i].tag = indexedAnswers[i].index + 1
            } else {
                buttons4[i].visibility = View.GONE
            }
        }

        viewModel.originalAnswerOrder = answersName
        viewModel.newAnswerOrder = newAnswerOrder
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun synthInputData() {
        viewModel.pathStructure?.nameQuiz = intent.getStringExtra(KEY_ID_QUIZ) ?: ""
        viewModel.pathStructure?.nameCategory = intent.getStringExtra(KEY_ID_CATEGORY) ?: ""
        viewModel.pathStructure?.nameSubCategory = intent.getStringExtra(KEY_ID_SUB_CATEGORY) ?: ""
        viewModel.pathStructure?.nameSubsubCategory = intent.getStringExtra(KEY_ID_SUB_SUB_CATEGORY) ?: ""
        viewModel.pathStructure?.nameEvent = intent.getStringExtra(KEY_ID_EVENT) ?: ""
        viewModel.hardQuiz = intent.getBooleanExtra(KEY_HARD_QUESTION, false)
        languageUser = settingsConfig.languages
        viewModel.life = intent.getIntExtra(KEY_LIFE, 0)
    }

    private fun visibleCheatButton(it: Boolean) {
        binding.bCheat.isClickable = it
        binding.bCheat.isEnabled = it
    }

    private fun springAnim(next: Boolean) = with(binding) {
        val START_VELOCITY = if (next) ANIM_SPRING_VELOCITY_LEFT
        else ANIM_SPRING_VELOCITY_RIGHT

        var springAnimation = SpringAnimation(tvQuestionText, DynamicAnimation.X)
        var springForce = SpringForce()
        springForce.finalPosition = tvQuestionText.x
        springForce.dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
        springForce.stiffness = SpringForce.STIFFNESS_HIGH

        springAnimation.spring = springForce
        springAnimation.setStartVelocity(START_VELOCITY)
        springAnimation.start()
    }

    private fun actionBarSettings() {
        val ab = supportActionBar
        ab?.setDisplayHomeAsUpEnabled(true)
    }

    private fun startTimer(is4Button: Boolean) {
        val totalTime = TIME_QUESTION * 1000L
        val interval = 1000L

        object : CountDownTimer(totalTime, interval) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                if (secondsRemaining <= 3) anim321(secondsRemaining.toInt())
                binding.tvTimer.text = "$secondsRemaining s"
            }

            override fun onFinish() {
                evaluateAnswer()
            }
        }.start()
    }

    private fun evaluateAnswer() {
        viewModel.checkAnswer(insertedOrder, false)
        insertedOrder.clear()
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

    companion object {
        const val KEY_NAME_USER = "name_user"
        const val KEY_ID_QUIZ = "id_quiz"
        const val KEY_HARD_QUESTION = "hard_question"
        const val KEY_LANGUAGE_USER = "language_user"
        const val KEY_LIFE = "life"
        const val RESULT_TRANSLATE = 2
        const val RESULT_OK = 1

        const val DELAY_SHOW_TEXT = DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY

        fun newIntent(
            context: Context,
            hardQuestion: Boolean,
            pathStructure: PathStructure,
            life: Int
        ): Intent {
            return Intent(context, QuestionActivity::class.java).apply {
                putExtra(KEY_ID_EVENT, pathStructure.nameEvent)
                putExtra(KEY_ID_CATEGORY, pathStructure.nameCategory)
                putExtra(KEY_ID_SUB_CATEGORY, pathStructure.nameSubCategory)
                putExtra(KEY_ID_SUB_SUB_CATEGORY, pathStructure.nameSubsubCategory)
                putExtra(KEY_ID_QUIZ, pathStructure.nameQuiz)
                putExtra(KEY_HARD_QUESTION, hardQuestion)
                putExtra(KEY_LIFE, life)
            }
        }
    }
}
