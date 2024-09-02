package com.tpov.schoolquiz.presentation.create_quiz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.main.MainViewModel
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

class CreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: MainViewModel
    private var idQuiz = -1
    private var lvlTranslate = 0
    private var counter = 0

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
        val adapter: CustomSpinnerAdapter = CustomSpinnerAdapter(this@CreateQuizActivity, questionsShortEntity)
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
                    saveThisNumberQuestion(false)
                    counter = position
                    idGroup = 0
                    updateUiQuestion()

                    initSp = false
                    val newAdapter = CustomSpinnerAdapter(this@CreateQuizActivity, questionsShortEntity)
                    spNumQuestion.adapter = newAdapter
                    spNumQuestion.setSelection(counter)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun initSetOnClickListeners() = with(binding) {

        bSave.setOnClickListener {
            saveThisNumberQuestion()
            saveThisQuiz()
            saveQuiz()
        }
        bCencel.setOnClickListener {
            finish()
        }
        bAddQuestion.setOnClickListener {
            saveThisNumberQuestion()
            setNewCounterAndShortList()
            idGroup = 0
            setupQuestionSpinner()
            updateUiQuestion()
        }
        bAddTranslate.setOnClickListener {
            addQuestionToLayout("", getUserLanguage())
            addOrUpdateAnswerGroup(idGroup, getUserLanguage(), false, "")
        }
        bAddAnswer.setOnClickListener {
            addOrUpdateAnswerGroup(1, getUserLanguage(), true, "")
        }

        imvCategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_CATEGORY) }
        imvSubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBCATEGORY) }
        imvSubsubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBSUBCATEGORY) }
        imvQuiz.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUIZ) }
        imvQuestion.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUESTION) }

        setCategorySpinnerListeners()
    }

    private fun saveThisQuiz() {

    }
    private fun setNewCounterAndShortList(isInit: Boolean = false) {
        if (isInit) {
            // Инициализация списка
            counter = 0
            questionsShortEntity = arrayListOf(
                QuestionShortEntity(
                    id = 0,
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

        val newNumQuestion = missingNumber ?: ((questionsShortEntity.maxOfOrNull { it.numQuestion } ?: 1) + 1)

        // Создаем новый элемент
        val newQuestionItem = QuestionShortEntity(
            id = questionsShortEntity.size,
            numQuestion = newNumQuestion,
            nameQuestion = "New Question",
            hardQuestion = isHardQuestion
        )

        // Добавляем новый элемент в нужную позицию и обновляем счетчик
        if (missingNumber != null) {
            val insertPosition = questionsShortEntity.indexOfFirst { it.numQuestion > newNumQuestion }
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
                Log.d("QuizApp", "languageSpinner.onItemSelectedListener")
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true
                    return
                }
                addOrUpdateAnswerGroup(
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

    private fun saveThisNumberQuestion(isNewQuestion: Boolean = true) {
        val numQuestionThis = questionsShortEntity[counter].numQuestion
        val hardQuestionThis = questionsShortEntity[counter].hardQuestion
        val newQuestionEntity = questionsEntity.filter {
            it.numQuestion != numQuestionThis || it.hardQuestion != hardQuestionThis
        }

        questionsEntity = newQuestionEntity as ArrayList<QuestionEntity>

        val newQuestions = getAllQuestionsAndLanguages()
        val newAnswers = getThisAnswers()

        if (newAnswers.size != newQuestions.size) errorCountLanguage()
        else {

            val pathPicture = getPathPicture()
            newQuestions.forEach { newQuestion ->

                val filterAnswer = newAnswers.filter { it.first == newQuestion.second }
                if (filterAnswer.isNotEmpty()) {
                    val answer = filterAnswer[0]
                    val isHardQuestion = binding.chbTypeQuestion.isChecked

                    var thisNumQuestion: Int = if(isNewQuestion) questionsEntity
                        .filter { it.hardQuestion == isHardQuestion }
                        .maxOfOrNull { it.numQuestion } ?: 0
                    else questionsShortEntity[counter].numQuestion

                    if (questionsShortEntity[counter].hardQuestion != hardQuestionThis) {
                        val referenceElement = questionsShortEntity[counter]
                        val numberQuestionToRemove = referenceElement.numQuestion
                        val hardQuestionToRemove = referenceElement.hardQuestion

                        questionsEntity = questionsEntity.filterNot {
                            it.numQuestion == numberQuestionToRemove && it.hardQuestion == hardQuestionToRemove
                        }.toMutableList() as ArrayList<QuestionEntity>
                    }

                    Log.d("saveThisNumberQuestion", "questionsEntity: $questionsEntity")
                    Log.d("saveThisNumberQuestion", "questionsShortEntity: $questionsShortEntity")
                    Log.d("saveThisNumberQuestion", "newQuestion: $newQuestion")
                    Log.d("saveThisNumberQuestion", "thisNumQuestion: $thisNumQuestion")
                    Log.d("saveThisNumberQuestion", "isNewQuestion: $isNewQuestion")
                    if (isNewQuestion) thisNumQuestion += 1

                    questionsEntity?.add(
                        QuestionEntity(
                            null,
                            thisNumQuestion,
                            newQuestion.first,
                            pathPicture,
                            answer.third,
                            answer.second,
                            isHardQuestion,
                            idQuiz,
                            newQuestion.second,
                            lvlTranslate
                        )
                    )
                }
            }
            questionsShortEntity = viewModel.getQuestionListShortEntity(questionsEntity, getUserLanguage())
            Log.d("saveThisNumberQuestion", "newQuestionsShortEntity: $questionsShortEntity")
            Log.d("saveThisNumberQuestion", "newQuestionsEntity: $questionsEntity")
        }
    }

    private fun getPathPicture(): String {
        return ""
    }

    private fun getAllQuestionsAndLanguages(): List<Pair<String, String>> {
        val questionsAndLanguages = mutableListOf<Pair<String, String>>()
        val childCount = binding.llQuestions.childCount
        // Начинаем с последнего элемента и двигаемся к первому
        for (i in childCount - 1 downTo 0) {
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
            idCounters.asReversed().forEach {
                answers.add(answerLayout.findViewById<EditText>(it).text.toString())
            }
            val language =
                LanguageUtils.getLanguageShortCode(answerLanguageTextView.text.toString())

            val answersString = answers.joinToString("|")

            Log.d(
                "getThisAnswers",
                "answerLanguageTextView: $answerLanguageTextView.text.toString()"
            )
            Log.d("getThisAnswers", "language: $language")
            Log.d("getThisAnswers", "answersString: $answersString")
            Log.d("getThisAnswers", "correctAnswerIndex: ${idCounters[0]}")
            answersList.add(Triple(language, answersString, idCounters[0]))
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
            if (questionEntitiesLanguage.isNotEmpty()) questionEntitiesLanguage.get(0).pathPictureQuestion
            else ""
        if (!imagePath.isNullOrEmpty()) imvQuestion.setImageURI(Uri.parse(imagePath))
        else imvQuestion.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        chbTypeQuestion.isChecked = hardQuestionThis

        questionEntitiesLanguage?.forEachIndexed { indexLanguage, question ->
            idQuiz = question.idQuiz
            addQuestionToLayout(question.nameQuestion, question.language)

            val answers = question.nameAnswers.split("|").toMutableList()
            answers.add(0, answers.removeAt(question.answer))

            answers.forEachIndexed { indexAnswer, answerText ->
                addOrUpdateAnswerGroup(
                    indexLanguage,
                    question.language,
                    (indexAnswer > 2 && indexAnswer % 2 != 0),
                    answerText
                )
            }
        }
    }

    private val idCounters: MutableList<Int> = mutableListOf(1, 2)

    private fun addOrUpdateAnswerGroup(
        groupNumber: Int,
        language: String? = null,
        addAnswers: Boolean? = null,
        answerText: String? = null
    ) {
        val existingGroup = binding.llGroupAnswer.findViewWithTag<View>("group_$groupNumber")
        Log.d(
            "QuizApp",
            "addOrUpdateAnswerGroup called with groupNumber: $groupNumber, language: $language, addAnswers: $addAnswers, answerText: $answerText, existingGroup: $existingGroup"
        )
        if (existingGroup != null) {
            if (language != null) existingGroup.findViewById<TextView>(R.id.tv_answer_language).text =
                LanguageUtils.getLanguageFullName(language)
            if (answerText != null) existingGroup.findViewById<TextView>(idCounters.lastIndex).text =
                answerText

            if (addAnswers == true) {
                idCounters.add(idCounters.lastIndex + 1)
                for (i in 0 until binding.llGroupAnswer.childCount) {
                    val firstAnswerLayout = LayoutInflater.from(this).inflate(
                        R.layout.linear_layout_answer,
                        binding.llGroupAnswer.getChildAt(i) as LinearLayout,
                        true
                    ) as LinearLayout

                    firstAnswerLayout.findViewById<EditText>(R.id.edt_answer).id =
                        idCounters.lastIndex
                }
            }
        } else {
            val inflater = LayoutInflater.from(this)

// Сначала создаем View без добавления в иерархию
            val newGroup = inflater.inflate(
                com.tpov.schoolquiz.R.layout.item_create_quiz_answer,
                binding.llGroupAnswer,
                false
            ) as LinearLayout

// Присваиваем тег
            newGroup.tag = "group_$groupNumber"

// Устанавливаем язык
            newGroup.findViewById<TextView>(R.id.tv_answer_language).text =
                LanguageUtils.getLanguageFullName(language ?: getUserLanguage())

// Теперь выполняем оставшиеся операции
            idCounters.forEachIndexed { index, id ->
                val firstAnswerLayout = inflater.inflate(
                    R.layout.linear_layout_answer, newGroup, false
                ) as LinearLayout

                firstAnswerLayout.findViewById<EditText>(R.id.edt_answer).id = id

                val firstTextView = firstAnswerLayout.findViewById<EditText>(id)
                firstTextView?.setText(answerText ?: "")
                if (id == 1) firstTextView.setTextColor(
                    ContextCompat.getColor(this, R.color.back_main_green)
                )

                // Добавляем firstAnswerLayout в newGroup
                newGroup.addView(firstAnswerLayout)
            }

// Теперь добавляем newGroup в binding.llGroupAnswer
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

    private fun saveQuiz() = lifecycleScope.launch {
        quizEntity?.let {
            viewModel.insertQuiz(it)
            viewModel.insertQuestion(questionsEntity ?: errorLoadQuestionEntity())
            finish()
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

