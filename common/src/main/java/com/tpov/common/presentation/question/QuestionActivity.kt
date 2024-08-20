package com.tpov.common.presentation.question

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.os.Bundle
import android.os.CountDownTimer
import android.view.DragEvent
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import com.tpov.common.DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY
import com.tpov.common.R
import com.tpov.common.databinding.ActivityQuestionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch

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
class QuestionActivity : AppCompatActivity(){

    lateinit var viewModel: QuestionViewModel
    private var languageUser: String? = null


    private val binding: ActivityQuestionBinding by lazy {
        ActivityQuestionBinding.inflate(layoutInflater)
    }


    val doter =  Regex("\\(.{7}\\)")
    private var originalText: String = ""
    private val insertedOrder = mutableListOf<Int>()

    private val buttons8: List<Button> = listOf(
        binding.b1, binding.b2, binding.b3, binding.b4,
        binding.b5, binding.b6, binding.b7, binding.b8
    )

    val buttons4 = listOf(
        binding.button1, binding.button2, binding.button3, binding.button4
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()
        actionBarSettings()

        synthInputData()
        loadQuizData()
        showQuestion()
        makeButtonsDraggable()
    }

    private fun loadQuizData() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.getQuizById()
            viewModel.quiz.collect {
                it?.let {
                    viewModel.initQuizValues()
                    viewModel.getQuestionList(
                        languageUser ?: viewModel.notFoundInputData().toString()
                    )
                    viewModel.questionList.collect { questionList ->
                        questionList?.let {
                            viewModel.getQuestionDetailByIdQuiz()
                            viewModel.questionDetailList.collect { questionDetailList ->
                                questionDetailList?.let {
                                    viewModel.initQuestionDetail()
                                    viewModel.questionDetail.collect { questionDetail ->
                                        questionDetail?.let {
                                            viewModel.initQuestionValues()
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

    private fun initDefaultView() {
        visibleCheatButton(viewModel.hardQuiz ?: viewModel.notFoundInputData().toString().toBoolean())
    }

    @SuppressLint("DiscouragedApi")
    private fun showQuestion() {
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.currentQuestion.collect { currentQuestion ->
                if (currentQuestion != null && currentQuestion != viewModel.unknownCurrentQuestion) {
                    val questionText =
                        viewModel.questionList.value?.get(currentQuestion)?.nameQuestion
                    val answer = viewModel.questionList.value?.get(currentQuestion)?.answer
                    val answersName = viewModel.questionList.value?.get(currentQuestion)?.nameAnswers
                    val is4Button = questionText?.let { doter.containsMatchIn(it) } == true
                    if (is4Button) show4Answers(answer, answersName)
                    else show8Answers(answer, answersName)
                    startTimer(is4Button)

                    setBlockButton(viewModel.codeAnswer.get(currentQuestion).code == 0)
                    binding.tvQuestionText.setText(questionText)

                    binding.imvQuestion.setImageResource(resources.getIdentifier(
                        viewModel.questionList.value?.get(viewModel.currentQuestion.value ?: 0)?.pathPictureQuestion,
                        "drawable",
                        packageName
                    ))



                }
            }
        }
    }

    private fun makeButtonsDraggable() {

        for (button in buttons8) {
            button.setOnLongClickListener { view ->
                val clipText = (view as Button).text.toString()
                val item = ClipData.Item(clipText)
                val dragData = ClipData(clipText, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
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
                            val nearestMatch = if (offset - matchBefore.range.last <= matchAfter.range.first) {
                                matchBefore
                            } else {
                                matchAfter
                            }
                            val start = nearestMatch.range.first
                            val newText = text.replaceRange(start, start + 8, dragData)
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

    private fun show8Answers(answer: Int?, answersName: String?) {
        val answers = answersName?.split("|") ?: return
        val maxAnswers = 8

        val paddedAnswerValue = answer?.toString()?.padEnd(answers.size, '0') ?: "0".repeat(answers.size)
        val indexedAnswers = answers.withIndex().toList().shuffled()
        val newAnswerOrder = indexedAnswers.take(maxAnswers).map { it.index + 1 }.joinToString("").toInt()


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

        viewModel.originalAnswerOrder = paddedAnswerValue.padStart(maxAnswers, '0').toInt()
        viewModel.newAnswerOrder = newAnswerOrder
    }

    private fun show4Answers(answer: Int?, answersName: String?) {
        val answers = answersName?.split("|") ?: return
        val maxAnswers = 4

        val paddedAnswerValue = answer?.toString()?.padEnd(answers.size, '0') ?: "0".repeat(answers.size)
        val indexedAnswers = answers.withIndex().toList().shuffled()
        val newAnswerOrder = indexedAnswers.take(maxAnswers).map { it.index + 1 }.joinToString("").toInt()

        for (i in buttons4.indices) {
            if (i < indexedAnswers.size && i < maxAnswers) {
                buttons4[i].text = indexedAnswers[i].value
                buttons4[i].visibility = View.VISIBLE
                buttons4[i].tag = indexedAnswers[i].index + 1
            } else {
                buttons4[i].visibility = View.GONE
            }
        }

        viewModel.originalAnswerOrder = paddedAnswerValue.padStart(maxAnswers, '0').toInt()
        viewModel.newAnswerOrder = newAnswerOrder
    }

    private fun checkAnswer(selectedTags: List<Int>, is4Button: Boolean) {
        if (selectedTags.isEmpty()) return
        val score: Int

        if (is4Button) {
            val correctAnswerIndex = viewModel.originalAnswerOrder.toString().toIntOrNull() ?: return
            score = if (selectedTags.contains(correctAnswerIndex)) {
                9
            } else {
                1
            }
        } else {
            var correctCount = 0

            val originalOrder = viewModel.originalAnswerOrder.toString().take(8).map { it.toString().toInt() }
            val totalCorrectAnswers = originalOrder.size

            for (i in selectedTags.indices) {
                val originalAnswer = originalOrder.getOrNull(i) ?: continue
                val selectedAnswer = selectedTags.getOrNull(i) ?: continue

                if (originalAnswer == selectedAnswer) {
                    correctCount += 1
                }
            }

            val percentage = (correctCount.toFloat() / totalCorrectAnswers) * 100
            score = when {
                percentage >= 100 -> 9
                percentage >= 87.5 -> 8
                percentage >= 75 -> 7
                percentage >= 62.5 -> 6
                percentage >= 50 -> 5
                percentage >= 37.5 -> 4
                percentage >= 25 -> 3
                percentage >= 12.5 -> 2
                else -> 1
            }
        }

        setCodeInCodeAnswer(score)
        setNextQuestion()
    }

    private fun setNextQuestion() {
        if (viewModel.currentQuestion.value?.plus(1)!! >= viewModel.numQuestions!!) springAnim(true)
        else viewModel.setNewCurrentQuestion(viewModel.currentQuestion.value?.plus(1)!!)
    }

    private fun setPrefQuestion() {
        if (viewModel.currentQuestion.value?.plus(1)!! <= 1) springAnim(false)
        else viewModel.setNewCurrentQuestion(viewModel.currentQuestion.value?.minus(1)!!)
    }

    private fun setCodeInCodeAnswer(score: Int) {
        val index = viewModel.currentQuestion.value!!

        if (index < viewModel.codeAnswer.length) {
            viewModel.codeAnswer = viewModel.codeAnswer.substring(0, index) + score.toString() +
                    viewModel.codeAnswer.substring(index + 1)
        } else {
            //error
            viewModel.codeAnswer = viewModel.codeAnswer.padEnd(index, '0') + score.toString()
        }

        if (!viewModel.codeAnswer.contains('0')) viewModel.result()
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
        //stopService(Intent(this, MusicService::class.java))
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        //menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }


    private fun springAnim(next: Boolean) = with(binding) {
        val START_VELOCITY = if (next) -5000f
        else 5000f

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
        val totalTime = 30 * 1000L
        val interval = 1000L

        object : CountDownTimer(totalTime, interval) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                if (secondsRemaining <= 3) anim321(secondsRemaining.toInt())
                binding.tvTimer.text = "$secondsRemaining с" // Обновляем текст таймера
            }

            override fun onFinish() {
                if (is4Button) evaluateAnswer()
                setCodeInCodeAnswer(3)
                setNextQuestion()
            }
        }.start()
    }

    private fun evaluateAnswer() {
        checkAnswer(insertedOrder, false)
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

    private fun setBlockButton(state: Boolean) = with(binding) {
        for (button in buttons4) {
            button.isEnabled = state
            button.isClickable = state
        }
        for (button in buttons8) {
            button.isEnabled = state
            button.isClickable = state
        }
    }

    companion object {
        const val ID_QUIZ = "name_question"
        const val NAME_USER = "name_user"
        const val HARD_QUESTION = "hard_question"
        const val LANGUAGE_USER = "language_user"
        const val LIFE = "life"
        const val RESULT_TRANSLATE = 2
        const val RESULT_OK = 1

        const val DELAY_SHOW_TEXT = DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY
    }
}
