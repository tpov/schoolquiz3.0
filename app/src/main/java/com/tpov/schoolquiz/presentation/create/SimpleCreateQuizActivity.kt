package com.tpov.schoolquiz.presentation.create

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.databinding.ActivitySimpleCreateQuizBinding
import com.tpov.schoolquiz.presentation.dialog.QuizDetailsDialogFragment // Added import
import javax.inject.Inject

class SimpleCreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: ActivitySimpleCreateQuizBinding
    private lateinit var viewModel: SimpleCreateQuizViewModel
    private lateinit var questionPagerAdapter: QuestionPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as? MainApp)?.applicationComponent?.inject(this)
        binding = ActivitySimpleCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, viewModelFactory)[SimpleCreateQuizViewModel::class.java]

        setupViewPager()
        setupListeners()
        observeViewModel()
    }

    private fun setupViewPager() {
        questionPagerAdapter = QuestionPagerAdapter(this)
        binding.viewPagerQuestions.adapter = questionPagerAdapter
        // TODO: Add logic to load initial questions if any
    }

    private fun setupListeners() {
        binding.buttonAddQuestion.setOnClickListener {
            viewModel.addQuestion()
        }

        binding.buttonSaveQuiz.setOnClickListener {
            // Collect current questions from adapter. Ideally, ViewModel should already have the latest.
            val questionsToSave = questionPagerAdapter.getQuestions() // Ensure this gets the latest data
            // Or better, ensure fragments update ViewModel continuously, then get from ViewModel
            // viewModel.updateAllQuestionsFromFragmentsIfNeeded(questionPagerAdapter.getFragments()) -> if fragments hold state

            if (questionsToSave.isNotEmpty() && questionsToSave.all { it.questionText.isNotBlank() }) {
                viewModel.triggerQuizDetailsDialog()
            } else {
                // Show some error message if no questions or questions are empty
                // For example, using a Toast:
                // Toast.makeText(this, "Пожалуйста, добавьте вопросы и заполните их.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.questions.observe(this) { questions ->
            questionPagerAdapter.submitList(questions)
        }

        viewModel.showQuizDetailsDialog.observe(this) { shouldShow ->
            if (shouldShow) {
                QuizDetailsDialogFragment.newInstance().show(supportFragmentManager, "QuizDetailsDialog")
            }
        }
    }
}
