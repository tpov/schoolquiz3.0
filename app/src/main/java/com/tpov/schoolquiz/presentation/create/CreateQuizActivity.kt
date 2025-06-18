package com.tpov.schoolquiz.presentation.create

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import com.tpov.schoolquiz.presentation.create.adapter.AnswerListAdapter
import com.tpov.schoolquiz.presentation.create.adapter.QuestionTranslationListAdapter
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.IsUiState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
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

    private val PICK_IMAGE_REQUEST = 1001
    private var currentImageUploadType: ImageUploadType = ImageUploadType.QUIZ

    enum class ImageUploadType {
        QUIZ, QUESTION, CATEGORY, SUB_CATEGORY, SUB_SUB_CATEGORY
    }

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
        observeUIState()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupListeners() {
        binding.bSave.setOnClickListener {
            val defaultImage =
                getDrawable(R.drawable.ic_upload) ?: throw IllegalStateException("Default image not found")
            viewModel.saveDataForCurrentRegime(
                listOf(
                    binding.tvCategory.text.toString(),
                    binding.tvSubCategory.text.toString(),
                    binding.tvSubsubCategory.text.toString(),
                    binding.tvQuizName.text.toString()
                ),
                listOf(
                    convertToBitmapDrawable(binding.imvCategory.drawable),
                    convertToBitmapDrawable(binding.imvSubcategory.drawable),
                    convertToBitmapDrawable(binding.imvSubsubcategory.drawable),
                    convertToBitmapDrawable(binding.imvQuiz.drawable)
                ),
                defaultImage
            )
            finish()
        }

        binding.spCategory.setOnItemSelectedListener { selectedItem ->
            binding.tvCategory.setText(selectedItem)
            viewModel.selectCategory(Triple(selectedItem, "", ""))
        }

        binding.spCategory.setOnActionItemClickListener {
            viewModel.toggleNewCategoryFields()
        }

        binding.spSubCategory.setOnItemSelectedListener { selectedItem ->
            binding.tvSubCategory.setText(selectedItem)
            viewModel.selectCategory(Triple("", selectedItem, ""))
        }

        binding.spSubCategory.setOnActionItemClickListener {
            viewModel.toggleNewCategoryFields()
        }

        binding.spSubsubCategory.setOnItemSelectedListener { selectedItem ->
            binding.tvSubsubCategory.setText(selectedItem)
            viewModel.selectCategory(Triple("", "", selectedItem))
        }

        binding.spSubsubCategory.setOnActionItemClickListener {
            viewModel.toggleNewCategoryFields()
        }

        binding.imvCategory.setOnClickListener {
            openImagePicker(ImageUploadType.CATEGORY)
        }

        binding.imvSubcategory.setOnClickListener {
            openImagePicker(ImageUploadType.SUB_CATEGORY)
        }

        binding.imvSubsubcategory.setOnClickListener {
            openImagePicker(ImageUploadType.SUB_SUB_CATEGORY)
        }

        binding.imvQuiz.setOnClickListener {
            openImagePicker(ImageUploadType.QUIZ)
        }

        binding.imvQuestion.setOnClickListener {
            openImagePicker(ImageUploadType.QUESTION)
        }

        binding.spNumQuestion.setOnItemSelectedListener { selectedItem ->
            val numQuestion = selectedItem.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            val hardQuestion = selectedItem.contains('*')
            viewModel.selectQuestion(numQuestion, hardQuestion)
        }

        binding.spNumQuestion.setOnActionItemClickListener {
            viewModel.createNewQuestion()
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
            viewModel.updateCheckBox()
        }

        // Add other listeners for text changes in EditTexts, image clicks, etc.
    }

    /**
     * РЕФАКТОРИНГ: observeUIState можно значительно упростить с generic UiState<T>
     *
     * БЫЛО:
     * state.quizNameUiState?.let { uiState ->
     *     when (uiState) {
     *         is TextUiState.Hidden -> binding.tvQuizName.visibility = View.GONE
     *         is TextUiState.Visible -> {
     *             binding.tvQuizName.visibility = View.VISIBLE
     *             binding.tvQuizName.isEnabled = uiState.isEnabled
     *             uiState.text?.let { binding.tvQuizName.setText(it) }
     *         }
     *     }
     * }
     *
     * СТАНЕТ:
     * binding.tvQuizName.updateVisibility(state.quizName) { text ->
     *     binding.tvQuizName.setText(text)
     * }
     *
     * Extension функция:
     * fun <T> View.updateVisibility(uiState: UiState<T>, onVisible: (T) -> Unit = {}) {
     *     when (uiState) {
     *         is UiState.Hidden -> visibility = View.GONE
     *         is UiState.Visible -> {
     *             visibility = View.VISIBLE
     *             isEnabled = uiState.isEnabled
     *             onVisible(uiState.data)
     *         }
     *     }
     * }
     */
    private fun observeUIState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // РЕФАКТОРИНГ: Все эти блоки можно заменить на 1-2 строки с UiState<T>

                    state.quizNameUiState?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.tvQuizName.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.tvQuizName.visibility = View.VISIBLE
                                binding.tvQuizName.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.tvQuizName.setText(it) }
                            }
                        }
                    }
                    // РЕФАКТОРИНГ: После -> binding.tvQuizName.updateVisibility(state.quizName) { setText(it) }

                    // Quiz Image
                    // РЕФАКТОРИНГ: ImageUiState -> UiState<BitmapDrawable>
                    state.quizImageUiState?.let { uiState ->
                        when (uiState) {
                            is ImageUiState.Hidden -> binding.imvQuiz.visibility = View.GONE
                            is ImageUiState.Visible -> {
                                binding.imvQuiz.visibility = View.VISIBLE
                                binding.imvQuiz.isEnabled = uiState.isEnabled
                                // uiState.imageUri?.let { /* установка изображения */ }
                            }
                        }
                    }

                    // РЕФАКТОРИНГ: SpinnerUiState -> UiState<SpinnerData>
                    // Category Spinner
                    state.categorySpinnerUiState?.let { uiState ->
                        when (uiState) {
                            is SpinnerUiState.Hidden -> binding.spCategory.visibility = View.GONE
                            is SpinnerUiState.Visible -> {
                                binding.spCategory.visibility = View.VISIBLE
                                binding.spCategory.isEnabled = uiState.isEnabled
                                uiState.items?.let {
                                    binding.spCategory.setItems(it)
                                    binding.spCategory.setItemsWithAction(getString(R.string.add_category))
                                }
                                uiState.selectedIndex?.let { binding.spCategory.setSelection(it, false) }
                            }
                        }
                    }

                    // Sub Category Spinner
                    state.subCategorySpinnerUiState?.let { uiState ->
                        when (uiState) {
                            is SpinnerUiState.Hidden -> binding.spSubCategory.visibility = View.GONE
                            is SpinnerUiState.Visible -> {
                                binding.spSubCategory.visibility = View.VISIBLE
                                binding.spSubCategory.isEnabled = uiState.isEnabled
                                uiState.items?.let {
                                    binding.spSubCategory.setItems(it)
                                    binding.spSubCategory.setItemsWithAction(getString(R.string.add_subcategory))
                                }
                                uiState.selectedIndex?.let { binding.spSubCategory.setSelection(it, false) }
                            }
                        }
                    }

                    // Sub-sub Category Spinner
                    state.subsubCategorySpinnerUiState?.let { uiState ->
                        when (uiState) {
                            is SpinnerUiState.Hidden -> binding.spSubsubCategory.visibility = View.GONE
                            is SpinnerUiState.Visible -> {
                                binding.spSubsubCategory.visibility = View.VISIBLE
                                binding.spSubsubCategory.isEnabled = uiState.isEnabled
                                uiState.items?.let {
                                    binding.spSubsubCategory.setItems(it)
                                    binding.spSubsubCategory.setItemsWithAction(getString(R.string.add_subsubcategory))
                                }
                                uiState.selectedIndex?.let { binding.spSubsubCategory.setSelection(it, false) }
                            }
                        }
                    }

                    // РЕФАКТОРИНГ: IsUiState -> UiState<Unit> или Boolean
                    // Create New Category Layout
                    state.llCreateNewCategory?.let { uiState ->
                        binding.llCreateNewCategory.visibility = when (uiState) {
                            IsUiState.Hidden -> View.GONE
                            IsUiState.Visible -> View.VISIBLE
                        }
                    }

                    // Category Text
                    state.tvCategory?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.tvCategory.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.tvCategory.visibility = View.VISIBLE
                                binding.tvCategory.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.tvCategory.setText(it) }
                            }
                        }
                    }

                    // Category Image
                    state.imvCategory?.let { uiState ->
                        when (uiState) {
                            is ImageUiState.Hidden -> binding.imvCategory.visibility = View.GONE
                            is ImageUiState.Visible -> {
                                binding.imvCategory.visibility = View.VISIBLE
                                binding.imvCategory.isEnabled = uiState.isEnabled
                                // uiState.imageUri?.let { /* установка изображения */ }
                            }
                        }
                    }

                    // Sub Category Text
                    state.tvSubCategory?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.tvSubCategory.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.tvSubCategory.visibility = View.VISIBLE
                                binding.tvSubCategory.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.tvSubCategory.setText(it) }
                            }
                        }
                    }

                    // Sub Category Image
                    state.imvSubCategory?.let { uiState ->
                        when (uiState) {
                            is ImageUiState.Hidden -> binding.imvSubcategory.visibility = View.GONE
                            is ImageUiState.Visible -> {
                                binding.imvSubcategory.visibility = View.VISIBLE
                                binding.imvSubcategory.isEnabled = uiState.isEnabled
                                // uiState.imageUri?.let { /* установка изображения */ }
                            }
                        }
                    }

                    // Sub-sub Category Text
                    state.tvSubsubCategory?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.tvSubsubCategory.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.tvSubsubCategory.visibility = View.VISIBLE
                                binding.tvSubsubCategory.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.tvSubsubCategory.setText(it) }
                            }
                        }
                    }

                    // Sub-sub Category Image
                    state.imvSubsubCategory?.let { uiState ->
                        when (uiState) {
                            is ImageUiState.Hidden -> binding.imvSubsubcategory.visibility = View.GONE
                            is ImageUiState.Visible -> {
                                binding.imvSubsubcategory.visibility = View.VISIBLE
                                binding.imvSubsubcategory.isEnabled = uiState.isEnabled
                                // uiState.imageUri?.let { /* установка изображения */ }
                            }
                        }
                    }

                    // Stroke Top
                    state.stroceTop?.let { uiState ->
                        binding.stroceTop.visibility = when (uiState) {
                            IsUiState.Hidden -> View.GONE
                            IsUiState.Visible -> View.VISIBLE
                        }
                    }

                    // Question Image
                    state.questionImageUiState?.let { uiState ->
                        when (uiState) {
                            is ImageUiState.Hidden -> binding.imvQuestion.visibility = View.GONE
                            is ImageUiState.Visible -> {
                                binding.imvQuestion.visibility = View.VISIBLE
                                binding.imvQuestion.isEnabled = uiState.isEnabled
                                // uiState.imageUri?.let { /* установка изображения */ }
                            }
                        }
                    }

                    // Fullscreen Button
                    state.fullscreenButtonUiState?.let { uiState ->
                        when (uiState) {
                            is CheckBoxUiState.Hidden -> binding.imvFullscreen.visibility = View.GONE
                            is CheckBoxUiState.Visible -> {
                                binding.imvFullscreen.visibility = View.VISIBLE
                                uiState.isEnabled?.let { binding.imvFullscreen.isEnabled = it }
                                uiState.isChecked?.let { /* установка состояния кнопки */ }
                            }
                        }
                    }

                    // Fullscreen
                    state.fullScreen?.let { uiState ->
                        // Handle fullScreen visibility if needed
                    }

                    // Question Number Spinner
                    state.questionNumberSpinnerUiState?.let { uiState ->
                        when (uiState) {
                            is SpinnerUiState.Hidden -> binding.spNumQuestion.visibility = View.GONE
                            is SpinnerUiState.Visible -> {
                                binding.spNumQuestion.visibility = View.VISIBLE
                                binding.spNumQuestion.isEnabled = uiState.isEnabled
                                uiState.items?.let {
                                    binding.spNumQuestion.setItems(it)
                                    binding.spNumQuestion.setItemsWithAction("Создать новый вопрос")
                                }
                                uiState.selectedIndex?.let { binding.spNumQuestion.setSelection(it, false) }
                            }
                        }
                    }

                    // Stroke Bottom
                    state.stroceBottom?.let { uiState ->
                        binding.stroceBottom.visibility = when (uiState) {
                            IsUiState.Hidden -> View.GONE
                            IsUiState.Visible -> View.VISIBLE
                        }
                    }

                    // Type Question CheckBox
                    state.typeQuestionCheckBoxState?.let { uiState ->
                        when (uiState) {
                            is CheckBoxUiState.Hidden -> binding.chbTypeQuestion.visibility = View.GONE
                            is CheckBoxUiState.Visible -> {
                                if (uiState.isInit) {
                                    binding.chbTypeQuestion.visibility = View.VISIBLE
                                    uiState.isChecked?.let { binding.chbTypeQuestion.isChecked = it }
                                    uiState.isEnabled?.let { binding.chbTypeQuestion.isEnabled = it }
                                    uiState.text?.let { binding.chbTypeQuestion.text = it }
                                }
                            }
                        }
                    }

                    // Before Edit Translate Button
                    state.bBeforeEditTranslate?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.bBeforeEditTranslate.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.bBeforeEditTranslate.visibility = View.VISIBLE
                                binding.bBeforeEditTranslate.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.bBeforeEditTranslate.text = it }
                            }
                        }
                    }

                    // After Edit Translate Button
                    state.bAfterEditTranslate?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.bAfterEditTranslate.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.bAfterEditTranslate.visibility = View.VISIBLE
                                binding.bAfterEditTranslate.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.bAfterEditTranslate.text = it }
                            }
                        }
                    }

                    // Add Answer Button
                    state.addAnswerButtonUiState?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.bAddAnswer.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.bAddAnswer.visibility = View.VISIBLE
                                binding.bAddAnswer.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.bAddAnswer.text = it }
                            }
                        }
                    }

                    // Add Translate Button
                    state.addTranslateButtonUiState?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.bAddTranslate.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.bAddTranslate.visibility = View.VISIBLE
                                binding.bAddTranslate.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.bAddTranslate.text = it }
                            }
                        }
                    }

                    // Cancel Button
                    state.cancelButtonUiState?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.bCencel.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.bCencel.visibility = View.VISIBLE
                                binding.bCencel.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.bCencel.text = it }
                            }
                        }
                    }

                    // Save Quiz Button
                    state.saveQuizButtonUiState?.let { uiState ->
                        when (uiState) {
                            is TextUiState.Hidden -> binding.bSave.visibility = View.GONE
                            is TextUiState.Visible -> {
                                binding.bSave.visibility = View.VISIBLE
                                binding.bSave.isEnabled = uiState.isEnabled
                                uiState.text?.let { binding.bSave.text = it }
                            }
                        }
                    }

                    // РЕФАКТОРИНГ: List<T>? -> UiState<List<T>>
                    // Question Translations RecyclerView
                    // БЫЛО: state.rvQuestionTranslate?.let { ... }
                    // СТАНЕТ: state.questionTranslations.dataOrNull()?.let { adapter.submitList(it) }
                    state.rvQuestionTranslate?.let { questionTranslateList ->
                        (binding.rvTranslateQuestions.adapter as? QuestionTranslationListAdapter)?.submitList(
                            questionTranslateList
                        )
                    }

                    // Answer Translations RecyclerView
                    state.rvAnswerTranslate?.let { answerTranslateList ->
                        (binding.rvTranslateAnswers.adapter as? AnswerListAdapter)?.submitList(answerTranslateList)
                    }
                }
            }
        }
    }

    private fun setupAnswersRecyclerView() {
        val answersAdapter = AnswerListAdapter { updatedAnswer ->
            viewModel.onAnswerOptionsChanged(updatedAnswer)
        }

        binding.rvTranslateAnswers.layoutManager = LinearLayoutManager(this@CreateQuizActivity)
        binding.rvTranslateAnswers.adapter = answersAdapter
    }

    private fun setupQuestionTranslationsRecyclerView() {
        val translationsAdapter = QuestionTranslationListAdapter(
            onQuestionTextChanged = { updatedQuestion ->
                viewModel.onQuestionTextChanged(updatedQuestion)
            },
            onLanguageChanged = { oldLanguage, newLanguage ->
                viewModel.onQuestionLanguageChanged(oldLanguage, newLanguage)
            }
        )

        binding.rvTranslateQuestions.layoutManager = LinearLayoutManager(this@CreateQuizActivity)
        binding.rvTranslateQuestions.adapter = translationsAdapter
    }

    private fun openImagePicker(view: ImageUploadType) {
        currentImageUploadType = view
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }

        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data?.toString()

            when (currentImageUploadType) {
                ImageUploadType.QUIZ -> {
                    Glide.with(this)
                        .load(imageUri)
                        .into(binding.imvQuiz)
                }

                ImageUploadType.QUESTION -> {
                    Glide.with(this)
                        .load(imageUri)
                        .listener(object :
                            com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {

                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                if (resource is BitmapDrawable) {
                                    viewModel.setPhotoQuestion(resource)
                                }
                                return false
                            }
                        })
                        .into(binding.imvQuestion)
                }

                ImageUploadType.CATEGORY -> {
                    Glide.with(this)
                        .load(imageUri)
                        .into(binding.imvCategory)
                }

                ImageUploadType.SUB_CATEGORY -> {
                    Glide.with(this)
                        .load(imageUri)
                        .into(binding.imvSubcategory)
                }

                ImageUploadType.SUB_SUB_CATEGORY -> {
                    Glide.with(this)
                        .load(imageUri)
                        .into(binding.imvSubsubcategory)
                }
            }
        }
    }

    private fun optimizeImageForSaving(uri: String?): Bitmap? {
        if (uri == null) return null

        return try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels

            // Загружаем оригинальное изображение
            val originalBitmap = Glide.with(this)
                .asBitmap()
                .load(uri)
                .submit()
                .get()

            // Вычисляем новые размеры, сохраняя пропорции
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()

            // Новая ширина равна ширине экрана
            val newWidth = screenWidth
            // Новая высота вычисляется с сохранением пропорций
            val newHeight = (screenWidth / ratio).toInt()

            // Создаем новое изображение с оптимизированным размером
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun convertToBitmapDrawable(drawable: Drawable?): BitmapDrawable {
        if (drawable == null) {
            return BitmapDrawable(resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
        return when (drawable) {
            is BitmapDrawable -> drawable
            is VectorDrawable -> {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                BitmapDrawable(resources, bitmap)
            }

            else -> {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                BitmapDrawable(resources, bitmap)
            }
        }
    }
}
