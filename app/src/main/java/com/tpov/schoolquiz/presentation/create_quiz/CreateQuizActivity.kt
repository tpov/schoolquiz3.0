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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.EVENT_QUIZ_HOME
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.presentation.utils.DataUtil
import com.tpov.common.presentation.utils.NamesUtils
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: CreateQuizViewModel

    private val PICK_IMAGE_CATEGORY = 1001
    private val PICK_IMAGE_SUBCATEGORY = 1002
    private val PICK_IMAGE_SUBSUBCATEGORY = 1003
    private val PICK_IMAGE_QUIZ = 1004
    private val PICK_IMAGE_QUESTION = 1005

    private lateinit var binding: ActivityCreateQuizBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        (application as MainApp).applicationComponent.inject(this)
        binding = ActivityCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent?.let {
            viewModel = ViewModelProvider(this, viewModelFactory)[CreateQuizViewModel::class.java]
        }

        viewModel.quizEntity = intent.getParcelableExtra(ARG_QUIZ)
        viewModel.questionsEntity =
            intent.getParcelableArrayListExtra(ARG_QUESTION) ?: arrayListOf()
        viewModel.regime = intent.getIntExtra(REGIME, viewModel.regime)
        viewModel.questionsShortEntity =
            viewModel.getQuestionListShortEntity(
                viewModel.questionsEntity,
                viewModel.getUserLanguage()
            )
        initView()
        initSetOnClickListeners()
    }

    private fun setupQuestionSpinner() = with(binding) {
        updateQuestionsAdapter(spNumQuestion)

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
                    if (position == viewModel.questionsShortEntity.size - 1) {
                        getThisQuestionWithUI()
                        viewModel.updateNewCounterAndShortList()
                        viewModel.idGroup = 0
                        updateUiQuestion()
                        initSp = false
                        updateQuestionsAdapter(spNumQuestion)
                        initNewTranslateViews()
                    } else {
                        getThisQuestionWithUI()
                        viewModel.counter = position
                        viewModel.idGroup = 0
                        updateUiQuestion()
                        initSp = false
                        updateQuestionsAdapter(spNumQuestion)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateQuestionsAdapter(spNumQuestion: Spinner) {
        val newAdapter =
            CustomSpinnerAdapter(this@CreateQuizActivity, viewModel.questionsShortEntity)
        spNumQuestion.adapter = newAdapter
        spNumQuestion.setSelection(viewModel.counter)
    }

    private fun initNewTranslateViews() {
        addQuestionToLayout("", viewModel.getUserLanguage())
        addOrUpdateAnswerGroup(viewModel.idGroup, viewModel.getUserLanguage(), false, "")
    }

    private fun initSetOnClickListeners() = with(binding) {

        bSave.setOnClickListener {
            getThisQuestionWithUI()
            getThisQuizWithUI()
            getStructureCategoryWithUI(viewModel.quizEntity?.event ?: 1)
            saveQuiz()
        }
        bCencel.setOnClickListener {
            finish()
        }
        bAddTranslate.setOnClickListener {
            addQuestionToLayout("", viewModel.getUserLanguage())
            addOrUpdateAnswerGroup(viewModel.idGroup, viewModel.getUserLanguage(), false, "")
        }
        bAddAnswer.setOnClickListener {
            addOrUpdateAnswerGroup(1, viewModel.getUserLanguage(), true)
        }

        imvCategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_CATEGORY) }
        imvSubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBCATEGORY) }
        imvSubsubcategory.setOnClickListener { pickImageFromGallery(PICK_IMAGE_SUBSUBCATEGORY) }
        imvQuiz.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUIZ) }
        imvQuestion.setOnClickListener { pickImageFromGallery(PICK_IMAGE_QUESTION) }
    }

    private fun getThisQuizWithUI(): QuizEntity? = with(binding) {
        val pathPicture = NamesUtils().getPathPicture()
        viewModel.quizEntity = (viewModel.quizEntity ?: QuizEntity()).copy(
            idCategory = viewModel.getNewCategory().first,
            idSubcategory = viewModel.getNewCategory().second,
            idSubsubcategory = viewModel.getNewCategory().third,
            nameQuiz = tvQuizName.text.toString(),
            userName = viewModel.getUserName(),
            dataUpdate = DataUtil().getDataQuiz(),
            numQ = viewModel.questionsShortEntity.filter { !it.hardQuestion }.size,
            numHQ = viewModel.questionsShortEntity.filter { it.hardQuestion }.size,
            versionQuiz = viewModel.quizEntity?.versionQuiz?.plus(1) ?: QuizEntity().versionQuiz,
            picture = pathPicture,
            languages = viewModel.getLanguageQuizByQuestions(),
        )
        viewModel.scaledANDSaveImage(imvQuiz, pathPicture)
        return viewModel.quizEntity
    }

    private var structureCategoryDataEntity = StructureCategoryDataEntity()

    private fun getStructureCategoryWithUI(newEvent: Int) {
        val nameCategory = NamesUtils().getPathPicture()
        val nameSubCategory = NamesUtils().getPathPicture()
        val nameSubsubCategory = NamesUtils().getPathPicture()

        viewModel.scaledANDSaveImage(binding.imvCategory, nameCategory)
        viewModel.scaledANDSaveImage(binding.imvSubcategory, nameSubCategory)
        viewModel.scaledANDSaveImage(binding.imvSubsubcategory, nameSubsubCategory)

        structureCategoryDataEntity = StructureCategoryDataEntity().initCreateQuizActivity(
            viewModel.quizEntity!!,
            getCategoriesWithLayout(),
            newEvent,
            nameCategory,
            nameSubCategory,
            nameSubsubCategory,
            binding.tvQuizName.text.toString()
        )
    }

    private fun getCategoriesWithLayout() = Triple(
        binding.tvCategory.text.toString(),
        binding.tvSubCategory.text.toString(),
        binding.tvSubsubCategory.text.toString()
    )

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

    private fun initView() {
        when (viewModel.regime) {
            REGIME_CREATE_QUIZ -> {
                viewModel.updateNewCounterAndShortList(true)
                setupQuestionSpinner()
                updateUiQuestion()
                setupUiQuiz()
                initNewTranslateViews()

            }

            REGIME_EDIT_QUIZ -> {
                setupQuestionSpinner()
                updateUiQuestion()
                setupUiQuiz()

                binding.bSave.text = "Update"
            }

            REGIME_EDIT_ARCHIVE_MY_QUIZ -> {
                setupQuestionSpinner()
                updateUiQuestion()
                setupUiQuiz()

                binding.bSave.text = "Update in archive"
            }

            REGIME_EDIT_ARCHIVE_QUIZ -> {
                setupQuestionSpinner()
                updateUiQuestion()

                binding.bSave.text = "Update in archive"
                hideQuizViews()
            }

            REGIME_TRANSLATE_QUIZ -> {
                setupQuestionSpinner()
                updateUiQuestion()

                hideQuizViews()
                hideTopQuestionViews()
                binding.bSave.text = "Save"

            }
        }
    }

    private fun hideTopQuestionViews() = with(binding) {
        imvQuestion.visibility = View.GONE
        spNumQuestion.visibility = View.GONE
        chbTypeQuestion.visibility = View.GONE
    }

    private fun hideQuizViews() = with(binding) {
        tvQuizName.visibility = View.GONE
        imvQuiz.visibility = View.GONE
        spCategory.visibility = View.GONE
        spSubCategory.visibility = View.GONE
        spSubsubCategory.visibility = View.GONE
        stroce.visibility = View.GONE
    }

    private fun setupUiQuiz() = with(binding) {
        tvQuizName.setText(viewModel.quizEntity?.nameQuiz)

        setCategorySpinnerListeners()
        initCategorySpinnerAdapter()

        val imagePath = viewModel.quizEntity?.picture
        if (!imagePath.isNullOrEmpty()) {
            imvQuiz.setImageURI(Uri.parse(imagePath))
        }
    }

    var categories = mutableListOf("Создать новую категорию")
    var subcategories = mutableListOf("Создать новую подкатегорию")
    var subSubcategories = mutableListOf("Создать новую под-субкатегорию")
    private fun fulledDataSpinnersCategory(position: Int, idTree: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val structureData = viewModel.getStructureData()

            categories = mutableListOf("Создать новую категорию")
            structureData?.event?.filter { it.id == EVENT_QUIZ_HOME }?.get(0)
                ?.category?.forEachIndexed { catIndex, cat ->
                    categories.add(cat.nameQuiz)
                }

            when (idTree) {
                1 -> {
                    subcategories = mutableListOf("Создать новую подкатегорию")
                    structureData?.event?.filter { it.id == EVENT_QUIZ_HOME }?.get(0)
                        ?.category?.get(position)?.subcategory?.forEachIndexed { catIndex, cat ->
                            subcategories.add(cat.nameQuiz)
                        }
                }

                2 -> {
                    structureData?.event?.filter { it.id == EVENT_QUIZ_HOME }?.getOrNull(0)
                        ?.category?.filter { it.nameQuiz == viewModel.subCategory }
                        ?.getOrNull(0)?.subcategory?.getOrNull(position)?.subSubcategory?.let { subSubcatList ->
                            subSubcatList.forEachIndexed { catIndex, subsubCat ->
                                subSubcategories.add(subsubCat.nameQuiz)
                            }
                        }
                }
            }
        }
Log.d("dfgdfgdf","position: $position, idTree: $idTree")
Log.d("dfgdfgdf","categories: $categories")
Log.d("dfgdfgdf","subcategories: $subcategories")
Log.d("dfgdfgdf","subSubcategories: $subSubcategories")
Log.d("dfgdfgdf","category: ${viewModel.category}")
Log.d("dfgdfgdf","subCategory: ${viewModel.subCategory}")
Log.d("dfgdfgdf","subsubCategory: ${viewModel.subsubCategory}")
        when (idTree) {
            1 -> {
                binding.tvCategory.setText(viewModel.category)
                binding.spCategory.setSelection(position)
            }

            2 -> {
                binding.tvSubCategory.setText(viewModel.subCategory)
                binding.spSubCategory.setSelection(position)
            }

            3 -> {
                binding.tvSubsubCategory.setText(viewModel.subsubCategory)
                binding.spSubsubCategory.setSelection(position)
            }
        }
    }

    private fun initCategorySpinnerAdapter() {
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
    }

    private fun setCategorySpinnerListeners() {
        Log.d("dfgdfgdf","setCategorySpinnerListeners()")
        binding.spCategory.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    Log.d("dfgdfgdf","onItemSelectedListener")
                    if (viewModel.isInitialSetupCategorySpinner) {
                        Log.d("dfgdfgdf","isInitialSetupCategorySpinner")
                        fulledDataSpinnersCategory(categories.size - 1, 1)
                    } else {
                        (view as? TextView)?.setTextColor(Color.GRAY)
                        val selectedCategory = parent.getItemAtPosition(position) as String
                        if (position == 1) {
                            showCreateNewCategory()
                        } else {
                            viewModel.category = selectedCategory
                            fulledDataSpinnersCategory(position, 1)
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    Log.d("dfgdfgdf","onNothingSelected")}
            }

        binding.spSubCategory.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (viewModel.isInitialSetupCategorySpinner) {
                        fulledDataSpinnersCategory(subcategories.size - 1, 2)

                    } else {
                        (view as? TextView)?.setTextColor(Color.GRAY)
                        val selectedSubCategory = parent.getItemAtPosition(position) as String
                        if (position == 1) {
                            showCreateNewCategory()
                        } else {
                            viewModel.subCategory = selectedSubCategory
                            fulledDataSpinnersCategory(position, 2)
                        }
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
                    if (viewModel.isInitialSetupCategorySpinner) {
                        fulledDataSpinnersCategory(subSubcategories.size - 1, 3)

                    } else {
                        (view as? TextView)?.setTextColor(Color.GRAY)
                        val selectedSubSubCategory = parent.getItemAtPosition(position) as String
                        if (position == 1) {
                            showCreateNewCategory()
                        } else {
                            viewModel.subsubCategory = selectedSubSubCategory
                            fulledDataSpinnersCategory(position, 3)
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        viewModel.isInitialSetupCategorySpinner = false
    }

    private fun showCreateNewCategory() {
        binding.llCreateNewCategory.visibility = View.VISIBLE
        filledTVCategory()
    }

    private fun filledTVCategory() {
        binding.tvCategory.setText(viewModel.category)
        binding.tvSubCategory.setText(viewModel.subCategory)
        binding.tvSubsubCategory.setText(viewModel.subsubCategory)
    }

    private fun addQuestionToLayout(questionText: String? = null, language: String? = null) {
        viewModel.idGroup += 1
        val questionLayout = LayoutInflater.from(this).inflate(
            R.layout.item_create_quiz_question,
            binding.llQuestions,
            false
        ) as LinearLayout

        val questionTextView: EditText =
            questionLayout.findViewById(R.id.tv_question_text1)
        val languageSpinner: Spinner =
            questionLayout.findViewById(R.id.sp_language_question1)

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
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty() && s.last() == ' ') {
                    val textBeforeSpace = s.substring(0, s.length - 1)
                    val detectedLanguage = viewModel.determineLanguage(textBeforeSpace)
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

    private fun getThisQuestionWithUI() {
        val numQuestionThis = viewModel.questionsShortEntity[viewModel.counter].numQuestion
        val hardQuestionThis = binding.chbTypeQuestion.isChecked
        val newQuestionEntity = viewModel.questionsEntity.filter {
            it.numQuestion != numQuestionThis || it.hardQuestion != hardQuestionThis
        }

        viewModel.questionsEntity = newQuestionEntity as ArrayList<QuestionEntity>

        val newQuestions = viewModel.getAllQuestionsAndLanguagesWithUI(binding.llQuestions)
        val newAnswers = viewModel.getAnswersWithUI(binding.llGroupAnswer, idCounters)

        if (newAnswers.size != newQuestions.size) viewModel.errorCountLanguage()
        else {
            if (viewModel.questionsShortEntity[viewModel.counter].hardQuestion != hardQuestionThis) {
                viewModel.questionsEntity = viewModel.questionsEntity.filter {
                    it.numQuestion != numQuestionThis || it.hardQuestion == hardQuestionThis
                } as ArrayList<QuestionEntity>

                viewModel.questionsShortEntity[viewModel.counter].id = -1
                Toast.makeText(
                    this,
                    "${if (!hardQuestionThis) "Сложние" else "Легкие"} вопроси сдвинулись",
                    Toast.LENGTH_LONG
                ).show()
            }

            val isNewQuestion = viewModel.questionsShortEntity[viewModel.counter].id == -1
            var thisNumQuestion: Int = if (isNewQuestion) viewModel.questionsEntity
                .filter { it.hardQuestion == hardQuestionThis }
                .maxOfOrNull { it.numQuestion } ?: 0
            else viewModel.questionsShortEntity[viewModel.counter].numQuestion

            if (isNewQuestion) thisNumQuestion += 1
            newQuestions.forEach { newQuestion ->

                val filterAnswer = newAnswers.filter { it.first == newQuestion.second }
                if (filterAnswer.isNotEmpty()) {
                    val answer = filterAnswer[0]
                    val pathPictureQuestion = NamesUtils().getPathPicture()
                    viewModel.scaledANDSaveImage(binding.imvQuestion, pathPictureQuestion)
                    viewModel.questionsEntity?.add(
                        QuestionEntity(
                            null,
                            thisNumQuestion,
                            newQuestion.first,
                            pathPictureQuestion,
                            answer.third,
                            answer.second,
                            hardQuestionThis,
                            viewModel.idQuiz,
                            newQuestion.second,
                            viewModel.lvlTranslate
                        )
                    )
                }
            }

            val currentQuestion = viewModel.questionsShortEntity[viewModel.counter]
            viewModel.questionsShortEntity =
                viewModel.getQuestionListShortEntity(
                    viewModel.questionsEntity,
                    viewModel.getUserLanguage()
                )
            viewModel.counter =
                viewModel.questionsShortEntity.indexOfFirst { it.nameQuestion == currentQuestion.nameQuestion }
            if (viewModel.counter == -1) viewModel.counter = 0

            idCounters = mutableListOf(mutableListOf())
        }
    }


    private fun updateUiQuestion() = with(binding) {
        val numQuestionThis = viewModel.questionsShortEntity[viewModel.counter].numQuestion
        val hardQuestionThis = viewModel.questionsShortEntity[viewModel.counter].hardQuestion
        val questionEntitiesLanguage = viewModel.questionsEntity.filter {
            it.numQuestion == numQuestionThis && it.hardQuestion == hardQuestionThis
        }

        llQuestions.removeAllViews()
        llGroupAnswer.removeAllViews()

        val imagePath = if (questionEntitiesLanguage.isNotEmpty())
            questionEntitiesLanguage[0].pathPictureQuestion
        else ""
        if (!imagePath.isNullOrEmpty()) imvQuestion.setImageURI(Uri.parse(imagePath))
        else imvQuestion.setImageResource(R.drawable.ic_upload)

        chbTypeQuestion.isChecked = hardQuestionThis

        questionEntitiesLanguage.forEachIndexed { indexLanguage, question ->
            viewModel.idQuiz = question.idQuiz
            addQuestionToLayout(question.nameQuestion, question.language)

            val answers = question.nameAnswers.split("|").toMutableList()
            answers.add(0, answers.removeAt(question.answer - 1))

            answers.forEachIndexed { indexAnswer, answerText ->

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
                R.layout.item_create_quiz_answer,
                binding.llGroupAnswer,
                false
            ) as LinearLayout

            newGroup.tag = "group_$groupNumber"

            newGroup.findViewById<TextView>(R.id.tv_answer_language).text =
                LanguageUtils.getLanguageFullName(language ?: viewModel.getUserLanguage())

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


    private fun saveQuiz() = lifecycleScope.launch(Dispatchers.Default) {
        viewModel.questionsEntity.let { questionsIt ->
            viewModel.quizEntity?.let { quizIt ->
                viewModel.insertQuizThis(structureCategoryDataEntity, quizIt, questionsIt)
                finish()
            }
        }
    }

    companion object {
        private const val ARG_QUIZ = "arg_quiz"
        private const val ARG_QUESTION = "arg_question"
        private const val REGIME = "arg_regime"

        const val REGIME_CREATE_QUIZ = 1
        const val REGIME_EDIT_QUIZ = 2
        const val REGIME_EDIT_ARCHIVE_MY_QUIZ = 3
        const val REGIME_EDIT_ARCHIVE_QUIZ = 4
        const val REGIME_TRANSLATE_QUIZ = 5

        fun newInstance(
            context: Context,
            quiz: QuizEntity? = null,
            questions: List<QuestionEntity>? = null,
            regime: Int
        ): Intent {
            return Intent(context, CreateQuizActivity::class.java).apply {
                putExtra(ARG_QUIZ, quiz)
                putParcelableArrayListExtra(ARG_QUESTION, questions?.let { ArrayList(it) })
                putExtra(REGIME, regime)
            }
        }
    }
}

