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
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.model.PathStructureName
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.NamesUtils
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityCreateQuizBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

open class CreateQuizActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    internal lateinit var viewModel: CreateQuizViewModel

    private val PICK_IMAGE_CATEGORY = 1001
    private val PICK_IMAGE_SUBCATEGORY = 1002
    private val PICK_IMAGE_SUBSUBCATEGORY = 1003
    private val PICK_IMAGE_QUIZ = 1004
    private val PICK_IMAGE_QUESTION = 1005

    var regime: Int = 0
    private var isFullscreen = false
    internal var isCreateCategory = false

    val handler by lazy { RegimeHandlerImpl(this).handler() }
    lateinit var binding: ActivityCreateQuizBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        (application as MainApp).applicationComponent.inject(this)
        binding = ActivityCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, viewModelFactory)[CreateQuizViewModel::class.java]
        regime = intent.getIntExtra(REGIME, regime)
        viewModel.pathStructure = intent.getParcelableExtra<PathStructure>(ARG_PATH_STRUCTURE)!!

        handler.initData()
        handler.initViews()

        Log.d("rkfgujrdjkgjk", "onCreate questionsShortEntity : ${viewModel.questionsShortEntity}")
        initSetOnClickListeners()
        Log.d("rkfgujrdjkgjk", "onCreate initSetOnClickListeners : ${viewModel.questionsShortEntity}")
        initObserversCategories()
        Log.d("rkfgujrdjkgjk", "onCreate rkfgujrdjkgjk: ${viewModel.questionsShortEntity}")
    }

    private fun setupSpinner(
        spinner: Spinner,
        textView: TextView,
        items: List<StructureDataLocal?>?,
        onItemSelected: (String) -> Unit
    ) {
        val spinnerItems = mutableListOf("Create")

        items?.let { itemList ->
            spinnerItems.addAll(itemList.map { it?.nameItem!! })
        }

        val adapter = ArrayAdapter(
            this@CreateQuizActivity,
            android.R.layout.simple_spinner_item,
            spinnerItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinner.adapter = adapter
        spinner.setSelection(spinnerItems.size - 1)

        Log.d("dawdasfesersd", "onItemSelectedListener")
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                Log.d("dawdasfesersd", "onItemSelected: $position")
                textView.text = spinnerItems[position]
                onItemSelected(spinnerItems[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun initObserversCategories() {
        var nameCat = ""
        var nameSubCat = ""
        var nameSubsubCat = ""
        lifecycleScope.launch(Dispatchers.IO) {

            viewModel.categoryDataFlow.collect { categories ->

                Log.d("rkfgujrdjkgjk", "categories: ${categories}")
                setupSpinner(binding.spCategory,binding.tvCategory, categories) {position ->
                    nameCat = position
                    if (position == "Create") {
                        binding.llCreateNewCategory.visibility = View.VISIBLE
                        viewModel.isCreateCategory = true
                        viewModel.initCategories(PathStructureName("", "", "", "", ""))
                    } else {
                        viewModel.isCreateCategory = false
                        viewModel.initCategories(PathStructureName("", position, "","",""))
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.subCategoryDataFlow.collect { subcategories ->
                Log.d("rkfgujrdjkgjk", "subcategories: ${subcategories}")
                setupSpinner(binding.spSubCategory,binding.tvSubCategory, subcategories){position ->
                    nameSubCat = position
                    if (position == "Create") {
                        binding.llCreateNewCategory.visibility = View.VISIBLE
                        viewModel.isCreateSubCategory = true
                        viewModel.initCategories(PathStructureName("",nameCat,
                            "","",""))
                    } else {
                        viewModel.isCreateSubCategory = false
                        viewModel.initCategories(PathStructureName("", nameCat, position,"",""))
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.subsubCategoryDataFlow.collect { subsubcategories ->
                Log.d("rkfgujrdjkgjk", "subsubcategories: ${subsubcategories}")
                setupSpinner(binding.spSubsubCategory, binding.tvSubsubCategory, subsubcategories){position ->
                    if (position == "Create") {
                        binding.llCreateNewCategory.visibility = View.VISIBLE
                        viewModel.isCreateSubsubCategory = true
                        viewModel.initCategories(PathStructureName("", nameCat, nameSubCat,"",""))
                    } else {
                        viewModel.isCreateSubsubCategory = false
                        viewModel.initCategories(PathStructureName("", nameCat, nameSubCat,position,""))
                    }
                }
            }
        }
    }

    internal fun setupQuestionSpinner() = with(binding) {
        Log.d("rkfgujrdjkgjk", "setupQuestionSpinner ${viewModel.questionsShortEntity}")
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

    internal fun initNewTranslateViews() {
        addQuestionToLayout("", viewModel.getUserLanguage())
        addOrUpdateAnswerGroup(viewModel.idGroup, viewModel.getUserLanguage(), false, "")
    }

    private fun initSetOnClickListeners() = with(binding) {

        bSave.setOnClickListener {
            getThisQuestionWithUI()
            getThisQuizWithUI()
            setStructureCategoryWithUI()
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
        imvFullscreen.setOnClickListener {
            Log.d("adwasdwf", "isFullscreen: $isFullscreen")
            if (isFullscreen) {
                isFullscreen = false
                showQuizUI()
                showSystemUI()
            } else {
                isFullscreen = true
                hideQuizUI()
                hideSystemUI()
            }
        }
    }

    private fun hideQuizUI() = with(binding) {
        tvQuizName.visibility = View.GONE
        imvQuiz.visibility = View.GONE
        spCategory.visibility = View.GONE
        spSubCategory.visibility = View.GONE
        spSubsubCategory.visibility = View.GONE
        stroce.visibility = View.GONE

        bCencel.visibility = View.GONE
        bSave.visibility = View.GONE

        llCreateNewCategory.visibility = View.GONE
        imvFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
    }

    private fun hideInfoQuestion() = with(binding) {
        spNumQuestion.visibility = View.GONE
        chbTypeQuestion.visibility = View.GONE
        imvFullscreen.visibility = View.GONE
        imvQuestion.visibility = View.GONE

        bAddAnswer.visibility = View.GONE
        bAddTranslate.visibility = View.GONE
        bAddGap.visibility = View.GONE

    }

    private fun showInfoQuestion() = with(binding) {
        spNumQuestion.visibility = View.VISIBLE
        chbTypeQuestion.visibility = View.VISIBLE
        imvFullscreen.visibility = View.VISIBLE
        imvQuestion.visibility = View.VISIBLE

        bAddAnswer.visibility = View.VISIBLE
        bAddTranslate.visibility = View.VISIBLE
        bAddGap.visibility = View.VISIBLE

    }

    private fun showQuizUI() = with(binding) {
        tvQuizName.visibility = View.VISIBLE
        imvQuiz.visibility = View.VISIBLE
        spCategory.visibility = View.VISIBLE
        spSubCategory.visibility = View.VISIBLE
        spSubsubCategory.visibility = View.VISIBLE
        stroce.visibility = View.VISIBLE

        bCencel.visibility = View.VISIBLE
        bSave.visibility = View.VISIBLE

        if (isCreateCategory) llCreateNewCategory.visibility = View.VISIBLE
        imvFullscreen.setImageResource(R.drawable.ic_fullscreen)
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            view.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun getThisQuizWithUI() = with(binding) {
        val pathPicture = NamesUtils().getPathPicture()
        viewModel.quizEntity = StructureDataLocal().create(
            null,
            tvQuizName.text.toString(),
            viewModel.questionsShortEntity.filter { !it.hardQuestion }.size,
            viewModel.questionsShortEntity.filter { it.hardQuestion }.size,
            viewModel.getLanguageQuizByQuestions(),
            pathPicture
        )

        viewModel.scaledANDSaveImage(imvQuiz, pathPicture)
    }

    private fun setStructureCategoryWithUI() {
        val nameCategory = NamesUtils().getPathPicture()
        val nameSubCategory = NamesUtils().getPathPicture()
        val nameSubsubCategory = NamesUtils().getPathPicture()

        viewModel.scaledANDSaveImage(binding.imvCategory, nameCategory)
        viewModel.scaledANDSaveImage(binding.imvSubcategory, nameSubCategory)
        viewModel.scaledANDSaveImage(binding.imvSubsubcategory, nameSubsubCategory)

        viewModel.categoryStructure = StructureDataLocal().create(
            null,
            getCategoriesWithLayout().first,
            0,
            0,
            viewModel.getLanguageQuizByQuestions(),
            nameCategory
        )
        viewModel.subCategoryStructure = StructureDataLocal().create(
            null,
            getCategoriesWithLayout().second,
            0,
            0,
            viewModel.getLanguageQuizByQuestions(),
            nameSubCategory
        )
        viewModel.subsubCategoryStructure = StructureDataLocal().create(
            null,
            getCategoriesWithLayout().third,
            0,
            0,
            viewModel.getLanguageQuizByQuestions(),
            nameSubsubCategory
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

    internal fun hideTopQuestionViews() = with(binding) {
        imvQuestion.visibility = View.GONE
        spNumQuestion.visibility = View.GONE
        chbTypeQuestion.visibility = View.GONE
    }

    internal fun hideQuizViews() = with(binding) {
        tvQuizName.visibility = View.GONE
        imvQuiz.visibility = View.GONE
        spCategory.visibility = View.GONE
        spSubCategory.visibility = View.GONE
        spSubsubCategory.visibility = View.GONE
        stroce.visibility = View.GONE
    }

    internal fun setupUiQuiz() = with(binding) {
        tvQuizName.setText(viewModel.quizEntity?.nameItem)

        val imagePath = viewModel.quizEntity?.picture
        if (!imagePath.isNullOrEmpty()) {
            imvQuiz.setImageURI(Uri.parse(imagePath))
        }
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
        Log.d("rkfgujrdjkgjk", "questionsShortEntity: ${viewModel.questionsShortEntity}")
        Log.d("rkfgujrdjkgjk", "counter: ${viewModel.counter}")
        Log.d("rkfgujrdjkgjk", "questionsShortEntity[counter]: ${viewModel.questionsShortEntity[viewModel.counter]}")
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
                            answer.second,
                            hardQuestionThis,
                            viewModel.pathStructure.idEvent,
                            viewModel.pathStructure.idCategory,
                            viewModel.pathStructure.idSubCategory,
                            viewModel.pathStructure.idSubsubCategory,
                            viewModel.pathStructure.idQuiz,
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

    internal fun updateUiQuestion() = with(binding) {
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
            addQuestionToLayout(question.nameQuestion, question.language)

            val answers = question.nameAnswers.split("|").toMutableList()
            answers.add(0, answers.removeAt(0))

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
        handler.saveData()
        finish()
    }

    companion object {
        private const val ARG_PATH_STRUCTURE = "arg_path_structure"
        private const val REGIME = "arg_regime"

        const val REGIME_CREATE_QUIZ = 1
        const val REGIME_EDIT_QUIZ = 2
        const val REGIME_EDIT_ARCHIVE_MY_QUIZ = 3
        const val REGIME_EDIT_ARCHIVE_QUIZ = 4
        const val REGIME_TRANSLATE_QUIZ = 5

        fun newInstance(
            context: Context,
            pathStructure: PathStructure,
            regime: Int
        ): Intent {
            Log.d("sdfesfes", "setRegime: $regime")
            return Intent(context, CreateQuizActivity::class.java).apply {
                putExtra(REGIME, regime)
                putExtra(ARG_PATH_STRUCTURE, pathStructure)
            }
        }
    }
}

