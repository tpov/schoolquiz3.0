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
        observeViewModel()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupListeners() {
        binding.bSave.setOnClickListener {
            val defaultImage = getDrawable(R.drawable.ic_upload) ?: throw IllegalStateException("Default image not found")
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
            viewModel.selectCategory(selectedItem)
        }

        binding.spCategory.setOnActionItemClickListener {
            viewModel.toggleNewCategoryFields()
        }

        binding.spSubCategory.setOnItemSelectedListener { selectedItem ->
            binding.tvSubCategory.setText(selectedItem)
            viewModel.selectSubCategory(selectedItem)
        }

        binding.spSubCategory.setOnActionItemClickListener {
            viewModel.toggleNewCategoryFields()
        }

        binding.spSubsubCategory.setOnItemSelectedListener { selectedItem ->
            binding.tvSubsubCategory.setText(selectedItem)
            viewModel.selectSubsubCategory(selectedItem)
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
            viewModel.updateQuestionsState(numQuestion = numQuestion, hardQuestion = hardQuestion)
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

    private fun setupAnswersRecyclerView() {
        val answersAdapter = AnswerListAdapter { updatedAnswer ->
            viewModel.onAnswerOptionsChanged(updatedAnswer)
        }


        lifecycleScope.launch {
            binding.rvTranslateAnswers.layoutManager = LinearLayoutManager(this@CreateQuizActivity)
            binding.rvTranslateAnswers.adapter = answersAdapter
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.answerListState.collect { answersList ->
                    answersAdapter.submitList(answersList)
                }
            }
        }
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

        lifecycleScope.launch {

            binding.rvTranslateQuestions.layoutManager = LinearLayoutManager(this@CreateQuizActivity)
            binding.rvTranslateQuestions.adapter = translationsAdapter

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentQuestionTranslationsState.collect { translationsList ->
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
                            state.isEnabled?.let { binding.tvQuizName.isEnabled = it }
                            state.text?.let { binding.tvQuizName.setText(it) }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.quizImageUiState.collect { state ->
                    when (state) {
                        is ImageUiState.Hidden -> binding.imvQuiz.visibility = View.GONE
                        is ImageUiState.Visible -> {
                            binding.imvQuiz.visibility = View.VISIBLE
                            state.imageUri?.let { /* установка изображения */ }
                            state.isEnabled?.let { binding.imvQuiz.isEnabled = it }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch { // Use lifecycleScope
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Use repeatOnLifecycle
                viewModel.showNewCategoryFields.collect { state ->
                    when (state) {
                        false -> binding.llCreateNewCategory.visibility = View.GONE
                        true -> {
                            binding.llCreateNewCategory.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categorySpinnerUiState.collect { state ->
                    when (state) {
                        is SpinnerUiState.Hidden -> binding.spCategory.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                            binding.spCategory.visibility = View.VISIBLE
                            state.items?.let { binding.spCategory.setItems(it) }
                            binding.spCategory.setItemsWithAction(getString(R.string.add_category))
                            state.isEnabled?.let { binding.spCategory.isEnabled = it }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.subCategorySpinnerUiState.collect { state ->
                    when (state) {
                        is SpinnerUiState.Hidden -> binding.spSubCategory.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                            binding.spSubCategory.visibility = View.VISIBLE
                            state.items?.let { binding.spSubCategory.setItems(it) }
                            binding.spSubCategory.setItemsWithAction(getString(R.string.add_subcategory))
                            state.isEnabled?.let { binding.spSubCategory.isEnabled = it }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.subsubCategorySpinnerUiState.collect { state ->
                    when (state) {
                        is SpinnerUiState.Hidden -> binding.spSubsubCategory.visibility = View.GONE
                        is SpinnerUiState.Visible -> {
                            binding.spSubsubCategory.visibility = View.VISIBLE
                            state.items?.let { binding.spSubsubCategory.setItems(it) }
                            binding.spSubsubCategory.setItemsWithAction(getString(R.string.add_subsubcategory))
                            state.isEnabled?.let { binding.spSubsubCategory.isEnabled = it }
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
                            state.isEnabled?.let { binding.imvQuestion.isEnabled = it }
                            state.imageUri?.let { /* установка изображения */ }
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
                            state.isEnabled?.let { binding.imvFullscreen.isEnabled = it }
                            state.isChecked?.let { /* установка состояния кнопки */ }
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
                            state.items?.let {
                                binding.spNumQuestion.setItems(it)
                                binding.spNumQuestion.setItemsWithAction("Создать новый вопрос")
                            }
                            state.selectedIndex?.let { binding.spNumQuestion.setSelection(state.selectedIndex, false) }
                            state.isEnabled?.let { binding.spNumQuestion.isEnabled = it }
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
                            state.isEnabled?.let { binding.bAddAnswer.isEnabled = it }
                            state.text?.let { binding.bAddAnswer.text = it }
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
                            state.isEnabled?.let { binding.bSave.isEnabled = it }
                            state.text?.let { binding.bSave.text = it }
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
                            state.isEnabled?.let { binding.bAddTranslate.isEnabled = it }
                            state.text?.let { binding.bAddTranslate.text = it }
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
                            state.isEnabled?.let { binding.bBeforeEditTranslate.isEnabled = it }
                            state.text?.let { binding.bBeforeEditTranslate.text = it }
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
                            state.isEnabled?.let { binding.bAfterEditTranslate.isEnabled = it }
                            state.text?.let { binding.bAfterEditTranslate.text = it }
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
                            state.isEnabled?.let { binding.bCencel.isEnabled = it }
                            state.text?.let { binding.bCencel.text = it }
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
                            if (state.isInit) {
                                binding.chbTypeQuestion.visibility = View.VISIBLE
                                state.isChecked?.let { binding.chbTypeQuestion.isChecked = it }
                                state.isEnabled?.let { binding.chbTypeQuestion.isEnabled = it }
                            }
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
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {

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
