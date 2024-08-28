package com.tpov.schoolquiz.presentation.create_quiz

import android.R
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.schoolquiz.databinding.FragmentCreateQuizBinding
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.main.MainViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: MainViewModel
    private var idQuiz = -1
    private var lvlTranslate = 0

    private val binding get() = _binding!!
    private var _binding: FragmentCreateQuizBinding? = null

    private var questionsEntity: ArrayList<QuestionEntity>? = null
    private var quizEntity: QuizEntity? = null

    private var numQuestion = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            idQuiz = it.getInt(ARG_QUIZ)
            viewModel.getQuizByIdQuiz(idQuiz)
            viewModel.getQuestionByIdQuiz(idQuiz)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initSetOnClickListeners()

    }

    private fun setupQuestionSpinner() = with(binding) {
        val questionForSpinner = questionsEntity
            ?.filter { it.language == getUserLanguage() }
            ?.sortedBy { it.numQuestion }?.sortedBy { !it.hardQuestion }

        val adapter = questionForSpinner?.let {
            CustomSpinnerAdapter(root.context, it)
        }

        spNumQuestion.adapter = adapter
        spNumQuestion.setSelection(0)
        spNumQuestion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                saveThisNumberQuestion()
                updateUiQuestion(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun initSetOnClickListeners() = with(binding) {
        setupQuestionSpinner()
        binding.bSave.setOnClickListener {
            saveThisNumberQuestion()
            saveQuiz()
        }
        setCategorySpinnerListeners()

    }

    private fun getUserLanguage(): String {
        return ""
    }

    private fun initView() {
        if (idQuiz != -1) {
            lifecycleScope.launch { viewModel.quizData.collect { it.let { updateUiQuiz(it!!) } } }
            lifecycleScope.launch {
                viewModel.questionData.collect {
                    it.let {
                        questionsEntity = (it as ArrayList<QuestionEntity>?)!!
                        updateUiQuestion(1)
                    }
                }
            }
        }
    }

    private fun updateUiQuiz(quiz: QuizEntity) = with(binding) {
        tvQuizName.setText(quiz.nameQuiz)

        val imagePath = quiz.picture
        if (!imagePath.isNullOrEmpty()) {
            imvQuiz.setImageURI(Uri.parse(imagePath))
        }


    }

    private fun setSpinnersCategory(category: String, subCategory: String, subSubCategory: String) {
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
                                    if (subSubCat.nameQuiz == subSubCategory) {
                                        if (!subSubCat.picture.isNullOrEmpty()) {
                                            binding.imvSubcategory.setImageURI(Uri.parse(subSubCat.picture))
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
            binding.tvSubsubCategory.setText(subSubCategory)

            // Устанавливаем адаптеры для спиннеров
            val categoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, categories)
            categoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spCategory.adapter = categoryAdapter

            val subCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subcategories)
            subCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubCategory.adapter = subCategoryAdapter

            val subSubCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subSubcategories)
            subSubCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
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
                    loadSubCategories(selectedCategory)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Слушатель для спиннера подкатегорий
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
                    loadSubSubCategories(selectedSubCategory)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Слушатель для спиннера под-субкатегорий
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
                        // Обработка выбора под-субкатегории
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun loadSubCategories(category: String) {
        lifecycleScope.launch {
            val structureData = viewModel.getStructureData()

            val subcategories = mutableListOf("Создать новую подкатегорию")

            structureData?.event?.forEach { event ->
                event.category.firstOrNull { it.nameQuiz == category }?.subcategory?.forEach {
                    subcategories.add(it.nameQuiz)
                }
            }

            val subCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subcategories)
            subCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubCategory.adapter = subCategoryAdapter
        }
    }

    private fun loadSubSubCategories(subCategory: String) {
        lifecycleScope.launch {
            val structureData = viewModel.getStructureData()

            val subSubcategories = mutableListOf("Создать новую под-субкатегорию")

            structureData?.event?.forEach { event ->
                event.category.forEach { category ->
                    category.subcategory.firstOrNull { it.nameQuiz == subCategory }?.subSubcategory?.forEach {
                        subSubcategories.add(it.nameQuiz)
                    }
                }
            }

            val subSubCategoryAdapter =
                ArrayAdapter(requireContext(), R.layout.simple_spinner_item, subSubcategories)
            subSubCategoryAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            binding.spSubsubCategory.adapter = subSubCategoryAdapter
        }
    }

    private fun createNewCategory() {
        // Логика создания новой категории
    }

    private fun createNewSubCategory() {
        // Логика создания новой подкатегории
    }

    private fun createNewSubSubCategory() {
        // Логика создания новой под-субкатегории
    }

    private fun addQuestionToLayout(questionText: String? = null, language: String? = null) {
        val questionLayout = LayoutInflater.from(context).inflate(
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
            view?.context!!,
            R.layout.simple_spinner_item,
            languagesFullNames
        )
        languagesAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
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

    //Удаляем в вопросах тот номер вопроса который отабрежен, затем считываем отобреженный номер вопроса и добавляем в список вопросов
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

            val language = LanguageUtils.getLanguageShortCode(answerLanguageTextView.text.toString())

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

            for (j in 0 until answerLayout.childCount) { // Проходим по всем дочерним элементам
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
    private fun updateUiQuestion(numQuestion: Int) = with(binding) {
        val questionEntitiesLanguage = questionsEntity?.filter {
            it.numQuestion == numQuestion && it.hardQuestion == (numQuestion < 0)
        }

        val imagePath = questionEntitiesLanguage?.get(0)?.pathPictureQuestion
        if (!imagePath.isNullOrEmpty()) imvQuestion.setImageURI(Uri.parse(imagePath))
        else imvQuestion.setImageResource(com.tpov.schoolquiz.R.drawable.ic_upload)

        chbTypeQuestion.isChecked = numQuestion < 0

        questionEntitiesLanguage?.forEachIndexed { indexLanguage, question ->
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
                val inflater = LayoutInflater.from(requireContext())
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
                        requireContext(),
                        android.R.color.holo_green_light
                    )
                )
            } else {
                existingGroup.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        android.R.color.transparent
                    )
                )
            }
        } else {
            val inflater = LayoutInflater.from(requireContext())
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
                        requireContext(),
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
        // Ваша логика определения правильного ответа
        return 0
    }

    private fun saveQuiz() = lifecycleScope.launch {
        quizEntity?.let { //then create quiz, else translate quiz
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            idQuiz: Int
        ): CreateQuizFragment {
            val fragment = CreateQuizFragment()
            val args = Bundle()
            args.putInt(ARG_QUIZ, idQuiz)
            fragment.arguments = args
            return fragment
        }
    }
}