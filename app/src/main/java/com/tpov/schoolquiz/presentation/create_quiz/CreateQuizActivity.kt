package com.tpov.schoolquiz.presentation.create_quiz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.main.MainViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: MainViewModel
    private var idQuiz = -1
    private var lvlTranslate = 0

    private val PICK_IMAGE_CATEGORY = 1001
    private val PICK_IMAGE_SUBCATEGORY = 1002
    private val PICK_IMAGE_SUBSUBCATEGORY = 1003
    private val PICK_IMAGE_QUIZ = 1004
    private val PICK_IMAGE_QUESTION = 1005

    private lateinit var binding: ActivityCreateQuizBinding

    private var questionsEntity: ArrayList<QuestionEntity>? = null
    private var quizEntity: QuizEntity? = null

    private var category: String = ""
    private var subCategory: String = ""
    private var subsubCategory: String = ""

    private var numQuestion = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as MainApp).applicationComponent.inject(this)
        binding = ActivityCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent?.let {
            idQuiz = it.getIntExtra(ARG_QUIZ, -1)
            viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
            viewModel.getQuizByIdQuiz(idQuiz)
            viewModel.getQuestionByIdQuiz(idQuiz)
        }

        quizEntity = intent.getParcelableExtra(ARG_QUIZ)
        questionsEntity = intent.getParcelableArrayListExtra(ARG_QUESTION)

        initView()
        initSetOnClickListeners()
    }

    private fun setupQuestionSpinner() = with(binding) {
        val questionForSpinner = questionsEntity
            ?.filter { it.language == getUserLanguage() }
            ?.sortedBy { it.numQuestion }
            ?.sortedBy { !it.hardQuestion }

        val adapter = questionForSpinner?.let {
            CustomSpinnerAdapter(this@CreateQuizActivity, it)
        }

        spNumQuestion.adapter = adapter
        spNumQuestion.setSelection(numQuestion - 1)
        spNumQuestion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                saveThisNumberQuestion()
                numQuestion = position + 1
                saveThisNumberQuestion()
                updateUiQuestion()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun initSetOnClickListeners() = with(binding) {

        bSave.setOnClickListener {
            saveThisNumberQuestion()
            saveQuiz()
        }

        imvCategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_CATEGORY) }
        imvSubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBCATEGORY) }
        imvSubsubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBSUBCATEGORY) }
        imvQuiz.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUIZ) }
        imvQuestion.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUESTION) }

        setCategorySpinnerListeners()
    }

    private fun pickImageFromGallery(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, requestCode)
    }

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
        return ""
    }

    private fun initView() {
        questionsEntity.let {

            numQuestion = 1
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
        lifecycleScope.launch {
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
                                            binding.imvSubsubcategory.setImageURI(Uri.parse(subSubCat.picture))
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
                ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, categories)
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spCategory.adapter = categoryAdapter

            val subCategoryAdapter =
                ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, subcategories)
            subCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spSubCategory.adapter = subCategoryAdapter

            val subSubCategoryAdapter =
                ArrayAdapter(this@CreateQuizActivity, android.R.layout.simple_spinner_item, subSubcategories)
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
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                (view as? TextView)?.text = languagesShortCodes[position]
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
        questionsEntity?.filter {
            it.numQuestion != numQuestion || it.hardQuestion != (numQuestion < 0)
        }

        val newQuestions = getAllQuestionsAndLanguages()
        val newAnswers = getThisAnswers()
        if (newAnswers != newQuestions) errorCountLanguage()
        else {
            val pathPicture = getPathPicture()
            newQuestions.forEach { newQuestion ->
                val filterAnswer = newAnswers.filter { it.first == newQuestion.second }[0]
                questionsEntity?.add(
                    QuestionEntity(
                        null,
                        numQuestion,
                        newQuestion.first,
                        pathPicture,
                        filterAnswer.third,
                        filterAnswer.second,
                        binding.chbTypeQuestion.isChecked,
                        idQuiz,
                        newQuestion.second,
                        lvlTranslate
                    )
                )
            }
        }
    }

    private fun getPathPicture(): String {
        return ""
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
        for (i in 0 until childCount) {
            val answerLayout = binding.llGroupAnswer.getChildAt(i) as LinearLayout

            val answerLanguageTextView: TextView =
                answerLayout.findViewById(com.tpov.schoolquiz.R.id.tv_answer_language)
            val leftAnswerEditText: EditText =
                answerLayout.findViewById(com.tpov.schoolquiz.R.id.edt_answer1)
            val rightAnswerEditText: EditText =
                answerLayout.findViewById(com.tpov.schoolquiz.R.id.edt_answer2)

            val language =
                LanguageUtils.getLanguageShortCode(answerLanguageTextView.text.toString())

            val answers = mutableListOf<String>()
            var correctAnswerIndex = -1

            if (leftAnswerEditText.text.isNotEmpty()) {
                answers.add(leftAnswerEditText.text.toString())
                if (leftAnswerEditText.background is ColorDrawable && (leftAnswerEditText.background as ColorDrawable).color == Color.GREEN) {
                    correctAnswerIndex = answers.size - 1
                }
            }

            if (rightAnswerEditText.text.isNotEmpty()) {
                answers.add(rightAnswerEditText.text.toString())
                if (rightAnswerEditText.background is ColorDrawable && (rightAnswerEditText.background as ColorDrawable).color == Color.GREEN) {
                    correctAnswerIndex = answers.size - 1
                }
            }

            for (j in 0 until answerLayout.childCount) {
                val child = answerLayout.getChildAt(j)
                if (child is EditText && child != leftAnswerEditText && child != rightAnswerEditText) {
                    if (child.text.isNotEmpty()) {
                        answers.add(child.text.toString())
                        if (child.background is ColorDrawable && (child.background as ColorDrawable).color == Color.GREEN) {
                            correctAnswerIndex = answers.size - 1
                        }
                    }
                }
            }

            val answersString = answers.joinToString("|")
            answersList.add(Triple(language, answersString, correctAnswerIndex))
        }

        return answersList
    }

    private fun updateUiQuestion() = with(binding) {
        val questionEntitiesLanguage = questionsEntity?.filter {
            it.numQuestion == numQuestion && it.hardQuestion == (numQuestion < 0)
        }

        val imagePath = questionEntitiesLanguage?.get(0)?.pathPictureQuestion
        if (!imagePath.isNullOrEmpty()) imvQuestion.setImageURI(Uri.parse(imagePath))
        else imvQuestion.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        chbTypeQuestion.isChecked = numQuestion < 0

        questionEntitiesLanguage?.forEachIndexed { indexLanguage, question ->
            idQuiz = question.idQuiz
            addQuestionToLayout(question.nameQuestion, question.language)

            val answers = question.nameAnswers.split("|")
            val correctAnswerIndex = question.answer

            answers.forEachIndexed { indexAnswer, answerText ->
                val isCorrect = indexAnswer == correctAnswerIndex
                addOrUpdateAnswerGroup(
                    indexLanguage,
                    question.language,
                    (indexAnswer > 2 && indexAnswer % 2 != 0),
                    isCorrect,
                    answerText
                )
            }
        }
    }

    private fun addOrUpdateAnswerGroup(
        groupNumber: Int,
        language: String,
        addAnswers: Boolean,
        isCorrect: Boolean,
        answerText: String
    ) {
        val existingGroup = binding.llGroupAnswer.findViewWithTag<View>("group_$groupNumber")

        if (existingGroup != null) {
            val languageTextView =
                existingGroup.findViewById<TextView>(com.tpov.schoolquiz.R.id.tv_answer_language)
            languageTextView.text = language

            if (addAnswers) {
                val answerContainer =
                    existingGroup.findViewById<LinearLayout>(com.tpov.schoolquiz.R.id.ll_answer)
                val inflater = LayoutInflater.from(this)
                val newAnswerView = inflater.inflate(
                    com.tpov.schoolquiz.R.layout.item_create_quiz_answer,
                    answerContainer,
                    false
                )

                val answerEditText =
                    newAnswerView.findViewById<EditText>(com.tpov.schoolquiz.R.id.edt_answer1)
                answerEditText.setText(answerText)

                answerContainer.addView(newAnswerView)
            }

            if (isCorrect) {
                existingGroup.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        android.R.color.holo_green_light
                    )
                )
            } else {
                existingGroup.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        android.R.color.transparent
                    )
                )
            }
        } else {
            val inflater = LayoutInflater.from(this)
            val newGroup = inflater.inflate(
                com.tpov.schoolquiz.R.layout.item_create_quiz_answer,
                binding.llGroupAnswer,
                false
            )

            val languageTextView =
                newGroup.findViewById<TextView>(com.tpov.schoolquiz.R.id.tv_answer_language)
            languageTextView.text = language

            val answerEditText =
                newGroup.findViewById<EditText>(com.tpov.schoolquiz.R.id.edt_answer1)
            answerEditText.setText(answerText)

            newGroup.tag = "group_$groupNumber"

            if (isCorrect) {
                newGroup.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        android.R.color.holo_green_light
                    )
                )
            }

            binding.llGroupAnswer.addView(newGroup)
        }
    }

    private fun findLayoutByLanguage(language: String): LinearLayout? {
        for (i in 0 until binding.llGroupAnswer.childCount) {
            val layout = binding.llGroupAnswer.getChildAt(i) as LinearLayout
            val languageTextView: TextView =
                layout.findViewById(com.tpov.schoolquiz.R.id.tv_answer_language)
            if (languageTextView.text == LanguageUtils.getLanguageFullName(language)) {
                return layout
            }
        }
        return null
    }

    private fun determineCorrectAnswer(): Int {
        return 0
    }

    private fun saveQuiz() = lifecycleScope.launch {
        quizEntity?.let {
            viewModel.insertQuiz(it)
            viewModel.insertQuestion(questionsEntity ?: errorLoadQuestionEntity())
        } ?: saveTranslate()
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
        return "ua"
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
