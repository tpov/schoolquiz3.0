package com.tpov.schoolquiz.presentation.create_quiz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.domain.SettingConfigObject.settingsConfig
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.main.MainViewModel
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject

class CreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: MainViewModel
    private var idQuiz = -1
    private var lvlTranslate = 0
    private var counter = 0
    private var isCreateNewCategory = false

    private val PICK_IMAGE_CATEGORY = 1001
    private val PICK_IMAGE_SUBCATEGORY = 1002
    private val PICK_IMAGE_SUBSUBCATEGORY = 1003
    private val PICK_IMAGE_QUIZ = 1004
    private val PICK_IMAGE_QUESTION = 1005

    private lateinit var binding: ActivityCreateQuizBinding

    private var questionsEntity: ArrayList<QuestionEntity> = arrayListOf()
    private var questionsShortEntity: ArrayList<QuestionShortEntity> = arrayListOf()
    private var quizEntity: QuizEntity? = null

    private var category: String = ""
    private var subCategory: String = ""
    private var subsubCategory: String = ""

    private var idGroup = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        (application as MainApp).applicationComponent.inject(this)
        binding = ActivityCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent?.let {
            viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
        }

        quizEntity = intent.getParcelableExtra(ARG_QUIZ)
        questionsEntity = intent.getParcelableArrayListExtra(ARG_QUESTION) ?: arrayListOf()
        questionsShortEntity =
            viewModel.getQuestionListShortEntity(questionsEntity, getUserLanguage())
        initView()
        initSetOnClickListeners()
    }

    private fun setupQuestionSpinner() = with(binding) {

        Log.d("segfdf", "newList setupQuestionSpinner: $questionsShortEntity")
        Log.d("segfdf", "counter setupQuestionSpinner: $counter")
        val adapter = CustomSpinnerAdapter(this@CreateQuizActivity, questionsShortEntity)
        Log.d("setupQuestionSpinner", "$questionsShortEntity")
        spNumQuestion.adapter = adapter
        spNumQuestion.setSelection(counter)

        spNumQuestion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var initSp: Boolean = false

            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!initSp) {
                    initSp = true
                } else {
                    if (position == questionsShortEntity.size - 1) {
                        saveThisNumberQuestion()

                        setNewCounterAndShortList()
                        idGroup = 0
                        updateUiQuestion()

                        initSp = false
                        val newAdapter = CustomSpinnerAdapter(this@CreateQuizActivity, questionsShortEntity)
                        spNumQuestion.adapter = newAdapter
                        spNumQuestion.setSelection(counter)
                    } else {
                        saveThisNumberQuestion()
                        counter = position
                        idGroup = 0
                        updateUiQuestion()
                        initSp = false
                        val newAdapter = CustomSpinnerAdapter(this@CreateQuizActivity, questionsShortEntity)
                        spNumQuestion.adapter = newAdapter
                        spNumQuestion.setSelection(counter)

                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }



    private fun initSetOnClickListeners() = with(binding) {

        bSave.setOnClickListener {
            saveThisNumberQuestion()
            saveThisQuiz()
            saveStructureCategory(quizEntity?.event ?: 1)
            saveQuiz()
        }
        bCencel.setOnClickListener {
            finish()
        }
        bAddTranslate.setOnClickListener {
            addQuestionToLayout("", getUserLanguage())
            addOrUpdateAnswerGroup(idGroup, getUserLanguage(), false, "")
        }
        bAddAnswer.setOnClickListener {
            addOrUpdateAnswerGroup(1, getUserLanguage(), true)
        }

        imvCategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_CATEGORY) }
        imvSubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBCATEGORY) }
        imvSubsubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBSUBCATEGORY) }
        imvQuiz.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUIZ) }
        imvQuestion.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUESTION) }

        setCategorySpinnerListeners()
    }

    private fun saveThisQuiz(): QuizEntity? = with(binding) {
        quizEntity = (quizEntity ?: QuizEntity()).copy(
            idCategory = getNewCategory().first,
            idSubcategory = getNewCategory().second,
            idSubsubcategory = getNewCategory().third,
            nameQuiz = tvQuizName.text.toString(),
            userName = getUserName(),
            dataUpdate = getDataQuiz(),
            numQ = questionsShortEntity.filter { !it.hardQuestion }.size,
            numHQ = questionsShortEntity.filter { it.hardQuestion }.size,
            versionQuiz = quizEntity?.versionQuiz?.plus(1) ?: QuizEntity().versionQuiz,
            picture = saveImage(imvQuiz, (viewModel.getPathPicture())),
            languages = getLanguageQuizByQuestions(),
        )
        return quizEntity
    }

    private fun getNewCategory() = if (!isCreateNewCategory) Triple(
        quizEntity?.idCategory ?: 0,
        quizEntity?.idSubcategory ?: 0,
        quizEntity?.idSubsubcategory ?: 0
    ) else Triple(0, 0, 0)

    private var structureCategoryDataEntity = StructureCategoryDataEntity()

    private fun saveStructureCategory(newEvent: Int) {
        val pathCategory = saveImage(binding.imvCategory, viewModel.getPathPicture())
        val pathSubCategory = saveImage(binding.imvSubcategory, viewModel.getPathPicture())
        val pathSubsubCategory = saveImage(binding.imvSubsubcategory, viewModel.getPathPicture())
        structureCategoryDataEntity = StructureCategoryDataEntity().initCreateQuizActivity(
            quizEntity!!,
            getCategoriesByLayout(),
            newEvent,
            pathCategory,
            pathSubCategory,
            pathSubsubCategory,
            binding.tvQuizName.text.toString()
        )
    }

    private fun saveImage(imageView: ImageView, fileName: String): String {
        val drawable = imageView.drawable

        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap

            // Указываем максимальные размеры изображения
            val maxWidth = 1024  // Задай нужные значения, например половину экрана планшета
            val maxHeight = 1024

            // Проверяем, нужно ли уменьшать изображение
            val scaledBitmap = if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                scaleBitmap(
                    bitmap,
                    maxWidth,
                    maxHeight
                )
            } else {
                bitmap
            }

            val file = File(applicationContext.filesDir, fileName)
            var fileOutputStream: FileOutputStream? = null
            try {
                fileOutputStream = FileOutputStream(file)

                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)

                Toast.makeText(this, "Image saved to $fileName in Pictures", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                fileOutputStream?.close()
            }

            // Возвращаем короткий путь без полного пути к директории
            return fileName
        } else {
            // Если изображение не является BitmapDrawable, возвращаем пустую строку
            return ""
        }
    }

    // Функция для масштабирования изображения
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Вычисляем соотношение сторон
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight

        // Подгоняем размеры под максимальные параметры
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth / ratioBitmap).toInt()
        }

        // Возвращаем масштабированный Bitmap
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun getLanguageQuizByQuestions(): String {
        if (questionsEntity.isEmpty()) return ""

        val firstLanguage = questionsEntity.first().language
        val commonLanguage = questionsEntity.all { it.language == firstLanguage }

        return if (commonLanguage) firstLanguage else ""
    }


    private fun getCategoriesByLayout() = Triple(
        binding.tvCategory.text.toString(),
        binding.tvSubCategory.text.toString(),
        binding.tvSubsubCategory.text.toString()
    )

    private fun getUserName(): String {
        return settingsConfig.name
    }

    private fun getDataQuiz(): String {
        val unixTime = System.currentTimeMillis() / 1000
        return unixTime.toString()
    }

    private fun setNewCounterAndShortList(isInit: Boolean = false) {
        if (isInit) {
            counter = 0
            questionsShortEntity = arrayListOf(
                QuestionShortEntity(
                    id = null,
                    numQuestion = 1,
                    nameQuestion = "New Question",
                    hardQuestion = false
                )
            )
            return
        }
        val questionItemThis = questionsShortEntity[counter]
        val isHardQuestion = questionItemThis.hardQuestion

        // Ищем отсутствующий номер вопроса
        val missingNumber = findMissingNumber(isHardQuestion)

        val newNumQuestion =
            missingNumber ?: ((questionsShortEntity.maxOfOrNull { it.numQuestion } ?: 1) + 1)

        // Создаем новый элемент
        val newQuestionItem = QuestionShortEntity(
            id = -1,
            numQuestion = newNumQuestion,
            nameQuestion = "New Question",
            hardQuestion = isHardQuestion
        )

        // Добавляем новый элемент в нужную позицию и обновляем счетчик
        if (missingNumber != null) {
            val insertPosition =
                questionsShortEntity.indexOfFirst { it.numQuestion > newNumQuestion }
            if (insertPosition >= 0) {
                questionsShortEntity.add(insertPosition, newQuestionItem)
                counter = insertPosition
            } else {
                questionsShortEntity.add(newQuestionItem)
                counter = questionsShortEntity.size - 1
            }
        } else {
            questionsShortEntity.add(newQuestionItem)
            counter = questionsShortEntity.size - 1
        }
    }

    private fun findMissingNumber(isHardQuestion: Boolean): Int {
        val relevantQuestions = questionsShortEntity.filter { it.hardQuestion == isHardQuestion }
        val maxNumQuestion = relevantQuestions.maxOfOrNull { it.numQuestion } ?: 0
        return maxNumQuestion + 1
    }

    private fun pickImageFromGallery(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            when (requestCode) {
                PICK_IMAGE_CATEGORY -> binding.imvCategory.setImageURI(imageUri)
                PICK_IMAGE_SUBCATEGORY -> binding.imvSubcategory.setImageURI(imageUri)
                PICK_IMAGE_SUBSUBCATEGORY -> binding.imvSubsubcategory.setImageURI(imageUri)
                PICK_IMAGE_QUIZ -> binding.imvQuiz.setImageURI(imageUri)
                PICK_IMAGE_QUESTION -> binding.imvQuestion.setImageURI(imageUri)
            }
        }
    }

    private fun getUserLanguage(): String {
        return Locale.getDefault().language
    }

    private fun initView() {
        if (questionsEntity.isEmpty()) {
            setNewCounterAndShortList(true)
            Log.d("wadwad", "questionsEntity ?: ")
            setupQuestionSpinner()
            updateUiQuestion()
            setupUiQuiz()
            setSpinnersCategory()
        } else {
            setupQuestionSpinner()
            updateUiQuestion()
            quizEntity.let {
                setupUiQuiz()
                setSpinnersCategory()
            }
        }

    }

    private fun setupUiQuiz() = with(binding) {
        tvQuizName.setText(quizEntity?.nameQuiz)

        setCategorySpinnerListeners()

        val imagePath = quizEntity?.picture
        if (!imagePath.isNullOrEmpty()) {
            imvQuiz.setImageURI(Uri.parse(imagePath))
        }
    }

    private fun setSpinnersCategory() {
        Log.d("wadwad", "setSpinnersCategory")
        lifecycleScope.launch(Dispatchers.Default) {
            val structureData = viewModel.getStructureData()
            var selectedCategoryIndex = 0
            var selectedSubCategoryIndex = 0
            var selectedSubSubCategoryIndex = 0

            val categories = mutableListOf("Создать новую категорию")
            val subcategories = mutableListOf("Создать новую подкатегорию")
            val subSubcategories = mutableListOf("Создать новую под-субкатегорию")

            structureData?.event?.forEach { event ->
                event.category.forEachIndexed { catIndex, cat ->
                    categories.add(cat.nameQuiz)
                    if (cat.nameQuiz == category) {
                        if (!cat.picture.isNullOrEmpty()) {
                            binding.imvCategory.setImageURI(Uri.parse(cat.picture))
                        }
                        selectedCategoryIndex = catIndex + 1

                        cat.subcategory.forEachIndexed { subCatIndex, subCat ->
                            subcategories.add(subCat.nameQuiz)
                            if (subCat.nameQuiz == subCategory) {
                                if (!subCat.picture.isNullOrEmpty()) {
                                    binding.imvSubcategory.setImageURI(Uri.parse(subCat.picture))
                                }
                                selectedSubCategoryIndex = subCatIndex + 1

                                subCat.subSubcategory.forEachIndexed { subSubCatIndex, subSubCat ->
                                    subSubcategories.add(subSubCat.nameQuiz)
                                    if (subSubCat.nameQuiz == subsubCategory) {
                                        if (!subSubCat.picture.isNullOrEmpty()) {
                                            binding.imvSubsubcategory.setImageURI(
                                                Uri.parse(
                                                    subSubCat.picture
                                                )
                                            )
                                        }
                                        selectedSubSubCategoryIndex = subSubCatIndex + 1
                                    }
                                }
                            }
                        }
                    }
                }
            }

            binding.tvCategory.setText(category)
            binding.tvSubCategory.setText(subCategory)
            binding.tvSubsubCategory.setText(subsubCategory)

            // Устанавливаем адаптеры для спиннеров
            val categoryAdapter =
                ArrayAdapter(
                    this@CreateQuizActivity,
                    android.R.layout.simple_spinner_item,
                    categories
                )
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spCategory.adapter = categoryAdapter

            val subCategoryAdapter =
                ArrayAdapter(
                    this@CreateQuizActivity,
                    android.R.layout.simple_spinner_item,
                    subcategories
                )
            subCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spSubCategory.adapter = subCategoryAdapter

            val subSubCategoryAdapter =
                ArrayAdapter(
                    this@CreateQuizActivity,
                    android.R.layout.simple_spinner_item,
                    subSubcategories
                )
            subSubCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spSubsubCategory.adapter = subSubCategoryAdapter

            // Устанавливаем выбранные элементы в спиннерах
            binding.spCategory.setSelection(selectedCategoryIndex)
            binding.spSubCategory.setSelection(selectedSubCategoryIndex)
            binding.spSubsubCategory.setSelection(selectedSubSubCategoryIndex)

        }
    }

    private fun setCategorySpinnerListeners() {
        binding.spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                (view as? TextView)?.setTextColor(Color.GRAY)
                val selectedCategory = parent.getItemAtPosition(position) as String
                if (selectedCategory == "Создать новую категорию") {
                    createNewCategory()
                } else {
                    category = selectedCategory
                    filledTVCategory()
                    setSpinnersCategory()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.spSubCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                (view as? TextView)?.setTextColor(Color.GRAY)
                val selectedSubCategory = parent.getItemAtPosition(position) as String
                if (selectedSubCategory == "Создать новую подкатегорию") {
                    createNewSubCategory()
                } else {
                    subCategory = selectedSubCategory
                    filledTVCategory()
                    setSpinnersCategory()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.spSubsubCategory.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    (view as? TextView)?.setTextColor(Color.GRAY)
                    val selectedSubSubCategory = parent.getItemAtPosition(position) as String
                    if (selectedSubSubCategory == "Создать новую под-субкатегорию") {
                        createNewSubSubCategory()
                    } else {
                        subsubCategory = selectedSubSubCategory
                        filledTVCategory()
                        setSpinnersCategory()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun createNewCategory() {
        category = ""
        subCategory = ""
        subsubCategory = ""

        binding.imvCategory.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)
        binding.imvSubcategory.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)
        binding.imvSubsubcategory.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        showCreateNewCategory()
    }

    private fun createNewSubCategory() {
        subCategory = ""
        subsubCategory = ""

        binding.imvSubcategory.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)
        binding.imvSubsubcategory.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        showCreateNewCategory()
    }

    private fun createNewSubSubCategory() {
        subsubCategory = ""
        binding.imvSubsubcategory.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)
        showCreateNewCategory()
    }

    private fun showCreateNewCategory() {
        binding.llCreateNewCategory.visibility = View.VISIBLE
        filledTVCategory()
    }

    private fun filledTVCategory() {
        binding.tvCategory.setText(category)
        binding.tvSubCategory.setText(subCategory)
        binding.tvSubsubCategory.setText(subsubCategory)
    }

    private fun addQuestionToLayout(questionText: String? = null, language: String? = null) {
        idGroup += 1
        val questionLayout = LayoutInflater.from(this).inflate(
            com.tpov.schoolquiz.R.layout.item_create_quiz_question,
            binding.llQuestions,
            false
        ) as LinearLayout

        val questionTextView: EditText =
            questionLayout.findViewById(com.tpov.schoolquiz.R.id.tv_question_text1)
        val languageSpinner: Spinner =
            questionLayout.findViewById(com.tpov.schoolquiz.R.id.sp_language_question1)

        questionTextView.setText(questionText ?: "")

        val languagesFullNames = LanguageUtils.languagesFullNames
        val languagesShortCodes = LanguageUtils.languagesShortCodes

        val languagesAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languagesFullNames
        )
        languagesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = languagesAdapter

        val selectedLanguageIndex = language?.let { languagesShortCodes.indexOf(it) } ?: -1

        if (selectedLanguageIndex != -1) {
            languageSpinner.setSelection(selectedLanguageIndex)
        }

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            var isSpinnerInitialized = false
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                (view as? TextView)?.setTextColor(Color.GRAY)
                (view as? TextView)?.text = languagesShortCodes[position]
                Log.d(
                    "QuizApp",
                    "languageSpinner.onItemSelectedListener isSpinnerInitialized: $isSpinnerInitialized"
                )
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true
                } else addOrUpdateAnswerGroup(
                    binding.llQuestions.indexOfChild(questionLayout) + 1,
                    languagesShortCodes[position]
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        questionTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty() && s.last() == ' ') {
                    val textBeforeSpace = s.substring(0, s.length - 1)
                    val detectedLanguage = determineLanguage(textBeforeSpace)
                    val detectedLanguageIndex = languagesShortCodes.indexOf(detectedLanguage)
                    if (detectedLanguageIndex != -1) {
                        languageSpinner.setSelection(detectedLanguageIndex)
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.llQuestions.addView(questionLayout)
    }

    private fun saveThisNumberQuestion() {
        val numQuestionThis = questionsShortEntity[counter].numQuestion
        val hardQuestionThis = binding.chbTypeQuestion.isChecked
        val newQuestionEntity = questionsEntity.filter {
            it.numQuestion != numQuestionThis || it.hardQuestion != hardQuestionThis
        }

        questionsEntity = newQuestionEntity as ArrayList<QuestionEntity>

        val newQuestions = getAllQuestionsAndLanguages()
        val newAnswers = getThisAnswers()

        if (newAnswers.size != newQuestions.size) errorCountLanguage()
        else {
            if (questionsShortEntity[counter].hardQuestion != hardQuestionThis) {
                questionsEntity = questionsEntity.filter {
                    it.numQuestion != numQuestionThis || it.hardQuestion == hardQuestionThis
                } as ArrayList<QuestionEntity>

                questionsShortEntity[counter].id = -1
                Toast.makeText(
                    this,
                    "${if (!hardQuestionThis) "Сложние" else "Легкие"} вопроси сдвинулись",
                    Toast.LENGTH_LONG
                ).show()
            }

            val isNewQuestion = questionsShortEntity[counter].id == -1
            var thisNumQuestion: Int = if (isNewQuestion) questionsEntity
                .filter { it.hardQuestion == hardQuestionThis }
                .maxOfOrNull { it.numQuestion } ?: 0
            else questionsShortEntity[counter].numQuestion

            if (isNewQuestion) thisNumQuestion += 1
            val pathPicture = viewModel.getPathPicture()
            newQuestions.forEach { newQuestion ->

                val filterAnswer = newAnswers.filter { it.first == newQuestion.second }
                if (filterAnswer.isNotEmpty()) {
                    val answer = filterAnswer[0]
                    Log.d(
                        "saveThisNumberQuestion",
                        "question: $thisNumQuestion. ${newQuestion.first}"
                    )

                    questionsEntity?.add(
                        QuestionEntity(
                            null,
                            thisNumQuestion,
                            newQuestion.first,
                            saveImage(binding.imvQuestion, viewModel.getPathPicture()),
                            answer.third,
                            answer.second,
                            hardQuestionThis,
                            idQuiz,
                            newQuestion.second,
                            lvlTranslate
                        )
                    )
                }
            }
            fillMissingQuestionNumbersByHardQuestion()
            questionsShortEntity =
                viewModel.getQuestionListShortEntity(questionsEntity, getUserLanguage())
            idCounters = mutableListOf(mutableListOf())
        }
    }

    private fun fillMissingQuestionNumbersByHardQuestion() {
        val groupedByHardQuestion = questionsEntity.groupBy { it.hardQuestion }

        groupedByHardQuestion.forEach { (hardQuestion, questions) ->
            val sortedQuestions = questions.sortedBy { it.numQuestion }
            var expectedNumber = 1

            sortedQuestions.forEachIndexed { index, element ->
                if (element.numQuestion > expectedNumber) {
                    val missingNumber = expectedNumber

                    for (i in index until sortedQuestions.size) {
                        sortedQuestions[i].numQuestion -= 1
                    }
                    expectedNumber = missingNumber
                } else {
                    expectedNumber++
                }
            }
        }
    }

    private fun getAllQuestionsAndLanguages(): List<Pair<String, String>> {
        val questionsAndLanguages = mutableListOf<Pair<String, String>>()
        val childCount = binding.llQuestions.childCount

        for (i in 0 until childCount) {
            val questionLayout = binding.llQuestions.getChildAt(i) as LinearLayout

            val questionTextView: EditText =
                questionLayout.findViewById(com.tpov.schoolquiz.R.id.tv_question_text1)
            val languageSpinner: Spinner =
                questionLayout.findViewById(com.tpov.schoolquiz.R.id.sp_language_question1)

            val questionText = questionTextView.text.toString()
            val selectedLanguageIndex = languageSpinner.selectedItemPosition
            val selectedLanguageCode = LanguageUtils.languagesShortCodes[selectedLanguageIndex]

            questionsAndLanguages.add(Pair(questionText, selectedLanguageCode))
        }

        return questionsAndLanguages
    }

    private fun getThisAnswers(): List<Triple<String, String, Int>> {
        val answersList = mutableListOf<Triple<String, String, Int>>()
        val childCount = binding.llGroupAnswer.childCount

        Log.d("getThisAnswers", "childCount: $childCount")
        for (i in 0 until childCount) {
            val answerLayout = binding.llGroupAnswer.getChildAt(i) as LinearLayout

            val answerLanguageTextView: TextView =
                answerLayout.findViewById(R.id.tv_answer_language)

            val answers = mutableListOf<String>()
            idCounters[i].forEach {
                answers.add(answerLayout.findViewById<EditText>(it).text.toString())
            }
            val language =
                LanguageUtils.getLanguageShortCode(answerLanguageTextView.text.toString())

            val answersString = answers.joinToString("|")

            Log.d("getThisAnswers", "language: $language")
            Log.d("getThisAnswers", "answersString: $answersString")
            Log.d("getThisAnswers", "correctAnswerIndex: ${idCounters[0]}")
            answersList.add(Triple(language, answersString, 1))
        }

        return answersList
    }

    private fun updateUiQuestion() = with(binding) {
        val numQuestionThis = questionsShortEntity[counter].numQuestion
        val hardQuestionThis = questionsShortEntity[counter].hardQuestion
        val questionEntitiesLanguage = questionsEntity.filter {
            it.numQuestion == numQuestionThis && it.hardQuestion == hardQuestionThis
        }

        llQuestions.removeAllViews()
        llGroupAnswer.removeAllViews()

        val imagePath =
            if (questionEntitiesLanguage.isNotEmpty()) questionEntitiesLanguage[0].pathPictureQuestion
            else ""
        if (!imagePath.isNullOrEmpty()) imvQuestion.setImageURI(Uri.parse(imagePath))
        else imvQuestion.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        chbTypeQuestion.isChecked = hardQuestionThis

        questionEntitiesLanguage?.forEachIndexed { indexLanguage, question ->
            idQuiz = question.idQuiz
            addQuestionToLayout(question.nameQuestion, question.language)

            val answers = question.nameAnswers.split("|").toMutableList()
            answers.add(0, answers.removeAt(question.answer - 1))

            answers.forEachIndexed { indexAnswer, answerText ->

                Log.d("getThisAnswers", "indexAnswer: ${indexAnswer}, answerText: ${answerText}")
                addOrUpdateAnswerGroup(
                    indexLanguage,
                    question.language,
                    true,
                    answerText,
                    indexAnswer
                )
            }
        }
    }

    private var idCounters: MutableList<MutableList<Int>> =
        mutableListOf(mutableListOf())

    private fun addOrUpdateAnswerGroup(
        groupNumber: Int,
        language: String? = null,
        addAnswers: Boolean? = null,
        answerText: String? = null,
        idAnswer: Int? = null
    ) {
        val existingGroup = binding.llGroupAnswer.findViewWithTag<View>("group_$groupNumber")
        Log.d(
            "getThisAnswers",
            "addOrUpdateAnswerGroup called with groupNumber: $groupNumber, language: $language, addAnswers: $addAnswers, answerText: $answerText, existingGroup: $existingGroup, idAnswer: $idAnswer"
        )
        if (existingGroup != null) {

            if (addAnswers == true) {
                for (i in 0 until binding.llGroupAnswer.childCount) {
                    val firstAnswerLayout = LayoutInflater.from(this).inflate(
                        R.layout.linear_layout_answer,
                        binding.llGroupAnswer.getChildAt(i) as LinearLayout,
                        false
                    ) as LinearLayout

                    val idGroupCounter = idCounters[i]
                    val newIdEdtAnswer = idAnswer ?: (idGroupCounter.last() + 1)

                    if (idAnswer != null) {
                        if (idGroupCounter.last() < idAnswer) {
                            idGroupCounter.add(newIdEdtAnswer)
                            (binding.llGroupAnswer.getChildAt(i) as LinearLayout).addView(
                                firstAnswerLayout
                            )
                            firstAnswerLayout.findViewById<EditText>(R.id.edt_answer).id =
                                newIdEdtAnswer
                            Log.d("getThisAnswers", "newIdEdtAnswer EditText: $newIdEdtAnswer")
                        }
                    } else {
                        idGroupCounter.add(newIdEdtAnswer)
                        (binding.llGroupAnswer.getChildAt(i) as LinearLayout).addView(
                            firstAnswerLayout
                        )
                        firstAnswerLayout.findViewById<EditText>(R.id.edt_answer).id =
                            newIdEdtAnswer
                        Log.d("getThisAnswers", "newIdEdtAnswer idAnswer: $newIdEdtAnswer")
                    }
                }
            }
            if (language != null) existingGroup.findViewById<TextView>(R.id.tv_answer_language).text =
                LanguageUtils.getLanguageFullName(language)
            if (answerText != null && idAnswer != null && answerText != "") existingGroup.findViewById<TextView>(
                idCounters[groupNumber][idAnswer]
            ).text =
                answerText

        } else {
            val inflater = LayoutInflater.from(this)

            val newGroup = inflater.inflate(
                com.tpov.schoolquiz.R.layout.item_create_quiz_answer,
                binding.llGroupAnswer,
                false
            ) as LinearLayout

            newGroup.tag = "group_$groupNumber"

            newGroup.findViewById<TextView>(R.id.tv_answer_language).text =
                LanguageUtils.getLanguageFullName(language ?: getUserLanguage())

            if (idCounters.isEmpty()) {
                idCounters.add(mutableListOf(0, 1))
            } else if (idCounters[0].isEmpty()) {
                idCounters[0] = mutableListOf(0, 1)
            }
            Log.d("getThisAnswers", "idCounters[counter][0]: ${idCounters[0]}")
            idCounters[0].forEachIndexed { index, it ->
                Log.d("wafsfe", "idAnswer: $idAnswer")
                val firstAnswerLayout = inflater.inflate(
                    R.layout.linear_layout_answer, newGroup, false
                ) as LinearLayout

                firstAnswerLayout.findViewById<EditText>(R.id.edt_answer).id = it

                Log.d("getThisAnswers", "idEdtAnswer: ${it}")
                val firstTextView = firstAnswerLayout.findViewById<EditText>(it)
                firstTextView?.setText(answerText ?: "")
                if (it == 0) firstTextView.setTextColor(
                    ContextCompat.getColor(this, R.color.back_main_green)
                )
                idCounters.add(idCounters[0])

                newGroup.addView(firstAnswerLayout)
            }

            binding.llGroupAnswer.addView(newGroup)
        }
    }

    private fun setGroupBackgroundColorAndTextColor(textView: TextView, isCorrect: Boolean?) {
        if (isCorrect == true) {
            // Установим зеленую полоску снизу
            textView.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    android.R.color.holo_green_light
                )
            )
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            // Установим прозрачный фон и стандартный цвет текста
            textView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        }
    }

    private fun findLayoutByLanguage(language: String): LinearLayout? {
        for (i in 0 until binding.llGroupAnswer.childCount) {
            val layout = binding.llGroupAnswer.getChildAt(i) as LinearLayout
            val languageTextView: TextView =
                layout.findViewById(R.id.tv_answer_language)
            if (languageTextView.text == LanguageUtils.getLanguageFullName(language)) {
                return layout
            }
        }
        return null
    }

    private fun determineCorrectAnswer(): Int {
        return 0
    }

    private fun saveQuiz() = lifecycleScope.launch(Dispatchers.Default) {
        questionsEntity.let { questionsIt ->
            quizEntity?.let { quizIt ->
                viewModel.insertQuizThis(structureCategoryDataEntity, quizIt, questionsIt)
                finish()
            } ?: saveTranslate()
        }
    }

    private fun getFilteredAndSortedQuestions(): List<String> {
        return questionsEntity?.filter { it.language == getUserLanguage() }
            ?.sortedBy { it.numQuestion }
            ?.sortedBy { !it.hardQuestion }
            ?.map { "${it.numQuestion}. ${it.nameQuestion}" }
            ?: listOf()
    }

    private fun saveTranslate() {

    }


    private fun determineLanguage(textBeforeSpace: String): String {
        return getUserLanguage()
    }

    private fun errorLoadQuestionEntity(): List<QuestionEntity> {
        return null!!
    }

    private fun errorCountLanguage() {

    }

    companion object {
        private const val ARG_QUIZ = "arg_quiz"
        private const val ARG_QUESTION = "arg_question"

        fun newInstance(
            context: Context,
            quiz: QuizEntity? = null,
            questions: List<QuestionEntity>? = null
        ): Intent {
            return Intent(context, CreateQuizActivity::class.java).apply {
                putExtra(ARG_QUIZ, quiz)
                putParcelableArrayListExtra(ARG_QUESTION, ArrayList(questions))
            }
        }
    }
}

