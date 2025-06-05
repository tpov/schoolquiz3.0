package com.tpov.schoolquiz.presentation.create

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
import com.tpov.schoolquiz.presentation.create.model.isUiState
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var structureUseCase: StructureUseCase
    @Inject
    lateinit var questionUseCase: QuestionUseCase
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: ActivityCreateQuizBinding
    private var currentRegime: Int = -1
    private var pathStructure: PathStructure? = null

    private lateinit var viewModel: CreateQuizViewModel

    companion object {
        private const val EXTRA_REGIME = "extra_regime"
        private const val EXTRA_PATH_STRUCTURE = "extra_path_structure"

        fun newIntent(context: Context, regime: Int, pathStructure: PathStructure? = null): Intent {
            return Intent(context, CreateQuizActivity::class.java).apply {
                putExtra(EXTRA_REGIME, regime)
                putExtra(EXTRA_PATH_STRUCTURE, pathStructure)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        (application as? MainApp)?.applicationComponent?.inject(this)
        binding = ActivityCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, viewModelFactory)[CreateQuizViewModel::class.java]

        currentRegime = intent.getIntExtra("extra_regime", -1)
        pathStructure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("extra_path_structure", PathStructure::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("extra_path_structure") as? PathStructure
        }

        setupAnswersRecyclerView()
        setupQuestionTranslationsRecyclerView()

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
         binding.bSave.setOnClickListener { viewModel.saveDataForCurrentRegime() }

        binding.spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

         binding.spSubCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

         binding.spSubsubCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        binding.bAddAnswer.setOnClickListener {
            viewModel.addAnswerOption()
        }

        binding.bAddTranslate.setOnClickListener {
            viewModel.addTranslate()
        }

         binding.bBeforeEditTranslate.setOnClickListener {
             // TODO: Handle click on before edit translate button
         }

         binding.bAfterEditTranslate.setOnClickListener {
             // TODO: Handle click on after edit translate button
         }

         binding.bCencel.setOnClickListener {
             // TODO: Handle click on cancel button
         }

         binding.chbTypeQuestion.setOnCheckedChangeListener { _, isChecked ->
             // TODO: Handle type question checkbox change
             // viewModel.onTypeQuestionChanged(isChecked)
         }

        // Add other listeners for text changes in EditTexts, image clicks, etc.
    }

    private fun setupAnswersRecyclerView() {
        val answersAdapter = AnswerListAdapter { updatedAnswer ->
            viewModel.onAnswerOptionsChanged(updatedAnswer)
        }

        binding.rvTranslateAnswers.layoutManager = LinearLayoutManager(this)
        binding.rvTranslateAnswers.adapter = answersAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.answerListState.collect { answersList ->
                    answersAdapter.submitList(answersList)
                }
            }
        }
    }

    private fun setupQuestionTranslationsRecyclerView() {
        val translationsAdapter = QuestionTranslationListAdapter { updatedQuestion ->
            viewModel.onQuestionTextChanged(updatedQuestion)
        }

        binding.rvTranslateQuestions.layoutManager = LinearLayoutManager(this)
        binding.rvTranslateQuestions.adapter = translationsAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentQuestionTranslationsState.collect { translationsList ->
                    Log.d("drgsef", "translationsList: ${translationsList}")
                    translationsAdapter.submitList(translationsList)
                }
            }
        }
    }


    private fun observeViewModel() {
        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.quizNameUiState.collect { state ->
                    when (state) {
                        is TextUiState.Hidden -> binding.tvQuizName.visibility = View.GONE
                        is TextUiState.Visible -> {
                            binding.tvQuizName.visibility = View.VISIBLE
                            binding.tvQuizName.isEnabled = state.isEnabled
                            // TODO: Set text if TextUiState.Visible has a text property
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.quizImageUiState.collect { state ->
                    when (state) {
                        is ImageUiState.Hidden -> binding.imvQuiz.visibility = View.GONE
                        is ImageUiState.Visible -> {
                            binding.imvQuiz.visibility = View.VISIBLE
                            // TODO: Set image if ImageUiState.Visible has an image Uri
                            binding.imvQuiz.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.categorySpinnerUiState.collect { state ->
                    when (state) {
                        is SpinnerUiState.Hidden -> binding.spCategory.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                            binding.spCategory.visibility = View.VISIBLE
                            val adapter =
                                ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, state.items ?: emptyList()) // Handle null items
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            binding.spCategory.adapter = adapter
                            binding.spCategory.setSelection(state.selectedIndex ?: 0) // Handle null selectedIndex
                            binding.spCategory.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }
        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.subCategorySpinnerUiState.collect { state ->
                    when (state) {
                        is SpinnerUiState.Hidden -> binding.spSubCategory.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                            binding.spSubCategory.visibility = View.VISIBLE
                            val adapter =
                                ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, state.items ?: emptyList())
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            binding.spSubCategory.adapter = adapter
                            binding.spSubCategory.setSelection(state.selectedIndex ?: 0)
                            binding.spSubCategory.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.subsubCategorySpinnerUiState.collect { state ->
                    when (state) {
                        is SpinnerUiState.Hidden -> binding.spSubsubCategory.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                            binding.spSubsubCategory.visibility = View.VISIBLE
                            val adapter =
                                ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, state.items ?: emptyList())
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            binding.spSubsubCategory.adapter = adapter
                            binding.spSubsubCategory.setSelection(state.selectedIndex ?: 0)
                            binding.spSubsubCategory.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.questionImageUiState.collect { state ->
                    when (state) {
                        is ImageUiState.Hidden -> binding.imvQuestion.visibility = View.GONE
                        is ImageUiState.Visible -> {
                            binding.imvQuestion.visibility = View.VISIBLE
                            // TODO: Set image if ImageUiState.Visible has an image Uri
                            binding.imvQuestion.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        // Observe fullscreenButtonUiState (which is CheckBoxUiState)
        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.fullscreenButtonUiState.collect { state ->
                    // Handle CheckBoxUiState
                    when (state) {
                        is CheckBoxUiState.Hidden -> binding.imvFullscreen.visibility = View.GONE
                        is CheckBoxUiState.Visible -> {
                            binding.imvFullscreen.visibility = View.VISIBLE
                            // TODO: Handle text/image for image button - this might need logic based on state properties if available/needed
                            binding.imvFullscreen.isEnabled = state.isEnabled ?: true
                            // Note: ImageViews don't have a text property like CheckBox.
                            // If the image button should change based on state, this logic needs adjustment.
                        }
                    }
                }
            }
        }

         lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.questionNumberSpinnerUiState.collect { state ->
                    when (state) {
                         is SpinnerUiState.Hidden -> binding.spNumQuestion.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                             binding.spNumQuestion.visibility = View.VISIBLE
                             // Spinner items will be updated by observing viewModel.allQuestionsState
                             binding.spNumQuestion.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }


        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.addAnswerButtonUiState.collect { state ->
                     when (state) {
                         is TextUiState.Hidden -> binding.bAddAnswer.visibility = View.GONE
                        is TextUiState.Visible -> {
                             binding.bAddAnswer.visibility = View.VISIBLE
                             // TODO: Set text if TextUiState.Visible has a text property
                             binding.bAddAnswer.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.saveQuizButtonUiState.collect { state ->
                    when (state) {
                        is TextUiState.Hidden -> binding.bSave.visibility = View.GONE
                        is TextUiState.Visible -> {
                            binding.bSave.visibility = View.VISIBLE
                            // TODO: Set text if TextUiState.Visible has a text property
                            binding.bSave.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.addTranslateButtonUiState.collect { state ->
                     when (state) {
                         is TextUiState.Hidden -> binding.bAddTranslate.visibility = View.GONE
                        is TextUiState.Visible -> {
                             binding.bAddTranslate.visibility = View.VISIBLE
                             // TODO: Set text if TextUiState.Visible has a text property
                             binding.bAddTranslate.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }


        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.beforeEditTranslateButtonUiState.collect { state ->
                     when (state) {
                        is TextUiState.Hidden -> binding.bBeforeEditTranslate.visibility = View.GONE
                        is TextUiState.Visible -> {
                             binding.bBeforeEditTranslate.visibility = View.VISIBLE
                             // TODO: Set text if TextUiState.Visible has a text property
                             binding.bBeforeEditTranslate.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.afterEditTranslateButtonUiState.collect { state ->
                     when (state) {
                        is TextUiState.Hidden -> binding.bAfterEditTranslate.visibility = View.GONE
                        is TextUiState.Visible -> {
                             binding.bAfterEditTranslate.visibility = View.VISIBLE
                             // TODO: Set text if TextUiState.Visible has a text property
                             binding.bAfterEditTranslate.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.cancelButtonUiState.collect { state ->
                     when (state) {
                         is TextUiState.Hidden -> binding.bCencel.visibility = View.GONE
                        is TextUiState.Visible -> {
                             binding.bCencel.visibility = View.VISIBLE
                             // TODO: Set text if TextUiState.Visible has a text property
                             binding.bCencel.isEnabled = state.isEnabled
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.typeQuestionCheckBoxState.collect { state ->
                    // Handle CheckBoxUiState
                    when (state) {
                         is CheckBoxUiState.Hidden -> binding.chbTypeQuestion.visibility = View.GONE
                        is CheckBoxUiState.Visible -> {
                             binding.chbTypeQuestion.visibility = View.VISIBLE
                             binding.chbTypeQuestion.isChecked = state.isChecked ?: false
                             binding.chbTypeQuestion.isEnabled = state.isEnabled ?: true
                             binding.chbTypeQuestion.text = state.text // Assuming CheckBoxUiState.Visible has a text property
                        }
                    }
                }
            }
        }

        // Observe ContainerUiStates
         lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.llCreateNewCategoryUiState.collect { state ->
                    binding.llCreateNewCategory.visibility = when (state) {
                        isUiState.Hidden -> View.GONE
                        isUiState.Visible -> View.VISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.stroceTopUiState.collect { state ->
                     binding.stroceTop.visibility = when (state) {
                        isUiState.Hidden -> View.GONE
                        isUiState.Visible -> View.VISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.stroceBottomUiState.collect { state ->
                    binding.stroceBottom.visibility = when (state) {
                        isUiState.Hidden -> View.GONE
                        isUiState.Visible -> View.VISIBLE
                    }
                }
            }
        }


        // Observe lists (not UI state wrappers)
        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.answerListState.collect { answerList ->
                    // Update UI for answer list
                    // Example: Update RecyclerView adapter
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.questionList.collect { allQuestions ->
                    // Update UI based on allQuestionsState (e.g., update question number spinner)
                    // Example:
                     val questionNumbers = allQuestions.indices.map { (it + 1).toString() }
                     val adapter = ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, questionNumbers)
                     adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                     binding.spNumQuestion.adapter = adapter
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.currentQuestionNumber.collect { index ->
                    // Update UI based on the current question index
                     // Example: Select item in question number spinner
                     if (index >= 0 && index < binding.spNumQuestion.count) {
                         binding.spNumQuestion.setSelection(index)
                     }
                }
            }
        }

    }

    // TODO: Add functions to handle callbacks from UI elements (e.g., spinner item selected, text changed)
    // These functions should call corresponding ViewModel functions.

}
